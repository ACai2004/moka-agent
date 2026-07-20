# Moka Agent MVP 工程搭建计划

> **文档版本**：1.0
> **参考依据**：`ARCHITECTURE.md`（系统架构设计）、`Static System Prompt.md`（静态 System Prompt）
> **目标读者**：执行编码的开发人员（人 or AI），按此文档可逐模块搭建出可运行的 MVP 系统

---

## 决策记录（开始前必读）

### D1: LLM 提供商与模型选型

| 用途 | 模型 | 提供商 | 备注 |
|---|---|---|---|
| 文本推理（ExperienceUnderstanding / ConversationPlanner） | `deepseek-v4` | DeepSeek（直连） | 直接调用 DeepSeek API，不经 OpenRouter |
| 视觉解析（OrderUnderstanding：小票照片→结构化数据） | `qwen3.6-plus` | OpenRouter | 视觉模型走 OpenRouter |
| Embedding | 不需要 | — | DishRetriever 使用精确匹配（HashMap），无需 Embedding |

**API 配置**（双模型独立配置）：

| 服务 | base URL | API Key |
|---|---|---|
| DeepSeek（文本） | `https://api.deepseek.com` | `${DEEPSEEK_API_KEY}` |
| OpenRouter（视觉） | `https://openrouter.ai/api/v1` | `${OPENROUTER_API_KEY}` |

### D2: 模块包结构

```
com.moka
├── MokaApplication.java                  # Spring Boot 启动类
├── ai
│   ├── workflow
│   │   ├── WorkflowNode.java             # Node 接口（15.2.1 节）
│   │   └── ContextPreparationWorkflow.java # Workflow 编排
│   ├── agent
│   │   ├── OrderUnderstandingAgent.java  # @AiService 接口
│   │   ├── ExperienceUnderstandingAgent.java
│   │   └── ConversationPlannerAgent.java
│   ├── retrieval
│   │   └── DishRetriever.java
│   ├── tools
│   │   └── WeatherTool.java
│   ├── context
│   │   └── ContextAssembler.java
│   ├── prompt
│   │   └── PromptAssembler.java
│   └── adapter
│       └── VolcanoAdapter.java
├── biz
│   ├── entity
│   │   ├── User.java
│   │   ├── Restaurant.java
│   │   ├── Order.java
│   │   └── CallTask.java
│   ├── repository
│   │   ├── UserRepository.java
│   │   ├── RestaurantRepository.java
│   │   ├── OrderRepository.java
│   │   └── CallTaskRepository.java
│   └── controller
│       ├── OrderController.java
│       └── CallController.java
└── common
    ├── config
    │   ├── LangChain4jConfig.java
    │   └── OpenRouterConfig.java
    ├── mock
    │   └── MockLlmService.java           # 开发阶段 Mock LLM
    └── exception
        ├── LlmException.java
        └── GlobalExceptionHandler.java
```

### D3: 菜品知识数据（自建样本数据）

MVP 阶段没有真实餐厅数据，需要**手动创建一批样本数据**。建议包含 2~3 家虚构餐厅 + 每餐厅 5~8 道菜。数据结构见第 9.3 节，样本数据文件路径：

```
src/main/resources/data/sample-restaurants.json
src/main/resources/data/sample-dish-knowledge.json
```

**创建时机**：Phase 2B 之前完成。

### D4: Mock LLM 策略

开发阶段避免每次调用真实 OpenRouter API，策略如下：

| 场景 | Mock 方式 |
|---|---|
| 单元测试 | 用固定 JSON 响应模拟 `@AiService` 代理 |
| 本地开发 Workflow | 用一个 `MockLlmService` 开关，返回预设的结构化数据 |
| 集成测试 | 调用真实 OpenRouter API（需配置 API Key） |

`application-dev.yml` 中配置 `moka.llm.mock=true` 时启用 Mock，`false` 时调用真实 API（DeepSeek 文本 + OpenRouter 视觉）。

### D5: 本地依赖环境

| 依赖 | 方式 | 备注 |
|---|---|---|
| PostgreSQL | Docker (`docker-compose.yml`) | 业务数据存储 |
| Redis | Docker (`docker-compose.yml`) | 缓存 / 会话管理 |
| DeepSeek API | 外部服务 | 文本推理模型 |
| OpenRouter API | 外部服务 | 视觉模型（小票解析） |

---

## 分阶段搭建计划

---

### Phase 0：项目脚手架 + 基础设施

**目标**：一个能启动的 Spring Boot 项目，依赖全部配置好。

#### 步骤 0.1：创建 Maven 项目结构

```
moka-agent/
├── pom.xml
├── docker-compose.yml
├── src/
│   └── main/
│       ├── java/com/moka/
│       │   └── MokaApplication.java
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml    # 开发环境配置（含 Mock 开关）
│           └── logback-spring.xml
```

#### 步骤 0.2：编写 pom.xml

必须包含的依赖组：

```xml
<!-- Spring Boot 3.x Starter -->
spring-boot-starter-web
spring-boot-starter-data-jpa

<!-- LangChain4j（核心依赖） -->
langchain4j-core
langchain4j-open-ai             <!-- OpenRouter 兼容 OpenAI 接口 -->
langchain4j-spring-boot-starter <!-- Spring Boot 整合 -->

<!-- 数据库 -->
postgresql (或 mysql-connector-j)

<!-- Redis -->
spring-boot-starter-data-redis

<!-- 工具 -->
lombok
jackson-databind
```

**关键配置**：LangChain4j 的 `open-ai` 模块可以兼容 OpenRouter API，只需覆盖 `baseUrl` 即可。

#### 步骤 0.3：配置 application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/moka
    username: moka
    password: ${DB_PASSWORD}

moka:
  llm:
    mock: true   # 开发阶段默认为 true

  deepseek:
    api-key: ${DEEPSEEK_API_KEY}
    base-url: https://api.deepseek.com
    text-model: deepseek-v4

  openrouter:
    api-key: ${OPENROUTER_API_KEY}
    base-url: https://openrouter.ai/api/v1
    vision-model: qwen3.6-plus
```

#### 步骤 0.4：创建 docker-compose.yml

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: moka
      POSTGRES_USER: moka
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"
  redis:
    image: redis:7
    ports:
      - "6379:6379"
```

#### 步骤 0.5：验证

```bash
docker-compose up -d
mvn spring-boot:run
# 控制台输出 Spring Boot 启动成功，无报错
```

✅ **完成标志**：`mvn clean compile` 成功，`mvn spring-boot:run` 启动无异常。

---

### Phase 1：核心数据模型

**目标**：定义所有 Agent 之间传递的 POJO，这是整个系统的"通信协议"。

#### 步骤 1.1：Layer 1 数据模型

创建包 `com.moka.ai.context`，包含以下 Record / POJO：

| 类名 | 字段 | 用途 |
|---|---|---|
| `OrderData` | `restaurant, time, people, items, duration` | 订单解析结果（第 9.2 节） |
| `DishItem` | `name, quantity, spiceLevel, notes, category, price` | 单道菜品的完整信息（数量、辣度、备注等） |
| `RestaurantProfile` | `restaurantName, positioning, experienceTags, environmentFeatures, serviceFeatures` | 餐厅信息（第 9.1 节） |
| `DishKnowledge` | `dishName, dishRole, features, experienceTags` | 菜品知识（第 9.3 节），含用餐角色 |
| `DishRole` | 枚举：`SIGNATURE, MAIN, SIDE, STAPLE, DESSERT, DRINK, CONDIMENT` | 菜品在用餐中的角色 |
| `RealtimeInfo` | `weather, holiday, traffic, currentTime` | 实时信息（第 8 节） |

**注意事项**：
- 所有类使用 Java `record` 或 `@Data`（Lombok）
- `OrderData.items` 类型为 `List<DishItem>`，保留小票原始细节（不压缩为纯菜名列表）
- `DishItem` 中 `quantity` 默认 1，其余可选字段保留 `null` 语义（小票没写就是 null）
- `RealtimeInfo` 中的字段 MVP 阶段可为 `String` 类型，不强求结构化

**DishItem 定义示例**：

```java
public record DishItem(
    String name,            // 菜品名称（必填）
    int quantity,           // 数量，默认 1
    String spiceLevel,      // 辣度要求（可选），如"不可免辣"、"微辣"
    String notes,           // 备注（可选），如"加牛肉"、"不要香菜"
    String category,        // 品类（可选），如"主食"、"饮品"、"小菜"
    String price            // 单价（可选），String 类型
) {
    public DishItem {
        quantity = quantity <= 0 ? 1 : quantity;
    }

    // 快捷构造：只有菜名时
    public DishItem(String name) {
        this(name, 1, null, null, null, null);
    }
}
```

**OrderData 定义示例**：

```java
public record OrderData(
    String restaurant,        // 餐厅名
    String time,              // 用餐时间，如"周五 19:20"
    int people,               // 人数
    List<DishItem> items,     // 菜品列表（保留完整信息）
    String duration           // 用餐时长，如"2h15min"
) {}
```

**DishRole 枚举定义**：

```java
public enum DishRole {
    SIGNATURE,   // 招牌/特色菜 → 值得主动聊
    MAIN,        // 主菜 → 可以聊
    SIDE,        // 配菜/辅料 → 不主动提及
    STAPLE,      // 主食 → 可以聊
    DESSERT,     // 甜品 → 自然流动时提及
    DRINK,       // 饮品 → 普通不提，特色可一带而过
    CONDIMENT    // 调料/小料 → 不提及
}
```

**DishKnowledge 定义示例**（包含新增的 dishRole）：

```java
public record DishKnowledge(
    String dishName,
    DishRole dishRole,           // 菜品在用餐中的角色，控制对话参与程度
    List<String> features,       // 菜品特点，如["经典泰式", "酸辣开胃"]
    List<String> experienceTags  // 体验方向，如["下饭", "招牌必点"]
) {}
```

> **设计说明**：`dishRole` 是菜品知识库的固有属性，在数据录入时一次性标注。Agent 根据 `dishRole` 决定参与程度，从 `features` 和 `experienceTags` 自行推导自然切入角度。详见 `ARCHITECTURE.md` 第 9.3 节。

#### 步骤 1.2：Layer 2 数据模型

| 类名 | 字段 | 关键约束 |
|---|---|---|
| `ExperienceUnderstanding` | `possibilities: List<ExperiencePossibility>` | 每个可能性带确定性标记 |
| `ExperiencePossibility` | `description, confidenceLevel(LOW/MEDIUM/HIGH), evidenceSource` | `confidenceLevel` 不允许为 HIGH（第 4.2 节） |

#### 步骤 1.3：Layer 3 数据模型

| 类名 | 字段 | 备注 |
|---|---|---|
| `ConversationPlan` | `directions: List<String>` | 方向 |
| | `availableHooks: List<String>` | 机会点 |
| | `avoid: List<String>` | 限制 |

> **预留说明**：`subGoals`（子目标分解）和 `contingencies`（条件分支预案）是第 15.2.4 节提出的未来演进字段，MVP 阶段暂不定义。

#### 步骤 1.4：WorkflowContext（第 15.2.2 节）

```java
@Data
public class WorkflowContext {
    // 输入
    private String photoBase64;            // 原始输入：订单照片

    // Layer 1
    private OrderData order;
    private List<DishKnowledge> dishes;
    private RealtimeInfo realtime;

    // Layer 2
    private ExperienceUnderstanding experience;

    // Layer 3
    private ConversationPlan plan;

    // 最终输出
    private String runtimePrompt;

    // 元数据（预留）
    private Map<String, Object> metadata = new HashMap<>();
    private int executionRound = 0;
}
```

#### 步骤 1.5：RuntimePrompt 最终输出

```java
@Data
public class RuntimePrompt {
    private String finalPrompt;            // 合并后的完整 Prompt
    private String systemPromptVersion;    // 版本号（预留）
    private long generatedAt;              // 生成时间戳
    private int assemblyDurationMs;        // 组装耗时
}
```

✅ **完成标志**：所有数据模型类可编译，可编写独立测试验证序列化为 JSON。

---

### Phase 1.5：Static System Prompt 加载

#### 步骤 1.5.1

`Static System Prompt.md` 已经写好（位于项目根目录）。将该文件**复制或引用到资源目录**：

```
src/main/resources/prompt/system-prompt.md
```

#### 步骤 1.5.2

创建 `SystemPromptLoader.java`：

```java
@Component
public class SystemPromptLoader {
    public String load() {
        // 从 classpath:prompt/system-prompt.md 读取内容
        // 返回值作为 Static System Prompt
    }
}
```

**注意**：未来如果需要版本管理（第 11.4 节），可以改为从 `prompt/v1/system-prompt.md`、`prompt/v2/system-prompt.md` 等路径加载。

✅ **完成标志**：`SystemPromptLoader` 能正确返回 `Static System Prompt.md` 的文本内容。

---

### Phase 2：上游数据模块（第 12 节第一优先 [1][2][3]）

**目标**：实现前 3 个数据模块，每个可独立运行、独立测试。

#### 步骤 2.0：Mock LLM 机制

在开发阶段，LLM 调用都应可 mock。在 `common/mock/` 下创建：

```java
@Component
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "true")
public class MockLlmService {
    // 为每个 Agent 提供预设的 JSON 响应
    public String mockOrderUnderstanding() { /* 返回预设 OrderData JSON */ }
    public String mockExperienceUnderstanding() { /* 返回预设 ExperienceUnderstanding JSON */ }
    public String mockConversationPlan() { /* 返回预设 ConversationPlan JSON */ }
}
```

**注意**：@AiService 的 mock 可以通过 Spring 的 `@MockitoBean` 或手动实现接口 mock 来完成。具体方式取决于 LangChain4j 的配置方式。

#### 步骤 2.1：OrderUnderstandingAgent

**文件**：`com.moka.ai.agent.OrderUnderstandingAgent.java`

```java
@AiService
public interface OrderUnderstandingAgent {

    @SystemPrompt("""
        你是一个订单理解专家。分析用户上传的餐厅小票照片，
        提取结构化订单信息。
        只输出 JSON 格式的结果，不要多余内容。
        """)
    OrderData analyzeOrder(@V("photo") String photoBase64);
}
```

**注意事项**：
- 视觉模型需要支持图片输入（通过 OpenRouter 的多模态模型）
- 如果 `qwen3.6-plus` 不支持视觉，需要单独设定一个视觉模型
- **错误处理**：如果照片无法解析，应返回特定的错误标记，而非抛异常

**单元测试**：输入一张测试用的小票照片（`src/test/resources/test-receipt.jpg`），验证输出结构。

#### 步骤 2.2：菜品知识源数据 + DishRetriever

**2.2A — 创建样本数据**（非编码任务）

```
src/main/resources/data/sample-restaurants.json
src/main/resources/data/sample-dish-knowledge.json
```

样本数据需要至少包含：
- 2~3 家虚构餐厅（含名称、定位、环境特征、服务特征）
- 每餐厅 5~8 道菜（含菜品名、特点、体验方向）

格式见 `ARCHITECTURE.md` 第 9.1 / 9.3 节。

**2.2B — DishRetriever**

```java
@Component
public class DishRetriever {
    // MVP 策略：精确匹配（HashMap），无需 Embedding
    // 1. 先用菜品名做精确匹配（O(1) 查找，覆盖所有场景）
    // 2. 匹配不到的菜品返回空（不阻塞下游）
    
    public List<DishKnowledge> retrieve(List<String> dishNames) {
        // 根据菜品名精确匹配知识库
        // 未匹配到的菜品返回空（不报错）
    }
}
```

**MVP 阶段要点**：
- 不上向量数据库（第 7.1 节）
- 内存加载 + 关键词匹配即可
- 未找到的菜品跳过，不影响下游流程

**单元测试**：输入 `["麻婆豆腐"]`，验证返回匹配结果。

#### 步骤 2.3：RealtimeInformationTool

```java
@Component
public class WeatherTool {
    @Tool("获取指定城市的当前天气情况")
    public String getWeather(@P("城市名") String city) {
        // 调用天气 API（免费方案：wttr.in 或 OpenWeatherMap）
        // 失败时返回 "未知" 而不是抛异常
    }
}
```

**注意事项**：
- 文档第 8 节明确说"不需要 Agent，通过 Tool / API 获取"
- 因此这不是 `@AiService`，而是 `@Tool`
- MVP 阶段天气 API 失败应优雅降级（返回"未知"），不阻塞 Workflow

**单元测试**：Mock 天气 API，验证输出格式。

#### 步骤 2.4：订单照片上传 API

```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    @PostMapping("/upload-photo")
    public ResponseEntity<OrderData> uploadPhoto(@RequestParam("file") MultipartFile file) {
        // 接收照片 → 调用 OrderUnderstandingAgent → 返回 OrderData
    }
}
```

**注意事项**：
- 文件上传是 MVP 入口，后续可被其他触发方式取代
- 建议同时保留直接传入 base64 的方式（方便测试）

✅ **完成标志**：三个模块各自独立可运行；上传照片 API 返回结构化的 `OrderData`。

---

### Phase 2.5：提前打通数据流（第 12 节第一优先 [4][5] 的骨架）

**目标**：在实现真正的 AI Agent 之前，先让数据流能跑通端到端。

#### 步骤 2.5.1：ContextAssembler

```java
@Component
public class ContextAssembler {

    public String assemble(WorkflowContext ctx) {
        // 将三层数据组装为可读的 Dynamic Context 文本
        // 格式：按 Layer 1 → Layer 2 → Layer 3 顺序拼接
        // 返回纯文本，不要 JSON
    }
}
```

**输入来源**：
- Layer 1：`ctx.order` + `ctx.dishes` + `ctx.realtime`
- Layer 2：`ctx.experience`（Phase 2.5 中用 MockLlmService 的 mock 数据）
- Layer 3：`ctx.plan`（Phase 2.5 中用 MockLlmService 的 mock 数据）

#### 步骤 2.5.2：PromptAssembler

```java
@Component
public class PromptAssembler {

    private final SystemPromptLoader systemPromptLoader;

    public RuntimePrompt assemble(WorkflowContext ctx) {
        String systemPrompt = systemPromptLoader.load();
        String dynamicContext = contextAssembler.assemble(ctx);
        String runtimePrompt = systemPrompt + "\n\n---\n\n" + dynamicContext;
        // 返回封装好的 RuntimePrompt
    }
}
```

**注意事项**：
- 第 3.1 节：由于火山引擎不支持分别传入，System Prompt 和 Dynamic Context 合并为一个完整 Prompt
- 分隔符 `\n\n---\n\n` 是推荐的，实际可根据效果调整

#### 步骤 2.5.3：端到端数据流集成测试（Mock 版）

创建一个集成测试，输入 mock 的 OrderData，经过完整流程后验证输出了 Runtime Prompt。

**测试路径**：

```
mock OrderData → [DishRetriever] → mock DishKnowledge
              → [WeatherTool]    → mock RealtimeInfo
              → [MockLlmService] → mock ExperienceUnderstanding + ConversationPlan
              → [ContextAssembler] → Dynamic Context 文本
              → [PromptAssembler]  → Runtime Prompt
```

**验证点**：
- Dynamic Context 包含三层信息
- Runtime Prompt 包含 System Prompt + Dynamic Context
- 输出不是空的

✅ **完成标志**：端到端数据流跑通，从 mock 输入到 Runtime Prompt 输出完整验证。

---

### Phase 3：核心 AI 推理模块（第 12 节第二优先 [4][5]）

**目标**：实现真正的 AI 推理，替换 Phase 2.5 中的 mock。

#### 步骤 3.1：ExperienceUnderstandingAgent

```java
@AiService
public interface ExperienceUnderstandingAgent {

    @SystemPrompt("""
        你是一个餐后体验分析专家。
        根据订单信息、菜品知识和实时环境，
        推测本次用餐可能存在的体验场景。

        关键约束（必须遵守）：
        1. 所有输出必须是「可能性」，不是「结论」
        2. 必须保持低确定性
        3. 禁止将推测当作事实
        4. 每条可能性必须标注 confidenceLevel（只能是 LOW 或 MEDIUM，不允许 HIGH）
        5. 如果信息不足以判断，输出空列表

        输出格式：JSON 数组，每项包含 description 和 confidenceLevel。
        """)
    ExperienceUnderstanding analyze(
        @V("order") OrderData order,
        @V("dishes") List<DishKnowledge> dishes,
        @V("realtime") RealtimeInfo realtime
    );
}
```

**关键约束**（第 4.2 节）：
- 不允许输出确定性结论
- 禁止使用"用户一定..."、"用户肯定..."等措辞
- `confidenceLevel` 不允许为 HIGH

**测试思路**：给一组固定输入，验证输出中没有确定性断言词。（集成测试阶段验证）

#### 步骤 3.2：ConversationPlannerAgent

```java
@AiService
public interface ConversationPlannerAgent {

    @SystemPrompt("""
        你是一个对话规划专家。
        根据订单信息、菜品知识、实时信息以及体验理解分析，
        为 Voice Agent 生成对话策略。

        输出结构：
        1. Directions（方向）：当前适合关注什么
        2. Available Hooks（机会点）：可以自然利用的话题
        3. Avoid（限制）：应避免的方向

        重要约束（必须遵守）：
        1. 不要输出聊天脚本（不要写"第一句话说什么，第二句话说什么"）
        2. 不要指定具体的台词
        3. 方向是框架性的，不是时间线顺序
        4. Hooks 是自然切入点，不是待办清单
        5. 根据菜品的 dishRole 决定交流参与程度：
           - SIGNATURE + MAIN：用户表现出兴趣时可以深入聊
           - STAPLE + DESSERT：自然流动时提及
           - SIDE + CONDIMENT：不主动提及，用户说到再跟
           - DRINK：普通饮品不提，特色饮品可一带而过
        """)
    ConversationPlan plan(
        @V("order") OrderData order,
        @V("dishes") List<DishKnowledge> dishes,
        @V("realtime") RealtimeInfo realtime,
        @V("experience") ExperienceUnderstanding experience
    );
}
```

**关键约束**（第 4.3 节）：
- 绝对不要输出聊天脚本
- 输出的是"方向/机会点/限制"，不是"第一句话问天气，第二句话问菜品"

#### 步骤 3.3：替换 Phase 2.5 中的 Mock

更新 `ContextPreparationWorkflow` 中的节点注册，将 mock 实现替换为真实 Agent 实现。

**注意**：替换后应保留切换回 mock 的能力（通过 `moka.llm.mock` 配置）。

✅ **完成标志**：输入完整真实数据，两个 Agent 返回非空且符合约束的结构化输出。

---

### Phase 4：Workflow 编排

**目标**：将各模块组装为可执行的 Pipeline。

#### 步骤 4.1：WorkflowNode 接口

```java
public interface WorkflowNode {
    String nodeName();
    WorkflowContext execute(WorkflowContext ctx);
    // 默认：该节点是否可选
    default boolean isOptional() { return false; }
    default WorkflowContext fallback(WorkflowContext ctx) { return ctx; }
}
```

**设计说明**：
- 不使用泛型 `<I, O>`，统一为 `WorkflowContext → WorkflowContext`（避免第 15.2.1 节接口中 `ctx` 和 `input` 的冗余问题）
- 每个 Node 从 `ctx` 读取自己需要的字段，写入自己负责的字段
- 可选节点（如未来的 Reflection Node）可以在不实现时走 fallback

#### 步骤 4.2：为每个 Agent 创建 Node 适配器

| 适配器类 | 内部调用 | 读写 ctx 字段 |
|---|---|---|
| `OrderNode` | `OrderUnderstandingAgent` | 读: `photoBase64`；写: `order` |
| `DishNode` | `DishRetriever` | 读: `order.dishes`；写: `dishes` |
| `RealtimeNode` | `WeatherTool` | 读: 无（或订单中的城市）；写: `realtime` |
| `ExperienceNode` | `ExperienceUnderstandingAgent` | 读: `order, dishes, realtime`；写: `experience` |
| `PlannerNode` | `ConversationPlannerAgent` | 读: `order, dishes, realtime, experience`；写: `plan` |
| `AssemblyNode` | `ContextAssembler` + `PromptAssembler` | 读: `order, dishes, realtime, experience, plan`；写: `runtimePrompt` |

#### 步骤 4.3：ContextPreparationWorkflow

```java
@Component
public class ContextPreparationWorkflow {

    private final List<WorkflowNode> nodes;

    public ContextPreparationWorkflow(List<WorkflowNode> nodeList) {
        this.nodes = nodeList;  // Spring 自动注入所有 WorkflowNode bean
    }

    public RuntimePrompt execute(String photoBase64) {
        WorkflowContext ctx = new WorkflowContext();
        ctx.setPhotoBase64(photoBase64);

        for (WorkflowNode node : nodes) {
            try {
                ctx = node.execute(ctx);
            } catch (Exception e) {
                if (node.isOptional()) {
                    ctx = node.fallback(ctx);
                    log.warn("Node {} failed, using fallback", node.nodeName());
                } else {
                    throw new WorkflowExecutionException("Node " + node.nodeName() + " failed", e);
                }
            }
        }

        return ctx.getRuntimePrompt();
    }
}
```

**错误处理**（响应 发现 11）：
- 必选节点失败 → 抛出 `WorkflowExecutionException`，中断流程
- 可选节点失败 → 调用 fallback，记录 warn 日志，继续执行
- 节点级别的超时控制（后续增加）

#### 步骤 4.4：完整集成测试

**测试场景 1：Happy Path**
输入一张真实（或 mock）的小票照片 → 验证完整 Runtime Prompt 输出。

**测试场景 2：部分数据缺失**
删除某个菜品知识条目 → 验证 DishNode 优雅降级（返回空列表），不阻塞流程。

**测试场景 3：可选节点失败**
模拟某个可选节点抛异常 → 验证 fallback 生效，流程继续。

✅ **完成标志**：Workflow 可完整运行，三种测试场景通过。

---

### Phase 5：火山引擎对接

**目标**：生成的 Context 真实发送给火山引擎 Voice Agent。

#### 步骤 5.1：定义火山引擎调用接口

```java
@Component
public class VolcanoAdapter {

    // 将 RuntimePrompt 转为火山引擎的请求格式
    public VolcanoRequest toVolcanoRequest(RuntimePrompt prompt, String phoneNumber) {
        // 转换逻辑
    }

    // 调用火山引擎发起通话
    public CallResult initiateCall(VolcanoRequest request) {
        // HTTP 调用火山引擎 API
    }
}
```

**注意**：火山引擎的实际 API 接口格式需要在对接时确认。`ARCHITECTURE.md` 第 10.1 节提到 `/voice/context/generate` 或等价接口。

#### 步骤 5.2：创建通话触发 API

```java
@RestController
@RequestMapping("/api/v1/calls")
public class CallController {

    @PostMapping("/prepare")
    public RuntimePrompt prepareCall(@RequestParam("photo") MultipartFile photo) {
        // 步骤 1~6：触发 Workflow
        // 返回 RuntimePrompt（用于预览 / 调试）
    }

    @PostMapping("/initiate")
    public CallResult initiateCall(@RequestParam("photo") MultipartFile photo,
                                   @RequestParam("phone") String phoneNumber) {
        // 步骤 1~6：触发 Workflow
        // 步骤 7：通过 VolcanoAdapter 发起电话
    }
}
```

**API 路径说明**（响应 发现 7）：
- `/api/v1/calls/prepare` — 本项目对外暴露的 API，返回 Runtime Prompt 供查看/调试
- `/api/v1/calls/initiate` — 触发完整流程 + 发起真实通话
- 火山引擎自身的接口由 `VolcanoAdapter` 封装，不暴露给外部

#### 步骤 5.3：手动验证

实际操作步骤：
1. 准备一张测试用小票照片
2. 调用 `/prepare` 查看生成的 Runtime Prompt
3. 如预期，调用 `/initiate` 发起真实通话
4. 接通后确认 AI 对话风格符合 System Prompt 的设定

✅ **完成标志**：能通过 API 触发 Pipeline、生成 Context、发起真实语音通话。

---

### Phase 6：工程化加固

**目标**：让系统可运维、可管理、可重试。

#### 步骤 6.1：业务实体与 CRUD

| 实体 | 主要字段 | 说明 |
|---|---|---|
| `User` | id, phone, name, createdAt | 用户信息 |
| `Restaurant` | id, name, address, profile(JSON) | 餐厅信息 |
| `Order` | id, restaurantId, userId, photoUrl, orderData(JSON) | 订单 + 解析结果 |
| `CallTask` | id, orderId, status, runtimePrompt(JSON/TEXT), result, createdAt | 通话任务 |

**说明**：使用 Spring Data JPA `@Entity` 标准注解。

#### 步骤 6.2：实时信息刷新机制

第 11.4 节：Context 生成后到实际通话可能有时间差。

```java
// 增量刷新，不重新跑完整 Workflow
public class ContextRefreshService {
    public void refreshRealtimeInfo(WorkflowContext ctx) {
        // 只重新获取天气和时间
        // 不重新执行 OrderUnderstanding、ExperienceUnderstanding 等
    }
}
```

#### 步骤 6.3：通话重试策略

- 第一次未接通时：
  - **可复用**：`OrderUnderstanding`、`DishRetrieval`、`ExperienceUnderstanding`、`ConversationPlan`
  - **需刷新**：`RealtimeInfo`（天气/时间可能变化）
- 实现方式：将 Phase 1~5 的结果缓存（Redis），重试时只执行 `RealtimeNode` + `AssemblyNode`

#### 步骤 6.4：System Prompt 版本管理

- 资源文件按版本组织：`prompt/v1/system-prompt.md`、`prompt/v2/system-prompt.md`
- `SystemPromptLoader` 从配置读取当前版本号
- 每个 `CallTask` 记录使用的 Prompt 版本

#### 步骤 6.5：质量评估（第 11.4 节）

MVP 阶段至少实现一个简单的质量检查：

```java
@Component
public class ContextQualityChecker {
    // 自动检查生成的 Runtime Prompt 是否存在以下问题：
    // 1. 是否包含确定的结论词（"一定"、"肯定"）
    // 2. 是否有聊天脚本迹象（"第一句话..."）
    // 3. 重要字段是否为空
    // 输出评分或 PASS/FAIL
}
```

后续可演进为 LLM self-evaluation 或 A/B 测试。

✅ **完成标志**：完整的业务管理 API，支持重试/缓存/版本管理/质量检查。

---

## 完整路线图总览

```
Phase 0 (脚手架)     → 可启动的 Spring Boot + Docker + OpenRouter 配置
Phase 1 (数据模型)   → 所有 POJO 定义完成
Phase 1.5 (Prompt)  → Static System Prompt 加载器完成
Phase 2 (上游模块)   → [1][2][3] 独立可运行 + 上传 API
Phase 2.5 (数据流)   → 端到端跑通（Mock LLM）
Phase 3 (核心推理)   → [4][5] 真实 AI Agent 替换 Mock
Phase 4 (Workflow)  → ContextPreparationWorkflow 完整可执行
Phase 5 (火山对接)   → 真实通话可发起
Phase 6 (工程加固)   → 业务管理 + 重试 + 缓存 + 版本 + 质量
```

---

## 每个阶段启动前的自查清单

开始新阶段前，确认以下事项：

- [ ] 前一阶段的 **完成标志** 已达成
- [ ] `ARCHITECTURE.md` 中相关章节已经重新阅读确认
- [ ] 本阶段涉及的 LLM 调用确认 mock/mock 开关状态
- [ ] 测试数据已就绪（样本菜品、测试小票照片等）
- [ ] 本阶段新增的模块是否遵循了第 15.2 节的演进预留建议

---

## 参考

- `ARCHITECTURE.md` — 系统架构设计（权威参考）
- `Static System Prompt.md` — 静态 System Prompt 内容
- `整体框架.md` — 早期设计讨论（注意：模块命名以 `ARCHITECTURE.md` 为准）
