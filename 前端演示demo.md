沐含，我觉得你这个网页设计方向**是对的，而且非常适合作为周五 Demo**。

因为你现在展示的重点不是“一个漂亮的管理后台”，而是要让老板看到：

> **从用户订单输入 → AI理解 → 体验推理 → 对话规划 → Runtime Prompt生成 → 自动发起电话**

整个 AI Pipeline 是怎么工作的。

所以前端不要设计成传统后台，而应该设计成一个 **AI Agent Pipeline Debug / Demo Console**。

你的核心目标：

**让每一步中间产物可视化。**

---

我建议不要设计成两个完全割裂的页面，而是：

```
页面1：
输入与基础信息解析

↓

页面2：
AI推理链路展示 + 通话启动
```

这个逻辑非常自然。

---

# 页面整体结构

## Page 1：订单输入 & Context构建

标题：

```
餐后体验回访 Agent
Context Preparation
```

下面分三大区域：

---

# 区域1：订单输入

一个大 Box：

```
┌──────────────────────┐
│    上传订单小票       │
│                      │
│                      │
│    Drag & Drop       │
│                      │
│                      │
│ [上传图片]           │
└──────────────────────┘

       ↓

[开始解析订单]
```

点击：

调用：

```
POST /api/v1/orders/upload-photo
```

或者你的：

```
/prepare
```

---

解析过程中：

显示状态：

```
订单解析中...

✓ 图片上传完成
✓ 视觉模型识别中
✓ OrderData生成完成
```

这个很适合Demo。

---

# 区域2：基础 Context 展示

不要把所有东西平铺。

建议按照你的三层模型拆。

你的数据其实天然分三层。

> **补充建议：也可以按「数据来源」分组展示，Demo 时对观众更直观：**
>
> | 展示分组 | 包含内容 | 数据来源 |
> |---|---|---|
> | 🧾 订单信息 | 餐厅名、时间、人数、菜品列表 | 小票照片 → Vision API |
> | 📖 菜品知识 | 每道菜的介绍、角色、体验方向 | DishKnowledge 知识库 |
> | 🌤️ 实时环境 | 天气、时间、节假日 | 高德 API + 系统时间 |
>
> 这样观众能一目了然地看到"AI 从不同的地方拿了哪些信息"，而不需要先理解代码的分层概念。

---

## Layer 1：Raw Facts

标题：

```
基础事实信息
```

里面放：

### OrderData Card

---

### DishItem Card

---

## Layer 2：Knowledge Context

---

## Layer 3：Realtime Context

---

# Page 2：AI Reasoning Pipeline

这个页面是Demo核心。

我甚至觉得这是老板最想看的。

展示：

```
Experience Understanding result

      ↓

Conversation Planning result

      ↓

Runtime Prompt result

      ↓

Start Call
```

---

## Box 1

### Experience Understanding

展示：

```
ExperienceUnderstanding
```

> **补充建议：每条推测加上置信度标签，视觉冲击力更强：**
>
> ```html
> 🟢 [MEDIUM] 顾客偏好辣味，选择泰酷辣并多加辣...
> 🟡 [LOW]   由于天气凉爽多云，热汤粉可能带来温暖舒适体验...
> ```
>
> 代码中已有 `ConfidenceLevel`（LOW / MEDIUM），UI 直接映射即可。
> 观众一眼就能看懂 AI 对不同结论的把握程度，比全是文字更有层次感。

---

## Box 2

### Conversation Planning

---

## Box 3

### Runtime Prompt

---

# 最下面：

一个写着 “创建对话” 的按钮（你先止步于此 点击按钮之后的响应先不用管）

---

