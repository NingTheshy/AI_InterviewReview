# AI 面试复盘系统 - 技术架构设计文档

> 版本: v1.0 MVP  
> 日期: 2026-05-26  
> 状态: 已确认

---

## 一、技术栈选型及版本

### 1.1 后端技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 LTS | 长期支持版本，Spring Boot 3.x 最低要求 |
| Spring Boot | 3.2.5 | 主框架，提供自动配置、嵌入式服务器 |
| Spring Security | 6.2.x | 安全框架，处理认证授权 |
| Spring Web (MVC) | 6.1.x | Web 框架，RESTful API 支持 |
| MyBatis-Plus | 3.5.5 | ORM 框架，简化 CRUD 操作 |
| MySQL Connector/J | 8.3.x | MySQL JDBC 驱动 |
| Druid | 1.2.21 | 数据库连接池，提供监控功能 |
| Knife4j | 4.4.0 | API 文档生成（基于 Swagger） |
| Lombok | 1.18.32 | 简化 Java 代码 |
| MapStruct | 1.5.5 | 对象映射，Entity/DTO/VO 转换 |
| JJWT | 0.12.5 | JWT Token 生成与验证 |

### 1.2 前端技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Node.js | 20 LTS | JavaScript 运行时 |
| Vue | 3.4.x | 前端框架 |
| Vite | 5.x | 构建工具 |
| Element Plus | 2.7.x | Vue 3 UI 组件库 |
| ECharts | 5.5.x | 图表库（评分雷达图） |
| Pinia | 2.1.x | 状态管理 |
| Vue Router | 4.3.x | 路由管理 |
| Axios | 1.7.x | HTTP 请求库 |
| TypeScript | 5.x | 类型系统（可选） |
| ESLint | 9.x | 代码规范检查 |
| Prettier | 3.x | 代码格式化 |

### 1.3 开发工具与环境

| 工具 | 版本 | 说明 |
|------|------|------|
| Maven | 3.8.x | 项目构建与依赖管理 |
| MySQL | 8.0+ | 关系型数据库 |
| Git | 2.x | 版本控制 |
| IntelliJ IDEA | 2022.x | 后端 IDE |
| VS Code | - | 前端 IDE |
| Apifox | - | API 接口测试 |

### 1.4 Maven 版本管理

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
</parent>

<properties>
    <java.version>17</java.version>
    <mybatis-plus.version>3.5.5</mybatis-plus.version>
    <druid.version>1.2.21</druid.version>
    <knife4j.version>4.4.0</knife4j.version>
    <jjwt.version>0.12.5</jjwt.version>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
    <lombok.version>1.18.32</lombok.version>
</properties>
```

---

## 二、系统架构设计

### 2.1 架构总览

采用标准**自上而下分层架构**，各层职责清晰、解耦彻底，避免跨层调用。

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (Vue3)                               │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP/REST (JSON)
┌───────────────────────────▼─────────────────────────────────────┐
│                     表现层 (Controller)                           │
│         接收请求 → 参数校验 → 路由分发 → 返回 JSON                 │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                     业务层 (Service / ServiceImpl)               │
│         业务逻辑 → 事务控制 → 数据组装 → 规则校验                 │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                     数据访问层 (Mapper)                           │
│         CRUD → 复杂查询 → 分页 → MyBatis Plus 增强               │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────────────┐
│                        MySQL 数据库                              │
└─────────────────────────────────────────────────────────────────┘
```

**贯穿各层：**

```
┌─────────────────────────────────────────────────────────────────┐
│                     实体层 (Entity / DTO / VO)                   │
│         数据库映射 → 字段约束 → 审计字段 → 数据载体                │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                 支撑层 (Config / Common / Exception / Util)      │
│         全局配置 → 通用工具 → 统一返回 → 异常处理 → 常量定义       │
└─────────────────────────────────────────────────────────────────┘
```

---

### 2.2 各层职责

#### 表现层 (Controller)

| 项目 | 说明 |
|------|------|
| **位置** | 各业务模块的 `controller` 包 |
| **职责** | 接收前端请求、参数校验（`@Valid`）、路由分发、返回 JSON |
| **规范** | 只做三件事：接收参数 → 调用 Service → 返回结果 |
| **禁止** | 不包含业务逻辑、不直接操作数据库、不做 if-else 业务判断 |

#### 业务层 (Service / ServiceImpl)

| 项目 | 说明 |
|------|------|
| **位置** | 各业务模块的 `service` + `service.impl` 包 |
| **职责** | 封装核心业务逻辑、事务控制（`@Transactional`）、数据组装、业务规则校验 |
| **规范** | 接口定义在 `service`，实现在 `service.impl`；返回 VO/DTO，不返回 Entity |
| **禁止** | 不直接操作数据库（通过 Mapper）、不做 HTTP 相关处理 |

#### 数据访问层 (Mapper)

| 项目 | 说明 |
|------|------|
| **位置** | 各业务模块的 `mapper` 包 |
| **职责** | 与数据库交互、CRUD 操作、复杂查询、分页查询 |
| **规范** | 继承 `BaseMapper<T>`；复杂查询用 `@Select` 或 XML；分页用 `Page<T>` |
| **禁止** | 不包含业务逻辑、不调用 Service |

#### 实体层 (Entity / DTO / VO)

| 项目 | 说明 |
|------|------|
| **位置** | `entity`（数据库映射）、`dto`（数据传输）、VO（前端展示） |
| **Entity** | 与数据库表一一映射，包含审计字段（created_at、updated_at）和逻辑删除字段 |
| **DTO** | 层间数据传输，按场景拆分（如 LoginRequest、RegisterRequest） |
| **VO** | 前端展示对象，只包含前端需要的字段 |
| **规范** | 使用 Lombok `@Data`；使用 MyBatis-Plus 注解（`@TableName`、`@TableId`、`@TableLogic`） |

#### 支撑层 (Config / Common / Exception / Util)

| 包 | 职责 |
|----|------|
| `config` | 全局配置类：MyBatisPlusConfig、SecurityConfig、CorsConfig、AsyncConfig |
| `common/constant` | 常量定义：InterviewStatus、ErrorCode、AiConfigType |
| `common/exception` | 异常处理：BusinessException、GlobalExceptionHandler |
| `common/result` | 统一返回封装：Result、PageResult |
| `utils` | 通用工具类：JwtUtil、FileUtil、SecurityUtils |

---

### 2.3 分层调用规则

| 调用方向 | 是否允许 | 说明 |
|---------|---------|------|
| Controller → Service | 允许 | 标准调用 |
| Controller → Mapper | **禁止** | 必须通过 Service |
| Service → Mapper | 允许 | 标准调用 |
| Service → Service | 允许 | 同模块直接调用，跨模块通过接口 |
| Mapper → Service | **禁止** | Mapper 不调用业务逻辑 |
| Entity → 任何层 | **禁止** | Entity 是数据载体，不包含逻辑 |

---

### 2.4 Maven 多模块与分层映射

```
ai-interview-backend/               # 父 POM
│
├── ai-common/                      # 支撑层 + 实体层（公共部分）
│   └── src/main/java/
│       └── com/interview/common/
│           ├── constant/           # 常量定义
│           ├── exception/          # 异常处理
│           ├── result/             # 统一返回
│           ├── utils/              # 工具类
│           └── entity/             # 公共实体
│
├── ai-auth/                        # 认证模块（完整五层）
│   └── src/main/java/
│       └── com/interview/auth/
│           ├── controller/         # 表现层
│           ├── service/            # 业务层（接口）
│           │   └── impl/           # 业务层（实现）
│           ├── mapper/             # 数据访问层
│           ├── entity/             # 实体层
│           ├── dto/                # 数据传输对象
│           └── config/             # 模块配置
│
├── ai-interview/                   # 面试模块（完整五层）
│   └── src/main/java/
│       └── com/interview/interview/
│           ├── controller/         # 表现层
│           ├── service/            # 业务层
│           │   └── impl/
│           ├── mapper/             # 数据访问层
│           ├── entity/             # 实体层
│           └── dto/                # 数据传输对象
│
├── ai-ai/                          # AI 能力模块（完整五层）
│   └── src/main/java/
│       └── com/interview/ai/
│           ├── controller/         # 表现层
│           ├── service/            # 业务层
│           │   └── impl/
│           ├── mapper/             # 数据访问层
│           ├── entity/             # 实体层
│           └── factory/            # AI 模型工厂
│
└── ai-web/                         # 启动模块
    └── src/main/java/
        └── com/interview/
            └── AiInterviewApplication.java
```

---

### 2.5 调用链路

```
前端请求
    │
    ▼
Controller ─── 接收、校验、路由
    │
    ▼
Service ────── 业务逻辑、事务、数据组装
    │
    ▼
Mapper ─────── 数据库操作
    │
    ▼
MySQL

数据载体：Entity / DTO / VO（贯穿各层）
支撑能力：Config / Common / Exception / Util（供各层复用）
```

---

## 三、配置文件规范

### 3.1 application.yml 结构

```yaml
server:
  port: 8080

spring:
  application:
    name: ai-interview
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/ai_interview?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:root}
    type: com.alibaba.druid.pool.DruidDataSource
  servlet:
    multipart:
      max-file-size: 200MB
      max-request-size: 200MB

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key}
  expiration: 604800

logging:
  level:
    com.interview: debug
```

### 3.2 多环境配置

```
src/main/resources/
├── application.yml              # 公共配置
├── application-dev.yml          # 开发环境
├── application-test.yml         # 测试环境
└── application-prod.yml         # 生产环境
```

---

## 四、接口规范

### 4.1 RESTful 规范

| 方法 | 语义 | 示例 |
|------|------|------|
| GET | 查询 | `GET /interviews/{id}` |
| POST | 创建 | `POST /interviews/upload` |
| PUT | 更新 | `PUT /ai-configs/{id}` |
| DELETE | 删除 | `DELETE /interviews/{id}` |

### 4.2 统一响应格式

```json
{
    "code": 200,
    "message": "success",
    "data": {}
}
```

### 4.3 分页响应格式

```json
{
    "code": 200,
    "message": "success",
    "data": {
        "records": [],
        "total": 100,
        "page": 1,
        "size": 10
    }
}
```

---

## 五、前后端交互规范

### 5.1 认证方式

- 前端登录后获取 JWT Token
- Token 存储在 localStorage
- 每次请求携带 `Authorization: Bearer {token}` Header
- 后端通过拦截器校验 Token

### 5.2 文件上传

- 使用 `multipart/form-data` 格式
- 后端使用 `@RequestParam("file") MultipartFile` 接收

### 5.3 异步处理进度

- 上传成功后返回面试记录 ID
- 前端轮询 `GET /interviews/{id}/status` 获取处理进度
- 状态：0-处理中、1-已完成、2-失败
