# AI 面试复盘系统 - 后端

基于 Spring Boot 3.2.5 的多模块面试复盘平台后端，提供面试录音上传、AI 智能分析评分、面经分享等功能。

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | LTS 版本 |
| Spring Boot | 3.2.5 | 基础框架 |
| Spring Security | 6.2.x | 认证授权 |
| MyBatis-Plus | 3.5.5 | ORM 框架 |
| Druid | 1.2.21 | 数据库连接池 |
| Redis | - | Token 黑名单 / 缓存 |
| MySQL | 8.0+ | 数据库 |
| Knife4j | 4.4.0 | API 文档 |
| JJWT | 0.12.5 | JWT 令牌 |
| Apache PDFBox | 3.0.1 | 简历 PDF 解析 |

## 项目结构

```
AI_InterviewReview/
├── ai-common          # 公共模块：常量、工具类、异常处理、实体基类
├── ai-auth            # 认证模块：注册、登录、JWT、RBAC
├── ai-interview       # 面试模块：上传、评分、分享、面经、笔记、收藏
├── ai-admin           # 管理模块：用户管理、面经管理、评论管理、统计
├── ai-ai              # AI 模块：多模型配置、语音转文字、智能评分
├── ai-web             # 启动模块：应用入口、配置文件
├── sql/               # 数据库建表脚本
├── docs/              # 设计文档
└── pom.xml            # 父 POM
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+

### 1. 克隆项目

```bash
git clone https://github.com/NingTheshy/AI_InterviewReview.git
cd AI_InterviewReview
```

### 2. 初始化数据库

```bash
mysql -u root -p < sql/schema.sql
```

### 3. 配置环境变量

复制环境变量模板并填入实际值：

```bash
cp .env.example .env
```

编辑 `.env` 文件，配置数据库、Redis、JWT 密钥和 AI API Key：

```env
DB_HOST=localhost
DB_PORT=3306
DB_NAME=ai_interview
DB_USERNAME=root
DB_PASSWORD=your_password

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password

JWT_SECRET=your_256_bit_secret_key

DEEPSEEK_API_KEY=your_deepseek_api_key
OPENAI_API_KEY=your_openai_api_key
WHISPER_API_KEY=your_whisper_api_key
XIAOMI_API_KEY=your_xiaomi_api_key
FUNASR_API_KEY=your_funasr_api_key
```

### 4. 编译运行

```bash
mvn clean compile
mvn spring-boot:run -pl ai-web
```

应用默认启动在 `http://localhost:8080`。

### 5. 切换环境

通过 `SPRING_PROFILES_ACTIVE` 环境变量切换：

- `dev` - 开发环境（默认）
- `test` - 测试环境
- `prod` - 生产环境

## API 接口

启动后访问 Knife4j 文档：`http://localhost:8080/swagger-ui.html`

### 认证模块 `/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/send-code` | 发送邮箱验证码 |
| POST | `/auth/register` | 用户注册 |
| POST | `/auth/login` | 用户登录 |
| POST | `/auth/logout` | 退出登录 |
| GET | `/auth/profile` | 获取当前用户信息 |
| PUT | `/auth/password` | 修改密码 |
| PUT | `/auth/profile` | 更新用户信息 |

### 面试模块 `/interviews`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/interviews/upload` | 上传面试（音频+简历+JD） |
| GET | `/interviews` | 获取面试列表（分页、筛选、排序） |
| GET | `/interviews/{id}` | 获取面试详情 |
| DELETE | `/interviews/{id}` | 删除面试记录 |
| GET | `/interviews/{id}/status` | 查询处理进度 |
| POST | `/interviews/{id}/retry` | 重新处理失败的面试 |

### 笔记模块

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/interviews/{id}/note` | 获取面试笔记 |
| PUT | `/interviews/{id}/note` | 保存面试笔记 |
| GET | `/interviews/{id}/questions/{qid}/note` | 获取问题笔记 |
| PUT | `/interviews/{id}/questions/{qid}/note` | 保存问题笔记 |

### 分享与面经

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/interviews/{id}/share` | 生成分享链接/发布面经 |
| GET | `/interviews/{id}/shares` | 获取分享记录 |
| DELETE | `/shares/{token}` | 撤销分享 |
| PUT | `/shares/{token}/public` | 切换面经公开状态 |
| GET | `/share/{token}` | 访问分享页面 |
| GET | `/experiences` | 面经广场列表（公开） |
| GET | `/experiences/{token}` | 面经详情 |

### 评论模块

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/experiences/{token}/comments` | 获取评论列表 |
| POST | `/experiences/{token}/comments` | 发表评论 |
| DELETE | `/comments/{id}` | 删除评论 |

### 收藏模块 `/favorites`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/favorites` | 收藏问题 |
| DELETE | `/favorites/{id}` | 取消收藏 |
| GET | `/favorites` | 获取收藏列表 |

### 文件下载

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/files/{filename}` | 文件/音频下载（支持 Range 分段） |

### 管理后台 `/admin` (需要 ADMIN 角色)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/users` | 用户列表 |
| GET | `/admin/users/{id}` | 用户详情 |
| PUT | `/admin/users/{id}/status` | 启用/禁用用户 |
| PUT | `/admin/users/{id}/role` | 修改用户角色 |
| GET | `/admin/stats/overview` | 系统统计概览 |
| GET | `/admin/experiences` | 面经列表 |
| PUT | `/admin/experiences/{token}/status` | 下架/恢复面经 |
| GET | `/admin/comments` | 评论列表 |
| DELETE | `/admin/comments/{id}` | 删除评论 |
| GET | `/admin/interviews` | 面试列表 |

### AI 配置管理 `/ai-configs` (需要 ADMIN 角色)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/ai-configs` | AI 配置列表 |
| GET | `/ai-configs/{id}` | 配置详情 |
| POST | `/ai-configs` | 新增配置 |
| PUT | `/ai-configs/{id}` | 更新配置 |
| DELETE | `/ai-configs/{id}` | 删除配置 |
| PUT | `/ai-configs/{id}/default` | 设为默认 |

## 核心功能

### AI 面试分析流程

1. **上传面试材料** - 用户上传音频文件（MP3/WAV）、简历 PDF、岗位 JD
2. **语音转文字** - 通过 Whisper / FunASR 进行语音识别
3. **问题边界识别** - AI 识别面试中的问答对
4. **逐题评分** - 从内容质量、逻辑性、表达能力、专业度四个维度评分
5. **整体评估** - 生成整体评分、优势总结、改进建议

### 支持的 AI 服务

| 服务 | 用途 |
|------|------|
| DeepSeek | 文本分析与评分 |
| OpenAI GPT-4o | 文本分析与评分 |
| OpenAI Whisper | 语音转文字 |
| 小米 MIMO-V2.5 | 文本分析与评分 |
| 阿里云 FunASR | 实时语音识别 |

可通过管理后台 `/ai-configs` 动态切换 AI 模型。

### 安全特性

- JWT 认证 + Token 黑名单（Redis）
- BCrypt 密码加密
- 登录失败锁定（5 次失败锁定 15 分钟）
- RBAC 角色权限控制（普通用户 / 管理员）
- 文件上传校验（音频 MP3/WAV 最大 200MB，简历 PDF 最大 20MB）
- 管理员自我保护（不可自我降级/禁用）

## 测试

```bash
# 运行所有测试
mvn test

# 运行单个模块测试
mvn test -pl ai-common
mvn test -pl ai-auth
mvn test -pl ai-interview
mvn test -pl ai-admin
mvn test -pl ai-ai
```

## License

MIT
