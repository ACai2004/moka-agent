# Mock LLM 阶段的设计意图与工程价值

> 本文档记录 Moka Agent 项目中 "Phase 2 — 全 Mock 模式" 的设计思路，用于面试时解释开发流程决策。

---

## 一、什么是 Mock 阶段？

在 Phase 2 中，我们不调用任何真实的 LLM（DeepSeek / OpenRouter），而是用一个 `MockLlmService` 返回**预设的、已知正确**的数据。所有 AI 推理模块（OrderUnderstanding / ExperienceUnderstanding / ConversationPlanner）在此时都是"模拟"的。

```
真实模式（Phase 3+）：
小票照片 → 视觉 LLM → OrderData → 文本 LLM → ExperienceUnderstanding → 文本 LLM → ConversationPlan

Mock 模式（Phase 2）：
小票照片（忽略） → MockLlmService 返回预设 OrderData → MockLlmService 返回预设 ExperienceUnderstanding → MockLlmService 返回预设 ConversationPlan
```

---

## 二、为什么要 Mock？四个核心原因

### 1. 分离验证：先确保管道通，再确保水干净

系统由两个独立维度组成：

| 维度 | 内容 | 出问题谁负责 |
|---|---|---|
| **数据流（管道）** | DishRetriever、ContextAssembler、PromptAssembler、Controller | 开发者（代码 bug） |
| **LLM 质量（内容）** | 视觉识别准不准、推理合不合理、Prompt 写得好不好 | LLM 选型 + Prompt 设计 |

如果不 Mock，调试场景是这样的：

```
我修改了 ContextAssembler 的输出格式，启动测试。
→ 调用真实 DeepSeek，等 5 秒
→ 报错了
→ 是 ContextAssembler 的 bug？还是 DeepSeek 返回的 JSON 格式变了？
→ 分不清 ❌
```

Mock 之后：

```
MockLlmService 返回已知正确的数据。
→ 如果下游出错 → 100% 是下游代码的 bug
→ 精准定位 ✅
```

### 2. 开发效率：毫秒级反馈 vs 秒级等待

| 操作 | 真实 LLM | Mock |
|---|---|---|
| 一次 AI 调用 | 2 ~ 10 秒 | < 1 毫秒 |
| 修改代码后验证 | 每次等 10 ~ 30 秒 | 即时 |
| 调试一个 bug 的迭代次数 | 少（因为等太久） | 多（因为反馈快） |

开发阶段每天可能修改代码几十次，如果用真实 LLM，大量时间花在等待上。

### 3. 零成本运行

真实 LLM 调用按 Token 计费。开发阶段如果每次启动都调一次完整流程，一天下来成本可观。Mock 阶段完全不产生任何 API 费用，可以随意启动、测试、重启。

### 4. 确定性测试

真实 LLM 有"不确定性"——同样的输入，每次输出可能不一样。这就导致：

```
第一次测试：通过 ✅
第二次测试：失败 ❌（因为 LLM 这次返回了不同的 JSON 格式）
```

这是 LLM 应用的固有问题（非确定性输出）。Mock 阶段用固定数据，**每次结果完全一致**，能确保测试的可靠性。

---

## 三、什么时候从 Mock 切换到真实 LLM？

当以下条件都满足时，才引入真实 LLM：

```
Phase 2：全部 Mock，验证数据流正确
   ↓
Phase 2.5：端到端集成测试跑通，6 步流程全部通过
   ↓
Phase 3：逐个替换为真实 LLM Agent
   ├── 先替换 ExperienceUnderstandingAgent
   ├── 验证输出质量，调整 Prompt
   ├── 再替换 ConversationPlannerAgent
   └── 最后替换 OrderUnderstandingAgent（视觉模型）
   ↓
始终保留 moka.llm.mock=true/false 切换开关
```

---

## 四、面试话术（可以直接用）

### 面试官问："我看你们项目有个 Mock 阶段，为什么这么做？"

> **"我们的系统重度依赖 LLM，如果直接从真实模型开始开发，出问题时很难分清是 LLM 的问题还是代码的问题。所以我们分了两个阶段：先用 Mock 数据跑通整个数据流，确认 DishRetriever、ContextAssembler、PromptAssembler 这些核心模块是正确的，再逐步替换成真实的 LLM Agent。这种做法在 AI 工程里叫'分层验证'——先确保管道通，再确保水干净。"**

### 如果面试官追问："那 Mock 阶段具体有什么收益？"

> **"三个收益：一是开发反馈从秒级降到毫秒级，改代码后验证非常快；二是开发阶段零 API 成本，可以随意启动测试；三是测试结果确定——每次相同的输入得到相同的输出，不会因为 LLM 的不确定性导致测试 flaky。"**

---

## 五、项目中的实际体现

```bash
# Mock 模式（Phase 2 默认）
moka.llm.mock=true

# 启动日志会看到：
# DishRetriever 已加载 115 道菜品知识
# Context 准备流程演示（Mock 版）
# [1] → [6] 全部通过
# ✅ 端到端数据流验证通过

# 不需要配置任何 API Key，不需要联网
# 不需要等待 LLM 响应，启动即完成验证
```

---

## 六、与直接调 LLM 的启动对比

| 对比项 | Mock 模式 | 真实 LLM 模式 |
|---|---|---|
| 启动时间 | ~3 秒（纯代码） | ~10+ 秒（含网络请求） |
| 网络依赖 | ❌ 不需要 | ✅ 需要 |
| API Key | ❌ 不需要 | ✅ 需要配置 |
| 产生费用 | ❌ 无 | ✅ 按 Token 计费 |
| 测试确定性 | ✅ 每次一样 | ❌ 可能有差异 |
| Debug 排查 | ✅ 出错 100% 是代码 bug | ❌ 可能是 LLM 输出问题 |
