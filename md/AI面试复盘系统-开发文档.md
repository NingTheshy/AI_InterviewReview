# AI 面试复盘系统 - 开发文档

> 版本: v2.0  
> 日期: 2026-06-07  
> 适用对象: 项目开发人员、运维人员、技术负责人

---

## 一、项目简介

### 1.1 项目定位

本系统是一个面向求职者的 **AI 面试复盘平台**。用户上传面试录音（音频文件）、岗位 JD（职位描述）和个人简历，系统自动完成语音转文字、问题识别、逐题评分，并生成多维度评估报告和改进建议。

### 1.2 核心功能概览

| 功能模块 | 说明 |
|---------|------|
| 用户系统 | 注册、登录、个人信息管理 |
| 面试上传 | 上传音频 + 简历 PDF + 岗位 JD 文本 |
| AI 分析 | 语音转文字 → 问题识别 → 多维度评分 → 改进建议 |
| 面试复盘 | 逐题评分卡片、雷达图、参考答案 |
| 用户笔记 | 面试笔记、问题笔记 |
| 问题收藏 | 收藏典型问题，便于反复学习 |
| 面试分享 | 生成分享链接 / 发布面经 |
| 面经广场 | 公开面经浏览、评论互动 |
| 管理后台 | 用户管理、系统统计、AI 模型配置 |

### 1.3 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 LTS | 编程语言 |
| Spring Boot | 3.2.5 | 后端框架 |
| Spring Security | 6.2.x | 安全认证 |
| MyBatis-Plus | 3.5.5 | ORM 框架 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 6.x+ | 缓存（验证码、Token 黑名单） |
| Druid | 1.2.21 | 数据库连接池 |
| Knife4j | 4.4.0 | API 文档（Swagger） |
| JJWT | 0.12.5 | JWT Token |
| Lombok | 1.18.32 | 代码简化 |
| Maven | 3.8.x | 项目构建 |
| javax.sound | JDK 内置 | WAV 音频处理 |
| FFmpeg | 系统级 | MP3/其他格式音频转换 |

### 1.4 项目结构总览

```
AI_InterviewReview/                    # 项目根目录
├── ai-common/                         # 公共模块：常量、异常、工具类、统一响应
├── ai-auth/                           # 认证模块：用户注册、登录、JWT、权限控制
├── ai-interview/                      # 面试模块：面试上传、复盘、笔记、收藏、分享、面经
├── ai-ai/                             # AI 模块：模型工厂、语音转文字、文本评分、音频分片
├── ai-admin/                          # 管理模块：用户管理、系统统计、内容管理
├── ai-web/                            # 启动模块：聚合所有模块，Spring Boot 入口
├── sql/                               # 数据库脚本
├── md/                                # 项目文档
└── pom.xml                            # 父 POM（依赖版本管理）
```

---

## 二、完整开发流程

### 2.1 开发流程概览

```
┌─────────────────────────────────────────────────────────────────┐
│                    完整开发流程                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. 环境准备 ──→ 2. 代码获取 ──→ 3. 数据库初始化 ──→ 4. 配置修改  │
│                                                                 │
│  5. 编译构建 ──→ 6. 启动验证 ──→ 7. 功能开发 ──→ 8. 联调测试    │
│                                                                 │
│  9. 代码提交 ──→ 10. 部署上线                                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 第一步：环境准备

#### 2.2.1 安装基础软件

| 软件 | 版本要求 | 安装说明 | 验证命令 |
|------|---------|---------|---------|
| JDK | 17+ | 推荐 Adoptium/Temurin | `java -version` |
| Maven | 3.8+ | 用于项目构建 | `mvn -version` |
| MySQL | 8.0+ | 关系型数据库 | `mysql --version` |
| Redis | 6.x+ | 缓存服务 | `redis-cli ping` → PONG |
| Git | 2.x | 版本控制 | `git --version` |
| FFmpeg | 4.x+ | 音频格式转换（可选） | `ffmpeg -version` |
| IntelliJ IDEA | 2022+ | 后端 IDE（需安装 Lombok 插件） | - |

#### 2.2.2 安装 IDE 插件

**IntelliJ IDEA 必装插件：**

1. **Lombok** - 自动生成 getter/setter/constructor 等
2. **MyBatisX** - Mapper 接口与 XML 文件跳转
3. **SonarLint** - 代码质量检查

### 2.3 第二步：获取代码

```bash
# 克隆后端仓库
git clone <repository-url> AI_InterviewReview
cd AI_InterviewReview

# 克隆前端仓库（独立仓库）
git clone <frontend-repo-url> AI_InterviewReview_front
```

### 2.4 第三步：数据库初始化

```bash
# 登录 MySQL
mysql -u root -p

# 执行建表脚本
source /path/to/AI_InterviewReview/sql/schema.sql
```

**建表脚本自动完成：**
- 创建 `ai_interview` 数据库
- 创建 12 张业务表
- 初始化角色数据（admin、user）
- 初始化权限数据（8 项权限）
- 创建默认管理员账号（用户名: admin，密码: admin123）
- 创建默认 AI 配置（DeepSeek + Whisper）

### 2.5 第四步：配置修改

#### 2.5.1 环境变量配置

| 环境变量 | 说明 | 默认值 | 必填 |
|---------|------|--------|------|
| `DB_HOST` | MySQL 主机地址 | localhost | 否 |
| `DB_PORT` | MySQL 端口 | 3306 | 否 |
| `DB_NAME` | 数据库名称 | ai_interview | 否 |
| `DB_USERNAME` | 数据库用户名 | root | 否 |
| `DB_PASSWORD` | 数据库密码 | - | 是 |
| `REDIS_HOST` | Redis 主机地址 | localhost | 否 |
| `REDIS_PORT` | Redis 端口 | 6379 | 否 |
| `REDIS_PASSWORD` | Redis 密码 | - | 是 |
| `JWT_SECRET` | JWT 签名密钥 | - | 生产环境必填 |
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥 | - | 是 |
| `XIAOMI_API_KEY` | 小米 MiMo API 密钥 | - | 否 |
| `OPENAI_API_KEY` | OpenAI API 密钥 | - | 否 |
| `FFMPEG_PATH` | FFmpeg 可执行文件路径 | ffmpeg | 否 |
| `SCORING_TWO_PHASE` | 启用双阶段评分模式 | false | 否 |

**配置方式（Windows）：**

```powershell
[System.Environment]::SetEnvironmentVariable("DB_PASSWORD", "your_password", "User")
[System.Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "sk-xxx", "User")
```

**配置方式（Linux/Mac）：**

```bash
echo 'export DB_PASSWORD="your_password"' >> ~/.bashrc
echo 'export DEEPSEEK_API_KEY="sk-xxx"' >> ~/.bashrc
source ~/.bashrc
```

#### 2.5.2 AI API 密钥获取

| 服务 | 用途 | 获取方式 |
|------|------|---------|
| DeepSeek | 文本分析与评分 | https://platform.deepseek.com/ |
| 小米 MiMo | LLM + ASR（可选） | https://xiaoai.mi.com/ |
| OpenAI Whisper | 语音转文字（可选） | https://platform.openai.com/ |
| 阿里云 DashScope | ASR（可选） | https://dashscope.aliyun.com/ |

### 2.6 第五步：编译构建

```bash
cd AI_InterviewReview

# 清理并编译
mvn clean compile

# 运行测试
mvn test

# 打包
mvn clean package -DskipTests

# 生成的 JAR 包位置
# ai-web/target/ai-web-1.0.0-SNAPSHOT.jar
```

### 2.7 第六步：启动验证

```bash
# 方式一：从 ai-web 模块启动（推荐开发时使用）
cd ai-web
mvn spring-boot:run

# 方式二：打包后直接运行 JAR
java -jar ai-web/target/ai-web-1.0.0-SNAPSHOT.jar
```

**验证服务：**

| 服务 | 地址 | 说明 |
|------|------|------|
| 后端应用 | http://localhost:8080 | 应用首页 |
| API 文档 | http://localhost:8080/swagger-ui.html | Knife4j 文档 |
| Druid 监控 | http://localhost:8080/druid/ | 数据库监控面板 |
| 前端应用 | http://localhost:5173 | Vue 开发服务器 |

---

## 三、项目架构

### 3.1 模块划分

```
ai-web (启动模块)
  ├── ai-auth    (认证模块)
  │     └── ai-common
  ├── ai-interview (面试模块)
  │     ├── ai-common
  │     └── ai-ai
  │           └── ai-common
  └── ai-admin   (管理模块)
        └── ai-common
```

### 3.2 各模块职责

| 模块 | 包路径 | 职责 |
|------|--------|------|
| ai-common | com.interview.common | 常量定义、全局异常处理、统一响应封装、工具类、公共配置 |
| ai-auth | com.interview.auth | 用户注册/登录、JWT 认证过滤器、Spring Security 配置 |
| ai-interview | com.interview.interview | 面试上传与管理、异步 AI 处理调度、笔记/收藏/分享/面经 |
| ai-ai | com.interview.ai | AI 模型客户端工厂、语音转文字、音频分片、文本评分 |
| ai-admin | com.interview.admin | 管理员后台接口 |
| ai-web | com.interview | Spring Boot 启动类、application.yml 配置文件 |

### 3.3 包结构规范

```
模块名/src/main/java/com/interview/{module}/
  ├── controller/       # 表现层：接收请求、参数校验、返回 JSON
  ├── service/          # 业务层（接口定义）
  │   └── impl/         # 业务层（实现类）
  ├── mapper/           # 数据访问层：数据库 CRUD
  ├── entity/           # 实体层：与数据库表一一映射
  ├── dto/              # 数据传输对象：接口入参和出参
  └── config/           # 模块级配置类
```

---

## 四、配置文件说明

### 4.1 配置文件结构

```
src/main/resources/
  ├── application.yml              # 主配置（所有环境共享）
  ├── application-dev.yml          # 开发环境配置
  ├── application-test.yml         # 测试环境配置
  └── application-prod.yml         # 生产环境配置
```

### 4.2 主配置文件 (application.yml) 完整说明

```yaml
server:
  port: 8080

spring:
  application:
    name: ai-interview
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:root}
      database: ${REDIS_DATABASE:0}
      timeout: 3000ms
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:ai_interview}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD}
  servlet:
    multipart:
      max-file-size: 200MB
      max-request-size: 200MB

jwt:
  secret: ${JWT_SECRET}
  expiration: 604800                  # Token 有效期 7 天

interview:
  file:
    upload-dir: ./uploads             # 文件存储目录
    base-url: /files                  # 文件访问 URL 前缀

ai:
  # AI 提供商配置
  providers:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      api-endpoint: https://api.deepseek.com/v1
      model-name: deepseek-chat
      timeout-seconds: 600            # LLM 调用超时 10 分钟
    xiaomi:
      api-key: ${XIAOMI_API_KEY}
      api-endpoint: https://token-plan-cn.xiaomimimo.com/v1
      model-name: mimo-v2.5-pro
    xiaomi-asr:
      api-key: ${XIAOMI_API_KEY}
      api-endpoint: https://token-plan-cn.xiaomimimo.com/v1
      model-name: mimo-v2.5-asr
    funasr:
      api-key: ${FUNASR_API_KEY}
      api-endpoint: ${FUNASR_API_ENDPOINT:https://dashscope.aliyuncs.com/api/v1}
      model-name: paraformer-v2

  # 音频分片转录配置
  chunk:
    max-direct-size: 6291456          # 6MB，小于该值直传 ASR
    min-segment-duration: 30          # 最小片段时长（秒）
    max-segment-duration: 180         # 最大片段时长（秒），约 5.76MB
    min-silence-duration: 1.0         # 最小静音间隙（秒）
    overlap-duration: 1.5             # 重叠窗口（秒）
    silence-threshold: 300.0          # 静音检测 RMS 阈值
    concurrency: 2                    # 并发发送窗口
    max-retries: 3                    # 单片段最大重试次数
    timeout-seconds: 30               # 单次 ASR 请求超时
    ffmpeg-path: ${FFMPEG_PATH:ffmpeg}

  # 评分模式配置
  scoring:
    two-phase-enabled: ${SCORING_TWO_PHASE}  # 双阶段评分（高精度，2 倍 LLM 调用）

# 异步线程池配置
async:
  core-pool-size: 4
  max-pool-size: 8
  queue-capacity: 100
  thread-name-prefix: interview-async-

knife4j:
  enable: true
  setting:
    language: zh_cn

logging:
  level:
    com.interview: debug
    root: info
```

---

## 五、数据库设计

### 5.1 数据表总览

系统共 12 张数据表，按业务域划分：

**用户与权限（4 张表）：**

| 表名 | 说明 |
|------|------|
| user | 用户表 |
| role | 角色表（admin、user） |
| user_role | 用户角色关联表 |
| role_permission | 角色权限关联表 |

**面试业务（5 张表）：**

| 表名 | 说明 |
|------|------|
| interview | 面试记录表 |
| interview_question | 面试问题表 |
| interview_note | 面试笔记表 |
| interview_favorite | 问题收藏表 |
| interview_share | 面试分享表 |

**面经与评论（2 张表）：**

| 表名 | 说明 |
|------|------|
| interview_share | 面试分享表（含面经公开标识） |
| interview_comment | 面经评论表 |

**AI 配置（1 张表）：**

| 表名 | 说明 |
|------|------|
| ai_config | AI 模型配置表 |

### 5.2 核心表关系

```
user (用户)
  │ 1:N
  ├── interview (面试记录)
  │     │ 1:N
  │     ├── interview_question (面试问题)
  │     │ 1:1
  │     ├── interview_note (面试笔记)
  │     │ 1:N
  │     ├── interview_favorite (问题收藏)
  │     │ 1:N
  │     └── interview_share (面试分享/面经)
  │           │ 1:N
  │           └── interview_comment (面经评论)
  │
  └── user_role (用户角色关联)
        │ N:1
        └── role (角色)
              │ 1:N
              └── role_permission (角色权限关联)
                    │ N:1
                    └── permission (权限)

ai_config (AI模型配置 - 独立表)
```

### 5.3 关键字段说明

**interview.status（面试处理状态）：**

| 值 | 含义 | 说明 |
|----|------|------|
| 0 | 处理中 | 上传成功后，AI 正在处理 |
| 1 | 已完成 | AI 分析完毕，评分已生成 |
| 2 | 失败 | 处理过程中发生错误 |

**interview.processing_step（处理步骤）：**

| 值 | 含义 | 说明 |
|----|------|------|
| 0 | 未开始 | 等待异步任务启动 |
| 1 | 语音转文字 | 正在调用 ASR 服务 |
| 2 | 公司分级 + 评分 | 正在调用 LLM 评估 |
| 3 | 解析结果 | 正在解析 JSON 保存到数据库 |
| 4 | 完成 | 处理完毕 |

---

## 六、核心流程详解

### 6.1 面试上传与异步处理流程

```
前端上传: audioFile + resumeFile + jdText + 公司/职位信息
    │
    ▼
InterviewController.upload()
    │
    ▼
InterviewServiceImpl.upload()
    ├── 1. 参数校验（音频格式、文件大小）
    ├── 2. 保存音频文件到本地（UUID 重命名）
    ├── 3. 保存简历 PDF 到本地（UUID 重命名）
    ├── 4. 创建 Interview 记录（status=0, processingStep=0）
    └── 5. 提交异步任务: InterviewAsyncService.processInterview(id)
    │
    ▼
返回面试记录 ID（前端用此 ID 轮询进度）
```

### 6.2 异步处理核心流程

```
InterviewAsyncServiceImpl.processInterview(interviewId)
    │
    ├── Step 1: 语音转文字 (processingStep=1)
    │   └── TranscriptionService.transcribe(audioPath, configId)
    │       ├── 小文件（≤ 6MB）：直传 ASR
    │       └── 大文件（> 6MB）：预处理 → 静音切片 → 并发 ASR → 文本合并
    │
    ├── Step 1.5: 公司分级
    │   └── CompanyClassifier.classify(companyName, industry, jdText)
    │       └── 返回 CompanyTier (TIER_1 ~ TIER_5)
    │
    ├── Step 2: 问题识别 + 评分 (processingStep=2)
    │   └── ScoringService.analyzeAndScoreBatch(transcript, jd, resume, configId, tier)
    │       ├── LLM 语义识别 QA 对（替代正则拆分）
    │       ├── 分批评分（每批 5000 字符）
    │       │   ├── 阶梯制评分（卓越/优秀/良好/一般/较弱/差）
    │       │   ├── 证据锚定（每个维度引用原文）
    │       │   └── 可选：双阶段评估（分析 → 评分，2 倍精度）
    │       └── 合并结果 → 生成最终评分报告
    │
    ├── Step 3: 解析结果 (processingStep=3)
    │   ├── 解析 JSON，保存到 interview 表和 interview_question 表
    │   └── 加权计算总分
    │       ├── dimensionContent × 0.25
    │       ├── dimensionLogic × 0.20
    │       ├── dimensionExpression × 0.15
    │       ├── dimensionProfessional × 0.25
    │       └── dimensionCommunication × 0.15
    │
    └── Step 4: 完成 (status=COMPLETED)
```

### 6.3 音频分片转录流程

```
原始音频文件
    │
    ▼
TranscriptionServiceImpl.transcribe()
    │
    ├── 文件 ≤ 6MB → 直传 ASR → 返回文本
    │
    └── 文件 > 6MB → 分片处理
        │
        ▼
    AudioPreprocessor.preprocess()
        ├── WAV: javax.sound.sampled 直接处理
        └── MP3/其他: FFmpeg 转换为 16kHz 16bit 单声道 WAV
        │
        ▼
    AudioSplitter.split()
        ├── SilenceDetector: 20ms 帧 RMS 能量检测
        │   └── RMS < 300 → 静音帧
        ├── 连续静音 ≥ 1.0s → 切分点
        ├── 最小片段 30s，最大片段 180s
        └── 每个片段附加上一片段尾部 1.5s 重叠窗口
        │
        ▼
    ReliableSender.sendAll()
        ├── Semaphore(2) 控制并发窗口
        ├── 每次 ASR 调用 Future.get(30s) 超时保护
        ├── 失败重试 3 次（指数退避）
        └── 全局超时保护
        │
        ▼
    TextMerger.merge()
        ├── 按 seq 排序
        ├── 滑动窗口匹配前一片段尾部与当前片段头部
        ├── 精确匹配优先，模糊匹配兜底（容差 15%）
        └── 保守裁剪：trimPoint = windowLen - diffCount
        │
        ▼
    完整转录文本
```

---

## 七、已实施的改进与问题修复

### 7.1 评分准确性改进

#### 问题描述

原始实现使用结构化 Prompt 让 LLM 直接输出 0-100 分数，LLM 对连续数值的输出不稳定，导致评分不准确。

#### 解决方案：阶梯制评分 + 证据锚定

**核心思路**：LLM 擅长分类（分类），不擅长回归（给分）。将评分改为阶梯制分类。

**阶梯制评分表：**

| 档位 | 含义 | 映射分数范围 |
|------|------|-------------|
| 卓越 | 远超岗位要求 | 90-100 |
| 优秀 | 完全满足岗位要求 | 75-89 |
| 良好 | 基本满足岗位要求 | 60-74 |
| 一般 | 部分满足岗位要求 | 45-59 |
| 较弱 | 较难满足岗位要求 | 30-44 |
| 差 | 无法满足岗位要求 | 0-29 |

**证据锚定**：每个评分维度必须引用面试原文作为证据，防止 LLM 凭空打分。

**代码实现**：
- `ScoringServiceImpl.java` — `TIER_RANGES` 映射表、`buildTranscriptBatchPrompt()`、`tierToScore()`
- `ScoringService.java` — 接口定义

#### 可选：双阶段评估模式

当 `ai.scoring.two-phase-enabled=true` 时：

```
阶段一：LLM 分析面试内容，输出阶梯制分类（不输出分数）
阶段二：代码将分类映射为分数，计算加权总分
```

**优势**：阶段一 LLM 只做分类（准确率高），阶段二代码计算分数（确定性）。

**代价**：2 倍 LLM 调用成本。

### 7.2 评分超时修复

#### 问题描述

重新评分时，处理持续超过一小时不出结果。

#### 根因分析

1. `WebClient` 的 `.block()` 没有设置超时，会无限等待
2. 原始 chunk size 为 15000 字符，LLM 处理慢且容易输出格式错误的 JSON

#### 解决方案

**1. WebClient 超时保护**

```java
// DeepSeekLlmClient.java
private Duration callTimeout = Duration.ofMinutes(5);  // 可配置

String responseJson = webClient.post()
    .uri("/chat/completions")
    .bodyValue(body)
    .retrieve()
    .bodyToMono(String.class)
    .timeout(callTimeout)  // 超时保护
    .block();
```

**2. Chunk size 优化**

```
TRANSCRIPT_CHUNK_SIZE: 15000 → 5000 字符
```

**3. 超时时间可配置**

```yaml
ai:
  providers:
    deepseek:
      timeout-seconds: 600  # 10 分钟（默认 5 分钟）
```

**文件变更**：
- `DeepSeekLlmClient.java` — 添加 timeout、重试机制
- `ScoringServiceImpl.java` — chunk size 15000 → 5000
- `AiProviderProperties.java` — 新增 `timeoutSeconds` 字段
- `AiClientConfig.java` — 设置超时
- `application.yml` — 新增 `timeout-seconds: 600`

### 7.3 LLM 调用重试机制

#### 问题描述

LLM 调用偶尔超时或返回错误，直接导致整个面试处理失败。

#### 解决方案

在 `DeepSeekLlmClient` 中添加自动重试：

```
第 1 次失败 → 等 5 秒 → 重试
第 2 次失败 → 等 10 秒 → 重试
第 3 次失败 → 等 20 秒 → 重试
最终失败 → 抛异常
```

**关键设计**：
- 业务异常（如 API 返回错误）不重试，直接抛出
- 网络异常/超时自动重试
- 最大 3 次重试，指数退避

**文件变更**：
- `DeepSeekLlmClient.java` — 重写 `call()` 方法，拆分为 `call()` + `doCall()`

### 7.4 重试功能扩展

#### 问题描述

原来只有失败（FAILED）状态的面试可以重试，已完成（COMPLETED）的面试无法手动重新评分。

#### 解决方案

放宽状态检查，允许 COMPLETED 和 FAILED 状态重试。PROCESSING 状态也允许强制重试（防止卡住的记录无法恢复）。

```java
// InterviewServiceImpl.retry()
if (interview.getStatus() == InterviewStatus.PROCESSING.getCode()) {
    log.warn("面试记录处于处理中状态，允许强制重试: interviewId={}", id);
}
```

**文件变更**：
- `InterviewServiceImpl.java` — 修改 `retry()` 方法的状态检查逻辑

### 7.5 问题识别改进

#### 问题描述

原始实现使用正则表达式（`问：`、`Q：`、`面试官：`）拆分问题，但 ASR 输出的转录文本是连续文本，没有这些标记，导致只识别出 1 个问题。

#### 解决方案：LLM 语义识别 QA 对

不再依赖正则拆分，而是将原始转录文本发送给 LLM，由 LLM 语义识别每个问答对。

```
原始方式（已废弃）：
  正则拆分 → "问：" "Q：" "面试官：" → 失败

新方式：
  原始转录文本 → LLM 语义理解 → 识别每个 QA 对
```

**Prompt 设计要点**：
- 告诉 LLM 这是面试转录文本，可能有 ASR 错误
- 要求 LLM 识别每个面试问题和对应的候选人回答
- 输出结构化的 QA 对列表

**文件变更**：
- `ScoringServiceImpl.java` — 替换 `splitIntoSegments()` 为 `splitTranscript()`（字符级分块），重写 Prompt

### 7.6 音频分片转录 — 内容丢失修复

#### 问题描述

分片转录后丢失约 60% 的文本内容。

#### 根因分析（6 个 Bug）

**Bug 1（最严重）：TextMerger 模糊匹配误裁内容**

`findTrimPoint()` 总是返回窗口全长，不管差异在哪个位置。

```
场景：
prevText 尾部   = "...负责系统架构设计"  (8字符)
currentText 头部 = "负责系统架构设"      (7字符，ASR少1字)

旧代码：findTrimPoint → 返回 8 → 裁掉 8 字符
实际应裁：7 字符（窗口长度 - 差异数）
结果：丢失 1 个真实字符
```

**Bug 2：ASR 调用无超时**

`client.transcribe()` 无超时，`timeoutSeconds` 配置项是死代码。ASR 卡住 → 线程永久阻塞 → 并发窗口被占满 → 后续片段全部排队等待。

**Bug 3：静音阈值硬编码 500.0**

安静的面试录音 RMS 可能 < 500，被误判为静音 → 切分点插入语音中间 → ASR 识别断裂。

**Bug 4：maxSegmentDuration=600 导致 chunk 超限**

600s × 32KB/s = 19.2MB，远超 ASR 10MB 限制。

**Bug 5：TextMerger 精确匹配窗口太小**

窗口仅 3 字符，中文同音字导致精确匹配频繁失败，退化为模糊匹配。

**Bug 6：模糊匹配容差 20% 太宽松**

短窗口下 20% 容差导致误匹配，错误裁剪。

#### 修复方案

**TextMerger.java**：
- `findTrimPoint()` → `trimPoint = windowLen - diffCount`（保守裁剪）
- 精确匹配窗口：3 → 5 字符
- 模糊匹配容差：20% → 15%
- 最小窗口：3 → 8 字符

**ReliableSender.java**：
- 每次 ASR 调用用 `Future.get(timeoutSeconds, SECONDS)` 包装
- 添加全局超时保护
- 添加 `totalAsrTime` 耗时统计

**SilenceDetector.java**：
- 阈值从 `AudioChunkProperties` 读取，不再硬编码 500.0

**AudioChunkProperties.java**：
- `maxSegmentDuration`: 600 → 180（5.76MB，base64 后 < 8MB）
- `minSegmentDuration`: 60 → 30
- `minSilenceDuration`: 2.0 → 1.0
- 新增 `silenceThreshold`: 300.0

**application.yml**：
- 新增 `silence-threshold: 300.0`
- `max-segment-duration: 180`

---

## 八、AI 模块架构

### 8.1 架构设计

AI 模块采用**注册式工厂 + 策略模式**，支持运行时切换 AI 模型。

```
AiProperties (yml 配置)
    │
    ▼
AiClientConfig (@Bean 初始化)
    │ 注册到 AiClientFactory
    │
    ├── "deepseek"  -> DeepSeekLlmClient (LLM)
    ├── "xiaomi"    -> XiaomiLlmClient (LLM)
    ├── "funasr"    -> DashScopeAsrClient (ASR)
    ├── "whisper"   -> WhisperAsrClient (ASR)
    └── "xiaomi-asr" -> XiaomiAsrClient (ASR)
    │
    ▼
TranscriptionService / ScoringService / CompanyClassifier
    │ 调用 AiClientFactory 获取具体客户端
    │
    ▼
LlmClient.call() / AsrClient.transcribe()
```

### 8.2 核心组件

| 组件 | 文件路径 | 说明 |
|------|---------|------|
| AiClientFactory | `ai-ai/.../factory/AiClientFactory.java` | AI 客户端工厂 |
| AiClientConfig | `ai-ai/.../config/AiClientConfig.java` | 客户端注册配置 |
| DeepSeekLlmClient | `ai-ai/.../client/DeepSeekLlmClient.java` | DeepSeek LLM 客户端（含重试） |
| XiaomiLlmClient | `ai-ai/.../client/XiaomiLlmClient.java` | 小米 LLM 客户端（继承 DeepSeek） |
| DashScopeAsrClient | `ai-ai/.../client/DashScopeAsrClient.java` | 阿里云 ASR 客户端 |
| WhisperAsrClient | `ai-ai/.../client/WhisperAsrClient.java` | OpenAI Whisper ASR 客户端 |
| XiaomiAsrClient | `ai-ai/.../client/XiaomiAsrClient.java` | 小米 ASR 客户端 |
| AudioPreprocessor | `ai-ai/.../audio/AudioPreprocessor.java` | 音频预处理器 |
| SilenceDetector | `ai-ai/.../audio/SilenceDetector.java` | 静音检测器 |
| AudioSplitter | `ai-ai/.../audio/AudioSplitter.java` | 智能切片器 |
| ReliableSender | `ai-ai/.../audio/ReliableSender.java` | 可靠并发发送器 |
| TextMerger | `ai-ai/.../audio/TextMerger.java` | 文本合并去重器 |
| StructuredOutputInvoker | `ai-ai/.../util/StructuredOutputInvoker.java` | LLM 调用工具（重试+JSON修复） |
| ScoringServiceImpl | `ai-ai/.../service/impl/ScoringServiceImpl.java` | 评分服务实现 |
| TranscriptionServiceImpl | `ai-ai/.../service/impl/TranscriptionServiceImpl.java` | 转录服务实现 |
| CompanyClassifierImpl | `ai-ai/.../service/impl/CompanyClassifierImpl.java` | 公司分级实现 |

### 8.3 LLM 客户端重试机制

```
DeepSeekLlmClient.call()
    │
    ├── 第 1 次调用 doCall()
    │   ├── 成功 → 返回结果
    │   └── 业务异常（API 错误） → 直接抛出，不重试
    │   └── 网络异常/超时 → 进入重试
    │
    ├── 等待 5 秒
    ├── 第 2 次调用 doCall()
    │   ├── 成功 → 返回结果
    │   └── 失败 → 进入重试
    │
    ├── 等待 10 秒
    ├── 第 3 次调用 doCall()
    │   ├── 成功 → 返回结果
    │   └── 失败 → 进入重试
    │
    ├── 等待 20 秒
    └── 第 4 次调用 doCall()
        ├── 成功 → 返回结果
        └── 最终失败 → 抛出异常
```

### 8.4 StructuredOutputInvoker 机制

```java
public JsonNode invoke(LlmClient client, String prompt, String systemPrompt, Long configId) {
    for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
        // 1. 调用 LLM
        String response = client.call(prompt, systemPrompt, configId);

        // 2. 清理响应（去除 markdown 代码块包裹）
        response = cleanJsonResponse(response);

        // 3. 尝试解析 JSON
        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            // 4. 尝试本地修复
            String repaired = repairJson(response);
            if (repaired != null) {
                return objectMapper.readTree(repaired);
            }
        }
    }
    throw new BusinessException("AI 返回格式异常");
}
```

### 8.5 评分维度权重

```java
// ScoringWeights.java
public static final double CONTENT = 0.25;        // 内容专业度
public static final double LOGIC = 0.20;          // 逻辑思维
public static final double EXPRESSION = 0.15;     // 表达能力
public static final double PROFESSIONAL = 0.25;   // 专业知识
public static final double COMMUNICATION = 0.15;  // 沟通协作

// 计算公式
overallScore = (int) Math.round(
    dimensionContent * 0.25 +
    dimensionLogic * 0.20 +
    dimensionExpression * 0.15 +
    dimensionProfessional * 0.25 +
    dimensionCommunication * 0.15
);
```

**注意**：`overallScore` 由后端使用加权公式计算，不使用 LLM 返回的值，确保评分一致性。

### 8.6 如何添加新的 AI 模型

**第一步：在 application.yml 中注册 provider**

```yaml
ai:
  providers:
    qwen:
      api-key: ${QWEN_API_KEY}
      api-endpoint: https://dashscope.aliyuncs.com/compatible-mode/v1
      model-name: qwen-plus
      timeout-seconds: 300
```

**第二步：在 AiClientConfig 中添加 Bean**

```java
@Bean
public LlmClient qwenLlmClient() {
    AiProviderProperties props = aiProperties.getProvider("qwen");
    if (props == null || isPlaceholderApiKey(props.getApiKey())) {
        log.warn("未配置 qwen LLM，跳过注册");
        return null;
    }
    DeepSeekLlmClient client = new DeepSeekLlmClient(props);
    client.setCallTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
    aiClientFactory.registerLlmClient("qwen", client);
    return client;
}
```

**第三步：在 ai_config 表中插入配置记录**

```sql
INSERT INTO ai_config (config_name, provider, model_name, api_endpoint, config_type, is_default)
VALUES ('通义千问文本分析', 'qwen', 'qwen-plus', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 2, 0);
```

**第四步：重启应用**

---

## 九、模块详细说明

### 9.1 认证模块 (ai-auth)

**注册流程：**
```
1. 用户发送邮箱 → POST /auth/send-code
2. 系统生成 6 位验证码，存入 Redis（有效期 5 分钟）
3. 用户填写邮箱+验证码+用户名+密码 → POST /auth/register
4. 系统校验验证码、用户名唯一性
5. 密码 BCrypt 加密后存入数据库
6. 注册成功，自动返回 JWT Token
```

**登录流程：**
```
1. 用户输入账号（邮箱或用户名）+ 密码 → POST /auth/login
2. 系统查询用户记录，校验密码
3. 检查账户是否被禁用
4. 生成 JWT Token，返回给前端
5. 前端将 Token 存入 localStorage
6. 后续请求携带 Header: Authorization: Bearer {token}
```

**权限控制：**

| 角色 | 标识 | 说明 |
|------|------|------|
| 管理员 | ROLE_ADMIN | 拥有系统所有权限（user.role = 1） |
| 普通用户 | ROLE_USER | 仅访问自身数据（user.role = 0） |

**接口列表：**

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | /auth/send-code | 发送邮箱验证码 | 否 |
| POST | /auth/register | 用户注册 | 否 |
| POST | /auth/login | 用户登录 | 否 |
| POST | /auth/logout | 退出登录 | 是 |
| GET | /auth/profile | 获取用户信息 | 是 |
| PUT | /auth/profile | 更新用户信息 | 是 |
| PUT | /auth/password | 修改密码 | 是 |

### 9.2 面试模块 (ai-interview)

**接口列表：**

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | /interviews/upload | 上传面试 | 是 |
| GET | /interviews | 面试列表（分页+筛选） | 是 |
| GET | /interviews/{id} | 面试详情 | 是 |
| DELETE | /interviews/{id} | 删除面试 | 是 |
| GET | /interviews/{id}/status | 处理进度 | 是 |
| POST | /interviews/{id}/retry | 重新处理（任意状态） | 是 |

### 9.3 笔记模块

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /interviews/{id}/note | 获取面试笔记 |
| PUT | /interviews/{id}/note | 保存面试笔记 |
| GET | /interviews/{id}/questions/{qid}/note | 获取问题笔记 |
| PUT | /interviews/{id}/questions/{qid}/note | 保存问题笔记 |

### 9.4 收藏模块

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /favorites | 收藏问题 |
| DELETE | /favorites/{id} | 取消收藏 |
| GET | /favorites | 收藏列表（分页） |

### 9.5 分享与面经模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | /interviews/{id}/share | 生成分享链接/发布面经 | 是 |
| GET | /interviews/{id}/shares | 分享记录列表 | 是 |
| DELETE | /shares/{token} | 撤销分享 | 是 |
| PUT | /shares/{token}/public | 切换公开状态 | 是 |
| GET | /share/{token} | 访问分享页面 | 否 |
| GET | /experiences | 面经广场列表 | 否 |
| GET | /experiences/{token} | 面经详情 | 否 |
| GET | /experiences/{token}/comments | 评论列表 | 否 |
| POST | /experiences/{token}/comments | 发表评论 | 是 |
| DELETE | /comments/{id} | 删除评论 | 是 |

### 9.6 管理模块 (ai-admin)

**权限说明**：所有管理员接口通过 `@PreAuthorize("hasRole('ADMIN')")` 进行角色级访问控制，即 `user.role=1` 的用户拥有全部管理权限。数据库 `permission` 表中定义的细粒度权限标识（如 `user:list`、`user:manage`）目前仅用于数据记录，实际鉴权以角色为准。

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /admin/users | 用户列表 | user:list |
| GET | /admin/users/{id} | 用户详情 | user:list |
| PUT | /admin/users/{id}/status | 禁用/启用用户 | user:manage |
| PUT | /admin/users/{id}/role | 修改用户角色 | user:manage |
| GET | /admin/stats/overview | 系统统计 | system:stats |
| GET | /admin/interviews | 所有面试数据 | interview:view_all |
| GET | /admin/experiences | 所有面经 | experience:manage |
| PUT | /admin/experiences/{token}/status | 下架/恢复面经 | experience:manage |
| GET | /admin/comments | 所有评论 | comment:manage |
| DELETE | /admin/comments/{id} | 删除评论 | comment:manage |

### 9.7 AI 配置管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /ai-configs | AI 配置列表 | ADMIN |
| GET | /ai-configs/{id} | 配置详情 | ADMIN |
| POST | /ai-configs | 新增配置 | ADMIN |
| PUT | /ai-configs/{id} | 更新配置 | ADMIN |
| DELETE | /ai-configs/{id} | 删除配置 | ADMIN |
| PUT | /ai-configs/{id}/default | 设为默认 | ADMIN |

---

## 十、统一响应格式

### 10.1 标准响应

```json
{
    "code": 200,
    "message": "success",
    "data": {}
}
```

### 10.2 分页响应

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

### 10.3 错误码一览

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证（Token 无效或过期） |
| 403 | 无权限访问 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 1001-1008 | 用户模块错误（用户名已存在、密码错误、账户禁用等） |
| 2001-2005 | 面试模块错误（格式不支持、文件过大、记录不存在等） |
| 3001-3003 | AI 模块错误（配置不存在、模型调用失败、面试处理失败） |
| 4001-4004 | 收藏/分享模块错误 |
| 5001-5006 | 管理员模块错误 |

---

## 十一、部署指南

### 11.1 构建

```bash
mvn clean package -DskipTests
# 生成: ai-web/target/ai-web-1.0.0-SNAPSHOT.jar
```

### 11.2 启动

```bash
java -jar ai-web/target/ai-web-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --DB_HOST=your-db-host \
  --DB_PASSWORD=your-db-password \
  --REDIS_PASSWORD=your-redis-password \
  --JWT_SECRET=your-256-bit-secret \
  --DEEPSEEK_API_KEY=your-key
```

### 11.3 生产环境注意事项

| 事项 | 说明 |
|------|------|
| JWT_SECRET | 必须使用高强度随机密钥（至少 256 位） |
| 数据库密码 | 必须设置环境变量，不可使用默认密码 |
| API 密钥 | 通过环境变量注入，不要写入代码或镜像 |
| Druid 面板 | 生产环境建议关闭或限制访问 |
| 日志级别 | 生产环境使用 info/warn，关闭 debug |
| 文件存储 | 建议迁移至 OSS/对象存储 |

---

## 十二、常见问题

### Q1: 启动时报数据库连接失败

检查项：
1. MySQL 服务是否启动
2. `DB_HOST`、`DB_PORT`、`DB_USERNAME`、`DB_PASSWORD` 环境变量是否正确
3. 数据库 `ai_interview` 是否已创建（执行 `sql/schema.sql`）

### Q2: AI 调用失败（3002 错误）

检查项：
1. `DEEPSEEK_API_KEY` 环境变量是否已设置
2. API Key 是否有效（非占位值 `your-xxx-api-key`）
3. 网络是否能访问 AI 服务端点（如 api.deepseek.com）

### Q3: 音频上传后一直处于"处理中"

检查项：
1. 查看应用日志中的错误信息
2. 确认 ASR API 密钥有效
3. 检查音频文件格式是否支持（MP3/WAV）
4. 通过 `POST /interviews/{id}/retry` 重新触发处理
5. 如果是卡住的旧记录，新版本已允许 PROCESSING 状态重试

### Q4: LLM 调用超时

检查项：
1. 网络是否稳定
2. `timeout-seconds` 配置是否足够（建议 600 秒）
3. 日志中查看 prompt 长度，过长的 prompt 会增加响应时间
4. 系统已内置 3 次自动重试，偶发超时会自动恢复

### Q5: 分片转录后文本丢失大量内容

检查项：
1. 查看日志中的 `Missing chunk` 警告（某些片段 ASR 失败）
2. 查看日志中的 `Fuzzy match` 信息（文本合并裁剪详情）
3. 确认 `silence-threshold` 配置合理（默认 300.0）
4. 确认 `max-segment-duration` 不超过 180 秒

### Q6: 评分结果不准确

检查项：
1. 确认使用的是阶梯制评分（而非直接 0-100 打分）
2. 可尝试启用双阶段评分：`SCORING_TWO_PHASE=true`
3. 检查 `ScoringWeights` 权重配置（总和应为 1.0）

### Q7: 如何切换默认 AI 模型

1. 确保新模型的 provider 已在 `application.yml` 中注册
2. 在 `ai_config` 表中插入对应配置记录
3. 通过管理员接口 `PUT /ai-configs/{id}/default` 设为默认
4. 无需重启应用

### Q8: FFmpeg 相关错误

检查项：
1. 非 WAV 格式音频需要 FFmpeg
2. 系统会自动搜索 FFmpeg 路径，大多数情况无需手动配置
3. 如果自动搜索失败，设置环境变量 `FFMPEG_PATH` 或在 `application.yml` 中配置 `ai.chunk.ffmpeg-path`

---

## 十三、版本变更记录

### v2.0 (2026-06-07)

#### 新增功能

| 功能 | 说明 | 涉及文件 |
|------|------|---------|
| 音频分片转录 | 大音频自动切片 → 并发 ASR → 文本合并 | `AudioPreprocessor`, `AudioSplitter`, `SilenceDetector`, `ReliableSender`, `TextMerger`, `AudioChunkProperties` |
| 阶梯制评分 | LLM 输出分类而非分数，提高准确性 | `ScoringServiceImpl` |
| 证据锚定 | 每个评分维度必须引用原文 | `ScoringServiceImpl` |
| 双阶段评估 | 分析 → 评分分离，可选高精度模式 | `ScoringServiceImpl`, `InterviewAsyncServiceImpl` |
| LLM 问题识别 | 语义识别 QA 对，替代正则拆分 | `ScoringServiceImpl` |
| LLM 调用重试 | 3 次重试，指数退避 | `DeepSeekLlmClient` |
| ASR 超时保护 | 每次调用 30 秒超时 | `ReliableSender` |
| LLM 超时可配置 | 通过 `timeout-seconds` 配置 | `AiProviderProperties`, `AiClientConfig` |
| PROCESSING 可重试 | 卡住的记录允许强制重试 | `InterviewServiceImpl` |

#### 问题修复

| 问题 | 根因 | 修复 | 涉及文件 |
|------|------|------|---------|
| 评分不准确 | LLM 直接输出 0-100 分数不稳定 | 阶梯制评分 + 证据锚定 | `ScoringServiceImpl` |
| 评分超时 | WebClient 无超时 + chunk 太大 | 添加 timeout + chunk 5000 | `DeepSeekLlmClient`, `ScoringServiceImpl` |
| 只识别 1 个问题 | 正则拆分在 ASR 文本上失效 | LLM 语义识别 | `ScoringServiceImpl` |
| 转录文本丢失 60% | TextMerger 误裁 + ASR 无超时 + 阈值硬编码 | 保守裁剪 + 超时保护 + 可配置阈值 | `TextMerger`, `ReliableSender`, `SilenceDetector`, `AudioChunkProperties` |
| 重试仅限失败状态 | 状态检查过严 | 放宽为任意状态可重试 | `InterviewServiceImpl` |

### v1.0 (2026-06-03)

- 初始版本发布
- 完整的用户系统、面试上传、AI 分析、复盘、笔记、收藏、分享、面经功能
- 管理后台
- 分批评估机制
- ASR 错误处理增强

---

## 十四、项目文件索引

### 14.1 关键配置文件

| 文件路径 | 说明 |
|---------|------|
| ai-web/src/main/resources/application.yml | 主配置文件 |
| ai-web/src/main/resources/application-dev.yml | 开发环境配置 |
| sql/schema.sql | 数据库建表脚本 |
| pom.xml | 父 POM |

### 14.2 核心源码文件

| 文件路径 | 说明 |
|---------|------|
| ai-web/.../AiInterviewApplication.java | Spring Boot 启动类 |
| ai-common/.../constant/InterviewStatus.java | 面试状态枚举 |
| ai-common/.../constant/CompanyTier.java | 公司档次枚举 |
| ai-common/.../constant/ScoringWeights.java | 评分维度权重 |
| ai-common/.../constant/ErrorCode.java | 错误码定义 |
| ai-common/.../exception/GlobalExceptionHandler.java | 全局异常处理 |
| ai-common/.../result/Result.java | 统一响应封装 |
| ai-common/.../utils/JwtUtil.java | JWT 工具类 |
| ai-common/.../utils/FileUtil.java | 文件操作工具类 |
| ai-common/.../utils/SecurityUtils.java | 安全上下文工具类 |
| ai-auth/.../config/SecurityConfig.java | Spring Security 配置 |
| ai-auth/.../config/JwtAuthenticationFilter.java | JWT 认证过滤器 |
| ai-auth/.../controller/AuthController.java | 认证接口 |
| ai-auth/.../service/impl/AuthServiceImpl.java | 认证业务实现 |
| ai-interview/.../controller/InterviewController.java | 面试接口 |
| ai-interview/.../service/impl/InterviewServiceImpl.java | 面试业务实现 |
| ai-interview/.../service/impl/InterviewAsyncServiceImpl.java | 异步处理核心 |
| ai-interview/.../service/impl/ScoreResultParser.java | 评分结果解析 |
| ai-ai/.../factory/AiClientFactory.java | AI 客户端工厂 |
| ai-ai/.../config/AiClientConfig.java | 客户端注册配置 |
| ai-ai/.../config/AiProviderProperties.java | AI 提供商配置属性 |
| ai-ai/.../config/AudioChunkProperties.java | 音频分片配置属性 |
| ai-ai/.../client/DeepSeekLlmClient.java | DeepSeek LLM 客户端（含重试） |
| ai-ai/.../client/XiaomiLlmClient.java | 小米 LLM 客户端 |
| ai-ai/.../client/DashScopeAsrClient.java | 阿里云 ASR 客户端 |
| ai-ai/.../client/WhisperAsrClient.java | Whisper ASR 客户端 |
| ai-ai/.../client/XiaomiAsrClient.java | 小米 ASR 客户端 |
| ai-ai/.../audio/AudioPreprocessor.java | 音频预处理器 |
| ai-ai/.../audio/SilenceDetector.java | 静音检测器 |
| ai-ai/.../audio/AudioSplitter.java | 智能切片器 |
| ai-ai/.../audio/ReliableSender.java | 可靠并发发送器 |
| ai-ai/.../audio/TextMerger.java | 文本合并去重器 |
| ai-ai/.../audio/FfmpegResolver.java | FFmpeg 路径探测器 |
| ai-ai/.../audio/AudioChunk.java | 音频片段数据类 |
| ai-ai/.../service/impl/TranscriptionServiceImpl.java | 语音转文字服务 |
| ai-ai/.../service/impl/ScoringServiceImpl.java | 文本评分服务 |
| ai-ai/.../service/impl/CompanyClassifierImpl.java | 公司分级服务 |
| ai-ai/.../util/StructuredOutputInvoker.java | LLM 调用工具 |
| ai-ai/.../config/StructuredOutputProperties.java | 重试配置属性 |
| ai-admin/.../controller/AdminUserController.java | 用户管理接口 |
| ai-admin/.../controller/AdminStatsController.java | 系统统计接口 |
