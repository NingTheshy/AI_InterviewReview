# AI 面试复盘系统 - 开发文档

> 版本: v1.1  
> 日期: 2026-06-03  
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
| AI 分析 | 语音转文字 -> 问题识别 -> 多维度评分 -> 改进建议 |
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

### 1.4 项目结构总览

```
AI_InterviewReview/                    # 项目根目录
├── ai-common/                         # 公共模块：常量、异常、工具类、统一响应
├── ai-auth/                           # 认证模块：用户注册、登录、JWT、权限控制
├── ai-interview/                      # 面试模块：面试上传、复盘、笔记、收藏、分享、面经
├── ai-ai/                             # AI 模块：模型工厂、语音转文字、文本评分
├── ai-admin/                          # 管理模块：用户管理、系统统计、内容管理
├── ai-web/                            # 启动模块：聚合所有模块，Spring Boot 入口
├── sql/                               # 数据库脚本
├── md/                                # 项目文档
└── pom.xml                            # 父 POM（依赖版本管理）
```

---

## 二、完整开发流程

本节详细描述从零开始搭建和开发本系统的完整流程，供新加入的开发人员快速上手。

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
| IntelliJ IDEA | 2022+ | 后端 IDE（需安装 Lombok 插件） | - |
| VS Code | - | 前端 IDE | - |

#### 2.2.2 安装 IDE 插件

**IntelliJ IDEA 必装插件：**

1. **Lombok** - 自动生成 getter/setter/constructor 等
2. **MyBatisX** - Mapper 接口与 XML 文件跳转
3. **SonarLint** - 代码质量检查
4. **Translation** - 翻译插件（中英文互译）

**VS Code 必装插件（前端开发）：**

1. **Vue - Official** - Vue 3 语法支持
2. **TypeScript** - TypeScript 支持
3. **ESLint** - 代码规范检查
4. **Prettier** - 代码格式化

### 2.3 第二步：获取代码

```bash
# 克隆后端仓库
git clone <repository-url> AI_InterviewReview
cd AI_InterviewReview

# 克隆前端仓库（独立仓库）
git clone <frontend-repo-url> AI_InterviewReview_front
```

### 2.4 第三步：数据库初始化

#### 2.4.1 创建数据库

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

#### 2.4.2 启动 Redis

```bash
# Linux/Mac
redis-server

# Windows
# 双击 redis-server.exe 或通过服务管理器启动

# 验证连接
redis-cli ping
# 期望输出: PONG
```

### 2.5 第四步：配置修改

#### 2.5.1 环境变量配置

系统所有敏感信息通过环境变量管理，**不要硬编码在配置文件中**。

**必须配置的环境变量：**

| 环境变量 | 说明 | 默认值 | 必填 |
|---------|------|--------|------|
| `DB_HOST` | MySQL 主机地址 | localhost | 否 |
| `DB_PORT` | MySQL 端口 | 3306 | 否 |
| `DB_NAME` | 数据库名称 | ai_interview | 否 |
| `DB_USERNAME` | 数据库用户名 | root | 否 |
| `DB_PASSWORD` | 数据库密码 | 220718 | 否 |
| `REDIS_HOST` | Redis 主机地址 | localhost | 否 |
| `REDIS_PORT` | Redis 端口 | 6379 | 否 |
| `REDIS_PASSWORD` | Redis 密码 | root | 否 |
| `JWT_SECRET` | JWT 签名密钥 | 见 yml 文件 | 生产环境必填 |
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥 | - | 是 |
| `OPENAI_API_KEY` | OpenAI API 密钥 | - | 是 |

**配置方式（Windows）：**

```powershell
# 通过系统设置 -> 高级系统设置 -> 环境变量 添加
# 或在 PowerShell 中：
[System.Environment]::SetEnvironmentVariable("DB_PASSWORD", "your_password", "User")
[System.Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "sk-xxx", "User")
```

**配置方式（Linux/Mac）：**

```bash
# 写入 shell 配置文件使其永久生效
echo 'export DB_PASSWORD="your_password"' >> ~/.bashrc
echo 'export DEEPSEEK_API_KEY="sk-xxx"' >> ~/.bashrc
source ~/.bashrc
```

#### 2.5.2 AI API 密钥获取

| 服务 | 用途 | 获取方式 |
|------|------|---------|
| DeepSeek | 文本分析与评分 | https://platform.deepseek.com/ |
| OpenAI Whisper | 语音转文字 | https://platform.openai.com/ |

### 2.6 第五步：编译构建

```bash
# 进入项目根目录
cd AI_InterviewReview

# 清理并编译（首次构建会下载依赖，需等待数分钟）
mvn clean compile

# 运行测试
mvn test

# 打包（跳过测试）
mvn clean package -DskipTests

# 生成的 JAR 包位置
# ai-web/target/ai-web-1.0.0-SNAPSHOT.jar
```

**编译验证：**
- 所有 6 个模块应显示 BUILD SUCCESS
- 无编译错误
- 警告信息可忽略（如重复依赖警告需修复）

### 2.7 第六步：启动验证

#### 2.7.1 启动后端

```bash
# 方式一：从 ai-web 模块启动（推荐开发时使用）
cd ai-web
mvn spring-boot:run

# 方式二：打包后直接运行 JAR
java -jar ai-web/target/ai-web-1.0.0-SNAPSHOT.jar

# 方式三：指定环境变量运行
java -jar ai-web/target/ai-web-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  --DB_HOST=localhost \
  --DB_PASSWORD=your_password \
  --DEEPSEEK_API_KEY=your_key
```

#### 2.7.2 启动前端

```bash
# 进入前端项目目录
cd AI_InterviewReview_front

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

#### 2.7.3 验证服务

| 服务 | 地址 | 说明 |
|------|------|------|
| 后端应用 | http://localhost:8080 | 应用首页 |
| API 文档 | http://localhost:8080/swagger-ui.html | Knife4j 文档 |
| Druid 监控 | http://localhost:8080/druid/ | 数据库监控面板 |
| 前端应用 | http://localhost:5173 | Vue 开发服务器 |

**验证步骤：**

```bash
# 1. 验证后端是否正常运行
curl http://localhost:8080/auth/profile
# 期望返回: {"code":401,"message":"未认证","data":null}

# 2. 验证 API 文档
curl http://localhost:8080/v3/api-docs
# 期望返回: OpenAPI JSON 格式的文档

# 3. 前端访问
# 浏览器打开 http://localhost:5173，应显示登录页面
```

### 2.8 第七步：功能开发

#### 2.8.1 开发流程

```
1. 需求分析 → 2. 数据库设计 → 3. 后端开发 → 4. 前端开发 → 5. 联调测试
```

#### 2.8.2 后端开发规范

**新增接口的标准流程：**

```
1. 在 entity 包中创建实体类（@Data, @TableName）
2. 在 mapper 包中创建 Mapper 接口（extends BaseMapper<T>）
3. 在 service 包中创建 Service 接口
4. 在 service.impl 包中创建 ServiceImpl 实现类
5. 在 dto 包中创建请求/响应 DTO
6. 在 controller 包中创建 Controller（@RestController）
7. 在 Knife4j 中测试接口
```

**代码规范：**

```java
// Controller 层：只做三件事
@RestController
@RequestMapping("/api/resource")
@RequiredArgsConstructor
public class ResourceController {
    
    private final ResourceService resourceService;
    
    // 1. 接收参数
    @GetMapping("/{id}")
    public Result<ResourceVO> getById(@PathVariable Long id) {
        // 2. 调用 Service
        ResourceVO vo = resourceService.getById(id);
        // 3. 返回结果
        return Result.success(vo);
    }
}

// Service 层：核心业务逻辑
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {
    
    private final ResourceMapper resourceMapper;
    
    @Override
    public ResourceVO getById(Long id) {
        Resource entity = resourceMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return convertToVO(entity);
    }
}
```

#### 2.8.3 前端开发规范

**新增页面的标准流程：**

```
1. 在 src/api/ 中创建 API 接口文件
2. 在 src/views/ 中创建页面组件
3. 在 src/router/ 中配置路由
4. 在 src/stores/ 中创建状态管理（如需要）
```

**API 调用规范：**

```typescript
// src/api/example.ts
import request from '@/utils/request'

export function getExample(id: number) {
  return request.get(`/api/resource/${id}`)
}

export function createExample(data: ExampleForm) {
  return request.post('/api/resource', data)
}
```

### 2.9 第八步：联调测试

#### 2.9.1 联调环境配置

前端 Vite 开发服务器已配置代理，将 API 请求转发到后端：

```typescript
// vite.config.ts
server: {
  proxy: {
    '/auth': { target: 'http://localhost:8080', changeOrigin: true },
    '/interviews': { target: 'http://localhost:8080', changeOrigin: true },
    '/favorites': { target: 'http://localhost:8080', changeOrigin: true },
    '/shares': { target: 'http://localhost:8080', changeOrigin: true },
    '/share': { target: 'http://localhost:8080', changeOrigin: true },
    '/experiences': { target: 'http://localhost:8080', changeOrigin: true },
    '/comments': { target: 'http://localhost:8080', changeOrigin: true },
    '/ai-configs': { target: 'http://localhost:8080', changeOrigin: true },
    '/admin': { target: 'http://localhost:8080', changeOrigin: true },
    '/files': { target: 'http://localhost:8080', changeOrigin: true }
  }
}
```

#### 2.9.2 联调测试流程

```
1. 启动后端（端口 8080）
2. 启动前端（端口 5173）
3. 浏览器打开 http://localhost:5173
4. 使用默认管理员账号登录（admin / admin123）
5. 测试各功能模块
```

#### 2.9.3 接口联调检查清单

| 检查项 | 说明 | 通过标准 |
|--------|------|---------|
| 登录注册 | POST /auth/login | 返回 JWT Token |
| 面试上传 | POST /interviews/upload | 返回面试记录 ID |
| 处理进度 | GET /interviews/{id}/status | 返回处理状态和步骤 |
| 面试详情 | GET /interviews/{id} | 返回完整评分数据 |
| 笔记保存 | PUT /interviews/{id}/note | 保存成功 |
| 收藏功能 | POST /favorites | 收藏成功 |
| 分享功能 | POST /interviews/{id}/share | 返回分享链接 |
| 面经广场 | GET /experiences | 返回公开面经列表 |

### 2.10 第九步：代码提交

#### 2.10.1 Git 工作流

```
main (生产分支)
  │
  ├── develop (开发分支)
  │     │
  │     ├── feature/xxx (功能分支)
  │     ├── bugfix/xxx (修复分支)
  │     └── hotfix/xxx (紧急修复)
  │
  └── release/x.x.x (发布分支)
```

#### 2.10.2 提交规范

```bash
# 功能开发
git commit -m "feat: 新增面试分享功能"

# Bug 修复
git commit -m "fix: 修复评分计算错误"

# 文档更新
git commit -m "docs: 更新开发文档"

# 代码重构
git commit -m "refactor: 重构 AI 客户端工厂"

# 测试相关
git commit -m "test: 添加评分服务单元测试"
```

### 2.11 第十步：部署上线

#### 2.11.1 生产环境构建

```bash
# 编译打包
mvn clean package -DskipTests

# 生成的 JAR 包
# ai-web/target/ai-web-1.0.0-SNAPSHOT.jar
```

#### 2.11.2 生产环境启动

```bash
java -jar ai-web/target/ai-web-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --DB_HOST=your-db-host \
  --DB_PASSWORD=your-db-password \
  --REDIS_PASSWORD=your-redis-password \
  --JWT_SECRET=your-256-bit-secret \
  --DEEPSEEK_API_KEY=your-key \
  --OPENAI_API_KEY=your-key
```

#### 2.11.3 生产环境注意事项

| 事项 | 说明 |
|------|------|
| JWT_SECRET | 必须使用高强度随机密钥（至少 256 位） |
| 数据库密码 | 必须设置环境变量，不可使用默认密码 |
| API 密钥 | 通过环境变量注入，不要写入代码或镜像 |
| Druid 面板 | 生产环境建议关闭或限制访问 |
| 日志级别 | 生产环境使用 info/warn，关闭 debug |
| 文件存储 | 建议迁移至 OSS/对象存储 |

---

## 三、环境搭建

### 3.1 前置条件

在开始之前，请确保已安装以下软件：

| 软件 | 最低版本 | 安装说明 |
|------|---------|---------|
| JDK | 17 | 推荐使用 Adoptium/Temurin 发行版 |
| Maven | 3.8 | 用于项目构建和依赖管理 |
| MySQL | 8.0 | 数据库服务 |
| Redis | 6.x | 缓存服务 |
| IDE | IntelliJ IDEA 2022+ | 推荐使用，需安装 Lombok 插件 |
| Git | 2.x | 版本控制 |

### 3.2 数据库初始化

**第一步：创建数据库并导入表结构**

```bash
# 登录 MySQL
mysql -u root -p

# 执行建表脚本（包含所有表结构和初始数据）
source /path/to/AI_InterviewReview/sql/schema.sql
```

建表脚本会自动完成以下操作：
- 创建 `ai_interview` 数据库
- 创建 12 张业务表（user、interview、interview_question、ai_config 等）
- 初始化角色数据（admin、user）
- 初始化权限数据（8 项权限）
- 创建默认管理员账号（用户名: admin，密码: admin123）
- 创建默认 AI 配置（DeepSeek 文本分析 + Whisper 语音转文字）

**第二步：确认 Redis 服务运行**

```bash
# 启动 Redis（Linux/Mac）
redis-server

# 启动 Redis（Windows）
# 双击 redis-server.exe 或通过服务管理器启动

# 验证连接
redis-cli ping
# 期望输出: PONG
```

### 3.3 环境变量配置

系统的所有敏感信息（数据库密码、API 密钥等）均通过环境变量管理，不会硬编码在配置文件中。

**需要配置的环境变量：**

| 环境变量 | 说明 | 默认值 | 必填 |
|---------|------|--------|------|
| `DB_HOST` | MySQL 主机地址 | localhost | 否 |
| `DB_PORT` | MySQL 端口 | 3306 | 否 |
| `DB_NAME` | 数据库名称 | ai_interview | 否 |
| `DB_USERNAME` | 数据库用户名 | root | 否 |
| `DB_PASSWORD` | 数据库密码 | 220718 | 否 |
| `REDIS_HOST` | Redis 主机地址 | localhost | 否 |
| `REDIS_PORT` | Redis 端口 | 6379 | 否 |
| `REDIS_PASSWORD` | Redis 密码 | root | 否 |
| `JWT_SECRET` | JWT 签名密钥 | 见 yml 文件 | 生产环境必填 |
| `SPRING_PROFILES_ACTIVE` | 激活的配置文件 | dev | 否 |
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥 | - | 是 |
| `OPENAI_API_KEY` | OpenAI API 密钥 | - | 是 |
| `WHISPER_API_KEY` | Whisper API 密钥 | - | 否（默认复用 OPENAI_API_KEY） |

**配置方式（以 Linux/Mac 为例）：**

```bash
# 在终端中临时设置（当前会话有效）
export DB_PASSWORD="your_password"
export DEEPSEEK_API_KEY="sk-xxx"

# 写入 shell 配置文件使其永久生效
echo 'export DB_PASSWORD="your_password"' >> ~/.bashrc
echo 'export DEEPSEEK_API_KEY="sk-xxx"' >> ~/.bashrc
source ~/.bashrc
```

**配置方式（Windows）：**

```
# 通过系统设置 -> 高级系统设置 -> 环境变量 添加
# 或在 PowerShell 中：
[System.Environment]::SetEnvironmentVariable("DB_PASSWORD", "your_password", "User")
[System.Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "sk-xxx", "User")
```

### 3.4 AI API 密钥获取

系统使用以下 AI 服务，需要分别获取 API 密钥：

| 服务 | 用途 | 获取方式 |
|------|------|---------|
| DeepSeek | 文本分析与评分 | https://platform.deepseek.com/ |
| OpenAI Whisper | 语音转文字 | https://platform.openai.com/ |

获取密钥后，将其设置为对应的环境变量即可。yml 配置文件中的 `${DEEPSEEK_API_KEY:your-deepseek-api-key}` 语法表示：优先读取环境变量，若未设置则使用冒号后的默认值。

### 3.5 项目构建与启动

```bash
# 进入项目根目录
cd AI_InterviewReview

# 编译项目（首次构建会下载依赖，需等待数分钟）
mvn clean compile

# 运行测试
mvn test

# 打包
mvn clean package -DskipTests

# 启动应用（从 ai-web 模块启动）
cd ai-web
mvn spring-boot:run

# 或打包后直接运行 JAR
java -jar ai-web/target/ai-web-1.0.0-SNAPSHOT.jar
```

应用启动后，访问以下地址验证：

| 地址 | 说明 |
|------|------|
| http://localhost:8080 | 应用首页 |
| http://localhost:8080/swagger-ui.html | API 文档（Knife4j） |
| http://localhost:8080/druid/ | Druid 监控面板 |

---

## 四、项目结构

### 4.1 模块划分

项目采用 Maven 多模块架构，共 6 个子模块：

```
ai-interview-backend/               # 父 POM（项目根目录）
│
├── ai-common/                      # 公共模块：常量、异常、工具类、统一响应
│
├── ai-auth/                        # 认证模块：用户注册、登录、JWT、权限控制
│
├── ai-interview/                   # 面试模块：面试上传、复盘、笔记、收藏、分享、面经
│
├── ai-ai/                          # AI 模块：模型工厂、语音转文字、文本评分
│
├── ai-admin/                       # 管理模块：用户管理、系统统计、内容管理
│
└── ai-web/                         # 启动模块：聚合所有模块，Spring Boot 入口
```

### 4.2 模块依赖关系

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

**关键规则：** ai-common 被所有模块依赖，但自身不依赖任何业务模块。业务模块之间通过接口调用，不直接引用实现类。

### 4.3 各模块职责

| 模块 | 包路径 | 职责 |
|------|--------|------|
| ai-common | com.interview.common | 常量定义、全局异常处理、统一响应封装、工具类（JWT、Redis、文件操作）、公共配置 |
| ai-auth | com.interview.auth | 用户注册/登录、JWT 认证过滤器、Spring Security 配置、角色权限校验 |
| ai-interview | com.interview.interview | 面试上传与管理、异步 AI 处理调度、笔记/收藏/分享/面经/评论业务 |
| ai-ai | com.interview.ai | AI 模型客户端工厂、语音转文字服务、文本分析评分服务、模型配置管理 |
| ai-admin | com.interview.admin | 管理员后台接口（用户管理、系统统计、面经/评论管理） |
| ai-web | com.interview | Spring Boot 启动类、application.yml 配置文件 |

### 4.4 包结构规范

每个业务模块内部遵循统一的五层架构：

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

**分层调用规则：**

| 允许的调用方向 | 禁止的调用方向 |
|--------------|--------------|
| Controller -> Service | Controller -> Mapper |
| Service -> Mapper | Mapper -> Service |
| Service -> Service | Entity -> 任何层 |

---

## 五、配置文件说明

### 5.1 配置文件结构

配置文件位于 `ai-web/src/main/resources/` 目录下：

```
src/main/resources/
  ├── application.yml              # 主配置（所有环境共享）
  ├── application-dev.yml          # 开发环境配置
  ├── application-test.yml         # 测试环境配置
  └── application-prod.yml         # 生产环境配置
```

### 5.2 主配置文件 (application.yml)

以下是各配置段的说明：

**服务器配置：**
```yaml
server:
  port: 8080                     # 应用端口
```

**Spring 配置：**
```yaml
spring:
  application:
    name: ai-interview           # 应用名称
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}   # 激活的环境（dev/test/prod）
  datasource:                    # 数据库连接
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:ai_interview}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:220718}
  data:
    redis:                       # Redis 缓存
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:root}
  servlet:
    multipart:
      max-file-size: 200MB       # 单文件上传大小上限
      max-request-size: 200MB    # 单次请求大小上限
```

**JWT 配置：**
```yaml
jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-change-in-production}  # 签名密钥
  expiration: 604800            # Token 有效期（秒），604800 = 7 天
```

**文件存储配置：**
```yaml
interview:
  file:
    upload-dir: ./uploads       # 文件存储目录（相对路径）
    base-url: /files            # 文件访问 URL 前缀
```

**AI 模型配置：**
```yaml
ai:
  providers:
    deepseek:                   # DeepSeek 文本分析
      api-key: ${DEEPSEEK_API_KEY:your-deepseek-api-key}
      api-endpoint: https://api.deepseek.com/v1
      model-name: deepseek-chat
    openai:                     # OpenAI GPT-4o
      api-key: ${OPENAI_API_KEY:your-openai-api-key}
      api-endpoint: https://api.openai.com/v1
      model-name: gpt-4o
    whisper:                    # OpenAI Whisper 语音转文字
      api-key: ${WHISPER_API_KEY:${OPENAI_API_KEY:your-openai-api-key}}
      api-endpoint: https://api.openai.com/v1
      model-name: whisper-1
```

**异步线程池配置：**
```yaml
async:
  core-pool-size: 4             # 核心线程数
  max-pool-size: 8              # 最大线程数
  queue-capacity: 100           # 等待队列容量
  thread-name-prefix: interview-async-  # 线程名前缀
```

### 5.3 多环境配置

| 文件 | 适用场景 | 关键差异 |
|------|---------|---------|
| application.yml | 所有环境共享 | 完整配置，所有值均有默认值 |
| application-dev.yml | 本地开发 | 开启 SQL 日志输出 |
| application-test.yml | 测试环境 | 使用独立测试数据库 |
| application-prod.yml | 生产环境 | 关闭 SQL 日志，关闭 Druid 面板 |

---

## 六、数据库设计

### 6.1 数据表总览

系统共 12 张数据表，按业务域划分：

**用户与权限（4 张表）：**

| 表名 | 说明 | 预估数据量 |
|------|------|-----------|
| user | 用户表 | 万级 |
| role | 角色表 | 固定 2 条 |
| user_role | 用户角色关联表 | 万级 |
| role_permission | 角色权限关联表 | 固定 8 条 |

**面试业务（5 张表）：**

| 表名 | 说明 | 预估数据量 |
|------|------|-----------|
| interview | 面试记录表 | 十万级 |
| interview_question | 面试问题表 | 百万级 |
| interview_note | 面试笔记表 | 十万级 |
| interview_favorite | 问题收藏表 | 万级 |
| interview_share | 面试分享表 | 万级 |

**面经与评论（2 张表）：**

| 表名 | 说明 | 预估数据量 |
|------|------|-----------|
| interview_share | 面试分享表（含面经公开标识） | 万级 |
| interview_comment | 面经评论表 | 十万级 |

**AI 配置（1 张表）：**

| 表名 | 说明 | 预估数据量 |
|------|------|-----------|
| ai_config | AI 模型配置表 | 十级 |

### 6.2 核心表关系

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

ai_config (AI模型配置 - 独立表，无外键关联)
```

### 6.3 关键字段说明

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
| 1 | 语音转文字 | 正在调用 Whisper API |
| 2 | 问题边界识别 | 正在调用 LLM 识别问题 |
| 3 | 逐题评分 | 正在逐题生成评分和建议 |
| 4 | 整体评分 | 正在生成综合评估 |

**ai_config.config_type（AI 配置用途）：**

| 值 | 含义 | 对应 AI 服务 |
|----|------|-------------|
| 1 | 语音转文字 (ASR) | Whisper / 阿里云 ASR |
| 2 | 文本分析评分 (LLM) | DeepSeek / GPT |

---

## 七、模块详细说明

### 7.1 认证模块 (ai-auth)

#### 6.1.1 认证流程

**注册流程：**

```
1. 用户发送邮箱 -> POST /auth/send-code
2. 系统生成 6 位验证码，存入 Redis（有效期 5 分钟）
3. 用户填写邮箱+验证码+用户名+密码 -> POST /auth/register
4. 系统校验验证码、用户名唯一性
5. 密码 BCrypt 加密后存入数据库
6. 注册成功，自动返回 JWT Token
```

**登录流程：**

```
1. 用户输入账号（邮箱或用户名）+ 密码 -> POST /auth/login
2. 系统查询用户记录，校验密码
3. 检查账户是否被禁用
4. 生成 JWT Token，返回给前端
5. 前端将 Token 存入 localStorage
6. 后续请求携带 Header: Authorization: Bearer {token}
```

**Token 校验流程（每次请求）：**

```
1. JwtAuthenticationFilter 拦截请求
2. 从 Header 中提取 Token
3. 解析 Token，验证签名和有效期
4. 检查 Token 是否在 Redis 黑名单中（退出登录后加入）
5. 从 Token 中提取用户 ID 和角色
6. 构建 SecurityContext，设置认证信息
7. 根据角色分配权限（role=1 -> ROLE_ADMIN）
```

#### 6.1.2 安全配置

- 密码使用 BCrypt 加密（salt rounds = 10）
- JWT Token 有效期 7 天
- 退出登录后 Token 加入 Redis 黑名单
- 邮箱验证码有效期 5 分钟，同一邮箱 60 秒内限发一次

#### 6.1.3 权限控制

| 角色 | 标识 | 说明 |
|------|------|------|
| 管理员 | ROLE_ADMIN | 拥有系统所有权限（user.role = 1） |
| 普通用户 | ROLE_USER | 仅访问自身数据（user.role = 0） |

权限通过 `@PreAuthorize("hasRole('ADMIN')")` 注解在接口层校验。

#### 6.1.4 接口列表

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | /auth/send-code | 发送邮箱验证码 | 否 |
| POST | /auth/register | 用户注册 | 否 |
| POST | /auth/login | 用户登录 | 否 |
| POST | /auth/logout | 退出登录 | 是 |
| GET | /auth/profile | 获取用户信息 | 是 |
| PUT | /auth/profile | 更新用户信息 | 是 |
| PUT | /auth/password | 修改密码 | 是 |

---

### 7.2 面试模块 (ai-interview)

#### 6.2.1 面试上传流程

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

#### 6.2.2 异步处理流程

异步处理是系统的核心流程，由 `InterviewAsyncServiceImpl` 执行：

```
InterviewAsyncServiceImpl.processInterview(interviewId)
    │
    ├── Step 1: 语音转文字 (processingStep=1)
    │   └── TranscriptionService.transcribe(audioPath, configId)
    │       ├── 读取音频文件
    │       ├── 通过 AiClientFactory 获取 ASR 客户端
    │       ├── 调用 Whisper API 进行语音识别
    │       │   └── DashScopeAsrClient: 带重试机制
    │       │       ├── submitTranscriptionTaskWithRetry() - 网络异常重试(3次)
    │       │       └── pollTaskResultWithRetry() - 轮询结果(最多150次)
    │       └── 返回转写文本
    │
    ├── Step 1.5: 公司分级
    │   └── CompanyClassifier.classify(companyName, industry, jdText)
    │       ├── 根据公司名称、行业、JD 自动分级
    │       └── 返回 CompanyTier (TIER_1 ~ TIER_5)
    │
    ├── Step 2: 问题识别 + 评分 (processingStep=2)
    │   └── ScoringService.analyzeAndScoreBatch(transcript, jd, resume, configId, tier)
    │       ├── splitIntoSegments() - 拆分面试文本为独立问题段落
    │       ├── 分批评估 (每批 8 个问题)
    │       │   └── StructuredOutputInvoker.invoke() - 带重试和JSON修复
    │       ├── mergeBatchResults() - 合并各批结果
    │       └── 二次汇总 - 生成最终评分报告
    │
    ├── Step 3: 解析结果 (processingStep=3)
    │   ├── 解析 JSON，保存到 interview 表和 interview_question 表
    │   └── ScoringWeights.calculateOverallScore() - 加权计算总分
    │       ├── dimensionContent × 0.25
    │       ├── dimensionLogic × 0.20
    │       ├── dimensionExpression × 0.15
    │       ├── dimensionProfessional × 0.25
    │       └── dimensionCommunication × 0.15
    │
    └── Step 4: 完成 (status=COMPLETED)
```

**失败处理：** 如果任何步骤失败，系统将 interview.status 设为 2（失败），error_message 记录失败原因。用户可通过 `POST /interviews/{id}/retry` 重新处理。

#### 6.2.3 文件存储

上传的文件保存在本地磁盘，路径由 `interview.file.upload-dir` 配置项控制：

```
./uploads/                          # 上传根目录
  ├── {uuid}.wav                    # 音频文件（UUID 重命名，防止路径遍历）
  └── {uuid}.pdf                    # 简历文件
```

文件访问通过 `FileController` 提供：
- URL: `GET /files/{filename}`
- 内置路径遍历防护

#### 6.2.4 接口列表

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | /interviews/upload | 上传面试 | 是 |
| GET | /interviews | 面试列表（分页+筛选） | 是 |
| GET | /interviews/{id} | 面试详情 | 是 |
| DELETE | /interviews/{id} | 删除面试 | 是 |
| GET | /interviews/{id}/status | 处理进度 | 是 |
| POST | /interviews/{id}/retry | 重新处理 | 是 |

---

### 7.3 笔记模块

#### 6.3.1 设计说明

笔记分为两种类型：

| 类型 | note_type | 说明 |
|------|-----------|------|
| 面试笔记 | INTERVIEW | 对整场面试的复盘笔记，每场面试只能有一条（覆盖式更新） |
| 问题笔记 | QUESTION | 对单个面试问题的笔记，需关联 question_id |

笔记内容支持富文本格式（标题、加粗、列表等）。

#### 6.3.2 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /interviews/{id}/note | 获取面试笔记 |
| PUT | /interviews/{id}/note | 保存面试笔记 |
| GET | /interviews/{id}/questions/{qid}/note | 获取问题笔记 |
| PUT | /interviews/{id}/questions/{qid}/note | 保存问题笔记 |

---

### 7.4 收藏模块

用户可以收藏面试中的问题，便于反复学习。

#### 6.4.1 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /favorites | 收藏问题 |
| DELETE | /favorites/{id} | 取消收藏 |
| GET | /favorites | 收藏列表（分页） |

---

### 7.5 分享与面经模块

#### 6.5.1 分享机制

用户可以将面试复盘结果生成分享链接，支持以下选项：

| 选项 | 说明 |
|------|------|
| 有效期 | 24 小时 / 7 天 / 30 天 / 永久 |
| 是否公开 | 选择"公开"则发布到面经广场 |

分享令牌（share_token）为 64 位随机字符串，保证唯一性。

#### 6.5.2 面经广场

- 无需登录即可浏览
- 支持按公司、职位、行业筛选
- 支持按评分、发布时间排序
- 每条面经显示：标题、公司、职位、评分、浏览量

#### 6.5.3 面经评论

- 登录用户可发表评论（纯文本，最长 500 字）
- 评论者本人、面经发布者、管理员均可删除评论
- 评论列表按时间倒序排列

#### 6.5.4 接口列表

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

---

### 7.6 AI 模块 (ai-ai)

#### 6.6.1 架构设计

AI 模块采用**注册式工厂 + 策略模式**，支持运行时切换 AI 模型。

```
AiProperties (yml 配置)
    │
    ▼
AiClientFactory (@PostConstruct 初始化)
    │ 注册到 ConcurrentHashMap<String, AiModelClient>
    │
    ├── "deepseek" -> OpenAiCompatibleClient (DeepSeek)
    ├── "openai"   -> OpenAiCompatibleClient (GPT-4o)
    ├── "whisper"  -> OpenAiCompatibleClient (Whisper)
    ├── "xiaomi"   -> OpenAiCompatibleClient (小米 MiMo)
    └── "funasr"   -> DashScopeAsrClient (阿里云 ASR)
    │
    ▼
TranscriptionService / ScoringService
    │ 调用 resolveClient(configId) 获取具体客户端
    │
    ▼
AiModelClient.call() / transcribe()
```

#### 6.6.2 核心组件

| 组件 | 文件路径 | 说明 |
|------|---------|------|
| AiClientFactory | `ai-ai/.../factory/AiClientFactory.java` | AI 客户端工厂，启动时注册所有 provider |
| OpenAiCompatibleClient | `ai-ai/.../client/OpenAiCompatibleClient.java` | OpenAI 兼容接口客户端（DeepSeek/GPT/小米） |
| DashScopeAsrClient | `ai-ai/.../client/DashScopeAsrClient.java` | 阿里云 ASR 客户端（带重试机制） |
| StructuredOutputInvoker | `ai-ai/.../util/StructuredOutputInvoker.java` | LLM 调用工具（带重试+JSON修复） |
| ScoringWeights | `ai-common/.../constant/ScoringWeights.java` | 评分维度权重常量 |
| StructuredOutputProperties | `ai-ai/.../config/StructuredOutputProperties.java` | 重试配置属性 |

#### 6.6.3 工厂注册流程

应用启动时，`AiClientFactory.init()` 执行以下操作：

1. 读取 `ai.providers` 配置（来自 application.yml）
2. 遍历每个 provider，检查 API Key 是否有效（跳过以 `your-` 开头的占位值）
3. 为有效 provider 创建对应的 Client 实例
4. 将实例注册到 `ConcurrentHashMap<String, AiModelClient>` 中

#### 6.6.4 客户端解析策略

当服务需要调用 AI 时，通过 `resolveClient(configId)` 解析具体客户端：

```
传入 configId?
  ├── 有（configId > 0）
  │     └── 查询 ai_config 表 -> 获取 provider 字段 -> 从 registry 获取客户端
  │
  └── 无（configId = null 或 0）
        └── getDefaultClient(configType)
              ├── 查询 ai_config 表: configType + isDefault=1 + status=1
              │     └── 找到 -> 从 registry 获取对应客户端
              └── 未找到 -> fallback 到 yml 中的优先级列表
                    ├── configType=1 (ASR): openai -> whisper -> funasr
                    └── configType=2 (LLM): deepseek -> openai -> xiaomi
```

#### 6.6.5 分批评估机制

为解决长文本导致 LLM 丢失格式或遗漏问题，系统采用**分批评估 + 二次汇总**策略：

```
面试转写文本
    │
    ▼
splitIntoSegments() - 拆分为独立问题段落
    │ 检测模式: "问：", "Q：", "面试官：", 数字序号
    │ 回退策略: 按 2000 字符强制拆分
    │
    ▼
分批评估 (每批 8 个问题)
    │
    ├── 第 1 批: questions[0-7]
    │   └── StructuredOutputInvoker.invoke()
    │       ├── 调用 LLM 评估
    │       ├── 清理响应 (去除 markdown 代码块)
    │       ├── JSON 修复 (尾部逗号、缺失括号)
    │       └── 失败重试 (最多 2 次)
    │
    ├── 第 2 批: questions[8-15]
    │   └── ...
    │
    └── 第 N 批: questions[最后一批]
        └── ...
    │
    ▼
mergeBatchResults() - 合并所有批次结果
    │
    ▼
二次汇总 - 生成最终评分报告
    │
    ▼
返回结构化 JSON
```

#### 6.6.6 StructuredOutputInvoker 机制

`StructuredOutputInvoker` 是 LLM 调用的健壮性保障组件：

```java
// 调用流程
public JsonNode invoke(AiModelClient client, String prompt, String systemPrompt, Long configId) {
    for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
        // 1. 调用 LLM
        String response = client.call(prompt, systemPrompt, configId);
        
        // 2. 清理响应 (去除 markdown 代码块包裹)
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

**重试配置：**

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `ai.structured-output.max-retry-attempts` | 2 | 最大重试次数 |
| `ai.structured-output.retry-with-strict-instruction` | true | 重试时追加严格 JSON 指令 |
| `ai.structured-output.enable-json-repair` | true | 启用本地 JSON 修复 |
| `ai.structured-output.enable-response-cleanup` | true | 启用响应清理 |

#### 6.6.7 评分维度权重

系统采用加权公式计算总分，确保评分准确性：

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

**注意：** `overallScore` 由后端使用加权公式计算，不使用 LLM 返回的值，确保评分一致性。

#### 6.6.8 ASR 错误处理机制

`DashScopeAsrClient` 实现了完整的错误处理机制：

```
提交转写任务
    │
    ▼
submitTranscriptionTaskWithRetry()
    │ 最多重试 3 次
    │ 指数退避: 1s, 2s, 3s
    │
    ▼
pollTaskResultWithRetry()
    │ 最多轮询 150 次 (间隔 2s, 共 5 分钟)
    │ 连续网络错误 3 次后放弃
    │
    ▼
返回转写结果
```

**配置参数：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `maxPollAttempts` | 150 | 最大轮询次数 |
| `pollIntervalMs` | 2000 | 轮询间隔（毫秒） |
| `MAX_NETWORK_RETRIES` | 3 | 网络异常最大重试次数 |

#### 6.6.9 如何添加新的 AI 模型

**第一步：在 application.yml 中注册 provider**

```yaml
ai:
  providers:
    # ... 已有配置 ...
    qwen:                          # 新增 provider 名称（自定义）
      api-key: ${QWEN_API_KEY:xxx}
      api-endpoint: https://dashscope.aliyuncs.com/compatible-mode/v1
      model-name: qwen-plus
```

**第二步：在 ai_config 表中插入配置记录**

```sql
INSERT INTO ai_config (config_name, provider, model_name, api_endpoint, config_type, is_default)
VALUES ('通义千问文本分析', 'qwen', 'qwen-plus', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 2, 0);
```

**第三步：重启应用**

`AiClientFactory.init()` 会在启动时自动读取新配置并注册客户端。

**第四步：切换为默认模型（可选）**

```bash
# 通过管理员接口将新模型设为默认
PUT /ai-configs/{新配置ID}/default
```

#### 6.6.10 AI 接口列表

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /ai-configs | AI 配置列表 | ADMIN |
| GET | /ai-configs/{id} | 配置详情 | ADMIN |
| POST | /ai-configs | 新增配置 | ADMIN |
| PUT | /ai-configs/{id} | 更新配置 | ADMIN |
| DELETE | /ai-configs/{id} | 删除配置 | ADMIN |
| PUT | /ai-configs/{id}/default | 设为默认 | ADMIN |

---

### 7.7 管理模块 (ai-admin)

#### 6.7.1 权限模型

采用 RBAC（基于角色的访问控制）模型：

```
User（用户）
  │ N:1
  └── Role（角色）
        │ 1:N
        └── Permission（权限）
```

**预置角色和权限：**

| 角色 | 权限标识 | 说明 |
|------|---------|------|
| admin | user:list | 查看用户列表 |
| admin | user:manage | 管理用户账号 |
| admin | ai_config:list | 查看 AI 配置 |
| admin | ai_config:manage | 管理 AI 配置 |
| admin | interview:view_all | 查看所有面试数据 |
| admin | experience:manage | 管理面经 |
| admin | comment:manage | 管理评论 |
| admin | system:stats | 查看系统统计 |

#### 6.7.2 接口列表

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /admin/users | 用户列表 | user:list |
| PUT | /admin/users/{id}/status | 禁用/启用用户 | user:manage |
| PUT | /admin/users/{id}/role | 修改用户角色 | user:manage |
| GET | /admin/stats/overview | 系统统计 | system:stats |
| GET | /admin/interviews | 所有面试数据 | interview:view_all |
| GET | /admin/experiences | 所有面经 | experience:manage |
| PUT | /admin/experiences/{token}/status | 下架/恢复面经 | experience:manage |
| GET | /admin/comments | 所有评论 | comment:manage |
| DELETE | /admin/comments/{id} | 删除评论 | comment:manage |

---

## 八、统一响应格式

### 8.1 标准响应

```json
{
    "code": 200,
    "message": "success",
    "data": {}
}
```

### 8.2 分页响应

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

### 8.3 错误码一览

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
| 3001-3002 | AI 模块错误（配置不存在、模型调用失败） |
| 4001-4004 | 收藏/分享模块错误 |
| 5001-5006 | 管理员模块错误 |

---

## 九、部署指南

### 9.1 构建

```bash
# 编译并打包
mvn clean package -DskipTests

# 生成的 JAR 包位置
# ai-web/target/ai-web-1.0.0-SNAPSHOT.jar
```

### 9.2 启动

```bash
# 方式一：直接运行 JAR
java -jar ai-web/target/ai-web-1.0.0-SNAPSHOT.jar

# 方式二：指定环境变量运行
java -jar ai-web/target/ai-web-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --DB_HOST=your-db-host \
  --DB_PASSWORD=your-db-password \
  --REDIS_PASSWORD=your-redis-password \
  --JWT_SECRET=your-256-bit-secret \
  --DEEPSEEK_API_KEY=your-key \
  --OPENAI_API_KEY=your-key
```

### 9.3 生产环境注意事项

| 事项 | 说明 |
|------|------|
| JWT_SECRET | 必须使用高强度随机密钥（至少 256 位），不可使用默认值 |
| 数据库密码 | 必须设置环境变量，不可使用默认密码 |
| API 密钥 | 通过环境变量注入，不要写入代码或镜像 |
| Druid 面板 | 生产环境建议关闭或限制访问 |
| 日志级别 | 生产环境使用 info/warn，关闭 debug |
| 文件存储 | 建议迁移至 OSS/对象存储 |

### 9.4 健康检查

```bash
# 验证应用是否正常运行
curl http://localhost:8080/auth/profile
# 未认证时应返回 401

# 查看 API 文档
curl http://localhost:8080/v3/api-docs
```

---

## 十、常见问题

### Q1: 启动时报数据库连接失败

检查项：
1. MySQL 服务是否启动
2. `DB_HOST`、`DB_PORT`、`DB_USERNAME`、`DB_PASSWORD` 环境变量是否正确
3. 数据库 `ai_interview` 是否已创建（执行 `sql/schema.sql`）

### Q2: AI 调用失败（3002 错误）

检查项：
1. `DEEPSEEK_API_KEY` 和 `OPENAI_API_KEY` 环境变量是否已设置
2. API Key 是否有效（非占位值 `your-xxx-api-key`）
3. 网络是否能访问 AI 服务端点（如 api.deepseek.com）

### Q3: 音频上传后一直处于"处理中"

检查项：
1. 查看应用日志中的错误信息
2. 确认 Whisper API 密钥有效
3. 检查音频文件格式是否支持（MP3/WAV）
4. 可通过 `POST /interviews/{id}/retry` 重新触发处理

### Q4: Swagger 文档无法访问

访问地址为 `http://localhost:8080/swagger-ui.html`。如果无法访问：
1. 确认 Knife4j 配置已启用（`knife4j.enable: true`）
2. 确认 Spring Security 未拦截该路径（已配置 permitAll）

### Q5: 如何切换默认 AI 模型

操作步骤：
1. 确保新模型的 provider 已在 `application.yml` 的 `ai.providers` 中注册
2. 在 `ai_config` 表中插入对应配置记录
3. 通过管理员接口 `PUT /ai-configs/{id}/default` 设为默认
4. 无需重启应用

### Q6: LLM 返回格式错误（JSON 解析失败）

系统已内置重试和修复机制：
1. `StructuredOutputInvoker` 会自动重试最多 2 次
2. 自动清理 markdown 代码块包裹
3. 自动修复常见 JSON 错误（尾部逗号、缺失括号）
4. 如果持续失败，检查 LLM 模型是否支持结构化输出

### Q7: 评分结果不准确

检查项：
1. 确认 `ScoringWeights` 权重配置正确（总和应为 1.0）
2. 检查 `InterviewAsyncServiceImpl.parseAndSaveScoreResult()` 是否使用加权公式
3. 确认各维度分数范围在 0-100 之间

### Q8: ASR 调用超时

检查项：
1. 确认音频文件大小不超过 200MB
2. 检查网络连接是否稳定
3. 查看日志中的轮询次数和错误信息
4. 可调整 `DashScopeAsrClient` 的 `maxPollAttempts` 和 `pollIntervalMs` 参数

---

## 十一、近期改进记录

### 11.1 改进 1：分批评估机制

**问题：** 长面试文本导致 LLM 丢失格式或遗漏问题

**解决方案：**
- 新增 `analyzeAndScoreBatch()` 方法
- 实现 `splitIntoSegments()` 拆分文本为独立问题段落
- 每批评估 8 个问题，合并后二次汇总
- 使用 `StructuredOutputInvoker` 保证调用健壮性

**文件变更：**
- `ai-ai/.../service/ScoringService.java` - 新增接口方法
- `ai-ai/.../service/impl/ScoringServiceImpl.java` - 实现分批评估

### 11.2 改进 2：LLM 调用重试与 JSON 修复

**问题：** LLM 返回格式错误时直接抛异常，整个处理失败

**解决方案：**
- 新增 `StructuredOutputInvoker` 组件
- 支持最多 2 次重试
- 自动清理 markdown 代码块
- 自动修复常见 JSON 错误

**文件变更：**
- `ai-ai/.../util/StructuredOutputInvoker.java` - 新增
- `ai-ai/.../config/StructuredOutputProperties.java` - 新增

### 11.3 改进 3：评分维度加权

**问题：** overallScore 由 LLM 直接返回，可能不准确

**解决方案：**
- 新增 `ScoringWeights` 常量类
- 使用加权公式计算总分
- 覆盖 LLM 返回的 overallScore

**文件变更：**
- `ai-common/.../constant/ScoringWeights.java` - 新增
- `ai-interview/.../service/impl/InterviewAsyncServiceImpl.java` - 使用加权公式

### 11.4 改进 4：ASR 错误处理增强

**问题：** ASR 调用失败直接抛异常，无重试机制

**解决方案：**
- 实现网络异常重试（最多 3 次，指数退避）
- 实现轮询结果重试（连续网络错误 3 次后放弃）
- 支持可配置的超时参数

**文件变更：**
- `ai-ai/.../client/DashScopeAsrClient.java` - 重写错误处理

---

## 十二、项目文件索引

### 12.1 关键配置文件

| 文件路径 | 说明 |
|---------|------|
| ai-web/src/main/resources/application.yml | 主配置文件 |
| ai-web/src/main/resources/application-dev.yml | 开发环境配置 |
| ai-web/src/main/resources/application-prod.yml | 生产环境配置 |
| sql/schema.sql | 数据库建表脚本 |
| pom.xml | 父 POM（依赖版本管理） |

### 12.2 核心源码文件

| 文件路径 | 说明 |
|---------|------|
| ai-web/src/main/java/com/interview/AiInterviewApplication.java | Spring Boot 启动类 |
| ai-common/src/main/java/com/interview/common/result/Result.java | 统一响应封装 |
| ai-common/src/main/java/com/interview/common/exception/GlobalExceptionHandler.java | 全局异常处理 |
| ai-common/src/main/java/com/interview/common/constant/ErrorCode.java | 错误码定义 |
| ai-common/src/main/java/com/interview/common/constant/ScoringWeights.java | 评分维度权重常量 |
| ai-common/src/main/java/com/interview/common/utils/JwtUtil.java | JWT 工具类 |
| ai-common/src/main/java/com/interview/common/utils/FileUtil.java | 文件操作工具类 |
| ai-common/src/main/java/com/interview/common/utils/SecurityUtils.java | 安全上下文工具类 |
| ai-auth/src/main/java/com/interview/auth/config/SecurityConfig.java | Spring Security 配置 |
| ai-auth/src/main/java/com/interview/auth/config/JwtAuthenticationFilter.java | JWT 认证过滤器 |
| ai-auth/src/main/java/com/interview/auth/controller/AuthController.java | 认证接口 |
| ai-auth/src/main/java/com/interview/auth/service/impl/AuthServiceImpl.java | 认证业务实现 |
| ai-interview/src/main/java/com/interview/interview/controller/InterviewController.java | 面试接口 |
| ai-interview/src/main/java/com/interview/interview/service/impl/InterviewServiceImpl.java | 面试业务实现 |
| ai-interview/src/main/java/com/interview/interview/service/impl/InterviewAsyncServiceImpl.java | 异步处理核心 |
| ai-interview/src/main/java/com/interview/interview/controller/FileController.java | 文件访问接口 |
| ai-ai/src/main/java/com/interview/ai/factory/AiClientFactory.java | AI 客户端工厂 |
| ai-ai/src/main/java/com/interview/ai/client/OpenAiCompatibleClient.java | AI 客户端实现 |
| ai-ai/src/main/java/com/interview/ai/client/DashScopeAsrClient.java | 阿里云 ASR 客户端（带重试） |
| ai-ai/src/main/java/com/interview/ai/service/impl/TranscriptionServiceImpl.java | 语音转文字服务 |
| ai-ai/src/main/java/com/interview/ai/service/impl/ScoringServiceImpl.java | 文本评分服务（分批评估） |
| ai-ai/src/main/java/com/interview/ai/util/StructuredOutputInvoker.java | LLM 调用工具（重试+JSON修复） |
| ai-ai/src/main/java/com/interview/ai/config/StructuredOutputProperties.java | 重试配置属性 |
| ai-ai/src/main/java/com/interview/ai/controller/AiConfigController.java | AI 配置管理接口 |
| ai-ai/src/main/java/com/interview/ai/config/AiProperties.java | AI 配置属性类 |
| ai-admin/src/main/java/com/interview/admin/controller/AdminUserController.java | 用户管理接口 |
| ai-admin/src/main/java/com/interview/admin/controller/AdminStatsController.java | 系统统计接口 |
