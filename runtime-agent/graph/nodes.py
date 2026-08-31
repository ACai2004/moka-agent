"""LangGraph 节点实现。

每个节点是 Agent 决策循环中的一步。
- intent_detection: 调用 DeepSeek 理解用户意图（结构化输出）
- tool_execution: 调用 Java Tool API（当前为 Mock，Phase 2 接真实 API）
- response_generation: 调用 DeepSeek 生成自然回复

所有 LLM 调用都有关键词规则作为回退方案，API 不可用时降级运行。
"""

import json
from pathlib import Path
from typing import Any, Dict, Optional

from langchain_openai import ChatOpenAI
from pydantic import BaseModel, Field, ValidationError

from config import DEEPSEEK_API_KEY, DEEPSEEK_BASE_URL, DEEPSEEK_MODEL
from .state import AgentState


# ============================================================
# 结构化输出模型
# ============================================================

class IntentOutput(BaseModel):
    """intent_detection 节点的结构化输出。"""
    current_topic: str = Field(
        description="当前话题: greeting/dining_scene/dish/environment/service/"
                    "restaurant_info/recommendation/personal_story/"
                    "weather_chitchat/parting/other"
    )
    user_engagement: str = Field(
        description="用户参与度: high(主动分享细节) / medium(简短回应) / low(无兴趣)"
    )
    user_sentiment: Optional[str] = Field(
        description="用户情绪: positive(积极) / neutral(中性) / negative(消极) / mixed(复杂)",
        default=None,
    )
    mentioned_dishes: list[str] = Field(
        description="用户明确提到的菜品名称（未提到则为空列表）",
        default=[],
    )
    need_tool: bool = Field(
        description="是否需要调用外部工具来获取信息",
        default=False,
    )


# ============================================================
# LLM 工具函数
# ============================================================

def _build_llm(temperature: float = 0.0) -> Optional[ChatOpenAI]:
    """构建 DeepSeek LLM 实例。API Key 为空时返回 None。"""
    if not DEEPSEEK_API_KEY:
        return None
    return ChatOpenAI(
        api_key=DEEPSEEK_API_KEY,
        base_url=DEEPSEEK_BASE_URL,
        model=DEEPSEEK_MODEL,
        temperature=temperature,
    )


# ============================================================
# 回退方案：关键词规则
# ============================================================

KEYWORD_TOOL_TRIGGERS = [
    "天气", "闷", "热", "冷", "下雨", "雨",
    "粉", "菜", "饭", "汤", "面", "味道",
    "推荐", "店", "分店", "其他", "还有",
]


def _keyword_intent_detection(user_message: str) -> Dict[str, Any]:
    """关键词规则判断意图（LLM 不可用时回退）。"""
    msg = user_message

    if any(kw in msg for kw in ["闷", "天气", "热", "冷", "下雨", "雨"]):
        topic = "weather_chitchat"
    elif any(kw in msg for kw in ["粉", "菜", "饭", "汤", "面", "味道"]):
        topic = "dish"
    elif any(kw in msg for kw in ["店", "分店", "推荐", "还有"]):
        topic = "restaurant_info"
    elif any(kw in msg for kw in ["开心", "不错", "好", "满意"]):
        topic = "dining_scene"
    else:
        topic = "other"

    need_tool = any(kw in msg for kw in KEYWORD_TOOL_TRIGGERS)

    return {
        "current_topic": topic,
        "user_engagement": "medium",
        "user_sentiment": "positive" if topic in ("dining_scene", "dish") else None,
        "mentioned_dishes": [],
        "need_tool": need_tool,
    }


def _keyword_response_generation(user_message: str, state: AgentState) -> str:
    """关键词规则生成回复（LLM 不可用时回退）。"""
    if "闷" in user_message:
        return "今天确实挺闷的，湿度比较大。您刚才出门的时候感觉怎么样？"
    elif "汤" in user_message:
        return "你说那个汤啊，据说他们家用牛骨熬了很久，所以味道特别浓。您觉得喝着怎么样？"
    elif "开心" in user_message or "不错" in user_message:
        return "那就好！听您这么说，看来今天这顿饭整体体验挺不错的。"
    elif "店" in user_message:
        return "他们家在三里屯还有国贸都有店，您要是喜欢可以试试其他分店。"
    else:
        plan = state.get("pre_call_context", {}).get("plan", {})
        hooks = plan.get("availableHooks", [])
        if hooks:
            return f"对了，刚才说到{hooks[0]}，您感觉怎么样？"
        return "原来是这样，您继续说，我在听。"


# ============================================================
# Prompt 模板
# ============================================================

INTENT_SYSTEM_PROMPT = """你是一个餐后回访对话的分析助手。
分析用户的最近一条消息，输出结构化的意图信息。

当前话题类型说明：
- greeting: 问候/开场白
- dining_scene: 整体用餐场景、离店感受
- dish: 具体菜品口味、做法、评价
- environment: 餐厅环境、氛围
- service: 餐厅服务
- restaurant_info: 餐厅信息、其他门店、推荐
- recommendation: 寻求推荐
- personal_story: 个人经历、故事
- weather_chitchat: 天气闲聊
- parting: 准备结束对话
- other: 其他

判断 need_tool 的标准（需要外部信息才能回答时设为 true）：
- 提到天气、体感 → 可能需要天气信息
- 询问菜品详情、做法 → 可能需要菜品知识
- 询问餐厅其他信息 → 可能需要餐厅资料
- 纯感受分享、闲聊 → false

请严格输出 JSON 格式，不要包含 markdown 代码块标记，不要额外文本：
{"current_topic": "分类结果", "user_engagement": "high/medium/low", "user_sentiment": "positive/neutral/negative/mixed 或 null", "mentioned_dishes": ["菜品1"], "need_tool": true/false}"""

# ============================================================
# 回复生成的 System Prompt：静态角色 + 动态上下文
# ============================================================

# Static System Prompt 文件路径（runtime-agent/static_system_prompt.md）
_STATIC_PROMPT_PATH = Path(__file__).resolve().parent.parent / "static_system_prompt.md"

# 内置简化版（Static System Prompt 文件加载失败时的回退）
_FALLBACK_STATIC_PROMPT = """你是一个餐后回访电话的 AI 助手，正在和顾客进行自然的餐后交流。

角色要求：
- 你不是问卷，不是调研员
- 你的回复要像真实电话里的自然对话，不书面化
- 根据用户情绪调整语气：积极时多聊，平淡时轻收
- 一次只聊一个点，不问多个问题
- 不超过 3 句话"""


def _load_static_prompt() -> str:
    """加载 Static System Prompt 文件（角色定义 + 行为规则）。"""
    try:
        with open(_STATIC_PROMPT_PATH, "r", encoding="utf-8") as f:
            content = f.read().strip()
        if content:
            return content
    except Exception as e:
        print(f"[response_generation] 加载 static_system_prompt.md 失败: {e}")
    return _FALLBACK_STATIC_PROMPT


# 模块加载时读取一次（静态内容不随对话变化）
STATIC_SYSTEM_PROMPT = _load_static_prompt()

# 动态上下文模板（拼在静态 prompt 后面）
DYNAMIC_CONTEXT_TEMPLATE = """

---

以下是本次通话的动态信息：

当前背景信息：
{context}

最近对话：
{history}

工具查询结果：
{tool_results}

当前话题：{topic}
用户情绪：{sentiment}
用户参与度：{engagement}"""

# 回复生成的结构化输出格式（让 LLM 同时输出回复文本 + 是否结束通话）
RESPONSE_OUTPUT_FORMAT = """

---

请以 JSON 格式输出，只输出 JSON，不要 markdown 代码块，不要多余文本：
{"response": "你的自然回复", "end_call": true 或 false}

end_call 判断规则（非常重要）：
- 如果你的回复是在向用户告别 / 收尾（如"那今天就聊到这儿""谢谢您分享，不打扰您了""再见"），
  则必须设 end_call=true
- 只有在自然收尾时才设 true；还在兴头上 / 还在深入聊时设 false
- 不要过早结束：用户还在分享时，即使你礼貌性收尾，也不要设 true"""


def _parse_response_json(text: str):
    """解析回复生成的结构化输出 {response, end_call}。失败返回 None。"""
    text = text.strip()
    # 清理可能的 markdown 代码块
    if text.startswith("```"):
        lines = text.split("\n")
        if len(lines) > 1:
            text = "\n".join(lines[1:-1]).strip()
    try:
        data = json.loads(text)
        if isinstance(data, dict) and "response" in data:
            return data
    except json.JSONDecodeError:
        pass
    return None


# ============================================================
# 节点 1：意图识别
# ============================================================

def _parse_intent_json(text: str) -> Optional[IntentOutput]:
    """从 LLM 响应文本中解析 JSON 并验证为 IntentOutput。"""
    text = text.strip()
    # 清理可能的 markdown 代码块标记
    if text.startswith("```"):
        lines = text.split("\n")
        start = 1
        end = -1
        if len(lines) > 1:
            text = "\n".join(lines[start:end]).strip()

    try:
        parsed = json.loads(text)
        return IntentOutput(**parsed)
    except (json.JSONDecodeError, ValidationError) as e:
        print(f"[intent_detection] JSON 解析失败: {e}")
        return None


def intent_detection(state: AgentState) -> Dict[str, Any]:
    """理解用户当前意图，输出 topic / engagement / sentiment / 工具需求。

    优先调用 DeepSeek 进行结构化分析（Prompt + JSON 解析）；
    API 不可用或解析失败时回退到关键词规则。
    所有输出字段在一次 LLM 调用中完成，不拆分独立节点以控制延迟。
    """
    user_message = state["messages"][-1]["content"] if state["messages"] else ""
    if not user_message:
        return _keyword_intent_detection("")

    # 尝试 LLM 方式
    llm = _build_llm(temperature=0.0)
    if llm is not None:
        try:
            result = llm.invoke([
                {"role": "system", "content": INTENT_SYSTEM_PROMPT},
                {"role": "user", "content": f"用户消息：{user_message}"},
            ])
            validated = _parse_intent_json(result.content)
            if validated is not None:
                print(f"[intent_detection] LLM 结果: topic={validated.current_topic}, "
                      f"engagement={validated.user_engagement}, "
                      f"sentiment={validated.user_sentiment}, "
                      f"need_tool={validated.need_tool}")
                return {
                    "current_topic": validated.current_topic,
                    "user_engagement": validated.user_engagement,
                    "user_sentiment": validated.user_sentiment,
                    "mentioned_dishes": validated.mentioned_dishes,
                    "need_tool": validated.need_tool,
                }
        except Exception as e:
            print(f"[intent_detection] LLM 调用失败，回退关键词规则: {e}")

    # 回退到关键词规则
    return _keyword_intent_detection(user_message)


# ============================================================
# 节点 2：工具执行
# ============================================================

def _extract_dish_name(user_message: str, state: AgentState) -> str:
    """从用户消息中提取菜品名。

    优先用 Context 中的已知菜品名做模糊匹配，再回退到关键词提取。
    """
    # 收集 Context 中的已知菜品名
    known_dishes = []
    for d in state.get("pre_call_context", {}).get("dishes", []):
        name = d.get("dishName", "")
        if name:
            known_dishes.append(name)

    # 1. 精确匹配（使用全名）：用户消息直接包含完整菜品名
    for name in known_dishes:
        if name in user_message:
            return name

    # 2. 模糊匹配（使用核心名）：去掉括号备注后匹配
    for name in known_dishes:
        core = name.split("（")[0].split("(")[0]
        if core and core in user_message:
            return name

    # 3. 关键词提取后，尝试匹配已知菜品
    for kw in ["那个", "这道", "这个", "我点的"]:
        idx = user_message.find(kw)
        if idx >= 0:
            candidate = user_message[idx + len(kw):].strip()
            for sep in ["为什么", "怎么", "的", "，", "。", "?"]:
                end = candidate.find(sep)
                if end > 0:
                    candidate = candidate[:end]
            candidate = candidate.strip()
            if candidate:
                # 用提取到的关键词在已知菜品中匹配
                for name in known_dishes:
                    if candidate in name or name.startswith(candidate):
                        return name
                # 实在匹配不上，返回关键词本身
                return candidate

    # 4. 兜底：返回第一个已知菜品名
    if known_dishes:
        return known_dishes[0]
    return ""


def tool_execution(state: AgentState) -> Dict[str, Any]:
    """执行工具调用（调 Java Tool API）。

    根据 intent_detection 标记的 current_topic 决定调用哪个工具。
    Java Tool API 不可用时回退到 mock 数据。
    """
    from tools.java_client import tool_client

    user_message = state["messages"][-1]["content"] if state["messages"] else ""
    topic = state.get("current_topic", "other")

    tool_calls = list(state.get("tool_calls", []))

    result_data = ""
    tool_name = f"mock_{topic}"

    try:
        if topic == "weather_chitchat":
            tool_name = "weather"
            resp = tool_client.get_weather(district="朝阳区", city="北京")
            if resp.get("status") == "success" and resp.get("data"):
                result_data = str(resp["data"])
            else:
                result_data = _mock_tool_result(topic)

        elif topic == "dish":
            tool_name = "dish_search"
            dish_name = _extract_dish_name(user_message, state)
            if dish_name:
                resp = tool_client.search_dish(name=dish_name)
                if resp.get("status") == "success" and resp.get("data"):
                    dishes = resp["data"]
                    if dishes:
                        features = dishes[0].get("features", [])
                        result_data = f"{dish_name}：{'、'.join(features[:3])}"
                    else:
                        result_data = _mock_tool_result(topic)
                else:
                    result_data = _mock_tool_result(topic)
            else:
                result_data = _mock_tool_result(topic)

        elif topic == "restaurant_info":
            tool_name = "restaurant"
            # 从 pre_call_context 取餐厅名
            ctx = state.get("pre_call_context", {})
            order = ctx.get("order", {})
            restaurant_name = order.get("restaurant", "売泰")
            resp = tool_client.get_restaurant(name=restaurant_name)
            if resp.get("status") == "success" and resp.get("data"):
                profile = resp["data"]
                addr = profile.get("address", "")
                pos = profile.get("positioning", "")
                parts = [f"餐厅：{restaurant_name}"]
                if addr:
                    parts.append(f"地址：{addr}")
                if pos:
                    parts.append(f"定位：{pos}")
                result_data = "，".join(parts)
            else:
                result_data = _mock_tool_result(topic)

        else:
            result_data = _mock_tool_result(topic)

    except Exception as e:
        print(f"[tool_execution] Java API 调用失败，回退 mock: {e}")
        result_data = _mock_tool_result(topic)

    tool_calls.append({
        "tool": tool_name,
        "params": {"topic": topic, "user_message": user_message},
        "result": result_data,
        "timestamp": "2026-07-27T00:00:00Z",
    })

    return {"tool_calls": tool_calls}


def _mock_tool_result(topic: str) -> str:
    """返回占位的工具调用结果。"""
    mock_results = {
        "weather_chitchat": "北京 朝阳区：多云 31°C，西南风 3 级，体感较闷",
        "dish": "牛肉船粉：经典泰式船粉，汤底用牛骨熬制 8 小时，浓郁醇厚",
        "restaurant_info": "売泰（三里屯店），另有国贸分店",
        "dining_scene": "",
    }
    return mock_results.get(topic, "")


# ============================================================
# 节点 3：回复生成
# ============================================================

def _build_context_summary(state: AgentState) -> str:
    """从 pre_call_context 中提取关键信息，供 LLM 生成回复。

    覆盖三层完整信息：
    - Layer 1 事实：order / dishes / restaurant_profile / realtime
    - Layer 2 体验：experience（标注"推测"/"参考"，防止当成事实）
    - Layer 3 策略：plan（方向/机会点/限制）
    """
    ctx = state.get("pre_call_context", {})
    parts = []

    # ---- Layer 1: 订单 ----
    order = ctx.get("order", {})
    if order:
        parts.append(f"餐厅：{order.get('restaurant', '未知')}")
        parts.append(f"时间：{order.get('time', '未知')}")
        parts.append(f"人数：{order.get('people', '未知')}人")

    # ---- Layer 1: 菜品知识 ----
    dishes = ctx.get("dishes", [])
    if dishes:
        dish_names = [d.get("dishName", "") for d in dishes if d.get("dishName")]
        if dish_names:
            parts.append(f"已点菜品：{'、'.join(dish_names)}")

    # ---- Layer 1: 餐厅资料 ----
    restaurant = ctx.get("restaurant_profile", {})
    if restaurant:
        if restaurant.get("positioning"):
            parts.append(f"餐厅定位：{restaurant['positioning']}")
        env = restaurant.get("environmentFeatures") or []
        if env:
            parts.append(f"餐厅环境：{'、'.join(env)}")
        service = restaurant.get("serviceFeatures") or []
        if service:
            parts.append(f"餐厅服务：{'、'.join(service)}")

    # ---- Layer 1: 实时信息 ----
    realtime = ctx.get("realtime", {})
    weather_items = []
    if realtime.get("weather"):
        weather_items.append(f"天气：{realtime['weather']}")
    if realtime.get("holiday"):
        weather_items.append(f"节日：{realtime['holiday']}")
    if weather_items:
        parts.append("；".join(weather_items))

    # ---- Layer 2: 体验理解（标注推测/参考）----
    experience = ctx.get("experience", {})
    possibilities = experience.get("possibilities", [])
    if possibilities:
        exp_lines = []
        for p in possibilities:
            level = p.get("confidenceLevel", "LOW")
            prefix = "推测" if level == "MEDIUM" else "参考"
            desc = p.get("description", "")
            evidence = p.get("evidenceSource", "")
            line = f"- {prefix}：{desc}"
            if evidence:
                line += f"（依据：{evidence}）"
            exp_lines.append(line)
        parts.append("体验理解：\n" + "\n".join(exp_lines))

    # ---- Layer 3: 对话策略 ----
    plan = ctx.get("plan", {})
    plan_parts = []
    directions = plan.get("directions") or []
    if directions:
        plan_parts.append("方向：" + "；".join(directions))
    hooks = plan.get("availableHooks") or []
    if hooks:
        plan_parts.append("机会点：" + "；".join(hooks))
    avoids = plan.get("avoid") or []
    if avoids:
        plan_parts.append("限制：" + "；".join(avoids))
    if plan_parts:
        parts.append("对话策略：\n" + "\n".join(plan_parts))

    return "\n".join(parts) if parts else "暂无背景信息"


def _build_history_text(state: AgentState) -> str:
    """提取最近几轮的对话历史（最多保留最近 4 条）。"""
    messages = state.get("messages", [])
    if not messages:
        return "暂无对话"

    recent = messages[-4:]
    lines = []
    for msg in recent:
        role = "用户" if msg.get("role") == "user" else "AI"
        lines.append(f"{role}：{msg['content']}")
    return "\n".join(lines)


def response_generation(state: AgentState) -> Dict[str, Any]:
    """根据当前状态生成最终回复文本。

    优先调用 DeepSeek 生成自然回复，API 不可用时回退到关键词规则。
    将 pre_call_context + 对话历史 + 工具结果 拼入 prompt。
    """
    user_message = state["messages"][-1]["content"] if state["messages"] else ""
    if not user_message:
        return {"response": ""}

    topic = state.get("current_topic", "other")
    tool_calls = state.get("tool_calls", [])

    # 拼接 prompt 上下文
    context = _build_context_summary(state)
    history = _build_history_text(state)

    # 工具结果
    tool_results = "无工具调用"
    if tool_calls:
        last_tool = tool_calls[-1]
        result_text = last_tool.get("result", "")
        if result_text:
            tool_results = f"工具「{last_tool.get('tool', '未知')}」返回：{result_text}"

    sentiment = state.get("user_sentiment") or "未知"
    engagement = state.get("user_engagement", "medium")

    prompt = STATIC_SYSTEM_PROMPT + DYNAMIC_CONTEXT_TEMPLATE.format(
        context=context,
        history=history,
        tool_results=tool_results,
        topic=topic,
        sentiment=sentiment,
        engagement=engagement,
    )

    # 强制收尾提示（由外层护栏触发：达到最大轮数）
    force_end = state.get("force_end", False)
    if force_end:
        prompt += "\n\n注意：这是本次通话的最后一句，请用自然的告别语收尾，并设 end_call=true。"

    prompt += RESPONSE_OUTPUT_FORMAT

    # 尝试 LLM 方式
    llm = _build_llm(temperature=0.7)
    if llm is not None:
        try:
            result = llm.invoke([
                {"role": "system", "content": prompt},
                {"role": "user", "content": user_message},
            ])
            text = result.content.strip()
            parsed = _parse_response_json(text)
            if parsed:
                response_text = str(parsed.get("response", "")).strip()
                end_call = bool(parsed.get("end_call", False))
                # 强制收尾时兜底：确保 end_call=true
                if force_end:
                    end_call = True
                if not response_text:
                    response_text = text
                print(f"[response_generation] LLM 回复: {response_text[:60]}... end_call={end_call}")
                return {"response": response_text, "end_call": end_call}
            # JSON 解析失败，把原文当回复
            print(f"[response_generation] JSON 解析失败，用原文: {text[:60]}...")
            return {"response": text, "end_call": force_end}
        except Exception as e:
            print(f"[response_generation] LLM 调用失败，回退关键词: {e}")

    # 回退到关键词规则
    return {"response": _keyword_response_generation(user_message, state), "end_call": force_end}
