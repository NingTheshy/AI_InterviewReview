# AI 面试复盘系统 - 需求文档

> 版本: v1.7 面试业务闭环
> 日期: 2026-10-21
> 状态: 已同步异步简历、任务状态机、标准答案缓存、问题归档与公开面经

> **当前实施基线：** 简历通过 `/resumes` 独立异步解析，创建面试必须绑定本人 `SUCCESS` 且正文非空的 `resumeId`；音频仅支持 MP3/WAV，最大 2GB、最长 2 小时。任务支持 `CANCELLED`、阶段重试和已完成面试独立重评。收藏仅面向问题。私密分享已移除，面经以数字 ID 发布，每场面试最多一篇，并受管理员下架锁定。本文旧章节若仍出现 `resumeFile`、200MB、分享 token 或 `share:manage`，均由本基线及下方“面试业务闭环”章节取代。

### 0.1 面试业务闭环（权威规则）

- `POST /resumes` 创建 `PROCESSING` 记录并异步解析；同一用户的相同 PDF 按 SHA-256 复用未隐藏记录，失败可重试。
- `POST /interviews/upload` 必填音频、非空 JD、`resumeId`、岗位和合法面试类型；公司、行业、标题选填，标题按“公司-岗位-日期”或“岗位-日期”生成。
- 每个用户最多一场 AI 处理中面试；取消保留阶段产物，`retry` 仅用于失败或取消，`reanalyze` 仅用于已完成面试且不再调用 ASR。
- 长转写按 FunASR 句子及时间戳以有限重叠窗口分块，跨块结果按时间范围和文本指纹去重。
- 技术/客观题标准答案不读取简历；个人/项目/行为题必须读取面试绑定简历，并按用户、面试、简历版本、模型和提示词版本隔离缓存。
- 重评按问题指纹和时间范围保留匹配问题 ID；未匹配旧问题归档，从而保留问题笔记与收藏。
- 面经只展示题目、分数、关键差距、改进建议、标准答案、昵称和头像，不展示回答、转写、音频、简历或私人笔记。

---

## 1. 项目概述

### 1.1 项目定位

一个面向**求职者个人**的 AI 面试复盘平台。用户上传面试录音（音频文件）、岗位 JD 和个人简历，系统自动完成语音转文字、说话人识别、问题边界识别；针对识别出的每个问题，AI 先独立生成标准满分回答和评分量表，再将候选人的实际回答与量表逐项比对，由服务端计算问题级评分和多维度整体评分，并生成针对性的改进建议。

### 1.2 核心价值

- 帮助求职者系统性地复盘面试表现
- 通过 AI 量化评估，发现薄弱环节
- 提供针对性的改进建议、标准满分回答和可追溯的评分依据
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
│       │   ├── LlmClient.java             # Spring AI 文本模型业务适配接口
│       │   └── AsrClient.java             # Spring AI 转写模型业务适配接口
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
| audioFile | File | 是 | 面试录音音频（MP3/WAV），最大 2GB、最长 2 小时 |
| jdText | String | 是 | 岗位 JD 文本（直接粘贴） |
| resumeId | Long | 是 | 当前用户已异步解析成功且正文非空的简历 ID |
| title | String | 否 | 面试标题（默认“公司-岗位-日期”或“岗位-日期”） |
| companyName | String | 否 | 公司名称 |
| positionTitle | String | 是 | 职位名称 |
| industry | String | 否 | 行业分类（互联网/金融/教育等） |
| interviewType | String | 是 | 面试类型：coding-技术面/behavioral-行为面/system_design-系统设计/comprehensive-综合 |

#### 3.2.2 面试处理流程（异步）

```
上传完成 → 创建记录(status=PROCESSING) → 异步处理：
  Step 1: FunASR 转写 + 句子时间戳 + 说话人角色映射 (processing_step=1)
  Step 2: 识别问答对 + 生成/复用标准满分回答和评分量表 + 逐项比对 (processing_step=2)
  Step 3: 服务端计算逐题分数、各维度分数并持久化评分明细 (processing_step=3)
  Step 4: 汇总整体评分、优势、不足和改进建议 (processing_step=4)
→ 处理完成(status=COMPLETED)、失败(status=FAILED) 或用户取消(status=CANCELLED)
```

前端通过轮询 `/interviews/{id}/status` 获取处理进度，返回当前步骤和进度百分比。

**处理进度响应：**
```json
{
    "code": 200,
    "data": {
        "status": 0,
        "processingStep": 2,
        "processingStepName": "标准答案生成与逐项比对",
        "progress": 50
    }
}
```

**重试与重评：** `FAILED/CANCELLED` 调用 `POST /interviews/{id}/retry` 并从已有阶段继续；`COMPLETED` 调用 `POST /interviews/{id}/reanalyze`，只重新识别问答和评分。

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
- 显示：问题内容、候选人回答、评分（0-100 分）、各维度分数、改进建议、标准满分回答
- 展示该题 5-8 个评分要点及权重，评分要点覆盖内容质量、逻辑性、表达能力和专业度
- 展示每个评分要点的覆盖状态、候选人回答证据、缺失点、错误点及改进方法
- AI 只负责生成标准和判断覆盖状态，不直接给出最终分数；最终分数由服务端根据固定规则计算
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
- 同一次面试可以保存多条问题笔记，不同问题的笔记相互独立
- 同一个问题默认保存一条笔记，重复保存时覆盖更新该问题原有笔记
- 问题笔记的唯一范围为“用户 + 面试 + 问题”，不得仅以面试作为唯一范围
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

### 3.5 公开面经模块

#### 3.5.1 发布管理

- 每场已完成面试最多对应一篇面经，公开访问使用数字 ID
- 发布者可填写最多 10,000 字 Markdown 经验总结，可主动撤回并重新发布
- 管理员下架后发布者不能自行恢复，只能由管理员解除限制
- 面试删除或发起重评时自动撤回，恢复或重评完成后不自动发布

#### 3.5.3 面经广场

- 面经广场页面展示所有公开的面经列表
- 支持分页浏览
- 支持按公司名称、职位名称、行业筛选
- 支持按评分、发布时间排序
- 每条面经卡片显示：标题、公司名称、职位名称、行业、整体评分、问题数量、发布时间、浏览量
- 无需登录即可访问面经广场

#### 3.5.4 面经详情页

- 展示面试基本信息及发布者昵称、头像；昵称为空时不回退到用户名
- 展示问题列表：每个问题的评分、关键差距、改进建议和标准满分回答
- 展示多维度评分雷达图
- 展示 AI 改进建议（优势总结、待提升方面、整体建议）
- 展示评论列表（按时间倒序）
- 不展示用户原回答、音频、转写文本、简历和个人笔记（隐私保护）
- 无需登录即可访问和查看评论

#### 3.5.5 面经评论

- 登录用户只能在当前可见的公开面经详情页发表评论
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
- 语音转文字：仅使用 DashScope FunASR
- 文本分析和评分：使用 Spring AI 接入 DeepSeek / MiMo

### 3.7 管理员模块

#### 3.7.1 RBAC 权限控制

系统采用“用户—角色—权限”完整 RBAC 模型。当前预置两个系统内置角色，后续允许扩展更多细粒度角色：

| 角色 | 标识 | 说明 |
|------|------|------|
| 普通用户 | user | 新账号默认角色，组织个人业务功能权限 |
| 管理员 | admin | 组织额外管理权限，不具备任何代码层面的隐式特权 |

核心授权规则：

- 一个用户可以关联多个角色，最终权限为所有有效角色所关联权限的并集，并对重复权限去重。
- 管理员账号同时关联 `user` 和 `admin`；管理员执行每项操作时也必须通过 `role_permission` 获得对应权限。
- 角色只负责组织权限，接口使用 `hasAuthority('权限码')` 等方式判断是否可以执行操作，不再通过 `hasRole('ADMIN')` 直接放行。
- 业务规则负责判断当前用户能否操作指定资源，例如面试、笔记、收藏和分享是否属于当前用户。
- 未明确授权时默认拒绝。公开接口必须进入显式白名单，不能因为遗漏安全注解而自动公开。
- JWT 仅保存用户身份，不保存角色或权限快照；每次请求从数据库读取当前账号状态、角色及权限，使禁用账号、撤销角色和调整权限在下一次请求立即生效。
- 当前阶段不引入权限缓存，也不建立通用的 `SELF/DEPARTMENT/ALL` 数据范围模型。跨用户查询通过 `*:read_all` 权限配合业务查询条件实现。
- `user`、`admin` 为系统内置角色，角色编码不可修改、角色不可删除；后续允许在受控管理功能中调整其权限关系。
- 新增权限默认不授予任何角色，必须通过 `role_permission` 显式配置后生效。

账号与角色安全规则：

- 新注册账号自动关联 `user` 角色。
- 系统不允许账号最终处于无角色状态；管理员清空某账号角色时，必须在同一事务内自动补回 `user`。
- 禁止拥有角色分配权限的用户增加、移除或替换自己的角色。
- 角色分配者只能分配权限集合不超过其自身有效权限集合的角色，禁止越权授权。
- 必须保护最后一个有效权限管理者：禁止删除、禁用或撤销最后一个仍拥有 `user:role:assign` 权限的有效账号。
- 最后管理者判断依据是账号的有效权限，而不是是否具有名为 `admin` 的角色；检查与变更必须置于同一事务并进行并发控制。
- 首个管理员不再由 SQL 写入固定默认密码。当系统不存在有效权限管理者时，从 `.env` 中读取初始化管理员账号和密码并创建；生产环境缺少该配置时启动失败。首次登录强制改密暂列后续优化。

权限码采用稳定的“业务资源 + 操作”命名方式，与 URL 和 HTTP 方法解耦。初始权限分配如下：

| 权限标识 | 说明 | 角色 |
|---------|------|------|
| interview:create | 创建并上传面试 | user |
| interview:read | 查看本人面试、状态及关联文件 | user |
| interview:manage | 删除或重试本人面试 | user |
| note:manage | 查看和管理本人面试笔记及问题笔记 | user |
| favorite:manage | 查看和管理本人收藏 | user |
| experience:publish | 发布、更新、撤回本人面经 | user |
| comment:manage_own | 发表评论，以及删除本人评论或本人发布面经下的评论 | user |
| user:read | 查询用户列表和用户详情 | admin |
| user:status:update | 启用或禁用用户账号 | admin |
| user:role:assign | 为其他账号分配预置角色 | admin |
| interview:read_all | 查看所有用户的面试数据 | admin |
| experience:read_all | 查看全部面经及其管理状态 | admin |
| experience:manage | 下架或恢复面经 | admin |
| comment:read_all | 查看全部评论 | admin |
| comment:manage | 删除任意评论 | admin |
| ai_config:read | 查看 AI 配置 | admin |
| ai_config:manage | 新增、修改、删除或设置默认 AI 配置 | admin |
| system:stats:read | 查看系统统计数据 | admin |
| system:monitor:read | 查看受保护的系统监控页面 | admin |
| audit:read | 分页查询权限与高风险操作审计日志 | admin |

`user` 和 `admin` 的权限取并集后构成管理员的完整能力。普通用户权限并不绕过资源归属校验，管理员权限也不因角色名称而自动扩展。

接口授权矩阵：

| 方法 | 路径 | 公开状态 / 权限码 | 资源或业务规则 |
|------|------|-------------------|----------------|
| POST | `/auth/send-code` | 公开 | 验证码频率限制 |
| POST | `/auth/register` | 公开 | 注册成功后自动关联 `user` |
| POST | `/auth/login` | 公开 | 校验账号有效状态 |
| POST | `/auth/logout` | 登录校验 | 仅注销当前令牌 |
| GET | `/auth/profile` | 登录校验 | 作为前端角色及权限状态的统一数据来源 |
| PUT | `/auth/profile` | 登录校验 | 只能修改本人资料 |
| PUT | `/auth/password` | 登录校验 | 只能修改本人密码并校验原密码 |
| POST | `/resumes` | `interview:create` | 创建本人异步简历解析任务 |
| GET | `/resumes`、`/resumes/{id}` | `interview:read` | 仅访问本人未隐藏简历 |
| POST | `/resumes/{id}/retry` | `interview:manage` | 仅本人失败简历可重试 |
| DELETE | `/resumes/{id}` | `interview:manage` | 仅隐藏简历；历史面试快照不受影响 |
| POST | `/interviews/upload` | `interview:create` | 新记录归属于当前用户 |
| GET | `/interviews` | `interview:read` | 只查询当前用户的数据 |
| GET | `/interviews/{id}` | `interview:read` | 仅资源所有者可见 |
| DELETE | `/interviews/{id}` | `interview:manage` | 仅资源所有者可删除 |
| GET | `/interviews/{id}/status` | `interview:read` | 仅资源所有者可见 |
| POST | `/interviews/{id}/cancel` | `interview:manage` | 仅资源所有者且正在处理时可取消 |
| POST | `/interviews/{id}/retry` | `interview:manage` | 仅资源所有者且状态为失败/取消时可重试 |
| POST | `/interviews/{id}/reanalyze` | `interview:manage` | 仅已完成面试，不重新调用 ASR |
| GET | `/files/{filename}` | `interview:read` | 文件必须关联当前用户的面试 |
| GET | `/asr-files/{filename}` | 签名公开接口 | 仅接受未过期且签名正确的模型取件 URL |
| GET | `/interviews/{id}/note` | `note:manage` | 面试属于当前用户 |
| PUT | `/interviews/{id}/note` | `note:manage` | 面试属于当前用户，同场仅一条总笔记，重复保存覆盖 |
| GET | `/interviews/{id}/questions/{qid}/note` | `note:manage` | 面试和问题均属于当前用户 |
| PUT | `/interviews/{id}/questions/{qid}/note` | `note:manage` | 面试和问题均属于当前用户；每题一条，同场不同题互不覆盖 |
| POST | `/favorites` | `favorite:manage` | 目标问题必须对当前用户可见 |
| DELETE | `/favorites/{id}` | `favorite:manage` | 收藏属于当前用户 |
| GET | `/favorites` | `favorite:manage` | 只查询当前用户的收藏 |
| POST/GET/PUT/DELETE | `/interviews/{id}/publication` | `experience:publish` | 仅所有者管理一篇公开面经；管理员锁定时用户不能恢复 |
| GET | `/experiences` | 公开 | 仅返回已发布且当前可见的面经 |
| GET | `/experiences/{id}` | 公开 | 仅返回已发布且当前可见的面经 |
| GET | `/experiences/{id}/comments` | 公开 | 面经必须公开可见 |
| POST | `/experiences/{id}/comments` | `comment:manage_own` | 面经必须公开可见 |
| DELETE | `/comments/{id}` | `comment:manage_own` | 仅评论作者或面经发布者可删除 |
| GET | `/admin/users` | `user:read` | 可按条件查询全部用户 |
| GET | `/admin/users/{id}` | `user:read` | 目标用户存在 |
| PUT | `/admin/users/{id}/status` | `user:status:update` | 禁止破坏最后管理者；记录审计日志 |
| PUT | `/admin/users/{id}/role` | `user:role:assign` | 禁止修改自己、越权授权和产生无角色账号；记录审计日志 |
| GET | `/admin/stats/overview` | `system:stats:read` | 统计口径仅包含允许纳入的数据 |
| GET | `/admin/interviews` | `interview:read_all` | 跨用户只读查询 |
| GET | `/admin/experiences` | `experience:read_all` | 查看公开及非公开面经状态 |
| PUT | `/admin/experiences/{id}/status` | `experience:manage` | 按数字 ID 下架/恢复并记录审计日志 |
| GET | `/admin/comments` | `comment:read_all` | 跨用户查询评论 |
| DELETE | `/admin/comments/{id}` | `comment:manage` | 删除任意评论并记录审计日志 |
| GET | `/admin/audit-logs` | `audit:read` | 只读分页查询，不提供修改或删除接口 |
| GET | `/ai-configs` | `ai_config:read` | 返回值必须对密钥脱敏 |
| GET | `/ai-configs/{id}` | `ai_config:read` | 返回值必须对密钥脱敏 |
| POST | `/ai-configs` | `ai_config:manage` | 密钥加密存储并记录审计日志 |
| PUT | `/ai-configs/{id}` | `ai_config:manage` | 密钥加密存储并记录审计日志 |
| DELETE | `/ai-configs/{id}` | `ai_config:manage` | 校验配置引用关系并记录审计日志 |
| PUT | `/ai-configs/{id}/default` | `ai_config:manage` | 同类型默认配置唯一；记录审计日志 |

Swagger/Knife4j 文档仅在开发、测试环境公开；Druid 等监控入口要求 `system:monitor:read`，生产环境不得仅依赖固定 URL 或角色名保护。

审计要求：

- 记录角色分配与移除、账号启用与禁用、权限关系调整，以及因最后管理者保护而被拒绝的操作。
- 审计内容至少包含操作者、目标对象、变更前后内容、操作时间、来源 IP 和执行结果。
- 审计日志只追加，不提供普通业务删除或修改接口；只有具备 `audit:read` 权限的用户可以分页查询。
- 密码、JWT、API Key、验证码等敏感信息不得写入审计日志。


#### 3.7.2 用户管理

- 分页查看所有用户列表（用户名、邮箱、角色、状态、注册时间）
- 支持按用户名、邮箱、状态筛选
- 支持禁用/启用用户账号（禁用后该用户无法登录）
- 支持为用户分配多个预置角色；当前预置 `user` 和 `admin`

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
            "nickname": "张三"
        }
    }
}
```

登录响应只返回身份信息和 Token，不返回角色或权限快照。前端登录成功后必须调用 `GET /auth/profile` 获取统一的权限状态。

**当前用户信息响应示例：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "username": "admin",
        "email": "admin@example.com",
        "nickname": "管理员",
        "roles": ["user", "admin"],
        "permissions": [
            "interview:create",
            "interview:read",
            "user:read",
            "user:role:assign"
        ]
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
resumeId: 123
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
                "overallScore": 75,
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
        "processingStepName": "标准答案生成与逐项比对",
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
| POST/GET/PUT/DELETE | `/interviews/{id}/publication` | 发布、查询、更新或撤回本人面经 | 是 |
| GET | `/experiences/{id}` | 访问可见公开面经 | 否 |
| GET | `/experiences` | 面经广场列表（分页+筛选，无需认证） | 否 |
| GET | `/experiences/{id}` | 面经详情（无需认证） | 否 |
| GET | `/experiences/{id}/comments` | 获取面经评论列表（无需认证） | 否 |
| POST | `/experiences/{id}/comments` | 发表评论 | 是 |
| DELETE | `/comments/{id}` | 删除评论（评论者/发布者/管理员） | 是 |

**发布面经请求示例：**
```json
POST /interviews/1/share
{
    "expireType": null,
    "isPublic": true,
    "accessPassword": null
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
                "overallScore": 75,
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
        "overallScore": 75,
        "dimensionContent": 80,
        "dimensionLogic": 75,
        "dimensionExpression": 70,
        "dimensionProfessional": 80,
        "dimensionCommunication": 70,
        "improvementSummary": "...",
        "strengths": "...",
        "weaknesses": "...",
        "questions": [
            {
                "questionIndex": 1,
                "questionText": "请介绍一下你最有成就感的项目",
                "score": 80,
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

| 方法 | 路径 | 说明 | 认证 | 权限 |
|------|------|------|------|------|
| GET | `/ai-configs` | 获取 AI 配置列表 | 是 | ai_config:read |
| GET | `/ai-configs/{id}` | 获取单个配置详情 | 是 | ai_config:read |
| POST | `/ai-configs` | 新增 AI 模型配置 | 是 | ai_config:manage |
| PUT | `/ai-configs/{id}` | 更新 AI 模型配置 | 是 | ai_config:manage |
| DELETE | `/ai-configs/{id}` | 删除 AI 模型配置（逻辑删除） | 是 | ai_config:manage |
| PUT | `/ai-configs/{id}/default` | 设为默认配置 | 是 | ai_config:manage |

### 4.8 管理员模块 (admin)

| 方法 | 路径 | 说明 | 认证 | 权限 |
|------|------|------|------|------|
| GET | `/admin/users` | 获取用户列表（分页，支持筛选） | 是 | user:read |
| GET | `/admin/users/{id}` | 获取用户详情 | 是 | user:read |
| PUT | `/admin/users/{id}/status` | 禁用/启用用户 | 是 | user:status:update |
| PUT | `/admin/users/{id}/role` | 替换用户的预置角色集合 | 是 | user:role:assign |
| GET | `/admin/stats/overview` | 获取系统统计数据 | 是 | system:stats:read |
| GET | `/admin/interviews` | 查看所有用户面试数据（分页） | 是 | interview:read_all |
| GET | `/admin/experiences` | 查看所有面经（分页） | 是 | experience:read_all |
| PUT | `/admin/experiences/{id}/status` | 下架/恢复面经 | 是 | experience:manage |
| GET | `/admin/comments` | 查看所有评论（分页） | 是 | comment:read_all |
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
    "roles": ["user", "admin"]
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
| 404 | 资源不存在或当前用户无权确认资源是否存在 |
| 409 | 资源可见，但当前业务状态不允许执行操作 |
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
| 面经详情 | `/experiences/:id` | 公开面经只读详情（无需登录） |
| 面经广场 | `/experiences` | 公开面经列表（无需登录） |
| 面经详情 | `/experience/:token` | 面经详情页面（无需登录） |
| AI 配置 | `/ai-config` | 管理 AI 模型配置 |
| 管理后台 | `/admin` | 管理员专属页面（用户管理、系统统计） |

### 5.2 面试复盘详情页布局

```
┌─────────────────────────────────────────────────────────┐
│  面试标题: 字节跳动后端面试                               │
│  公司: 字节跳动  职位: Java后端开发  类型: 技术面          │
│  整体评分: 75/100                                         │
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

### 6.2 FunASR 语音转文字、时间戳与说话人角色

- 使用 FunASR 非实时语音识别模型处理整段音频，原始音频转为模型支持的格式后一次性提交，不采用针对旧 ASR 模型的固定分片方案
- 开启 Speaker Diarization，保留 FunASR 返回的原始 `speakerId`
- 必须保存句子级时间戳，包括每句话的开始时间和结束时间；不得只保存拼接后的纯文本
- 根据问答语义、发言顺序和上下文，将原始 `speakerId` 映射为业务角色“面试官”或“候选人”
- 角色映射结果应在整场面试内保持一致，同时保留原始 `speakerId`，便于追溯和人工纠正
- 转写结果至少包含：`text`、`speakerId`、`speakerRole`、`startTime`、`endTime`
- 问题和回答的起止时间必须从其包含的句子时间戳计算，供音频跳转和文本高亮使用
- **降级策略**：ASR 服务不可用、结果为空或时间戳缺失时，记录失败原因并允许手动重试；不得静默丢弃时间戳后继续完成处理

### 6.3 问答识别

- 将带说话人角色和句子时间戳的转写结果发送给 LLM
- LLM 从完整对话中识别每个面试问题及对应的候选人回答
- 输出结构化 JSON：`[{questionIndex, questionText, answerText, questionSpeaker, answerSpeaker, startTime, endTime, confidence}]`
- `startTime`、`endTime` 必须来自 FunASR 原始句子时间戳，不允许由 LLM 猜测
- 问答识别采用分块处理时，必须保持问题序号连续，并避免在分块边界生成重复问题
- 未能识别任何有效问答对，或结构化结果校验失败且重试后仍失败时，将整次处理标记为失败，允许用户重新处理

### 6.4 标准满分回答、评分量表与逐项比对

#### 6.4.1 标准满分回答与评分量表生成

- 对每个识别出的面试问题，AI 在看不到候选人回答的前提下，结合问题、岗位、行业、企业等级、JD 和简历事实背景，独立生成标准满分回答
- 简历仅用于理解项目背景，不得因候选人的既有经历降低满分标准
- 每个问题生成 5-8 个可验证的评分要点，覆盖以下维度：内容质量、逻辑性、表达能力、专业度
- 每个评分要点包含：唯一标识、所属维度、名称、权重和满分判定标准
- 单题所有评分要点的正整数权重之和必须等于 100，四个评分维度均至少出现一次
- 开放式问题允许多种合理方案获得满分，标准满分回答不得被当作唯一正确措辞
- 相同问题在岗位、行业、企业等级和量表版本均一致时，重新处理可复用已保存的标准满分回答和评分量表

#### 6.4.2 用户回答逐项比对

- AI 将候选人回答与固定评分量表逐项比对，不得修改评分要点、权重或满分标准
- 每个要点输出以下覆盖状态之一：`FULLY_COVERED`、`MOSTLY_COVERED`、`PARTIALLY_COVERED`、`MISSING`、`INCORRECT`、`NOT_APPLICABLE`
- 除 `MISSING` 和 `NOT_APPLICABLE` 外，必须提供候选人回答中的原文证据；证据必须能够在回答原文中逐字匹配，不得编造
- 每个要点同时输出缺失点、错误点和针对性的改进方法
- AI 在此阶段只判断覆盖状态并提供证据，不直接输出最终分数

#### 6.4.3 服务端确定性计分

- 单题分数范围统一为 0-100 分
- 覆盖状态计分系数：完全覆盖 1.0、基本覆盖 0.75、部分覆盖 0.5、缺失 0、错误 0；不适用项不计入分母
- 单题得分由服务端按照“有效要点加权得分 ÷ 有效要点权重之和 × 100”计算并四舍五入
- 内容质量、逻辑性、表达能力和专业度分数按各维度有效要点的权重分别计算
- 将标准满分回答、评分量表、覆盖状态、原文证据、缺失点、错误点、改进方法和维度分数保存到 `rubric_detail`
- AI 返回结果必须通过 JSON 结构、题号、权重、维度、状态和证据一致性校验；校验失败时允许自动重试，重试后仍失败则将整次面试处理标记为失败

### 6.5 整体评分

汇总所有问题评分，生成：
- 内容质量、逻辑性、表达能力和专业度取所有有效问题对应维度分数的平均值
- 沟通协作维度由 AI 根据整场问答互动生成 0-100 分
- 综合得分由服务端按固定权重计算：内容质量 25%、逻辑性 20%、表达能力 15%、专业度 25%、沟通协作 15%
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

- 所有非公开业务接口必须先完成统一身份认证，再按照接口安全需求执行功能权限、资源归属和业务状态校验；未明确授权时默认拒绝
- 功能授权以 `role -> role_permission -> permission` 为唯一来源，通过具体权限码进行判断，不允许基于 `admin` 角色名称或 `/admin/**` 路径自动放行
- JWT 仅保存用户身份；账号状态、角色和权限在每次请求时从数据库加载，不把授权快照写入 Token
- 当前用户信息接口统一返回角色编码和去重后的权限码集合，作为前端权限状态的唯一数据来源；前端隐藏菜单或按钮不能替代后端鉴权
- 资源归属校验在业务层执行：普通用户只能访问和操作自己的面试、笔记、收藏、文件和分享记录
- 当前不建设通用数据范围引擎；跨用户查询必须要求对应的 `*:read_all` 权限，并在查询条件中显式限定允许的数据范围
- 评论删除规则：具有 `comment:manage_own` 的评论作者或面经发布者可按业务规则删除；删除任意评论还必须具有 `comment:manage`
- 公开接口仅包括认证入口、当前可见的公开面经列表和详情、公开面经评论列表，以及带有效签名的 ASR 临时取件地址
- 面经广场只能返回已发布且当前可见的内容；草稿、下架、失效或审核中的内容不得通过公开接口访问
- 分享访问必须校验不可预测令牌、状态、有效期及可选访问密码，并限制返回字段，禁止泄露原始音频、用户资料和其他关联资源
- 未登录或令牌无效返回 HTTP `401`；已登录但缺少功能权限返回 `403`
- 资源不存在或当前用户无权知道资源是否存在时统一返回 `404`，避免通过响应差异枚举其他用户资源
- 资源可见但违反当前业务状态约束时返回 `409` 或对应的业务错误码
- 用户角色、账号状态和角色权限关系变更必须记录不可篡改的审计日志；敏感字段必须脱敏
- 接口参数校验：使用 Jakarta Validation 注解
- SQL 注入防护：MyBatis-Plus 参数化查询
- XSS 防护：前端输入过滤，后端响应头设置

### 7.3 文件安全

- 音频文件上传校验：仅允许真实 MP3、WAV 格式，最大 2GB、最长 2 小时
- 简历文件上传类型校验：仅允许 PDF 格式，最大 20MB
- 文件名重命名：使用 UUID + 原始扩展名，防止路径遍历
- 文件存储路径：与应用分离，配置化管理

### 7.4 敏感数据安全

- API Key 加密存储：使用 AES-256-GCM，每条密文使用独立随机 IV；主密钥只通过环境变量提供
- 密钥配置在环境变量或配置中心，不写入代码仓库
- 日志脱敏：用户密码、API Key 等敏感信息不输出到日志

---

## 8. MVP 范围

### 8.1 包含

- [x] 用户注册/登录（JWT，支持邮箱注册+验证码验证，邮箱/用户名均可登录）
- [x] 音频上传 + JD文本 + 简历PDF上传 + 公司/职位信息
- [x] FunASR 语音转文字 + 句子级时间戳 + 说话人角色映射
- [x] 自动识别问答对及问题边界
- [x] 标准满分回答 + 评分量表 + 用户回答逐项证据比对
- [x] 服务端 0-100 分确定性计分 + 多维度整体评分
- [x] AI 改进建议 + 标准满分回答
- [x] 面试记录列表（支持筛选排序）+ 详情查看
- [x] 处理进度步骤展示 + 失败重试
- [x] 音频播放器（时间戳跳转）
- [x] 用户笔记（每场一条面试笔记 + 同场多个独立问题笔记）
- [x] 问题收藏功能
- [x] 面试分享功能 + 面经广场 + 面经评论
- [x] AI 模型配置管理
- [x] 管理员模块（用户管理、系统统计、面经与评论管理）
- [x] 完整 RBAC 运行时授权、角色迁移、最后管理者保护与权限审计

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
