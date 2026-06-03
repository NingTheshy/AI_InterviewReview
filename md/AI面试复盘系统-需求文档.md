# AI 面试复盘系统 - 需求文档

> 版本: v1.3 MVP  
> 日期: 2026-06-01  
> 状态: 已更新（新增面经功能）

---

## 1. 项目概述

### 1.1 项目定位

一个面向**求职者个人**的 AI 面试复盘平台。用户上传面试录音（音频文件）、岗位 JD 和个人简历，系统自动完成语音转文字、说话人识别、问题边界识别，并生成问题级评分和多维度整体评分，附带 AI 改进建议和参考答案。

### 1.2 核心价值

- 帮助求职者系统性地复盘面试表现
- 通过 AI 量化评估，发现薄弱环节
- 提供针对性的改进建议和参考答案
- 支持多次面试的历史记录对比

### 1.3 竞品参考

| 产品 | 核心功能 | 可借鉴之处 |
|------|---------|-----------|
| BrightHire | 面试录制、结构化评分卡、实时辅导、偏见检测 | AI 自动生成面试摘要和评分；结构化评分框架 |
| Metaview | AI 面试笔记、对话智能、招聘漏斗分析 | 自动从面试中提取关键信息；候选人对比分析 |
| Otter.ai | 音频转文字、AI 会议摘要、说话人识别、可搜索转写 | 转录精度高；说话人识别；自定义词汇 |
| HireVue | 视频面试、AI 语言分析、游戏化评估 | 综合评估候选人能力；结构化评估框架 |
| Grain | 会议录制、AI 高亮片段、可分享片段 | 关键片段提取和分享 |
| Looppanel | AI 会议转录、用户研究分析 | 转写+分析一体化 |

**竞品分析总结 - 本系统需借鉴的关键能力：**

| 能力维度 | 竞品做法 | 本系统借鉴方案 |
|---------|---------|--------------|
| 面试分类 | BrightHire 按岗位/公司结构化分类 | 新增 company_name、position_title、industry 字段 |
| 音频回放 | Otter.ai/Grain 内嵌播放器 | 复盘详情页集成音频播放器，支持时间戳跳转 |
| 用户笔记 | Metaview 协作笔记 | 新增用户笔记功能，支持对面试和单个问题添加笔记 |
| 问题收藏 | Looppanel 关键片段收藏 | 新增问题收藏功能，便于反复学习典型问题 |
| 面试分享 | BrightHire 协作反馈 | 新增面试分享功能，生成分享链接 |
| 处理进度 | BrightHire 实时状态 | 新增 processing_step 字段，展示当前处理步骤 |
| 失败重试 | 所有竞品 | 新增重试接口，支持重新处理失败的面试 |

---

## 2. 技术架构

### 2.1 整体架构

采用 **Spring Boot 多模块架构**，便于后期拆分为微服务。后端使用 Maven 多模块管理，前端独立仓库。

```
┌─────────────────────────────────────────────────────────┐
│                    前端 (Vue3 独立仓库)                    │
└──────────────────────────┬──────────────────────────────┘
                           │ REST API
┌──────────────────────────▼──────────────────────────────┐
│              后端 (Spring Boot Maven 多模块)              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ ai-common    │  │ ai-auth      │  │ ai-interview │  │
│  │ 公共模块      │  │ 认证模块      │  │ 面试业务模块  │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│  ┌──────────────┐  ┌──────────────┐                    │
│  │ ai-ai        │  │ ai-web       │                    │
│  │ AI能力模块    │  │ 启动模块      │                    │
│  └──────────────┘  └──────────────┘                    │
└───────┬──────────────────┬──────────────────┬───────────┘
        │                  │                  │
   ┌────▼────┐       ┌────▼────┐       ┌─────▼─────┐
   │  MySQL  │       │ 文件存储 │       │ AI模型API │
   │         │       │(本地/OSS)│       │(可配置)   │
   └─────────┘       └─────────┘       └───────────┘
```

### 2.2 技术选型

| 层级 | 技术 | 说明 |
|------|------|------|
| 前端框架 | Vue 3 + Vite | 现代化前端构建 |
| UI 组件库 | Element Plus | Vue3 生态成熟组件库 |
| 图表 | ECharts | 评分雷达图展示 |
| 状态管理 | Pinia | Vue3 官方推荐 |
| HTTP 请求 | Axios | 前后端通信 |
| 后端框架 | Spring Boot 3.x | Java 生态主流 |
| 权限认证 | Spring Security + JWT | 无状态认证 |
| 数据库 | MySQL 8.0 | 主流关系型数据库 |
| ORM | MyBatis-Plus | 简化数据库操作 |
| 异步处理 | Spring @Async + 线程池 | MVP 阶段异步任务 |
| 文件存储 | 本地存储（MVP）| 后续可迁移到 OSS |
| AI 模型 | 可配置，支持多模型 | 通过配置切换 |
| 参数校验 | Jakarta Validation | 接口参数校验 |
| 接口文档 | Knife4j (Swagger) | API 文档自动生成 |
| 缓存 | Redis | 验证码缓存、登录限流 |

### 2.3 Maven 多模块结构

```
ai-interview-backend/                    # 后端根项目 (父 POM)
├── pom.xml                              # 父 POM，管理依赖版本和模块
│
├── ai-common/                           # 公共模块 (被其他模块依赖)
│   ├── pom.xml
│   └── src/main/java/com/interview/common/
│       ├── constant/                    # 常量定义
│       │   └── InterviewStatus.java     # 面试状态枚举
│       ├── exception/                   # 全局异常定义
│       │   ├── BusinessException.java
│       │   └── GlobalExceptionHandler.java
│       ├── result/                      # 统一响应封装
│       │   └── Result.java
│       └── utils/                       # 工具类
│           └── JwtUtil.java
│
├── ai-auth/                             # 认证模块
│   ├── pom.xml                          # 依赖 ai-common
│   └── src/main/java/com/interview/auth/
│       ├── controller/
│       │   └── AuthController.java
│       ├── service/
│       │   └── AuthService.java
│       ├── mapper/
│       │   └── UserMapper.java
│       ├── entity/
│       │   └── User.java
│       ├── dto/
│       │   ├── LoginRequest.java
│       │   └── RegisterRequest.java
│       └── config/
│           └── SecurityConfig.java
│
├── ai-interview/                        # 面试业务模块
│   ├── pom.xml                          # 依赖 ai-common, ai-ai
│   └── src/main/java/com/interview/interview/
│       ├── controller/
│       │   └── InterviewController.java
│       ├── service/
│       │   ├── InterviewService.java
│       │   └── InterviewAsyncService.java
│       ├── mapper/
│       │   ├── InterviewMapper.java
│       │   └── InterviewQuestionMapper.java
│       ├── entity/
│       │   ├── Interview.java
│       │   └── InterviewQuestion.java
│       └── dto/
│           ├── InterviewUploadRequest.java
│           └── InterviewDetailResponse.java
│
├── ai-ai/                               # AI 能力模块
│   ├── pom.xml                          # 依赖 ai-common
│   └── src/main/java/com/interview/ai/
│       ├── controller/
│       │   └── AiConfigController.java
│       ├── service/
│       │   ├── AiConfigService.java
│       │   ├── TranscriptionService.java  # 语音转文字
│       │   ├── ScoringService.java        # 评分服务
│       │   └── AiModelClient.java         # AI模型调用客户端
│       ├── mapper/
│       │   └── AiConfigMapper.java
│       ├── entity/
│       │   └── AiConfig.java
│       └── factory/
│           └── AiClientFactory.java       # 模型工厂，支持切换
│
├── ai-web/                              # 启动模块 (聚合其他模块)
│   ├── pom.xml                          # 依赖所有模块
│   └── src/main/java/com/interview/
│       └── AiInterviewApplication.java  # 启动类
│
└── ai-interview-frontend/               # 前端 (独立仓库，可选放在此处)
    ├── src/
    │   ├── api/                         # API 接口定义
    │   ├── views/                       # 页面组件
    │   ├── components/                  # 公共组件
    │   ├── stores/                      # Pinia 状态管理
    │   ├── router/                      # 路由配置
    │   └── utils/                       # 工具函数
    ├── package.json
    └── vite.config.js
```

**模块依赖关系：**
```
ai-web → ai-interview → ai-common
                    → ai-ai → ai-common
       → ai-auth → ai-common
```

**后期拆分为微服务时：** 每个业务模块（ai-auth、ai-interview、ai-ai）可独立打包为 Spring Boot 应用，通过 Feign/OpenFeign 进行服务间调用。

---

## 3. 功能需求

### 3.1 用户模块

#### 3.1.1 注册

- 邮箱 + 用户名 + 密码注册
- 邮箱为必填项，需通过邮箱验证码验证
- 注册流程：发送验证码 → 输入验证码 → 填写用户名+密码 → 提交注册
- 验证码有效期 5 分钟，同一邮箱 60 秒内只能发送一次
- 密码 bcrypt 加密存储
- 注册成功自动登录
- 用户名为必填项，用于登录和显示

#### 3.1.2 登录

- 支持**邮箱**或**用户名** + 密码登录
- 返回 JWT Token（有效期 7 天）
- 前端存储 Token，每次请求携带

#### 3.1.3 退出登录

- 调用退出登录接口，使当前 Token 失效
- 服务端将 Token 的 JTI 加入 Redis 黑名单（TTL 为 Token 剩余有效期）
- 前端清除本地存储的 Token
- 退出后需重新登录获取新 Token

#### 3.1.4 用户信息

- 获取当前用户基本信息
- 修改密码

### 3.2 面试模块

#### 3.2.1 上传面试

用户需要提供以下信息：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| audioFile | File | 是 | 面试录音音频（MP3/WAV），最大 200MB |
| jdText | String | 是 | 岗位 JD 文本（直接粘贴） |
| resumeFile | File | 是 | 个人简历（PDF 格式），最大 20MB |
| title | String | 否 | 面试标题（默认用时间戳） |
| companyName | String | 否 | 公司名称 |
| positionTitle | String | 否 | 职位名称 |
| industry | String | 否 | 行业分类（互联网/金融/教育等） |
| interviewType | String | 否 | 面试类型：coding-技术面/behavioral-行为面/system_design-系统设计/comprehensive-综合 |

#### 3.2.2 面试处理流程（异步）

```
上传完成 → 创建记录(status=PROCESSING) → 异步处理：
  Step 1: 简历 PDF 提取文本 + 语音转文字 + 说话人识别 (processing_step=1)
  Step 2: 自动识别问题边界 (processing_step=2)
  Step 3: 逐题评分 (processing_step=3)
  Step 4: 整体评分 + 改进建议 (processing_step=4)
→ 处理完成(status=COMPLETED) 或 失败(status=FAILED)
```

前端通过轮询 `/interviews/{id}/status` 获取处理进度，返回当前步骤和进度百分比。

**处理进度响应：**
```json
{
    "code": 200,
    "data": {
        "status": 0,
        "processingStep": 2,
        "processingStepName": "问题边界识别",
        "progress": 50
    }
}
```

**失败重试：** 支持对 status=FAILED 的面试记录重新处理，调用 `POST /interviews/{id}/retry` 接口。

#### 3.2.3 面试记录列表

- 分页展示用户的面试记录
- 显示：标题、公司名称、职位名称、处理状态、整体评分、创建时间
- 支持按公司名称、职位名称、行业、面试类型筛选
- 支持按评分、创建时间排序
- 支持删除

#### 3.2.4 面试复盘详情

核心页面，包含以下内容：

**a) 面试基本信息**
- 面试标题、公司名称、职位名称、面试类型
- 音频播放器：支持播放/暂停、进度拖拽、倍速播放、时间戳跳转
- 面试时长、上传时间

**b) 转写文本**
- 完整的面试对话转写
- 带说话人标注（面试官 / 求职者）
- 高亮当前问题
- 点击时间戳可跳转到音频对应位置

**c) 逐题评分卡片**
- 每个问题一张卡片
- 显示：问题内容、回答内容、评分（1-10分）、改进建议、参考答案
- 支持对单个问题添加个人笔记
- 支持收藏问题到"问题收藏夹"

**d) 整体评分**
- 多维度评分雷达图
- 维度：内容质量、逻辑性、表达能力、专业度、沟通技巧
- 综合得分

**e) AI 改进建议**
- 整体改进建议
- 候选人优势总结
- 需要提升的方面

**f) 用户笔记**
- 支持对整场面试添加个人复盘笔记
- 笔记支持富文本格式

### 3.3 用户笔记模块

#### 3.3.1 面试笔记

- 支持对每场面试添加个人复盘笔记
- 笔记支持富文本格式（标题、加粗、列表等）
- 每场面试只能有一条笔记（覆盖式更新）
- 笔记在复盘详情页展示

#### 3.3.2 问题笔记

- 支持对每个面试问题添加个人笔记
- 便于记录自己的思考和改进计划

### 3.4 问题收藏模块

#### 3.4.1 收藏问题

- 支持将面试中的问题收藏到"我的收藏"
- 收藏时可添加备注（如"系统设计典型题"）
- 收藏列表支持按面试来源、评分筛选

#### 3.4.2 收藏列表

- 分页展示收藏的问题
- 显示：问题内容、来源面试、评分、收藏备注、收藏时间
- 支持取消收藏
- 支持按评分排序

### 3.5 面试分享模块

#### 3.5.1 生成分享链接

- 支持将面试复盘结果生成分享链接
- 分享链接有效期可选：24小时/7天/30天/永久
- 支持选择是否公开为"面经"（`isPublic`），公开面经无过期时间
- 分享页面为只读，不允许编辑

#### 3.5.2 分享页面

- 展示面试复盘的完整内容（转写文本、评分、建议）
- 不展示原始音频文件（保护隐私）
- 不展示用户笔记（保护隐私）

#### 3.5.3 面经广场

- 面经广场页面展示所有公开的面经列表
- 支持分页浏览
- 支持按公司名称、职位名称、行业筛选
- 支持按评分、发布时间排序
- 每条面经卡片显示：标题、公司名称、职位名称、行业、整体评分、问题数量、发布时间、浏览量
- 无需登录即可访问面经广场

#### 3.5.4 面经详情页

- 展示面试基本信息（标题、公司、职位、类型、整体评分）
- 展示问题列表：每个问题的评分、改进建议、参考答案
- 展示多维度评分雷达图
- 展示 AI 改进建议（优势总结、待提升方面、整体建议）
- 展示评论列表（按时间倒序）
- 不展示音频文件、转写文本、个人笔记（隐私保护）
- 无需登录即可访问和查看评论

#### 3.5.5 面经评论

- 登录用户可在面经详情页发表评论
- 评论内容为纯文本，最长 500 字
- 发布者可删除自己面经下的任意评论
- 评论者可删除自己的评论
- 管理员可删除任意评论
- 评论列表显示：评论者昵称、头像、评论内容、发布时间

### 3.6 AI 配置模块

#### 3.6.1 模型配置管理

支持配置不同的 AI 模型，用于不同步骤：

| 配置项 | 说明 |
|--------|------|
| provider | 模型提供商（openai/deepseek/aliyun/baidu等） |
| modelName | 模型名称（gpt-4o/deepseek-chat/qwen-turbo等） |
| apiKey | API 密钥（加密存储） |
| isDefault | 是否默认模型 |

可为不同步骤配置不同模型：
- 语音转文字：可用阿里云 ASR / Whisper
- 文本分析和评分：可用 DeepSeek / 通义千问 / GPT

### 3.7 管理员模块

#### 3.7.1 RBAC 权限控制

采用基于角色的访问控制（RBAC），固定两个角色：

| 角色 | 标识 | 说明 |
|------|------|------|
| 管理员 | admin | 拥有系统所有权限 |
| 普通用户 | user | 默认角色，仅访问自身数据 |

权限定义：

| 权限标识 | 说明 | 角色 |
|---------|------|------|
| user:list | 查看用户列表 | admin |
| user:manage | 禁用/启用用户账号 | admin |
| ai_config:list | 查看所有 AI 配置 | admin |
| ai_config:manage | 新增/编辑/删除 AI 配置 | admin |
| interview:view_all | 查看所有用户面试数据 | admin |
| experience:manage | 下架/恢复面经 | admin |
| comment:manage | 删除任意评论 | admin |
| system:stats | 查看系统统计数据 | admin |

- 新注册用户默认角色为 user
- 后端通过自定义注解 `@RequirePermission` 或 Spring Security 的 `@PreAuthorize` 在接口层校验权限
  - 管理员接口统一前缀 `/admin/**`


#### 3.7.2 用户管理

- 分页查看所有用户列表（用户名、邮箱、角色、状态、注册时间）
- 支持按用户名、邮箱、状态筛选
- 支持禁用/启用用户账号（禁用后该用户无法登录）
- 支持为用户分配角色（admin/user）

#### 3.7.3 系统统计

- 总用户数、今日新增用户数
- 总面试数、今日新增面试数
- 各处理状态面试数分布
- 平均面试评分

#### 3.7.4 面经与评论管理

- 管理员可查看所有面经列表，支持下架/恢复面经
- 管理员可查看所有评论列表，支持删除任意评论
- 下架后的面经在面经广场不可见，但原有分享链接仍可访问（标记为已下架）

---

> **数据库设计详见：** [AI面试复盘系统-数据库设计文档.md](./AI面试复盘系统-数据库设计文档.md)

---

## 4. API 接口设计

### 4.1 统一响应格式

```json
{
    "code": 200,
    "message": "success",
    "data": {}
}
```

### 4.2 认证模块 (ai-auth)

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/auth/send-code` | 发送邮箱验证码 | 否 |
| POST | `/auth/register` | 用户注册（需携带验证码） | 否 |
| POST | `/auth/login` | 用户登录（邮箱或用户名），返回 JWT | 否 |
| POST | `/auth/logout` | 退出登录，当前 Token 失效 | 是 |
| GET | `/auth/profile` | 获取当前用户信息 | 是 |
| PUT | `/auth/profile` | 更新用户信息 | 是 |
| PUT | `/auth/password` | 修改密码 | 是 |

**发送验证码请求示例：**
```json
POST /auth/send-code
{
    "email": "zhangsan@example.com"
}
```

**注册请求示例：**
```json
POST /auth/register
{
    "email": "zhangsan@example.com",
    "username": "zhangsan",
    "password": "123456",
    "code": "123456"
}
```

**登录请求示例（支持邮箱或用户名）：**
```json
POST /auth/login
{
    "account": "zhangsan@example.com",
    "password": "123456"
}
```

**登录响应示例：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "token": "eyJhbGciOiJIUzI1NiJ9...",
        "tokenType": "Bearer",
        "expiresIn": 604800,
        "user": {
            "id": 1,
            "username": "zhangsan",
            "email": "zhangsan@example.com",
            "nickname": "张三",
            "role": 0
        }
    }
}
```

**退出登录请求示例：**
```json
POST /auth/logout
Authorization: Bearer {token}

// 无需请求体，服务端将当前 Token 加入 Redis 黑名单使其失效
```

**退出登录响应示例：**
```json
{
    "code": 200,
    "message": "退出登录成功",
    "data": null
}
```

### 4.3 面试模块 (ai-interview)

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/interviews/upload` | 上传音频+JD+简历PDF，创建面试记录 | 是 |
| GET | `/interviews` | 获取面试记录列表（分页，支持筛选排序） | 是 |
| GET | `/interviews/{id}` | 获取面试详情（含问题评分） | 是 |
| DELETE | `/interviews/{id}` | 删除面试记录（逻辑删除） | 是 |
| GET | `/interviews/{id}/status` | 查询处理进度（含当前步骤） | 是 |
| POST | `/interviews/{id}/retry` | 重新处理失败的面试记录 | 是 |

**上传请求示例：**
```
POST /interviews/upload
Content-Type: multipart/form-data

audioFile: (binary)
resumeFile: (binary)
jdText: "岗位职责：..."
title: "2026-05-26 字节跳动后端面试"
companyName: "字节跳动"
positionTitle: "Java后端开发"
industry: "互联网"
interviewType: "coding"
```

**列表响应示例：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "records": [
            {
                "id": 1,
                "title": "2026-05-26 字节跳动后端面试",
                "companyName": "字节跳动",
                "positionTitle": "Java后端开发",
                "industry": "互联网",
                "interviewType": "coding",
                "status": 1,
                "overallScore": 7.5,
                "audioDuration": 1800,
                "createdAt": "2026-05-26T14:30:00"
            }
        ],
        "total": 10,
        "page": 1,
        "size": 10
    }
}
```

**处理进度响应示例：**
```json
{
    "code": 200,
    "data": {
        "status": 0,
        "processingStep": 2,
        "processingStepName": "问题边界识别",
        "progress": 50
    }
}
```

### 4.4 用户笔记模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/interviews/{id}/note` | 获取面试笔记 | 是 |
| PUT | `/interviews/{id}/note` | 保存面试笔记（覆盖式） | 是 |
| GET | `/interviews/{id}/questions/{qid}/note` | 获取问题笔记 | 是 |
| PUT | `/interviews/{id}/questions/{qid}/note` | 保存问题笔记 | 是 |

### 4.5 问题收藏模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/favorites` | 收藏问题 | 是 |
| DELETE | `/favorites/{id}` | 取消收藏 | 是 |
| GET | `/favorites` | 获取收藏列表（分页） | 是 |

### 4.6 面试分享模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/interviews/{id}/share` | 生成分享链接/发布面经 | 是 |
| GET | `/interviews/{id}/shares` | 获取分享记录列表 | 是 |
| DELETE | `/shares/{token}` | 撤销分享链接/下架面经 | 是 |
| PUT | `/shares/{token}/public` | 切换面经公开状态 | 是 |
| GET | `/share/{token}` | 访问分享页面（无需认证） | 否 |
| GET | `/experiences` | 面经广场列表（分页+筛选，无需认证） | 否 |
| GET | `/experiences/{token}` | 面经详情（无需认证） | 否 |
| GET | `/experiences/{token}/comments` | 获取面经评论列表（无需认证） | 否 |
| POST | `/experiences/{token}/comments` | 发表评论 | 是 |
| DELETE | `/comments/{id}` | 删除评论（评论者/发布者/管理员） | 是 |

**发布面经请求示例：**
```json
POST /interviews/1/share
{
    "expireType": null,
    "isPublic": true
}
```

**面经广场列表响应示例：**
```json
{
    "code": 200,
    "data": {
        "records": [
            {
                "token": "abc123",
                "title": "字节跳动后端面试",
                "companyName": "字节跳动",
                "positionTitle": "Java后端开发",
                "industry": "互联网",
                "interviewType": "coding",
                "overallScore": 7.5,
                "questionCount": 8,
                "viewCount": 128,
                "createdAt": "2026-05-26T14:30:00"
            }
        ],
        "total": 56,
        "page": 1,
        "size": 10
    }
}
```

**面经详情响应示例：**
```json
{
    "code": 200,
    "data": {
        "title": "字节跳动后端面试",
        "companyName": "字节跳动",
        "positionTitle": "Java后端开发",
        "industry": "互联网",
        "interviewType": "coding",
        "overallScore": 7.5,
        "dimensionContent": 8.0,
        "dimensionLogic": 7.5,
        "dimensionExpression": 7.0,
        "dimensionProfessional": 8.0,
        "dimensionCommunication": 7.0,
        "improvementSummary": "...",
        "strengths": "...",
        "weaknesses": "...",
        "questions": [
            {
                "questionIndex": 1,
                "questionText": "请介绍一下你最有成就感的项目",
                "score": 8.0,
                "improvementTip": "...",
                "referenceAnswer": "..."
            }
        ],
        "viewCount": 128,
        "createdAt": "2026-05-26T14:30:00"
    }
}
```

**评论列表响应示例：**
```json
{
    "code": 200,
    "data": {
        "records": [
            {
                "id": 1,
                "userId": 2,
                "nickname": "张三",
                "avatar": "https://...",
                "content": "感谢分享，很有帮助！",
                "createdAt": "2026-05-27T10:00:00"
            }
        ],
        "total": 5,
        "page": 1,
        "size": 20
    }
}
```

**发表评论请求示例：**
```json
POST /experiences/abc123/comments
{
    "content": "感谢分享，很有帮助！"
}
```

### 4.7 AI 配置模块 (ai-ai)

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/ai-configs` | 获取 AI 配置列表 | 是 |
| GET | `/ai-configs/{id}` | 获取单个配置详情 | 是 |
| POST | `/ai-configs` | 新增 AI 模型配置 | 是 |
| PUT | `/ai-configs/{id}` | 更新 AI 模型配置 | 是 |
| DELETE | `/ai-configs/{id}` | 删除 AI 模型配置（逻辑删除） | 是 |
| PUT | `/ai-configs/{id}/default` | 设为默认配置 | 是 |

### 4.8 管理员模块 (admin)

| 方法 | 路径 | 说明 | 认证 | 权限 |
|------|------|------|------|------|
| GET | `/admin/users` | 获取用户列表（分页，支持筛选） | 是 | user:list |
| GET | `/admin/users/{id}` | 获取用户详情 | 是 | user:list |
| PUT | `/admin/users/{id}/status` | 禁用/启用用户 | 是 | user:manage |
| PUT | `/admin/users/{id}/role` | 修改用户角色 | 是 | user:manage |
| GET | `/admin/stats/overview` | 获取系统统计数据 | 是 | system:stats |
| GET | `/admin/interviews` | 查看所有用户面试数据（分页） | 是 | interview:view_all |
| GET | `/admin/experiences` | 查看所有面经（分页） | 是 | experience:manage |
| PUT | `/admin/experiences/{token}/status` | 下架/恢复面经 | 是 | experience:manage |
| GET | `/admin/comments` | 查看所有评论（分页） | 是 | comment:manage |
| DELETE | `/admin/comments/{id}` | 删除任意评论 | 是 | comment:manage |

**禁用用户请求示例：**
```json
PUT /admin/users/2/status
{
    "enabled": false
}
```

**修改角色请求示例：**
```json
PUT /admin/users/2/role
{
    "role": "admin"
}
```

**统计数据响应示例：**
```json
{
    "code": 200,
    "data": {
        "totalUsers": 1280,
        "todayNewUsers": 15,
        "totalInterviews": 5630,
        "todayNewInterviews": 42,
        "statusDistribution": {
            "processing": 3,
            "completed": 5400,
            "failed": 227
        },
        "averageScore": 7.2
    }
}
```

### 4.9 错误码定义

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证（Token 无效或过期） |
| 403 | 无权限访问 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 1001 | 用户名已存在 |
| 1002 | 密码错误 |
| 1003 | 账户已被禁用 |
| 1004 | 登录失败次数过多 |
| 1005 | 邮箱已被注册 |
| 1006 | 验证码错误或已过期 |
| 1007 | 验证码发送过于频繁，请稍后再试 |
| 1008 | 邮箱格式不正确 |
| 2001 | 音频文件格式不支持 |
| 2002 | 音频文件过大 |
| 2003 | 面试记录不存在 |
| 2004 | 面试记录不属于当前用户 |
| 2005 | 面试记录正在处理中，无法重试 |
| 3001 | AI 模型配置不存在 |
| 3002 | AI 模型调用失败 |
| 4001 | 收藏数已达上限 |
| 4002 | 该问题已收藏 |
| 4003 | 分享链接已过期 |
| 4004 | 分享链接不存在 |
| 5001 | 无权限访问，需要管理员权限 |
| 5002 | 用户已被禁用 |
| 5003 | 面经已下架或不存在 |
| 5004 | 评论内容不能为空 |
| 5005 | 评论内容超出长度限制 |
| 5006 | 无权删除该评论 |

---

## 5. 前端页面设计

### 5.1 页面列表

| 页面 | 路由 | 说明 |
|------|------|------|
| 登录页 | `/login` | 邮箱或用户名+密码登录 |
| 注册页 | `/register` | 邮箱+验证码+用户名+密码注册 |
| 首页 | `/` | 面试记录列表（支持筛选排序） |
| 上传面试 | `/interview/upload` | 上传音频+填写JD+上传简历PDF+公司信息 |
| 面试复盘详情 | `/interview/:id` | 核心复盘页面（含音频播放器） |
| 我的收藏 | `/favorites` | 收藏的问题列表 |
| 分享页面 | `/share/:token` | 只读分享页面（无需登录） |
| 面经广场 | `/experiences` | 公开面经列表（无需登录） |
| 面经详情 | `/experience/:token` | 面经详情页面（无需登录） |
| AI 配置 | `/ai-config` | 管理 AI 模型配置 |
| 管理后台 | `/admin` | 管理员专属页面（用户管理、系统统计） |

### 5.2 面试复盘详情页布局

```
┌─────────────────────────────────────────────────────────┐
│  面试标题: 字节跳动后端面试                               │
│  公司: 字节跳动  职位: Java后端开发  类型: 技术面          │
│  整体评分: 7.5                                            │
├─────────────────────────────────────────────────────────┤
│  🎵 音频播放器                                           │
│  [▶] ──────●────────────── 00:15:30 / 00:30:00  [1.0x] │
├──────────────────────────┬──────────────────────────────┤
│                          │                              │
│   转写文本区域             │   评分概览区域                 │
│   (带说话人标注)           │   ├─ 雷达图                   │
│   (时间戳可跳转)           │   ├─ 各维度分数                │
│                          │   └─ 综合评价                 │
│                          │                              │
├──────────────────────────┴──────────────────────────────┤
│                                                         │
│     逐题评分卡片（横向滚动）                                │
│   ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐                   │
│   │问题1 │ │问题2  │ │问题3  │ │问题4  │                   │
│   │评分  │ │评分   │ │评分   │ │评分   │                   │
│   │建议  │ │建议   │ │建议   │ │建议   │                   │
│   │📝笔记│ │📝笔记│  │📝笔记│ │📝笔记 │                   │
│   │⭐收藏│ │⭐收藏│  │⭐收藏│ │⭐收藏 │                   │
│   └──────┘ └──────┘ └──────┘ └──────┘                    │
│                                                          │
├──────────────────────────────────────────────────────────┤
│   AI 改进建议                                             │
│   ├─ 优势总结                                             │
│   ├─ 待提升方面                                           │
│   └─ 整体建议                                             │
├──────────────────────────────────────────────────────────┤
│   📝 我的复盘笔记                                          │
│   ┌──────────────────────────────────────────────────┐   │
│   │ (富文本编辑区域)                                    │  │
│   └──────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
```

---

## 6. 核心流程详细设计

### 6.1 异步处理机制

- 使用 Spring `@Async` + 自定义线程池实现异步任务
- 音频上传后，创建面试记录（status=PROCESSING），提交异步任务
- 前端通过轮询 `/interviews/{id}/status` 获取处理进度
- 后续可升级为消息队列（RocketMQ/Kafka）实现更可靠的异步处理

**线程池配置：**
```yaml
async:
  core-pool-size: 4
  max-pool-size: 8
  queue-capacity: 100
  thread-name-prefix: interview-async-
```

### 6.2 语音转文字 + 说话人识别

- 调用 ASR 模型（如 Whisper、阿里云 ASR）将音频转为文字
- 通过说话人识别（Speaker Diarization）区分面试官和求职者
- 输出带时间戳和说话人标注的转写文本
- **降级策略**：ASR 服务不可用时，记录失败状态，支持手动重试

### 6.3 问题边界识别

- 将转写文本发送给 LLM
- Prompt 要求：识别出面试中的每个问题，输出问题列表和对应的回答
- 输出结构化 JSON：`[{question, answer, speaker, startTime, endTime}]`
- **降级策略**：LLM 调用失败时，按时间间隔自动切分（每 60 秒为一个问题）

### 6.4 逐题评分

对每个问题，发送以下信息给 LLM：
- 问题内容
- 求职者回答
- 岗位 JD
- 个人简历

LLM 输出：
- 评分（1-10 分）
- 各维度分数（内容质量、逻辑性、表达能力、专业度）
- 改进建议
- 参考答案
- **降级策略**：评分失败时，标记该问题为"待评分"，不影响其他问题

### 6.5 整体评分

汇总所有问题评分，生成：
- 各维度综合分数
- 雷达图数据
- 整体改进建议
- 优势和不足总结

---

## 7. 安全设计

### 7.1 认证安全

- 密码 BCrypt 加密存储（salt rounds=10）
- JWT Token 认证，有效期 7 天
- Token 存储在 HTTP Header：`Authorization: Bearer {token}`
- 退出登录后 Token 失效：将 Token 的 JTI 存入 Redis 黑名单，TTL 为 Token 剩余有效期；JWT 过滤器校验时先检查黑名单，命中则拒绝请求
- 登录失败 5 次后锁定账户 15 分钟
- 邮箱验证码有效期 5 分钟，同一邮箱 60 秒内只能发送一次
- 验证码存储 6 位数字，使用 Redis 缓存（key: `verify:{email}`，TTL=5min）

### 7.2 接口安全

- 所有业务接口需要 JWT 认证
- RBAC 权限校验：管理员接口（`/admin/**`）需验证用户角色为 admin，通过 `@PreAuthorize` 注解在方法级别控制
- 公开接口放行：`/share/{token}`、`/experiences`、`/experiences/{token}`、`/experiences/{token}/comments` 无需认证
- 数据权限校验：普通用户只能访问自己的面试记录
- 评论删除权限：评论者本人、面经发布者、管理员均可删除评论
- 接口参数校验：使用 Jakarta Validation 注解
- SQL 注入防护：MyBatis-Plus 参数化查询
- XSS 防护：前端输入过滤，后端响应头设置

### 7.3 文件安全

- 音频文件上传类型校验：仅允许 MP3、WAV 格式，最大 200MB
- 简历文件上传类型校验：仅允许 PDF 格式，最大 20MB
- 文件名重命名：使用 UUID + 原始扩展名，防止路径遍历
- 文件存储路径：与应用分离，配置化管理

### 7.4 敏感数据安全

- API Key 加密存储：使用 AES 对称加密
- 密钥配置在环境变量或配置中心，不写入代码仓库
- 日志脱敏：用户密码、API Key 等敏感信息不输出到日志

---

## 8. MVP 范围

### 8.1 包含

- [x] 用户注册/登录（JWT，支持邮箱注册+验证码验证，邮箱/用户名均可登录）
- [x] 音频上传 + JD文本 + 简历PDF上传 + 公司/职位信息
- [x] 语音转文字 + 说话人识别
- [x] 自动识别问题边界
- [x] 问题级评分 + 多维度整体评分
- [x] AI 改进建议 + 参考答案
- [x] 面试记录列表（支持筛选排序）+ 详情查看
- [x] 处理进度步骤展示 + 失败重试
- [x] 音频播放器（时间戳跳转）
- [x] 用户笔记（面试级 + 问题级）
- [x] 问题收藏功能
- [x] 面试分享功能 + 面经广场 + 面经评论
- [x] AI 模型配置管理
- [x] 管理员模块（RBAC 权限控制、用户管理、系统统计）

### 8.2 不包含（后续迭代）

- [ ] AI 模拟面试模块
- [ ] 历史趋势分析图表
- [ ] 多语言支持
- [ ] 导出 PDF 报告
- [ ] 团队协作功能
- [ ] 音频在线录制
- [ ] WebSocket 实时进度推送
- [ ] 面试题库（按岗位分类）

---

## 9. 验证方式

1. **后端 API 测试**: 启动后端，用 APIFOX 测试所有接口
2. **前端完整流程**: 注册 → 登录 → 上传面试 → 查看复盘结果
3. **AI 效果验证**: 用一段真实面试录音测试转写和评分效果
4. **异常处理**: 测试文件格式错误、超大文件、网络中断等场景

---

## 10. 后续扩展方向

- AI 模拟面试：模拟面试官提问，实时评估回答
- 历史趋势分析：多次面试的分数变化趋势
- 面试题库：按岗位分类的常见面试题
- 社区功能：分享面试经验、评分排行
- 移动端适配：响应式设计或小程序版本
