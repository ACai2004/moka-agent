# 前端演示 Demo — 技术搭建计划

> 本计划基于 `demo` 分支，所有代码新增/修改仅在该分支上进行，不影响 `main` 分支。
> 阅读对象：编码 AI。目标是照此文档可直接编码实现。

---

## 1. 概述

搭建一个**两页式 AI Pipeline Demo 控制台**，以可视化方式展示：

1. 用户上传小票 → 后端自动解析出订单信息、菜品知识、实时环境
2. 展示 AI 体验理解 → 对话规划 → Runtime Prompt 生成的完整推理链路

### 1.1 使用场景

周五 Demo 向老板展示。需要视觉清晰、步骤感强、有过程动画。

---

## 2. 技术选型

| 项目 | 选择 | 理由 |
|---|---|---|
| 宿主方式 | Spring Boot 静态资源托管 | 零构建步骤，启动后端即自动提供前端页面 |
| 语言 | HTML5 + CSS3 + Vanilla JS (ES6) | 无框架依赖，AI 编码最直接 |
| CSS 方案 | Tailwind CSS v3 (CDN) + 少量自定义样式 | 快速构建美观 UI，无需手写大量 CSS |
| 图标 | 无额外依赖，用 Unicode/Emoji 或纯文字 | 保持零构建 |
| 页面数量 | 单页（SPA 风格），通过 JS 控制页面切换 | 简单，无需路由库 |

### 2.1 文件结构

```
src/main/resources/static/demo/
├── index.html      # HTML 骨架 + 容器元素
├── style.css       # 全部样式（Tailwind 补充 + 自定义）
└── app.js          # 全部 JS 逻辑（状态 + 渲染 + API 调用 + 事件）
```

**定稿说明**：采用多文件拆分。`index.html` 负责结构和 CDN 引入，`style.css` 负责样式，`app.js` 负责所有逻辑。各文件职责清晰，编码 AI 可分别独立修改。

---

## 3. 访问与启动方式

启动 Spring Boot 应用（**必须用 Real 模式**）后，浏览器访问：

```
http://localhost:8080/demo/index.html
```

启动命令：

```bash
cd D:\IDEAProjects\moka-agent
mvn spring-boot:run -Dspring-boot.run.arguments="--moka.llm.mock=false"
```

> ⚠️ 必须使用 `mock=false`，否则 Demo 端点不会生效（`CallController` 标注了 `@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")`）。

Spring Boot 会自动将 `src/main/resources/static/` 下的文件作为静态资源提供。

---

## 4. 后端接口设计

### 4.1 现状

| 端点 | 返回 | 问题 |
|---|---|---|
| `POST /api/v1/orders/upload-photo` | 仅 `OrderData` | 没有后续步骤数据 |
| `POST /api/v1/calls/prepare` | 仅 `RuntimePrompt`（最终 Prompt） | `ContextPreparationWorkflow.execute()` 内部构建了完整的 `WorkflowContext`（含 `order`、`dishes`、`realtime`、`experience`、`plan`、`runtimePrompt`），但只返回了最终的 `RuntimePrompt`，中间数据被丢弃了 |

### 4.2 推荐方案

**方案**：新建 `DemoController` + `DemoResponse`，手动编排 Pipeline 步骤，保留所有中间数据一起返回。

**不修改现有代码**的路线（仅新增文件）：

```
src/main/java/com/moka/demo/
├── DemoController.java     # @RestController, POST /api/v1/calls/demo
└── DemoResponse.java       # record，包装所有中间数据
```

#### DemoResponse.java

```java
package com.moka.demo;

import com.moka.ai.context.*;
import java.util.List;

/**
 * Demo 端点专用返回体，包含 Pipeline 全部中间数据。
 */
public record DemoResponse(
    boolean success,
    OrderData orderData,
    List<DishKnowledge> dishKnowledge,
    RealtimeInfo realtimeInfo,
    ExperienceUnderstanding experienceUnderstanding,
    ConversationPlan conversationPlan,
    RuntimePrompt runtimePrompt
) {}
```

#### DemoController.java — 关键实现逻辑

```java
package com.moka.demo;

import com.moka.ai.agent.*;
import com.moka.ai.context.*;
import com.moka.ai.prompt.PromptAssembler;
import com.moka.ai.retrieval.*;
import com.moka.ai.tools.WeatherTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/calls")
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")
public class DemoController {

    // 注入所有需要的 bean（参考 ContextPreparationWorkflow 的构造器参数）
    private final OrderUnderstandingService orderService;
    private final DishRetriever dishRetriever;
    private final WeatherTool weatherTool;
    private final ExperienceUnderstandingAgent experienceAgent;
    private final ConversationPlannerAgent plannerAgent;
    private final ContextAssembler contextAssembler;
    private final PromptAssembler promptAssembler;
    private final RestaurantRepository restaurantRepository;
    private final ObjectMapper objectMapper;

    // 构造器注入（省略，参考 ContextPreparationWorkflow 实现）

    @PostMapping("/demo")
    public DemoResponse demo(@RequestParam("file") MultipartFile file) {
        // 1. 读取文件 -> base64
        String base64 = Base64.getEncoder().encodeToString(file.getBytes());

        // 2. 构建 WorkflowContext 并逐步骤执行
        WorkflowContext ctx = new WorkflowContext().withPhotoBase64(base64);

        // [1] Order
        OrderData order = orderService.analyzeOrder(base64);
        ctx.setOrder(order);

        // [2] Dish
        List<String> dishNames = order.items().stream()
                .map(DishItem::name).collect(Collectors.toList());
        List<DishKnowledge> dishes = dishRetriever.retrieve(dishNames);
        ctx.setDishes(dishes);

        // [3] Realtime（复用 WorkflowContext 的 RealtimeNode 逻辑）
        //    天气 + 时间 + 节假日
        String weather = "..."; // 调用 WeatherTool
        String currentTime = "..."; // LocalDateTime 格式化
        String holiday = "..."; // timor.tech API
        RealtimeInfo realtime = new RealtimeInfo(weather, holiday, null, currentTime);
        ctx.setRealtime(realtime);

        // [4] Experience
        ExperienceUnderstanding exp = experienceAgent.analyze(order, dishes, realtime);
        ctx.setExperience(exp);

        // [5] Plan
        ConversationPlan plan = plannerAgent.plan(order, dishes, realtime, exp);
        ctx.setPlan(plan);

        // [6] Assembly
        RuntimePrompt prompt = promptAssembler.assemble(ctx);
        ctx.setRuntimePrompt(prompt.finalPrompt());

        // 3. 包装返回
        return new DemoResponse(true, order, dishes, realtime, exp, plan, prompt);
    }
}
```

> **编码 AI 注意**：上面的 `// [3] Realtime` 节是示意。实际实现时请参照 `ContextPreparationWorkflow.RealtimeNode.execute()` 中的完整逻辑（天气 API 调用、地址区级提取、节假日 API）。

### 4.3 返回 JSON 结构

```json
{
  "success": true,

  "orderData": {
    "restaurant": "壳泰·泰式船粉",
    "time": "2026-07-16 19:54:16",
    "people": 2,
    "items": [
      {
        "name": "小食拼盘",
        "quantity": 1,
        "spiceLevel": null,
        "notes": null,
        "category": "MAIN",
        "price": "35"
      }
    ],
    "duration": null
  },

  "dishKnowledge": [
    {
      "dishName": "小食拼盘",
      "dishRole": "MAIN",
      "features": ["综合拼盘", "多种炸物", "适合分享"],
      "experienceTags": ["多人分享", "尝遍多样"]
    }
  ],

  "realtimeInfo": {
    "weather": "Clear : +24°C ←6km/h",
    "currentTime": "星期三 11:01",
    "holiday": null,
    "traffic": null
  },

  "experienceUnderstanding": {
    "possibilities": [
      {
        "description": "顾客可能希望与同伴一起分享多种炸物小吃...",
        "confidenceLevel": "MEDIUM",
        "evidenceSource": "订单包含小食拼盘...，且用餐人数为2人。"
      },
      {
        "description": "...",
        "confidenceLevel": "LOW",
        "evidenceSource": "..."
      }
    ]
  },

  "conversationPlan": {
    "directions": ["推荐搭配解辣饮品...", "建议将小食拼盘置于中间..."],
    "availableHooks": ["今天天气晴朗...", "鱿鱼船粉的海鲜风味..."],
    "avoid": ["避免推荐额外加辣...", "避免强调鱿鱼船粉的清爽属性..."]
  },

  "runtimePrompt": {
    "finalPrompt": "# 角色定义\n\n你是一个...",
    "systemPromptVersion": "v1",
    "generatedAt": 1784689319628,
    "assemblyDurationMs": 76523
  }
}
```

> ⚠️ **字段名必须与 Java record 字段完全一致**（驼峰命名）：
> - `DishKnowledge` → `dishName`（不是 `dish`）
> - `ExperiencePossibility` → `confidenceLevel`（不是 `confidence`）和 `evidenceSource`（不是 `evidence`）
> - `ConversationPlan` → `directions`（不是 `direction`）和 `availableHooks`（不是 `hooks`）
> - `RealtimeInfo` → `currentTime`（不是 `currentTime` ✅）

---

## 5. 页面数据流

### 5.1 整体流程

```
用户选择/拖拽小票照片
       │
       ▼
点击「开始解析订单」
       │
       ▼
POST /api/v1/calls/demo  (FormData 上传文件)
       │
       ▼
后端执行完整 Pipeline，返回所有中间数据
       │
       ▼
前端按步骤逐步渲染各区域
```

### 5.2 分步渲染时序

为了 Demo 效果，数据虽然一次性返回，但前端通过延迟动画逐步骤展示：

```
阶段一（后端请求中）：
0ms     → 按钮变「解析中...」，状态文字「📤 图片上传中...」
500ms   → 状态文字变「🔍 视觉模型识别中...」
（保持等待直到响应返回）

阶段二（后端数据到达）：
响应到达 → 隐藏加载状态
         → renderOrderData(orderData)
         → renderDishKnowledge(dishKnowledge)
         → renderRealtimeInfo(realtimeInfo)
         → 状态文字变「✅ 解析完成」
         → 步骤条前进到第 2 步（② 变亮）
         → 「→ AI 推理链路」按钮可用

阶段三（进入页面 2，逐个淡入）：
点击「→ AI 推理链路」
  → state.currentPage = 2
  → 页面 2 容器显示
  → 500ms  → Box 1「体验理解」淡入
  → 1000ms → 箭头 ↓ 淡入
  → 1500ms → Box 2「对话规划」淡入
  → 2000ms → 箭头 ↓ 淡入
  → 2500ms → Box 3「Runtime Prompt」淡入
  → 3000ms → 「☎️ 创建对话」按钮出现
```

> **优先级**：功能正确 > 动画效果。可以先不做动画、只做静态展示，完成后再加。

---

## 6. 前端状态管理

用一个全局状态对象 `AppState` 管理所有数据：

```javascript
const AppState = {
  // === 页面控制 ===
  currentPage: 1,            // 1 或 2

  // === API 调用状态 ===
  isUploading: false,
  uploadStatus: '',           // '', 'uploading', 'identifying', 'done', 'error'
  uploadError: '',

  // === 后端返回数据（初始均为 null） ===
  orderData: null,                      // OrderData
  dishKnowledge: null,                  // DishKnowledge[]
  realtimeInfo: null,                   // RealtimeInfo
  experienceUnderstanding: null,        // { possibilities: [...] }
  conversationPlan: null,               // { directions, availableHooks, avoid }
  runtimePrompt: null,                  // { finalPrompt, systemPromptVersion, ... }

  // === Pipeline 步骤动画状态（页面 2 用） ===
  showStepExperience: false,
  showArrow1: false,
  showStepPlan: false,
  showArrow2: false,
  showStepPrompt: false,
  showCallButton: false,
};
```

所有 UI 渲染基于这个状态对象。状态变化时调用对应的 `renderXxx()` 函数更新 DOM。

---

## 7. UI 组件拆分

### 7.1 页面整体布局

```
┌────────────────────────────────────────────────────────────────┐
│  Header: Logo + 标题「餐后体验回访 Agent · Demo Console」       │
├────────────────────────────────────────────────────────────────┤
│  StepIndicator:  ① 订单输入 → ② AI 推理链路                     │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  #page1 或 #page2（根据 currentPage 切换 display:block/none）   │
│                                                                │
├────────────────────────────────────────────────────────────────┤
│  Footer: 项目信息（可选）                                      │
└────────────────────────────────────────────────────────────────┘
```

### 7.2 步骤条（StepIndicator）

```
① 订单输入与解析  ───── ② AI 推理链路
     ● 当前步骤              ○ 未到达
```

功能：
- 初始状态：步骤 ① 高亮，步骤 ② 灰色
- 数据加载完成后：步骤 ② 变为可点击（点击切换至页面 2）
- 返回页面 1 时：步骤 ① 再次高亮

### 7.3 Page 1 — 订单输入 & Context 构建

```
┌─ Page 1 ────────────────────────────────────────────────────┐
│                                                              │
│  ┌──────────────────────┐  ┌──────────────────────────────┐  │
│  │  A. 上传区域           │  │  B. 结果显示面板              │  │
│  │                      │  │                              │  │
│  │  [Drag & Drop 区域]   │  │  ┌─ B1. 🧾 订单信息 ──────┐  │  │
│  │  [或点击选择文件]      │  │  │ 餐厅: 壳泰·泰式船粉     │  │  │
│  │                      │  │  │ 时间: 2026-07-16 19:54   │  │  │
│  │  [开始解析订单] 按钮   │  │  │ 人数: 2 人              │  │  │
│  │                      │  │  │ 菜品: 4 道              │  │  │
│  │  状态进度文字:         │  │  └──────────────────────────┘  │  │
│  │  📤 图片上传中...     │  │  ┌─ B2. 📖 菜品知识 ──────┐  │  │
│  │  🔍 视觉模型识别中...  │  │  │ 小食拼盘: 综合拼盘...   │  │  │
│  │  ✅ 解析完成          │  │  │ 泰式酸肠: 酸味特色...   │  │  │
│  └──────────────────────┘  │  └──────────────────────────┘  │  │
│                             │  ┌─ B3. 🌤️ 实时环境 ──────┐  │  │
│                             │  │ 天气: ☀️ 24°C          │  │  │
│                             │  │ 时间: 星期三 11:01      │  │  │
│                             │  │ 节假日: 无              │  │  │
│                             │  └──────────────────────────┘  │  │
│                             └──────────────────────────────┘  │
│                                                              │
│              [← 重置]     [→ AI 推理链路]                     │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

#### A. 上传区域

- `dragenter` / `dragover` / `drop` 事件处理，拖拽时高亮边框
- 点击选择文件：`<input type="file" accept="image/*">` 隐藏，由按钮触发
- 三种视觉状态：
  - **待上传**：虚线边框 + 拖拽提示
  - **上传中**：按钮禁用 + 加载动画 + 状态文字逐行动画
  - **完成**：缩略图预览 + 绿色状态

#### B1. 订单信息卡片

| 字段 | 显示方式 | 数据绑定路径 |
|---|---|---|
| 🏪 餐厅名称 | 大号加粗字体 | `state.orderData.restaurant` |
| 🕐 时间 | 标准日期格式 | `state.orderData.time` |
| 👥 人数 | 数字 + "人" | `state.orderData.people` |
| 🍽️ 菜品列表 | 表格或列表 | `state.orderData.items[]` |

菜品列表每项显示：`名称 × 数量` + 价格 `¥xx` + 特殊标注（辣度 `🌶️`、备注 `📝`）

#### B2. 菜品知识卡片

- 遍历 `state.dishKnowledge[]`
- 每项一个小卡片，显示：
  - **菜品名**：`item.dishName`
  - **特点标签**：`item.features[]`（chip 样式，如 `bg-indigo-100`）
  - **体验方向**：`item.experienceTags[]`（小字标签）
  - **菜品角色**：`item.dishRole`
- 如果某道菜的菜品名未在 `dishKnowledge` 中找到匹配项，显示：
  ```
  ⚠️ 未找到「xxx」的菜品知识
  ```
  灰色文字，不破坏整体布局

#### B3. 实时环境卡片

- ☀️ 天气：`state.realtimeInfo.weather`
- 🕐 当前时间：`state.realtimeInfo.currentTime`
- 🎉 节假日：`state.realtimeInfo.holiday`（为 null 时显示「无」）
- 🚗 交通：`state.realtimeInfo.traffic`（为 null 时不展示此行）

### 7.4 Page 2 — AI 推理链路

```
┌─ Page 2 ────────────────────────────────────────────────────┐
│                                                              │
│  ┌─── C1. 体验理解 ────────────────────────────────────────┐ │
│  │  🟢 MEDIUM  顾客偏好辣味，选择泰酷辣并多加辣...          │ │
│  │     依据：订单中鱿鱼船粉的 spiceLevel=泰酷辣...          │ │
│  │                                                         │ │
│  │  🟡 LOW     由于天气凉爽多云，热汤粉可能带来...          │ │
│  │     依据：实时天气 Cloudy +23°C...                      │ │
│  └──────────────────────────────────────────────────────────┘ │
│                           ↓                                   │
│  ┌─── C2. 对话规划 ────────────────────────────────────────┐ │
│  │  🟢 方向                                                 │ │
│  │    • 推荐搭配解辣饮品，如泰式奶茶或椰青                   │ │
│  │    • 建议将小食拼盘置于中间，方便两人分享                 │ │
│  │                                                         │ │
│  │  🔵 机会点                                               │ │
│  │    • 今天天气晴朗，适合品尝酸辣开胃的泰式酸肠             │ │
│  │                                                         │ │
│  │  🔴 限制                                                 │ │
│  │    • 避免推荐额外加辣，因为已点菜品辣度较高               │ │
│  └──────────────────────────────────────────────────────────┘ │
│                           ↓                                   │
│  ┌─── C3. Runtime Prompt ──────────────────────────────────┐ │
│  │  ┌─────────────────────────────────────────────────┐    │ │
│  │  │ # 角色定义                                       │    │ │
│  │  │                                                  │    │ │
│  │  │ 你是一个 "餐后体验回访电话 AI..."                 │    │ │
│  │  │ ...                                              │    │ │
│  │  └─────────────────────────────────────────────────┘    │ │
│  │  字符数: 4789 | 生成耗时: 76523ms                       │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                              │
│              [← 返回]          [☎️ 创建对话] (暂不实现)       │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

#### C1. 体验理解卡片

- 遍历 `state.experienceUnderstanding.possibilities[]`
- 每条记录一张子卡片：
  - 标题行：置信度标签 + 推测描述
  - 置信度标签样式（根据 `item.confidenceLevel` 的值）：

| `confidenceLevel` 值 | 视觉 | CSS 类 |
|---|---|---|
| `"MEDIUM"` | 🟢 绿色背景 + 白色文字 | `bg-green-600 text-white px-2 py-0.5 rounded` |
| `"LOW"` | 🟡 黄色背景 + 深色文字 | `bg-yellow-400 text-yellow-900 px-2 py-0.5 rounded` |
| 其他 | ⚪ 灰色 | `bg-gray-400 text-white px-2 py-0.5 rounded` |

- 详情行：灰色小字 `"依据：" + item.evidenceSource`

#### C2. 对话规划卡片

数据来自 `state.conversationPlan`，分为三个子区域：

| 子区域 | 数据路径 | 颜色标识 |
|---|---|---|
| 方向 | `item.directions[]` | 🟢 左侧绿色竖条 |
| 机会点 | `item.availableHooks[]` | 🔵 左侧蓝色竖条 |
| 限制 | `item.avoid[]` | 🔴 左侧红色竖条 |

- 每个子区域有标题文字（方向/机会点/限制）
- 内容为无序列表，每条一个 bullet

#### C3. Runtime Prompt 卡片

- `<pre><code>` 代码块样式
- `state.runtimePrompt.finalPrompt` 的内容
- 等宽字体（`font-mono`），可滚动（`overflow-auto`）
- 固定最大高度 `max-h-96`
- 底部元信息行：`字符数: ${state.runtimePrompt.finalPrompt.length} | 生成耗时: ${state.runtimePrompt.assemblyDurationMs}ms`
- 右上角可放「📋 复制」按钮（通过 `navigator.clipboard.writeText()` 实现，可选）

---

## 8. UI 样式规范

### 8.1 整体风格（定稿：深色科技风）

- 页面背景：深灰色 `bg-gray-950`
- 卡片背景：中灰色 `bg-gray-900`，圆角 `rounded-xl`，阴影 `shadow-lg`
- 文字：浅色 `text-gray-100` / `text-gray-300`
- 卡片悬停：轻微上浮效果（`hover:shadow-xl hover:-translate-y-0.5 transition`）
- 步骤之间连接箭头：`→` 字符，居中，`text-2xl text-gray-500`

### 8.2 颜色方案（Tailwind 色值）

| 用途 | 色值 |
|---|---|
| 页面背景 | `bg-gray-950` |
| 卡片背景 | `bg-gray-900` |
| 卡片边框 | `border border-gray-800` |
| 主文字 | `text-gray-100` |
| 次要文字 | `text-gray-400` |
| 主色调（按钮/高亮） | `bg-indigo-600 hover:bg-indigo-700` |
| 成功（已完成状态） | `text-green-400` |
| 警告（进行中） | `text-yellow-400` |
| 错误 | `text-red-400` |
| MEDIUM 置信度标签 | `bg-green-600 text-white` |
| LOW 置信度标签 | `bg-yellow-400 text-yellow-900` |
| 方向（对话规划） | 左侧绿色竖条 `border-l-4 border-green-500` |
| 机会点 | 左侧蓝色竖条 `border-l-4 border-blue-500` |
| 限制 | 左侧红色竖条 `border-l-4 border-red-500` |

### 8.3 响应式

- 主要针对桌面端设计（Demo 场景通常是投影/大屏）
- Page 1 的两栏布局在 `<1024px` 时变为单栏（`flex-col`）
- 最佳体验宽度：`≥1280px`

---

## 9. 实现步骤（按执行顺序）

### Step 1：创建文件结构

```
src/main/resources/static/demo/
├── index.html
├── style.css
└── app.js
```

同时创建后端文件：

```
src/main/java/com/moka/demo/
├── DemoController.java
└── DemoResponse.java
```

### Step 2：实现后端 DemoController + DemoResponse

- 创建 `DemoResponse.java` — 严格按第 4.2 节的字段定义
- 创建 `DemoController.java` — 注入所有必要 bean，手动编排 6 个步骤
- 参考 `ContextPreparationWorkflow` 的构造器注入和 Node 实现
- 天气部分直接参考 `RealtimeNode.execute()` 的完整逻辑（含地址提取、节假日 API）
- 确保 `@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")`

### Step 3：创建 index.html

- HTML5 骨架，`<html lang="zh-CN">`
- Tailwind CSS CDN：`<script src="https://cdn.tailwindcss.com"></script>`
- 引入 `style.css`：`<link rel="stylesheet" href="style.css">`
- 容器元素（全部用 `id` 标记方便 JS 查找）：
  - `#header` — 标题栏
  - `#step-indicator` — 步骤条
  - `#page1` — 页面 1 容器
  - `#page2` — 页面 2 容器（初始 `display:none`）
  - `#footer` — 页脚
- 引入 `app.js`：`<script src="app.js"></script>`（放在 `</body>` 前）

### Step 4：实现 CSS（style.css）

- Tailwind 无法覆盖的自定义动画（如淡入 `@keyframes fadeIn`）
- 上传区域的拖拽高亮状态（`.drag-over` 类）
- 加载脉冲动画（`.pulse-loader`）
- 步骤条样式（`.step-active`、`.step-inactive`、`.step-done`）
- 代码块样式
- 过渡动画

### Step 5：实现 JS（app.js）

**5.1 状态管理**
```javascript
const state = { /* 见第 6 节完整 AppState 对象 */ };
```

**5.2 缓存 DOM 元素**
```javascript
const $ = (id) => document.getElementById(id);
// 页面头部、步骤条、页面容器等
```

**5.3 渲染函数**
- `renderPage()` — 根据 `state.currentPage` 显示/隐藏 `#page1` / `#page2`
- `renderStepIndicator()` — 更新步骤条
- `renderUploadArea()` — 上传区域的三种状态
- `renderProgress(statusText)` — 更新状态文字
- `renderOrderData()` — 从 `state.orderData` 渲染 B1
- `renderDishKnowledge()` — 从 `state.dishKnowledge[]` 渲染 B2
- `renderRealtimeInfo()` — 从 `state.realtimeInfo` 渲染 B3
- `renderExperienceUnderstanding()` — 从 `state.experienceUnderstanding` 渲染 C1
- `renderConversationPlan()` — 从 `state.conversationPlan` 渲染 C2
- `renderRuntimePrompt()` — 从 `state.runtimePrompt` 渲染 C3
- `animatePage2()` — 逐步骤淡入 + 箭头

**5.4 核心 API 函数**
```javascript
async function handleFileUpload(file) {
    state.isUploading = true;
    state.uploadStatus = 'uploading';
    renderUploadArea();
    renderProgress('📤 图片上传中...');

    const formData = new FormData();
    formData.append('file', file);

    // 延迟 500ms 模拟上传动画，然后变状态
    setTimeout(() => {
        renderProgress('🔍 视觉模型识别中...');
    }, 500);

    try {
        const response = await fetch('/api/v1/calls/demo', {
            method: 'POST',
            body: formData
        });

        if (!response.ok) throw new Error(`服务器错误: ${response.status}`);

        const data = await response.json();
        // 填入 state
        state.orderData = data.orderData;
        state.dishKnowledge = data.dishKnowledge;
        state.realtimeInfo = data.realtimeInfo;
        state.experienceUnderstanding = data.experienceUnderstanding;
        state.conversationPlan = data.conversationPlan;
        state.runtimePrompt = data.runtimePrompt;

        state.isUploading = false;
        state.uploadStatus = 'done';
        renderUploadArea();
        renderProgress('✅ 解析完成');
        renderOrderData();
        renderDishKnowledge();
        renderRealtimeInfo();
        renderStepIndicator();  // 步骤 ② 变为可用

    } catch (err) {
        state.isUploading = false;
        state.uploadStatus = 'error';
        state.uploadError = err.message;
        renderUploadArea();
    }
}
```

**5.5 事件绑定**
- 拖拽上传
- 文件选择 input change
- 「开始解析」按钮 click → `handleFileUpload(file)`
- 「← 重置」按钮 click → 重置所有 state，重新渲染
- 「→ AI 推理链路」按钮 click → 切换页面 + 触发动画
- 「← 返回」按钮 click → 回到页面 1
- 步骤条点击步骤 ② → 切换到页面 2（仅数据已加载时有效）

### Step 6：HTML 容器 + 静态占位

详细列出各个容器元素应该包含的静态占位 HTML。编码 AI 需要先搭建出完整的 DOM 结构，再通过 JS 动态填充数据。

各容器的关键元素列表：

```
#page1
├── #upload-area         上传区域
│   ├── #drop-zone       拖拽区
│   ├── #file-input      隐藏的 <input type="file">
│   ├── #upload-btn      「开始解析订单」按钮
│   └── #progress-text   状态文字区
│
├── #result-panel        结果面板
│   ├── #order-card      B1 订单信息
│   ├── #dish-card       B2 菜品知识
│   └── #realtime-card   B3 实时环境
│
└── #page1-buttons       操作按钮
    ├── #reset-btn       「重置」
    └── #next-btn        「→ AI 推理链路」

#page2
├── #experience-box      C1 体验理解
│   └── #experience-list   动态填充
├── #arrow1              箭头
├── #plan-box            C2 对话规划
│   └── #plan-content      动态填充
├── #arrow2              箭头
├── #prompt-box          C3 Runtime Prompt
│   ├── #prompt-code       动态填充 <pre><code>
│   └── #prompt-meta       元信息
└── #page2-buttons       操作按钮
    ├── #back-btn        「← 返回」
    └── #call-btn        「☎️ 创建对话」(暂不实现)
```

> **编码 AI 注意**：在 `index.html` 中为每个容器元素写上 `id`，初始状态显示占位文字。
> 例如：`#order-card` 初始显示 `<p class="text-gray-500">等待上传...</p>`。

### Step 7：实现上传流程

按第 5.2 节的「阶段一」和「阶段二」时序实现完整上传流程。

### Step 8：实现页面 2 渲染 + 动画

- `animatePage2()` 函数使用 `setTimeout` 链控制淡入

### Step 9：实现错误处理

全覆盖三种错误场景：

```javascript
// 网络错误
if (!response.ok) { /* 显示服务器错误 */ }

// 文件类型校验
if (!file.type.startsWith('image/')) { /* 提示上传图片 */ }

// 后端返回错误（如 Vision API 失败）
if (!data.success) { /* 显示后端错误信息 */ }
```

每个错误状态：红色文字 + 错误详情 + 可点击「重试」按钮。

---

## 10. 边界情况

### 10.1 图片上传前

- 上传区域显示拖拽提示「将小票照片拖到此处，或点击选择」
- 结果面板所有卡片显示灰色占位文字「等待上传...」
- 「开始解析」按钮 `disabled`（灰色不可点击）
- 「→ AI 推理链路」按钮 `disabled`

### 10.2 图片上传中

- 「开始解析」按钮 `disabled`，显示「⏳ 解析中...」
- 状态文字区域每 500ms 更新一次状态（上传中 → 识别中）
- 结果面板显示加载骨架屏（灰色脉冲动画区块）

### 10.3 图片上传失败

- 上传区域底部显示红色错误提示框
- 「开始解析」按钮恢复为可用（可重试）
- 结果面板保持为空或显示上次数据（如有）
- 错误信息清晰展示具体原因

### 10.4 菜品知识未匹配

遍历 `dishKnowledge` 时，对比 `orderData.items[]` 中的菜品名：
```javascript
orderData.items.forEach(item => {
    const matched = dishKnowledge.find(dk => dk.dishName === item.name);
    if (!matched) {
        // 显示灰色「未找到 xxx 的菜品知识」
    }
});
```

### 10.5 Pipeline 数据为空

对于可能为空的数组或 null 字段：

| 场景 | 显示 |
|---|---|
| `experienceUnderstanding.possibilities` 为空/未提供 | `ℹ️ 暂无体验理解数据` |
| `conversationPlan.directions/availableHooks/avoid` 为空 | 该子区域显示 `无` |
| `realtimeInfo.holiday` 为 null | 显示 `无` 或隐藏该行 |
| `runtimePrompt` 为 null | 显示 `暂无数据` |

---

## 11. 与 `main` 分支的关系

| 变更类型 | 文件 | 说明 |
|---|---|---|
| **新增（仅 demo 分支）** | `src/main/resources/static/demo/index.html` | 前端页面骨架 |
| **新增（仅 demo 分支）** | `src/main/resources/static/demo/style.css` | 前端样式 |
| **新增（仅 demo 分支）** | `src/main/resources/static/demo/app.js` | 前端逻辑 |
| **新增（仅 demo 分支）** | `src/main/java/com/moka/demo/DemoController.java` | Demo 端点 |
| **新增（仅 demo 分支）** | `src/main/java/com/moka/demo/DemoResponse.java` | Demo 响应 DTO |
| **不修改** | `src/main/resources/application.yml` | 保持已清理后的版本 |
| **不修改** | `src/main/java/` 下除 `demo/` 包外的任何现有代码 | 仅新增，不改原有逻辑 |

---

## 12. 验收标准

完成后应满足：

1. ✅ 浏览器访问 `http://localhost:8080/demo/index.html` 正常显示（深色主题）
2. ✅ 拖拽或选择小票照片（`image/*`），「开始解析」按钮变为可用
3. ✅ 点击「开始解析」，按钮禁用，状态文字逐步更新（📤 → 🔍 → ✅）
4. ✅ 解析完成后，Page 1 正确显示订单信息（餐厅/时间/人数/菜品列表）
5. ✅ Page 1 正确显示菜品知识（菜品名/特点标签/体验方向），未匹配项显示灰色提示
6. ✅ Page 1 正确显示实时环境（天气/时间/节假日）
7. ✅ 「→ AI 推理链路」按钮可用，点击后切换到 Page 2
8. ✅ Page 2 的体验理解逐条显示，每条带置信度标签（🟢 MEDIUM / 🟡 LOW）
9. ✅ Page 2 的对话规划分三个区域（方向/机会点/限制）显示
10. ✅ Page 2 的 Runtime Prompt 以代码块形式展示，底部有字符数和耗时
11. ✅ 「返回」按钮可回到 Page 1，「重置」按钮可清空所有数据回到初始状态
12. ✅ 上传失败时显示明确的红色错误提示，可重试
13. ✅ 「☎️ 创建对话」按钮存在但点击暂无响应
14. ✅ 所有 API Key 不出现，不依赖任何未提交的配置文件
15. ✅ `demo` 分支和 `main` 分支的代码隔离，main 分支不受影响
