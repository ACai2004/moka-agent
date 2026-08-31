"""Agent State 定义。

State 字段 = Agent 的认知维度。
每个字段都是 Agent 每轮对话中需要感知或记住的信息。
"""

from typing import TypedDict, Optional, List, Dict, Any


class AgentState(TypedDict):
    # ========== 不变部分（通话期间不修改） ==========

    # Java 传来的完整 Pre-call Context
    pre_call_context: Dict[str, Any]

    # ========== 对话历史 ==========

    # [{role, content}, ...]，不断增长
    messages: List[Dict[str, str]]

    # ========== 当前状态（每轮更新） ==========

    # 当前话题 —— 用于条件路由
    # 枚举: greeting / dining_scene / dish / environment / service /
    #       restaurant_info / recommendation / personal_story /
    #       weather_chitchat / parting / other
    current_topic: str

    # 用户提到过的菜品名列表
    mentioned_dishes: List[str]

    # 用户参与度 —— 控制深入还是收尾
    user_engagement: str  # high / medium / low

    # 用户情绪倾向 —— 影响回复语气
    user_sentiment: Optional[str]  # positive / neutral / negative / mixed

    # ========== 内部路由标记（intent_detection → edges 传递） ==========

    # intent_detection 标记本轮是否需要调用工具
    need_tool: bool

    # 外层护栏触发：本轮强制收尾（达到最大轮数时置 True）
    force_end: bool = False

    # ========== 工具调用记录 ==========

    # [{tool, params, result, timestamp}]
    tool_calls: List[Dict[str, Any]]

    # ========== 本轮输出 ==========

    # Agent 本轮回复文本
    response: Optional[str]

    # 本轮是否应该结束通话（AI 判断 + 护栏兜底）
    end_call: bool = False
