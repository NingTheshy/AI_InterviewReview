# AI 面试复盘系统（后端）

基于 Spring Boot 3 的多模块后端服务。用户上传面试录音、简历和岗位描述后，系统通过 Spring AI 模型层调用 FunASR 完成句子级转写与说话人分离，再由 Spring AI 文本模型生成标准答案、逐项比对候选人回答，并输出可回放、可解释的复盘结果。

## 核心能力

- 面试音频、简历 PDF 与岗位 JD 上传，异步处理并提供进度查询与重试。
- FunASR 非实时整段转写：保留每句话的毫秒级时间戳和 `speakerId`，再根据问答识别映射为“面试官 / 候选人”。
- 标准答案量表评分：先生成或复用满分标准，再按评分要点比对回答；分数由服务端根据固定权重计算。
- 面试整体笔记一条、问题笔记按问题分别保存；支持收藏、分享、面经广场、评论和管理员审核。
- JWT + Redis 黑名单认证、RBAC、文件所有权校验，以及供 FunASR 拉取音频的短时 HMAC 签名 URL。

## 技术栈

| 技术 | 版本 / 说明 |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.15 |
| Spring AI | 1.1.8；统一承载 DeepSeek、MiMo、Whisper 与 FunASR 模型调用 |
| FunASR（DashScope） | 自定义 Spring AI `TranscriptionModel`，支持非实时转写与说话人分离 |
| Spring Security | JWT、RBAC、CORS |
| MyBatis-Plus / Druid | 数据访问与连接池 |
| MySQL / Redis | 业务数据、Token 黑名单与缓存 |
| Knife4j / OpenAPI | 接口文档 |
| Apache PDFBox | 简历 PDF 文本提取 |

## 项目结构

```text
AI_InterviewReview/
├── ai-common/       # 公共常量、统一响应、异常、JWT、文件与签名工具
├── ai-auth/         # 注册、登录、JWT 过滤器、用户资料与权限
├── ai-ai/           # Spring AI 模型适配、FunASR、音频预处理、评分量表与模型工厂
├── ai-interview/    # 面试上传、异步复盘、笔记、收藏、分享、面经和评论
├── ai-admin/        # 用户、面经、评论、面试和统计管理
├── ai-web/          # Spring Boot 启动模块与运行配置
├── sql/             # 新建库脚本与增量升级脚本
├── md/              # 需求、数据库、架构和代码注释规范文档
├── .env.example     # 环境变量模板（唯一格式来源）
└── pom.xml          # Maven 父工程
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+
- FFmpeg（使用 FunASR 时需要，用于转换为单声道 WAV）

### 1. 初始化数据库

SQL 脚本统一维护为结构与初始化数据两个文件。新建数据库按顺序执行：

```bash
mysql -u root -p < sql/schema.sql
mysql -u root -p ai_interview < sql/data.sql
```

`schema.sql` 只包含当前完整表结构，`data.sql` 包含预置角色、20 个权限码、角色权限关系和默认 AI 配置，且可重复执行。
项目不再保留分散的历史增量脚本。已有数据库升级前必须先备份并与当前 `schema.sql` 做结构差异迁移，不能直接在旧库上重复执行建表脚本；已经处于当前结构的数据库可单独重复执行 `data.sql` 补齐初始化数据。

### 2. 配置环境变量

严格以 [`.env.example`](.env.example) 为模板创建本地 `.env`，不要提交 `.env`：

```bash
cp .env.example .env
```

至少填写数据库、Redis、JWT 和实际使用的模型密钥。生产环境使用 FunASR 时，`FUNASR_PUBLIC_BASE_URL` 必须是 DashScope 能从互联网访问的 HTTPS 地址，不能是 `localhost`；开发测试可先不配置并避免调用真实 FunASR。

```env
DB_PASSWORD=your_database_password
REDIS_PASSWORD=your_redis_password
JWT_SECRET=your_256_bit_secret_key_here
DEEPSEEK_API_KEY=your_deepseek_api_key
FUNASR_API_KEY=your_funasr_api_key
AI_CONFIG_ENCRYPTION_KEY=replace_with_base64_encoded_32_byte_key
FUNASR_PUBLIC_BASE_URL=https://api.example.com
FUNASR_FILE_URL_SECRET=replace_with_a_long_random_secret
```

### 3. 编译、测试与启动

通用 Maven 命令：

```bash
mvn -s .mvn-settings.xml clean test
mvn -s .mvn-settings.xml spring-boot:run -pl ai-web
```

本项目在 Windows 的指定 Maven 与本地仓库环境可使用：

```powershell
& 'D:\Maven\apache-maven-3.8.4\bin\mvn.cmd' -s '.mvn-settings.xml' test
& 'D:\Maven\apache-maven-3.8.4\bin\mvn.cmd' -s '.mvn-settings.xml' spring-boot:run -pl ai-web
```

服务默认地址：`http://localhost:8080`。

接口文档：`http://localhost:8080/swagger-ui.html`。

## AI 复盘处理流程

```text
上传音频、简历、JD
        ↓
FunASR：整段单声道 WAV 转写 + 句子时间戳 + speakerId
        ↓
公司分级、问答识别、speakerId 角色映射
        ↓
生成或复用满分标准（量表）
        ↓
逐要点比对回答，服务端根据权重计算得分
        ↓
保存问题评分、角色化转写、整体评分和改进建议
```

处理状态依次为：FunASR 转写与说话人识别、标准答案生成与逐项比对、服务端计分与结果保存、整体评分与总结。历史面试只有重新处理后才会生成句子级转写和角色信息。

## 接口概览

项目共定义 **49 条业务 HTTP 路由**：普通业务与用户接口 31 条、管理员接口 17 条、FunASR 签名音频访问接口 1 条。Swagger、Knife4j 与 Druid 等基础设施地址不计入该数字。

| 分类 | 路径前缀 | 数量 | 说明 |
|---|---|---:|---|
| 认证与用户 | `/auth` | 7 | 注册、登录、验证码、资料、密码与退出登录 |
| 面试与笔记 | `/interviews` | 10 | 上传、列表、详情、状态、重试、整体/问题笔记 |
| 分享与面经 | `/share`、`/shares`、`/experiences` | 7 | 分享创建、公开状态、面经浏览 |
| 评论与收藏 | `/comments`、`/favorites` | 6 | 评论 CRUD、问题或面试收藏 |
| 文件 | `/files`、`/asr-files` | 2 | 用户文件 Range 下载、FunASR 短时签名下载 |
| AI 配置 | `/ai-configs` | 6 | 管理员维护模型配置与默认项 |
| 管理后台 | `/admin/**` | 11 | 用户、统计、面试、面经、评论和审计日志管理 |

认证接口中的登录、注册和验证码发送公开；公开面经浏览及公开分享无需登录。`/asr-files/**` 不用于普通前端访问，专供 DashScope 通过 HMAC 短时签名读取音频。

## 安全边界

- JWT 只携带身份，Redis 维护 Token 黑名单；每次请求从数据库实时加载权限和账户状态。
- 接口按具体权限码授权，角色仅用于组织权限；业务服务继续校验资源归属，未明确授权则拒绝。
- 最后管理者保护会阻止禁用或撤销系统中最后一个有效权限管理者。
- 普通用户只能访问自己上传的文件、面试、笔记和分享记录。
- FunASR 不携带用户 JWT，因此使用文件名与过期时间共同签名的 HMAC URL，且服务端拒绝路径穿越。
- API Key、JWT 密钥、AI 配置加密主密钥和数据库密码仅放在 `.env` 或部署环境变量中；`.env` 已被 Git 忽略。
- 数据库中的 AI API Key 使用 AES-256-GCM 加密；部署前必须为 `AI_CONFIG_ENCRYPTION_KEY` 配置独立的 32 字节 Base64 密钥。

## 文档

- [需求文档](md/AI面试复盘系统-需求文档.md)
- [数据库设计文档](md/AI面试复盘系统-数据库设计文档.md)
- [技术架构设计文档](md/AI面试复盘系统-技术架构设计文档.md)
- [代码注释规范](md/代码注释规范.md)

## 验证

当前全量测试结果：**346 tests，0 failures，0 errors**。

## License

MIT
