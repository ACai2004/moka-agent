那我# 摩卡餐后漫谈 AI Voice Agent 系统 — 架构设计文档

> 本文档整合了系统设计讨论的全部结论，作为后续工程实现的唯一参考依据。

---

## 1. 项目定位与核心理念

### 1.1 目标

构建一个面向多餐厅场景的 AI 餐后漫谈 Voice Agent 系统。用户在餐厅离店后，通过 AI 电话与用户进行自然交流，帮助用户回味本次用餐体验。

### 1.2 核心理念转变

| 传统反馈机器人 | 本系统 |
|---|---|
| 收集用户反馈 | 理解用户体验，陪伴回味 |
| 问卷式询问（菜好吃吗？满意吗？） | 像了解场景的伙伴自然交流 |
| 追求数据回收率 | 追求体验被认真倾听的感受 |
| 标准化问题模板 | 个性化聊天上下文 |

### 1.3 核心原则

- **不扮演问卷**：AI 不是一个调查满意度的问题列表
- **不假设用户感受**：所有推理保持不确定性，输出可能性而非结论
- **不替用户思考**：Context 帮助 AI 理解场景，而不是替 AI 写好台词
- **尊重用户节奏**：话题推进以用户反应为驱动，不是按预设脚本执行

---

## 2. 系统架构与边界

### 2.1 整体架构

```
业务后端 (Spring Boot)
     │
     ▼
AI Context Preparation Layer
     │
     ▼
Pre-call Context (Runtime Prompt)
     │
     ▼
火山引擎 Voice Agent (ASR / TTS / 电话链路 / 实时对话)
     │
     ▼
实时电话交流
```

### 2.2 系统边界（非常重要）

| 本项目负责 | 火山引擎负责 | 本项目不实现 |
|---|---|---|
| 通话前数据准备 | ASR（语音识别） | 语音识别引擎 |
| 用户/餐厅业务数据管理 | TTS（语音合成） | 语音合成引擎 |
| AI Context 生成 | 实时电话连接 | 电话信令系统 |
| Agent Workflow 编排 | 实时语音对话能力 | Voice Runtime |
| RAG / Tool Calling | Voice Agent 运行环境 | 语音模型 |
| Prompt Assembly | — | — |

### 2.3 核心架构决策：通话前处理 / 通话中低延迟

```
┌─────────────────────┐      ┌──────────────────────┐
│   通话前复杂处理      │      │   通话中低延迟交流     │
│                     │      │                      │
│  · 数据库查询        │      │  · 基于已有 Context   │
│  · RAG 检索          │  ──► │    进行自然对话       │
│  · 多步推理          │      │  · 不需要实时查库     │
│  · Context 组装      │      │  · 不需要复杂推理     │
│  · Prompt 合并       │      │                      │
└─────────────────────┘      └──────────────────────┘
```

---

## 3. Prompt 体系设计（核心设计决策）

### 3.1 工程约束

火山引擎 StartVoiceChat 接口的 `LLMConfig.SystemMessages` 支持传入多条系统提示词（`string[]`），且整个通话期间持续有效、不会被逐出。`UserPrompts` 则存在自动逐出机制——超出 `HistoryLength` 轮数后最早的消息会被移除。

因此，虽然技术上可将 System Prompt 和 Dynamic Context 分别放入不同的 `SystemMessages` 条目，但考虑到 MVP 阶段以尽快跑通为目标，仍采用合并方案：

**MVP 方案（方案 B）**：Pre-call Context 动态生成 + System Prompt 合并为最终 Runtime Prompt，整体传给 Voice Agent 的 `SystemMessages`。

> **未来升级路径（第 10.3 节）**：当需要独立更新各部分时，可演进为：
> ```
> SystemMessages[0] = Static System Prompt
> SystemMessages[1] = 三层 Dynamic Context（预组装为文本）
> ```
> 核心设计不变，只改变传入方式。

### 3.2 逻辑分层（必须保持）

虽然最终传给 Voice Agent 的是一个完整 Prompt，但内部逻辑必须保持分层：

```
Runtime Prompt = Static System Prompt + Dynamic Pre-call Context
```

### 3.3 Static System Prompt（稳定能力层）

**职责**：AI 是谁，以及应该如何交流。

内容覆盖：
- 角色定义（餐后体验回访 AI，不是问卷）
- 对话目标（帮助回味，不是收集反馈）
- 用户尊崇感原则
- 时间线推进原则
- 自然交流原则
- 情绪同步规则
- 话题退出机制
- 禁止行为
- 对话风格

**这些内容不因不同用户、不同订单而变化。**

### 3.4 Dynamic Pre-call Context（动态上下文层）

**职责**：这次电话发生的具体场景。

Dynamic Context **不负责定义 AI 行为**，而是提供当前事实、场景理解和交流方向。

---

## 4. Dynamic Context 三层结构

```
┌─────────────────────────────────────────────┐
│  Layer 3: Conversation Planner              │
│  (方向 / 机会点 / 限制 — 如何使用信息)        │
├─────────────────────────────────────────────┤
│  Layer 2: Experience Understanding          │
│  (事实 → 体验可能性 — 保持低确定性)           │
├─────────────────────────────────────────────┤
│  Layer 1: Raw Facts                         │
│  (订单 / 餐厅 / 菜品 / 实时信息 — 只描述事实) │
└─────────────────────────────────────────────┘
```

### 4.1 Layer 1: Raw Facts（原始事实层）

**来源**：用户上传订单照片、餐厅数据库、菜品知识库、实时信息服务。

**原则**：只描述事实，不做推理。

示例输出：
```
餐厅：川·隐味小馆
时间：周五 19:20
人数：3 人
用餐时长：2 小时 15 分钟
菜品：
  · 麻婆豆腐 ×1
  · 打抛饭 ×1（辣度：不可免辣，备注：加牛肉）
  · 冰柠檬水 ×2
当日天气：大雨
临近节日：端午节
```

> **注意**：Raw Facts 中的菜品信息保留了原始小票的细节（数量、辣度要求、备注等），这些细节是自然聊天的黄金素材。但 AI 不应主动逐项覆盖，具体使用方式由 Conversation Planner 控制（见第 5.2 节）。

### 4.2 Layer 2: Experience Understanding（体验理解层）

**作用**：将事实转换为对用户体验场景的理解可能性。

**关键约束**：
- 输出的是「可能性」，不是「结论」
- 必须保持低确定性
- 禁止将推测当事实

示例：
```
✓ 可能存在多人聚餐场景，用户可能更关注整体氛围
✓ 当天是端午节，这顿饭可能有节日聚餐的背景
✗ 用户今天一定是和朋友聚餐
✗ 用户一定很开心
```

### 4.3 Layer 3: Conversation Planner（对话规划层）

**作用**：告诉 Voice Agent 如何使用前两层的信息。

**这是整个系统的核心模块** — 它完成从「数据」到「策略」的转换。

#### 输出结构

输出包含三类内容，**不是聊天脚本**：

| 输出类型 | 说明 | 示例 |
|---|---|---|
| Direction（方向） | 当前适合关注什么 | 优先从整体离店感受切入，不要直接进入菜品评价 |
| Available Hooks（机会点） | 可以自然利用的话题 | 大雨天气、周五晚上、较长用餐时间、多人用餐 |
| Avoid（限制） | 应避免的方向 | 不假设同行关系、不主动评价菜品、不逐菜询问 |

**菜品角色（dishRole）对对话策略的影响**：

Planner 在生成方向、机会点、限制时，应考虑每道菜的 dishRole：

| dishRole | 策略 |
|---|---|
| `SIGNATURE` + `MAIN` | 用户表现出兴趣时可以深入聊 |
| `STAPLE` + `DESSERT` | 自然流动时提及 |
| `SIDE` + `CONDIMENT` | 不主动提及，用户说到再跟 |
| `DRINK` | 普通饮品不提，特色饮品可一带而过 |

**举例**：用户点了打抛饭（SIGNATURE）和生菜（SIDE）。Planner 应把打抛饭列为可深入的机会点，而生菜不主动提起——但如果用户自己说"那个生菜挺新鲜的"，Planner 不应禁止跟进。

#### 重要纠正

```
✗ Conversation Planner 不输出：
  第一句话问天气
  第二句话问朋友
  第三句话问菜品
  （这是聊天脚本，会导致对话机械化、像问卷）

✓ Conversation Planner 输出：
  方向 + 机会点 + 限制
  （让 Voice Agent 在框架内自由发挥，保持真人感）
```

---

## 5. 为什么三层都需要

### 5.1 不能只发 Conversation Planner

只发 Planner（如"可以从天气切入"），Voice Agent 不知道为什么天气重要、今天是什么天气、用户为什么可能关注。

```
✗ 只有 Planner："可以聊天气"
  生成 → "今天天气怎么样？"（机械）

✓ Planner + Raw Facts：
  今天端午节，离店时大雨。Planner 说可以自然利用天气作为离店后的关怀切入点。
  生成 → "刚刚出来的时候雨还挺大的吧，希望没有影响您回去的路。"（自然）
```

### 5.2 不能只发原始事实

只发菜品列表（麻婆豆腐、夫妻肺片、口水鸡），LLM 倾向于主动逐项询问，变成调查问卷。

Planner 的作用是告诉模型："这些信息只是记忆锚点，不要主动逐项覆盖。"

---

## 6. Agent Pipeline（完整流程）

### 6.1 数据流与依赖关系

```
用户拍摄/上传订单照片
         │
         ▼
[1] Order Understanding Agent
    输入：小票照片 / 订单截图
    输出：结构化订单数据（餐厅、时间、人数、菜品、用餐时长）
         │
         ▼
[2] Dish Knowledge Retrieval (RAG)
    输入：[1] 的菜品列表
    输出：已点菜品的背景知识（特点、体验方向、聊天切入点）
         │
         ▼
[3] Realtime Information Tool
    输入：当前时间、地点
    输出：天气、节假日、交通等实时环境数据
         │
         ▼
┌──────────────────────────────────────────────────┐
│  [4] Experience Understanding Agent               │
│  输入：[1] 订单 + [2] 菜品知识 + [3] 实时信息      │
│  输出：体验可能性（保持低确定性）                    │
│                                                   │
│  说明：必须同时接收订单/菜品/实时三层信息才能推理     │
│  只依赖任意单一信息源都会导致理解片面                 │
└──────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────┐
│  [5] Conversation Planner Agent                   │
│  输入：[1] 订单 + [2] 菜品知识 + [3] 实时信息      │
│        + [4] 体验理解输出                          │
│  输出：对话策略（方向 / 机会点 / 限制）              │
│                                                   │
│  说明：需要全部上游数据才能生成有依据的策略           │
│  尤其依赖 [4] 的体验理解来校准对话方向                │
└──────────────────────────────────────────────────┘
         │
         ▼
[6] Prompt Assembly
    输入：[4] 体验理解 + [5] 对话策略
         + Static System Prompt + Raw Facts
    输出：Runtime Prompt（System Prompt + Dynamic Context 三层）
         │
         ▼
[7] 火山引擎 Voice Agent
    基于完整 Runtime Prompt 进行实时语音对话
         │
         ▼
[8] 实时电话交流
```

### 6.2 关键说明

- 步骤 [1]~[6] 在**通话前**完成
- 步骤 [7]~[8] 在**通话中**进行
- 每个 Agent 当前是**逻辑模块**，不是独立服务
- Prompt Assembly 是显式的独立步骤（合并 System Prompt + Context）
- **数据依赖链**：[5] 依赖 [4] 是强依赖，[4] 依赖 [1][2][3] 也是强依赖。缺少任意前置输入，下游模块无法正常工作

---

## 7. 菜品知识库（Dish Knowledge Base）

### 7.1 技术选型

- **采用精确匹配方案**（菜品数量多，但输入为准确菜名，无需语义搜索）
- MVP 阶段：不引入 Embedding 模型，不上向量数据库
  - DishRetriever 通过 HashMap 精确匹配实现 O(1) 查找
  - 原因：输入是准确的菜名（从小票提取），不是自然语言搜索
  - 即使扩展到上万道菜，精确匹配仍足够

### 7.2 知识内容

每道菜包含：
- 菜品介绍
- 制作方式
- 口味特点
- **菜品角色（dishRole）**：在用餐中的定位——招牌菜、主菜、配菜、主食、甜品、饮品、调料
- 用户可能的体验方向

### 7.3 使用原则

- 检索结果只是**背景信息**
- 不能直接让 AI 假设用户一定喜欢某道菜
- 帮助 AI 理解："用户点的菜可能有什么体验特点"

### 7.4 餐厅信息 vs 菜品知识的存储策略

| 数据类型 | 存储方式 | 原因 |
|---|---|---|
| 餐厅 Profile（含地址） | 结构化数据库 / JSON 文件 | 信息稳定，字段固定 |
| 菜品知识 | JSON 文件 / 结构化数据库 | 菜品名精确匹配，无需向量检索 |

---

## 8. 实时信息（Realtime Information）

**不需要 Agent**，通过 Tool / API 获取。

获取内容：天气、时间、节假日、交通情况。

作用：辅助生成聊天切入点（如"大雨天离店"可以成为自然关怀话题）。

---

## 9. 数据模型设计概要

### 9.1 Restaurant Profile

```
{
  "restaurant_name": "売泰",
  "address": "北京市朝阳区三里屯 T+MALL 负一层",
  "positioning": "泰国街头小吃店，南洋复古风",
  "environment_features": ["复古海报", "马赛克瓷砖", "藤编灯具"],
  "service_features": ["快餐型服务", "点餐上菜效率高"],
  "experience_tags": ["朋友聚餐", "拍照打卡", "性价比高"]
}
```

结构化存储，不采用知识库（信息稳定）。地址字段用于自动定位城市和区，用于高德天气 API 查询。

### 9.2 Order Data

来源：用户上传小票照片 / 订单截图。

解析方式：LLM 视觉能力（Claude Vision / GPT-4o）端到端解析 → 结构化 JSON。

每道菜品保留原始小票中的丰富信息，不做压缩：

```
{
  "restaurant": "川·隐味小馆",
  "time": "周五 19:20",
  "people": 3,
  "items": [
    {
      "name": "麻婆豆腐",
      "quantity": 1,
      "spiceLevel": null,
      "notes": null,
      "category": null,
      "price": null
    },
    {
      "name": "打抛饭",
      "quantity": 1,
      "spiceLevel": "不可免辣",
      "notes": "加牛肉",
      "category": "主食",
      "price": "38"
    },
    {
      "name": "冰柠檬水",
      "quantity": 2,
      "spiceLevel": null,
      "notes": null,
      "category": "饮品",
      "price": "18"
    }
  ],
  "duration": "2h15min"
}
```

**设计原则**：`DishItem` 所有可选字段（`spiceLevel`、`notes`、`category`、`price`）保留 `null` 的语义——小票上没写就是 `null`，不填默认值。这样后续 `ContextAssembler` 可以判断"有信息就写，没有就不写"。

### 9.3 Dish Knowledge

```json
{
  "dish": "打抛饭",
  "dish_role": "SIGNATURE",
  "features": ["经典泰式", "酸辣开胃", "香料风味"],
  "experience_tags": ["下饭", "招牌必点"]
}
```

**菜品角色（DishRole）说明**：

| 角色 | 含义 | 对话策略 |
|---|---|---|
| `SIGNATURE` | 招牌/特色菜 | 值得主动聊，用户表现出兴趣时可深入 |
| `MAIN` | 主菜 | 可以聊，自然流动中提及 |
| `SIDE` | 配菜/辅料 | 不主动提及，用户说到再跟 |
| `STAPLE` | 主食 | 可以聊，如米饭/粉面类 |
| `DESSERT` | 甜品 | 自然流动时提及，适合作为轻松收尾话题 |
| `DRINK` | 饮品 | 普通饮品不提，特色饮品可一带而过 |
| `CONDIMENT` | 调料/小料 | 不提及 |

> **设计原则**：dishRole 是菜品知识库的固有属性，在数据录入时一次性标注。新餐厅上线只需完成菜品知识录入，后续全自动处理，无需人工干预每笔订单。

---

## 10. 火山引擎对接设计

### 10.1 当前阶段

- 后端生成完整 Runtime Prompt
- 通过 `/voice/context/generate` 或等价接口传递给火山引擎
- 火山引擎负责实时语音交互

### 10.2 Adapter 层

预留 Adapter 层，将内部 Context 格式转换为火山引擎需要的格式：

```
Internal Context → [Volcano Adapter] → Volcano Engine Format
```

目的：
- 隔离火山引擎接口变更的影响
- 未来可替换为其他 Voice Agent 平台

### 10.3 未来升级路径

当火山引擎支持以下能力时，可自然演进：

```
当前：            Runtime Prompt（System Prompt + Context 合并）
未来：  System Prompt（独立传入） + Dynamic Context API + Conversation Memory
```

核心设计不变，只改变对接方式。

---

## 11. 实现原则

### 11.1 MVP 阶段原则

1. **不追求复杂 Agent 自主规划**
2. **Context 质量优先于 Agent 架构复杂度**
3. **Conversation Planner 保持轻量**— 目标是减少 Voice Agent 推理压力，不是替代它思考
4. **先跑通数据流，再考虑 Workflow 复杂化**
5. **本项目最核心的竞争力**是：生成的 Context 是否真的能提升通话体验

### 11.2 技术栈

| 技术 | 用途 | 说明 |
|---|---|---|
| Java 21+ | 运行环境 | 当前使用 JDK 25 |
| Spring Boot 3.x | 业务后端框架 | API、数据层、依赖注入 |
| LangChain4j | AI SDK | LLM 调用、结构输出、Agent 接口（不含编排） |
| DeepSeek API | 文本推理（Experience / Planner） | 直连 `api.deepseek.com` |
| OpenRouter API | 视觉模型（小票 OCR） | 使用 `qwen3.6-plus` |
| 高德地图 API | 区级天气查询 | Web 服务类型，免费额度 30 万次/日 |
| 节假日 API | 节假日判断 | `timor.tech` 免费接口 |
| PostgreSQL | 业务数据存储 | 用户、餐厅、订单、通话任务 |
| Redis | 缓存、会话管理 | |
| Maven | 构建工具 | |

#### LangChain4j 在本项目中的定位

LangChain4j 被当作 **AI SDK** 使用，不是 Workflow 框架：

| 用途 | LangChain4j 组件 |
|---|---|
| Agent 声明式定义 | 使用 `AiServices.builder()` 手动创建代理（不加 `@AiService` 注解） |
| Agent 方法注解 | `@SystemMessage` + `@UserMessage` + `@V` 定义输入输出 |
| 结构化 LLM 输出 | 接口方法返回 POJO / Record，LangChain4j 自动解析 |
| 菜品精确匹配 | 自实现 HashMap 查找，不依赖 LangChain4j（无需 Embedding） |
| 实时信息获取 | 直接调用 API（高德 / wttr.in），不通过 `@Tool` |
| Workflow 编排 | **不用 LangChain4j Chain**，自己实现 |

> 关键约束：LangChain4j 只用在 `ai/` 层下的 LLM 调用部分。Workflow 编排由 `ContextPreparationWorkflow` 类手动控制，不引入 LangChain4j 的 Chain 机制。

### 11.3 模块结构（MVP）

```
ai/
├── workflow/
│   ├── WorkflowNode                  # Node 接口
│   ├── ContextPreparationWorkflow    # 6 步 Pipeline 编排（手动控制，非 Chain）
│   └── WorkflowExecutionException    # 自定义异常
├── agent/
│   ├── OrderUnderstandingService     # 订单理解服务接口（不是 @AiService）
│   ├── MockOrderUnderstandingService # Mock 实现（mock=true）
│   ├── RealOrderUnderstandingService # 真实视觉 LLM 实现（mock=false）
│   ├── ExperienceUnderstandingAgent  # AI Agent 接口（@SystemMessage + @UserMessage）
│   └── ConversationPlannerAgent      # AI Agent 接口（@SystemMessage + @UserMessage）
├── retrieval/
│   ├── DishRetriever                 # 菜品知识 HashMap 匹配
│   └── RestaurantRepository          # 餐厅信息模糊匹配
├── tools/
│   └── WeatherTool                   # 高德天气 API + wttr.in fallback
├── context/
│   └── ContextAssembler              # 三层 Context 组装
├── prompt/
│   ├── SystemPromptLoader            # Static System Prompt 加载
│   └── PromptAssembler               # System Prompt + Context 合并
└── adapter/
    └── VolcanoAdapter                # 火山引擎接口适配（预留）
```

Agent 定义示例（LangChain4j 手动代理模式）：

```java
// 接口定义（不加 @AiService 注解，避免自动代理冲突）
@SystemMessage("""
    你是一个餐后体验分析专家。
    根据订单信息、菜品知识和实时环境，
    推测本次用餐可能存在的体验场景。
    所有输出必须是可能性，不能是确定性结论。
    """)
interface ExperienceUnderstandingAgent {

    @UserMessage("订单信息：{{order}}\n\n菜品知识：{{dishes}}\n\n实时环境：{{realtime}}")
    ExperienceUnderstanding analyze(
        @V("order") OrderData order,
        @V("dishes") List<DishKnowledge> dishes,
        @V("realtime") RealtimeInfo realtime
    );
}

// 代理创建（在 @Configuration 类中，仅在 mock=false 时生效）
@Bean
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")
public ExperienceUnderstandingAgent experienceAgent(ChatLanguageModel model) {
    return AiServices.builder(ExperienceUnderstandingAgent.class)
            .chatLanguageModel(model)
            .build();
}
```

### 11.4 需要提前考虑的技术问题

- **Context 时效性**：Context 生成后到实际通话可能有时间差，需要"刷新实时信息但不重新跑完整 Workflow"的机制
- **通话重试策略**：第一次未接通，第二次是否需要重新生成 Context？哪些部分可复用？
- **Promppt 版本管理**：System Prompt 的迭代影响所有通话质量，建议早期引入版本管理
- **质量评估**：可使用 LLM self-evaluation 或 A/B 测试对比不同 Prompt 版本效果

---

## 12. 开发重点排序

### 第一优先（数据流打通）

1. `OrderUnderstandingAgent` — 订单解析
2. `DishRetriever` — 菜品知识检索
3. `RealtimeInformationTool` — 实时信息获取
4. `ContextAssembler` — 三层 Context 组装
5. `PromptAssembler` — System Prompt + Context 合并

### 第二优先（核心能力）

6. `ExperienceUnderstandingAgent` — 体验理解推理
7. `ConversationPlannerAgent` — 对话规划
8. `ContextPreparationWorkflow` — Workflow 编排

### 第二点五优先（视觉模型接入）

8.5. **视觉 LLM 接入** — 替换 Mock 订单数据，调用 OpenRouter qwen3.6-plus 识别真实小票照片

### 第三优先（工程化）

9. `VolcanoAdapter` — 火山引擎对接
10. 业务管理功能（用户 / 餐厅 / 订单 / 通话任务管理）
11. 重试、缓存、版本管理等工程能力

---

## 13. 关键禁止项（Do NOT）

| 禁止项 | 原因 |
|---|---|
| ❌ 不要在项目内实现 ASR / TTS / 电话系统 | 这些是火山引擎的职责 |
| ❌ 不要让 Conversation Planner 生成聊天脚本 | 会机械化、像问卷 |
| ❌ 不要只发 Planner 结果给 Voice Agent | Agent 缺乏上下文理解 |
| ❌ 不要只发原始数据给 Voice Agent | LLM 倾向于逐项询问 |
| ❌ 不要在通话中进行数据库查询或复杂推理 | 延迟会毁掉交流体验 |
| ❌ 不要让 Experience Understanding 输出确定性结论 | 推测不能等于事实 |
| ❌ 不要在 MVP 阶段拆分独立微服务 | 单一项目内部模块即可 |
| ❌ 不要自研 OCR 引擎 | 用 LLM 视觉能力端到端解析 |

---

## 14. 术语表

| 术语 | 含义 |
|---|---|
| Pre-call Context | 通话前生成的动态上下文 |
| Runtime Prompt | 最终传给 Voice Agent 的完整 Prompt（System Prompt + Context） |
| Static System Prompt | 稳定不变的角色 / 规则 / 风格定义 |
| Dynamic Context | 本次通话的三层动态信息（事实 / 理解 / 策略） |
| Conversation Planner | 将体验理解转换为对话策略的核心模块（输出方向/钩子/限制） |
| Experience Understanding | 从事实推测体验可能性的模块 |
| Prompt Assembly | 将 System Prompt 和 Dynamic Context 合并为 Runtime Prompt 的步骤 |
| Voice Agent | 火山引擎提供的实时语音对话能力（ASR + LLM + TTS） |

---

## 15. 未来演进规划：从 MVP Pipeline 到 LangGraph 风格 Multi-Agent

> 本文档第 1~14 节为当前 MVP 阶段的完整设计。本节讨论从中长期视角，如何将系统逐步演进到更强规划 / 反思 / 多智能体协作的架构。

### 15.1 演进目标

当前 MVP 是**通话前 6 步串行 Pipeline**，适合快速验证数据流和核心体验。

```
        未来演进路径：
MVP Pipeline  →  条件分支 / 循环  →  Multi-Agent Graph  →  LangGraph 风格
                               →  反射 / 自省机制     →  质量评估闭环
                               →  动态 Agent 注册     →  异步消息总线
```

| 阶段 | 特征 | 状态 |
|---|---|---|
| MVP（当前目标） | 6 步串行 Pipeline，in-process 调用，Context 质量优先 | **本文件定义的就是此阶段** |
| Phase 2：Multi-Agent | 引入条件分支和循环，Workflow 从线性执行变为图调度 | 需预留接口 |
| Phase 3：LangGraph 风格 | 反射节点、质量评估闭环、动态 Agent 注册、可独立部署 | 需预留接口 |

### 15.2 核心演进建议

以下建议**不会增加 MVP 交付复杂度**，但能在 MVP 阶段以极低成本为后续演进扫清障碍。

#### 15.2.1 定义通用 Node 接口

即使 MVP 阶段只有顺序执行，也建议为 `ContextPreparationWorkflow` 定义一个通用的 Node 接口：

```java
// MVP 阶段定义，所有 Agent 实现此接口
interface WorkflowNode {
    String nodeName();
    WorkflowContext execute(WorkflowContext ctx);
    // 可选：该节点失败时是否跳过
    default boolean isOptional() { return false; }
    default WorkflowContext fallback(WorkflowContext ctx) { return ctx; }
}
```

**设计说明**：不使用泛型，统一为 `WorkflowContext → WorkflowContext`。每个 Node 从 `ctx` 读取自己需要的字段，写入自己负责的字段。这与未来 LangGraph 的 StateGraph 模式一致——Node 读写 State，不关心其他 Node 的签名。

**为什么**：未来从 Pipeline 切换到 Graph 时，只需将"按列表顺序执行 Node"改为"按 DAG 调度 Node"，Node 本身不需要修改。这是架构演进成本最低的单一投资。

#### 15.2.2 尽早引入 WorkflowState / WorkflowContext

建议 MVP 阶段就创建一个集中的上下文对象，聚合所有 Agent 的输出：

```java
// MVP 阶段即可定义的上下文对象
class WorkflowContext {
    OrderData order;
    List<DishKnowledge> dishes;
    RealtimeInfo realtime;
    ExperienceUnderstanding experience;
    ConversationPlan plan;
    Map<String, Object> metadata;  // 未来扩展
    int executionRound;            // 未来循环计数
}
```

**为什么**：这个对象在 MVP 阶段是"Agent 输出的聚合容器"，在未来 LangGraph 中天然对应 **State** 概念。从第一天就使用它，切换时零成本。

每个 Agent 对外暴露的入口建议统一为 `WorkflowContext → WorkflowContext`：

```java
// 推荐的 Agent 包装方式
public WorkflowContext execute(WorkflowContext ctx) {
    OrderData order = orderAgent.run(ctx.photo);  // 内部调用 @AiService
    ctx.order = order;
    return ctx;
}
```

这样未来的 Graph 调度器只需要知道"这个 Node 读写 State 的哪些字段"，而不需要关注 Agent 内部的方法签名。

#### 15.2.3 保留 Agent 的可选性

某些 Agent（如未来的 Reflection Agent、Quality Evaluator）在 MVP 阶段不需要，但接口应允许它们"不存在时走默认路径"：

```java
// 推荐：Node 注册时可选的
workflow.registerNode(orderAgent);           // 必选
workflow.registerNode(reflectionAgent,       // 可选，MVP 阶段注册一个空实现或跳过
    .isOptional(true)
    .fallback(ctx -> ctx));                  // 不存在时直接跳过
```

**为什么**：后续新增 Agent 不需要修改现有代码，只需在注册时加入。这保持了 Open/Closed 原则。

#### 15.2.4 保持 Planning 与 Understanding 的分离

架构中 Layer 2（Experience Understanding）和 Layer 3（Conversation Planner）的分离是该设计中最具远见的决策之一，必须保持：

| 当前 MVP | 未来 LangGraph 映射 |
|---|---|
| Experience Understanding Agent | **Understanding Node** — 负责从事实推断体验可能性 |
| Conversation Planner Agent | **Planning Node** — 负责将理解转化为对话策略 |
| （暂无） | **Reflection Node** — 审视 Planner 输出质量，触发重规划 |
| （暂无） | **Quality Node** — 评估最终 Context 质量，决定是否重新执行 |

**为什么**：在 LangGraph 中，Planning 和 Understanding 天然对应不同类型的 Node，具有不同的重试策略、缓存策略和并行度。如果 MVP 阶段将它们混在一起，未来拆分成本远高于从一开始就保持分离。

### 15.3 演进路径推演

```
阶段零（现在）        阶段一（MVP）            阶段二（Multi-Agent）      阶段三（LangGraph 风格）

3 份设计文档          Spring Boot +            引入条件分支 / 循环       完整的图编排引擎
零代码               LangChain4j              动态 Agent 注册           反射 / 自省节点
                    串行 Pipeline              节点可独立部署            质量评估闭环
                    通话前 6 步                                       在线 A/B 测试
                    火山引擎对接

演进关键动作:
                    实现架构文档               Node 接口不变             引入 StateGraph 概念
                    引入 WorkflowContext       增加条件路由              增加 Reflection Node
                    定义 Node 接口             逐步解耦 Agent            异步消息总线
                    预留扩展字段               Adapter 层不变            Agent 可独立部署
```

### 15.4 技术选型演进路线

| 能力 | MVP | Phase 2 | Phase 3 |
|---|---|---|---|
| 编排方式 | 手动顺序编排（ContextPreparationWorkflow） | 条件路由 + 循环（仍 in-process） | 图编排引擎（类似 LangGraph） |
| Agent 定义 | LangChain4j @AiService | @AiService + Node Adapter 包装 | 多种 Agent 类型（@AiService + 动态 Agent） |
| 状态管理 | WorkflowContext（集中 POJO） | WorkflowContext + 版本化 | 状态图（StateGraph）+ 持久化 |
| 节点通信 | 方法调用 | 方法调用 + 事件通知 | 异步消息总线（可选跨进程） |
| 外部对接 | VolcanoAdapter | VolcanoAdapter（不变） | VolcanoAdapter（不变）+ 多平台适配 |
| 质量评估 | 人工验证 | LLM self-evaluation | 自动化质量闭环 + A/B 测试 |

### 15.5 关键原则

- 1~14 节的 MVP 设计是演进的基础，不是过渡品。所有演进都在其上增量完成
- **Adapter 层是架构的"不变点"**：无论内部如何演进，输出端只需一个 Adapter 转换
- **Node 接口和 WorkflowContext 是核心抽象**：MVP 阶段就引入，后续所有演进都建立在这两个抽象之上
- 不要为了"未来可能"而过度设计：本节建议的都是**零成本或极低成本的预留**，不引入额外框架依赖
- 每次演进都要回答一个问题：**Context 质量是否因此提升了？**——这是系统的核心竞争力，比架构风格更重要