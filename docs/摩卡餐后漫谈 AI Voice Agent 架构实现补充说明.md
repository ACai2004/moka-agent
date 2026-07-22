明白。你这里选择方案B（最终动态生成一个完整 Prompt 给火山 Voice Agent）是**工程妥协下完全合理的选择**。

我先强调一下：
**方案B不是架构错误。**
只是相比“System Prompt + Context 分离”，它牺牲了一些长期维护性，换来了当前前端/Voice Agent接入简单。

所以给 Claude Code 的文档里，需要明确：

1. **逻辑上仍然保持 System Prompt 和 Pre-call Context 分层**
2. **工程实现阶段将二者 merge 成最终 Runtime Prompt**
3. **不要让 Claude 误以为 Pre-call Context 要替代 System Prompt**
4. **不要让 Conversation Planner 变成生成聊天脚本的 Agent**

下面是我整理后的补充文档，你可以直接给 Claude Code。

---

# Voice Agent Prompt Assembly Design

## 1. 背景

当前餐后漫谈 AI 电话 Agent 的核心目标不是传统客服式反馈收集，而是通过自然电话交流帮助用户重新回忆刚刚结束的用餐体验。

为了提升通话中的自然感，需要在用户接通电话之前，根据当前用户、订单、餐厅、实时环境等信息提前构建本次通话上下文。

由于当前前端/Voice Agent 接入方式暂不支持分别传入 System Prompt 和 Dynamic Context，因此 MVP 阶段采用：

> Pre-call Context 动态生成 + System Prompt 合并生成最终 Runtime Prompt

然后将完整 Prompt 传递给 Voice Agent。

---

# 2. Prompt 逻辑分层

虽然最终传递给 Voice Agent 的形式是一个完整 Prompt，但是内部逻辑必须保持分层。

最终 Runtime Prompt：

```
Runtime Prompt

=
Static System Prompt

+

Dynamic Pre-call Context
```

其中：

---

## 2.1 Static System Prompt（稳定能力层）

负责描述：

> AI是谁，以及应该如何交流。

包括：

* 角色定义
* 用户尊崇感原则
* 对话目标
* 时间线推进原则
* 自然交流原则
* 禁止行为
* 情绪同步规则
* 话题退出机制
* 对话风格

这些内容不会因为不同用户、不同订单发生变化。

例如：

```
你是一个餐后体验回访电话 AI。

你的目标不是收集反馈，而是帮助用户重新回味刚才的用餐经历。

你不是问卷调查员，而是一个倾听体验的伙伴。
```

---

## 2.2 Dynamic Pre-call Context（动态上下文层）

负责描述：

> 这一次电话发生的具体场景。

Dynamic Context 不负责定义 AI 行为，而是提供：

* 当前事实
* 当前场景理解
* 当前交流方向

包含三个层级：

---

# 3. Dynamic Context 三层结构

## Layer 1：Raw Facts（原始事实层）

来源：

* 用户上传订单照片
* 餐厅数据库
* 菜品知识库
* 实时信息服务

这一层只描述事实，不做推理。

---

# Layer 2：Experience Understanding（体验理解层）

作用：

> 将事实转换成对用户体验场景的理解。

注意：

这一层不是事实。

它产生的是：

“可能性”。

不是：

“用户一定如此”。

例如：

输入：

```
周五晚上
端午节
3人
停留2小时15分钟
```

输出：

```
可能是一场多人聚餐。

用户可能更加关注陪伴和整体氛围，而不仅是菜品。

可以考虑从用餐过程中的记忆点切入。
```

必须保持：

低确定性。

禁止：

```
用户今天一定是和朋友聚餐。
```

应该：

```
可能存在聚餐场景。
```

---

# Layer 3：Conversation Planner（对话规划层）

作用：

> 告诉 Voice Agent 如何使用前面的信息。

非常重要：

Conversation Planner 不是生成聊天流程。

不是：

```
第一句话问天气。

第二句话问朋友。

第三句话问菜品。
```

这种设计会导致：

* 对话机械化
* 像问卷
* 降低真人感

Conversation Planner 输出应该是：

## 方向（Direction）

当前适合关注什么。

例如：

```
优先从整体离店感受切入。

不要直接进入菜品评价。
```

---

## 机会点（Available Hooks）

例如：

```
可以自然利用：

- 大雨天气
- 周五晚上
- 较长用餐时间
- 多人用餐场景
```

---

## 限制（Avoid）

例如：

```
避免：

- 假设同行关系
- 主动评价菜品
- 逐菜询问
- 将推测当事实
```

---

# 4. 最终 Runtime Prompt 结构

MVP 阶段：

后端生成：

```
Final_Runtime_Prompt
```

结构：

```
================

System Prompt

(角色、规则、交流原则)

================

Pre-call Context

## Raw Facts

当前订单：
...

实时环境：
...


## Experience Understanding

可能场景：
...


## Conversation Planner

交流方向：
...

可利用线索：
...

避免：
...


================
```

然后发送给 Voice Agent。

---

# 5. 为什么不是只发送 Conversation Planner？

因为 Planner 不是完整上下文。

如果只发送：

```
从天气切入。
```

Voice Agent不知道：

* 为什么天气重要？
* 今天是什么天气？
* 用户为什么可能关注？
* 还有什么其他体验信息？

例如：

错误：

```
Planner:
可以聊天气。
```

生成：

```
今天天气怎么样？
```

容易机械。

正确：

Context:

```
今天端午节。
用户晚上7点20入店。
用餐2小时15分钟。
离店时大雨。
```

Planner:

```
可以自然利用天气作为离店后的关怀切入点。
```

Voice Agent：

更容易生成：

```
刚刚出来的时候雨还挺大的吧，希望没有影响您回去的路。
```

这才像真人。

---

# 6. 为什么不是全部原始信息直接塞给 Voice Agent？

因为：

LLM并不知道哪些信息重要。

例如：

给：

```
麻婆豆腐
夫妻肺片
口水鸡
2碗米饭
```

模型可能：

主动逐个询问。

导致：

```
麻婆豆腐怎么样？
夫妻肺片怎么样？
口水鸡呢？
```

变成调查。

Planner 的作用就是：

告诉模型：

```
这些信息只是记忆锚点。

不要主动逐项覆盖。
```

---

# 7. Agent Pipeline

最终整体流程：

```
用户拍摄订单照片

        |

        v

Order Understanding Agent

(解析订单信息)


        |

        v

Dish Knowledge Retrieval

(获取菜品相关信息)


        |

        v

Realtime Information Agent

(天气/时间/交通)


        |

        v

Experience Understanding Agent

(事实 -> 体验可能性)


        |

        v

Conversation Planner Agent

(体验理解 -> 对话策略)


        |

        v

Prompt Assembly

(System Prompt + Context)


        |

        v

Voice Agent

(火山引擎)

        |

        v

实时电话交流

```

---

# 8. MVP实现原则

当前阶段：

不追求复杂 Agent 自主规划。

重点：

1. Context质量
2. Prompt组织方式
3. Voice Agent通话体验

Conversation Planner 应保持轻量。

它的目标不是替代 Voice Agent 思考。

而是：

> 提前减少 Voice Agent 在实时通话中的推理压力，让模型拥有更好的“现场感”。

---

# 9. 后续扩展方向

未来如果 Voice Agent 支持：

* System Prompt独立传入
* Context变量注入
* Memory接口

可以自然升级为：

```
System Prompt

+

Dynamic Context API

+

Conversation Memory
```

无需改变核心设计。

---

这个版本给 Claude Code 的定位应该会比较清楚：

**不是让它去重新设计 Agent，而是让它理解：你们要搭的是一个 Pre-call Context Generation + Prompt Assembly + Voice Agent 接入系统。**

另外我建议你后面让 Claude Code 开始写代码时，不要第一步就写 LangGraph Agent。
第一步应该是把这个流程落成：

```
Spring Boot
 |
 |-- order module
 |-- knowledge module
 |-- context generation module
 |-- prompt assembly module
 |-- volcano adapter
```

先把数据流跑通，再考虑 Agent Workflow 的复杂化。你现在这个项目最核心的竞争力其实不是“用了多少 Agent”，而是 **生成给 Voice Agent 的 Context 是否真的能提升通话体验**。
