# AI 面试复盘系统 - 数据库设计文档

> 版本: v1.3 MVP  
> 日期: 2026-06-01  
> 状态: 已更新（新增面经功能）

---

## 一、需求分析

### 1.1 数据需求概述

系统需要存储以下核心业务数据：

| 数据类别 | 说明 | 数据量预估 |
|---------|------|-----------|
| 用户数据 | 用户账号、个人信息、登录信息 | 万级 |
| 面试记录 | 面试元信息、公司/职位信息、转写文本、评分结果 | 十万级 |
| 面试问题 | 每个问题的回答和评分 | 百万级 |
| 用户笔记 | 面试笔记、问题笔记 | 十万级 |
| 问题收藏 | 用户收藏的面试问题 | 万级 |
| 面试分享 | 面试分享链接记录 | 万级 |
| 面经评论 | 面经下的用户评论 | 十万级 |
| AI 配置 | 模型提供商、API 密钥等 | 十级 |

### 1.2 数据流分析

```
用户上传音频 + JD + 简历
        │
        ▼
  ┌─────────────┐     ┌─────────────┐
  │   user 表    │────▶│ interview 表 │
  └─────────────┘     └──────┬──────┘
                             │
                     ┌───────▼────────┐
                     │interview_question│
                     └────────────────┘

  ┌─────────────┐     ┌─────────────────┐
  │interview_note│◀───│ interview 表     │  (面试笔记，1:1)
  └─────────────┘     └─────────────────┘

  ┌─────────────────────┐
  │interview_favorite    │  (问题收藏，多对多)
  └─────────────────────┘

  ┌─────────────────────┐
  │interview_share       │  (分享链接，1:N)
  └─────────────────────┘

  ┌─────────────┐
  │ ai_config 表 │  (独立配置，被 AI 模块读取)
  └─────────────┘
```

### 1.3 数据约束分析

| 约束类型 | 说明 |
|---------|------|
| 实体完整性 | 所有表使用 BIGINT 自增主键 |
| 参照完整性 | 应用层保证外键关系（不建数据库外键，便于分库分表） |
| 用户完整性 | username 唯一约束 |
| 业务完整性 | 同一 provider + config_type 下只能有一个默认配置 |

### 1.4 数据安全要求

- 密码字段 BCrypt 加密存储（255 字符）
- API Key 字段 AES 加密存储（500 字符预留）
- 所有表支持逻辑删除（deleted 字段）
- 敏感字段不输出到日志

---

## 二、概念结构设计

### 2.1 实体识别

从需求分析中识别出以下核心实体：

```
┌──────────┐       ┌──────────────┐       ┌──────────────────┐
│   用户   │       │   面试记录    │       │   面试问题        │
│  (User)  │       │ (Interview)  │       │(InterviewQuestion)│
└──────────┘       └──────────────┘       └──────────────────┘
                          │
              ┌───────────┼───────────┐
              │           │           │
              ▼           ▼           ▼
       ┌──────────┐ ┌──────────┐ ┌──────────┐
       │面试笔记  │ │问题收藏  │ │面试分享  │
       │(Note)    │ │(Favorite)│ │(Share)   │
       └──────────┘ └──────────┘ └──────────┘
                                  │
                                  │ 1:N
                                  ▼
                           ┌──────────────┐
                           │  面经评论     │
                           │ (Comment)    │
                           └──────────────┘

                   ┌──────────────┐
                   │  AI 模型配置  │
                   │  (AiConfig)  │
                   └──────────────┘

  ┌──────────┐       ┌──────────────────┐       ┌──────────────┐
  │  角色    │       │  用户角色关联    │       │  权限        │
  │  (Role)  │       │  (UserRole)      │       │ (Permission) │
  └──────────┘       └──────────────────┘       └──────────────┘
                           │
                           │       ┌──────────────────┐
                           └───────│  角色权限关联    │
                                   │(RolePermission)  │
                                   └──────────────────┘
```

### 2.2 实体属性分析

**用户 (User)**
- 标识属性：id
- 描述属性：username, password, nickname, email, avatar
- 状态属性：role, status, deleted
- 登录属性：last_login_at, login_count
- 时间属性：created_at, updated_at

**面试记录 (Interview)**
- 标识属性：id
- 关联属性：user_id
- 描述属性：title, company_name, position_title, industry, interview_type
- 音频属性：audio_file_path, audio_file_size, audio_duration
- 简历属性：resume_file_path, resume_text
- 内容属性：jd_text, transcript_text
- 评分属性：overall_score, dimension_content/logic/expression/professional/communication
- 建议属性：improvement_summary, strengths, weaknesses
- 处理属性：status, processing_step, error_message, deleted
- 时间属性：created_at, updated_at

**面试问题 (InterviewQuestion)**
- 标识属性：id
- 关联属性：interview_id
- 描述属性：question_index, question_text, question_speaker, answer_text, answer_speaker
- 时间属性：start_time, end_time
- 评分属性：score, dimension_content/logic/expression/professional
- 建议属性：improvement_tip, reference_answer
- 状态属性：deleted
- 时间属性：created_at, updated_at

**用户笔记 (InterviewNote)**
- 标识属性：id
- 关联属性：interview_id, user_id
- 内容属性：note_content (富文本)
- 类型属性：note_type (INTERVIEW-面试笔记 / QUESTION-问题笔记)
- 关联属性：question_id (问题笔记时关联问题ID，面试笔记时为NULL)
- 时间属性：created_at, updated_at

**问题收藏 (InterviewFavorite)**
- 标识属性：id
- 关联属性：user_id, interview_id, question_id
- 描述属性：remark (收藏备注)
- 时间属性：created_at

**面试分享 (InterviewShare)**
- 标识属性：id
- 关联属性：interview_id, user_id
- 安全属性：share_token (唯一分享令牌)
- 公开属性：is_public (是否公开面经), view_count (浏览量)
- 时间属性：expire_at (过期时间), created_at

**AI 模型配置 (AiConfig)**
- 标识属性：id
- 描述属性：config_name, provider, model_name, api_endpoint
- 安全属性：api_key
- 分类属性：config_type
- 状态属性：is_default, sort_order, status, deleted
- 时间属性：created_at, updated_at

**角色 (Role)**
- 标识属性：id
- 描述属性：role_name (角色标识，如 admin/user), role_label (角色显示名称)
- 状态属性：status, deleted
- 时间属性：created_at, updated_at

**权限 (Permission)**
- 标识属性：id
- 描述属性：permission_name (权限标识，如 user:list), permission_label (权限显示名称)
- 时间属性：created_at

**用户角色关联 (UserRole)**
- 关联属性：user_id, role_id
- 时间属性：created_at

**角色权限关联 (RolePermission)**
- 关联属性：role_id, permission_id
- 时间属性：created_at

**面经评论 (InterviewComment)**
- 标识属性：id
- 关联属性：share_id (面经分享ID), user_id (评论者ID)
- 内容属性：content (评论内容)
- 状态属性：deleted
- 时间属性：created_at

### 2.3 E-R 图

```
┌──────────────┐         ┌──────────────────────────────┐
│              │         │         Interview             │
│     User     │    1    │──────────────────────────────│
│              │─────────│ id (PK)                       │
│              │    N    │ user_id (FK → User.id)        │
│              │         │ title                         │
│              │         │ company_name                  │
│              │         │ position_title                │
│              │         │ ...                           │
└──────┬───────┘         └──────────────┬───────────────┘
       │                                │ 1
       │                                │
       │    ┌───────────────────────┐   │ N
       │    │                       │   │
       │    │ 1:N        ┌──────────▼──┴───────────────┐
       │    │            │    InterviewQuestion          │
       │    │            │──────────────────────────────│
       │    │            │ id (PK)                       │
       │    │            │ interview_id (FK → Intv.id)   │
       │    │            │ question_index                │
       │    │            │ ...                           │
       │    │            └──────────────────────────────┘
       │    │
       │    │ 1:1
       │    ▼
       │  ┌──────────────────────┐
       │  │   InterviewNote      │
       │  │──────────────────────│
       │  │ id (PK)              │
       │  │ interview_id (FK)    │
       │  │ user_id (FK)         │
       │  │ note_content         │
       │  │ note_type            │
       │  │ question_id (可选)   │
       │  └──────────────────────┘
       │
       │ 1:N
       ▼
     ┌──────────────────────┐
     │  InterviewFavorite   │
     │──────────────────────│
     │ id (PK)              │
     │ user_id (FK)         │
     │ interview_id (FK)    │
     │ question_id (FK)     │
     │ remark               │
     └──────────────────────┘

     ┌──────────────────────┐
     │  InterviewShare      │
     │──────────────────────│
     │ id (PK)              │
     │ interview_id (FK)    │
     │ user_id (FK)         │
     │ share_token (UK)     │
     │ expire_at            │
     └──────────────────────┘

┌──────────────────────┐
│      AiConfig        │  (独立实体，无外键关联)
│──────────────────────│
│ id (PK)              │
│ config_name          │
│ provider             │
│ model_name           │
│ config_type          │
│ is_default           │
│ ...                  │
└──────────────────────┘
```

---

## 三、逻辑结构设计

### 3.1 关系模式

将 E-R 图转换为关系模式：

```
User (id, username, password, nickname, email, avatar, role, status,
      last_login_at, login_count, deleted, created_at, updated_at)

Interview (id, user_id, title, company_name, position_title, industry, interview_type,
           audio_file_path, audio_file_size, audio_duration,
           resume_file_path, resume_text, jd_text, transcript_text,
           overall_score, dimension_content, dimension_logic, dimension_expression,
           dimension_professional, dimension_communication,
           improvement_summary, strengths, weaknesses,
           status, processing_step, error_message, deleted, created_at, updated_at)

InterviewQuestion (id, interview_id, question_index, question_text, question_speaker,
                   answer_text, answer_speaker, start_time, end_time,
                   score, dimension_content, dimension_logic, dimension_expression,
                   dimension_professional,
                   improvement_tip, reference_answer,
                   deleted, created_at, updated_at)

InterviewNote (id, interview_id, user_id, note_content, note_type, question_id,
               deleted, created_at, updated_at)

InterviewFavorite (id, user_id, interview_id, question_id, remark,
                   deleted, created_at)

InterviewShare (id, interview_id, user_id, share_token, expire_at,
                is_public, view_count, deleted, created_at)

AiConfig (id, config_name, provider, model_name, api_key, api_endpoint,
          config_type, is_default, sort_order, status, deleted, created_at, updated_at)

Role (id, role_name, role_label, status, deleted, created_at, updated_at)

Permission (id, permission_name, permission_label, created_at)

UserRole (user_id, role_id, created_at)

RolePermission (role_id, permission_id, created_at)

InterviewComment (id, share_id, user_id, content, deleted, created_at)
```

### 3.2 范式分析

| 表名 | 范式 | 说明 |
|------|------|------|
| User | 3NF | 无传递依赖，无部分依赖 |
| Interview | 3NF | 评分维度拆为独立字段，无冗余 |
| InterviewQuestion | 3NF | 各属性完全依赖于主键 |
| InterviewNote | 3NF | 各属性完全依赖于主键 |
| InterviewFavorite | 3NF | 各属性完全依赖于主键 |
| InterviewShare | 3NF | 各属性完全依赖于主键 |
| AiConfig | 3NF | 所有属性依赖于主键 |
| Role | 3NF | 角色定义表，无冗余 |
| Permission | 3NF | 权限定义表，无冗余 |
| UserRole | 3NF | 用户-角色关联表，无冗余 |
| RolePermission | 3NF | 角色-权限关联表，无冗余 |

**设计决策：为什么评分维度用独立字段而非 JSON？**
- MySQL 5.7+ 虽然支持 JSON 类型，但 JSON 字段无法建立索引
- 独立字段便于按维度统计、排序、筛选
- 便于后续做维度对比分析（如"所有面试的表达能力平均分"）

### 3.3 关系约束

| 关系 | 类型 | 约束 |
|------|------|------|
| User → Interview | 一对多 | 一个用户有多条面试记录 |
| Interview → InterviewQuestion | 一对多 | 一条面试记录有多个问题 |
| Interview → InterviewNote | 一对一 | 一条面试记录只能有一条面试笔记 |
| Interview → InterviewFavorite | 一对多 | 一条面试记录可被多人收藏 |
| Interview → InterviewShare | 一对多 | 一条面试记录可生成多个分享链接 |
| User → InterviewFavorite | 一对多 | 一个用户可收藏多个问题 |
| User → InterviewNote | 一对多 | 一个用户可添加多条笔记 |
| AiConfig | 独立 | 无外键关联 |
| User → UserRole | 一对多 | 一个用户可拥有多个角色 |
| Role → UserRole | 一对多 | 一个角色可分配给多个用户 |
| Role → RolePermission | 一对多 | 一个角色可拥有多个权限 |
| Permission → RolePermission | 一对多 | 一个权限可分配给多个角色 |
| InterviewShare → InterviewComment | 一对多 | 一条面经可有多条评论 |
| User → InterviewComment | 一对多 | 一个用户可发表多条评论 |

**外键约束策略：**
- 数据库层面**不建外键约束**（便于后续分库分表）
- 应用层通过代码保证数据一致性
- 删除面试记录时，级联逻辑删除其下的问题记录

---

## 四、物理结构设计

### 4.1 存储引擎

所有表使用 **InnoDB** 引擎：
- 支持事务
- 支持行级锁
- 支持外键（虽然不建，但保留能力）
- 支持 MVCC

### 4.2 字符集

统一使用 **utf8mb4** 字符集，utf8mb4_general_ci 排序规则：
- 支持完整 Unicode（包括 Emoji）
- 兼容性好

### 4.3 索引设计

| 表名 | 索引名 | 索引字段 | 类型 | 说明 |
|------|--------|---------|------|------|
| user | PRIMARY | id | 主键 | 自增主键 |
| user | uk_username | username | 唯一索引 | 用户名唯一 |
| user | uk_email | email | 唯一索引 | 邮箱唯一 |
| user | idx_status | status | 普通索引 | 按状态筛选 |
| interview | PRIMARY | id | 主键 | 自增主键 |
| interview | idx_user_id | user_id | 普通索引 | 按用户查询面试列表 |
| interview | idx_status | status | 普通索引 | 按处理状态筛选 |
| interview | idx_created_at | created_at | 普通索引 | 按时间排序 |
| interview | idx_company_name | company_name | 普通索引 | 按公司筛选 |
| interview | idx_industry | industry | 普通索引 | 按行业筛选 |
| interview | idx_interview_type | interview_type | 普通索引 | 按面试类型筛选 |
| interview_question | PRIMARY | id | 主键 | 自增主键 |
| interview_question | idx_interview_id | interview_id | 普通索引 | 按面试记录查询问题 |
| interview_question | idx_question_index | interview_id, question_index | 联合索引 | 按面试+序号查询 |
| interview_note | PRIMARY | id | 主键 | 自增主键 |
| interview_note | uk_interview_user | interview_id, user_id, note_type | 唯一索引 | 每场面试每种类型只能有一条笔记 |
| interview_note | idx_user_id | user_id | 普通索引 | 按用户查询笔记 |
| interview_favorite | PRIMARY | id | 主键 | 自增主键 |
| interview_favorite | uk_user_question | user_id, question_id | 唯一索引 | 同一用户不能重复收藏同一问题 |
| interview_favorite | idx_user_id | user_id | 普通索引 | 按用户查询收藏 |
| interview_share | PRIMARY | id | 主键 | 自增主键 |
| interview_share | uk_share_token | share_token | 唯一索引 | 分享令牌唯一 |
| interview_share | idx_interview_id | interview_id | 普通索引 | 按面试记录查询分享 |
| interview_share | idx_is_public | is_public | 普通索引 | 按公开状态筛选 |
| interview_share | idx_public_created | is_public, created_at | 联合索引 | 面经广场列表排序 |
| ai_config | PRIMARY | id | 主键 | 自增主键 |
| ai_config | idx_provider | provider | 普通索引 | 按提供商筛选 |
| ai_config | idx_config_type | config_type | 普通索引 | 按用途筛选 |
| role | PRIMARY | id | 主键 | 自增主键 |
| role | uk_role_name | role_name | 唯一索引 | 角色标识唯一 |
| permission | PRIMARY | id | 主键 | 自增主键 |
| permission | uk_permission_name | permission_name | 唯一索引 | 权限标识唯一 |
| user_role | PRIMARY | user_id, role_id | 联合主键 | 用户角色关联 |
| user_role | idx_role_id | role_id | 普通索引 | 按角色查询用户 |
| role_permission | PRIMARY | role_id, permission_id | 联合主键 | 角色权限关联 |
| role_permission | idx_permission_id | permission_id | 普通索引 | 按权限查询角色 |
| interview_comment | PRIMARY | id | 主键 | 自增主键 |
| interview_comment | idx_share_id | share_id | 普通索引 | 按面经查询评论 |
| interview_comment | idx_user_id | user_id | 普通索引 | 按用户查询评论 |

### 4.4 分区策略（预留）

MVP 阶段不分区，数据量增长后可按以下策略分区：

| 表名 | 分区方式 | 说明 |
|------|---------|------|
| interview | 按 created_at 范围分区 | 按年份分区，便于数据归档 |
| interview_question | 按 interview_id 哈希分区 | 均匀分布数据 |

### 4.5 数据量预估与存储估算

| 表名 | 单行大小 | 年数据量 | 年存储增量 |
|------|---------|---------|-----------|
| user | ~500B | 10 万 | ~50MB |
| interview | ~5KB（不含文本） | 100 万 | ~5GB |
| interview_question | ~2KB | 500 万 | ~10GB |
| interview_note | ~2KB | 50 万 | ~1GB |
| interview_favorite | ~500B | 10 万 | ~50MB |
| interview_share | ~600B | 5 万 | ~30MB |
| ai_config | ~1KB | 100 | ~100KB |
| role | ~200B | 10 | ~2KB |
| permission | ~200B | 20 | ~4KB |
| user_role | ~100B | 10 万 | ~10MB |
| role_permission | ~100B | 100 | ~10KB |
| interview_comment | ~500B | 50 万 | ~250MB |

**注意：** `transcript_text` 和 `jd_text/resume_text` 使用 TEXT/LONGTEXT 类型，实际存储取决于音频时长和文本长度，可能显著增加存储需求。

---

## 五、数据库实施

### 5.1 建表 SQL

#### 5.1.1 user 表

```sql
CREATE TABLE `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    `nickname` VARCHAR(50) COMMENT '昵称',
    `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
    `avatar` VARCHAR(500) COMMENT '头像URL',
    `role` TINYINT DEFAULT 0 COMMENT '角色: 0-普通用户 1-管理员',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-正常',
    `last_login_at` DATETIME COMMENT '最后登录时间',
    `login_count` INT DEFAULT 0 COMMENT '登录次数',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX `uk_username` (`username`),
    UNIQUE INDEX `uk_email` (`email`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

#### 5.1.2 interview 表

```sql
CREATE TABLE `interview` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '面试记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `title` VARCHAR(200) COMMENT '面试标题',
    `company_name` VARCHAR(100) COMMENT '公司名称',
    `position_title` VARCHAR(100) COMMENT '职位名称',
    `industry` VARCHAR(50) COMMENT '行业分类',
    `interview_type` VARCHAR(30) COMMENT '面试类型: coding/behavioral/system_design/comprehensive',
    `audio_file_path` VARCHAR(500) COMMENT '音频文件存储路径',
    `audio_file_size` BIGINT COMMENT '音频文件大小(字节)',
    `audio_duration` INT COMMENT '音频时长(秒)',
    `resume_file_path` VARCHAR(500) COMMENT '简历PDF文件存储路径',
    `resume_text` TEXT COMMENT '简历文本内容(系统从PDF提取)',
    `jd_text` TEXT COMMENT '岗位JD内容',
    `transcript_text` LONGTEXT COMMENT '完整转写文本',
    `overall_score` DECIMAL(3,1) COMMENT '整体评分(1-10)',
    `dimension_content` DECIMAL(3,1) COMMENT '内容质量评分',
    `dimension_logic` DECIMAL(3,1) COMMENT '逻辑性评分',
    `dimension_expression` DECIMAL(3,1) COMMENT '表达能力评分',
    `dimension_professional` DECIMAL(3,1) COMMENT '专业度评分',
    `dimension_communication` DECIMAL(3,1) COMMENT '沟通技巧评分',
    `improvement_summary` TEXT COMMENT '整体改进建议',
    `strengths` TEXT COMMENT '优势总结',
    `weaknesses` TEXT COMMENT '待提升方面',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-处理中 1-已完成 2-失败',
    `processing_step` TINYINT DEFAULT 0 COMMENT '处理步骤: 0-未开始 1-语音转文字 2-问题识别 3-逐题评分 4-整体评分',
    `error_message` TEXT COMMENT '失败原因',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_created_at` (`created_at`),
    INDEX `idx_company_name` (`company_name`),
    INDEX `idx_industry` (`industry`),
    INDEX `idx_interview_type` (`interview_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试记录表';
```

#### 5.1.3 interview_question 表

```sql
CREATE TABLE `interview_question` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '问题ID',
    `interview_id` BIGINT NOT NULL COMMENT '面试记录ID',
    `question_index` INT NOT NULL COMMENT '问题序号',
    `question_text` TEXT COMMENT '问题内容',
    `question_speaker` VARCHAR(20) COMMENT '提问者角色: INTERVIEWER/CANDIDATE',
    `answer_text` TEXT COMMENT '回答内容',
    `answer_speaker` VARCHAR(20) COMMENT '回答者角色: INTERVIEWER/CANDIDATE',
    `start_time` INT COMMENT '开始时间(秒)',
    `end_time` INT COMMENT '结束时间(秒)',
    `score` DECIMAL(3,1) COMMENT '评分(1-10)',
    `dimension_content` DECIMAL(3,1) COMMENT '内容质量评分',
    `dimension_logic` DECIMAL(3,1) COMMENT '逻辑性评分',
    `dimension_expression` DECIMAL(3,1) COMMENT '表达能力评分',
    `dimension_professional` DECIMAL(3,1) COMMENT '专业度评分',
    `improvement_tip` TEXT COMMENT '改进建议',
    `reference_answer` TEXT COMMENT '参考答案',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_interview_id` (`interview_id`),
    INDEX `idx_question_index` (`interview_id`, `question_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试问题表';
```

#### 5.1.4 ai_config 表

```sql
CREATE TABLE `ai_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '配置ID',
    `config_name` VARCHAR(100) NOT NULL COMMENT '配置名称',
    `provider` VARCHAR(50) NOT NULL COMMENT '模型提供商: openai/deepseek/aliyun/baidu',
    `model_name` VARCHAR(100) NOT NULL COMMENT '模型名称',
    `api_key` VARCHAR(500) COMMENT 'API密钥(加密存储)',
    `api_endpoint` VARCHAR(500) COMMENT 'API端点地址',
    `config_type` TINYINT NOT NULL COMMENT '用途: 1-语音转文字 2-文本分析评分',
    `is_default` TINYINT DEFAULT 0 COMMENT '是否默认: 0-否 1-是',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_provider` (`provider`),
    INDEX `idx_config_type` (`config_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型配置表';
```

#### 5.1.5 interview_note 表

```sql
CREATE TABLE `interview_note` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '笔记ID',
    `interview_id` BIGINT NOT NULL COMMENT '面试记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `note_content` LONGTEXT COMMENT '笔记内容(富文本)',
    `note_type` VARCHAR(20) NOT NULL COMMENT '笔记类型: INTERVIEW-面试笔记 QUESTION-问题笔记',
    `question_id` BIGINT COMMENT '问题ID(问题笔记时必填)',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX `uk_interview_user` (`interview_id`, `user_id`, `note_type`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试笔记表';
```

#### 5.1.6 interview_favorite 表

```sql
CREATE TABLE `interview_favorite` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '收藏ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `interview_id` BIGINT NOT NULL COMMENT '面试记录ID',
    `question_id` BIGINT NOT NULL COMMENT '问题ID',
    `remark` VARCHAR(500) COMMENT '收藏备注',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE INDEX `uk_user_question` (`user_id`, `question_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_interview_id` (`interview_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题收藏表';
```

#### 5.1.7 interview_share 表

```sql
CREATE TABLE `interview_share` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分享ID',
    `interview_id` BIGINT NOT NULL COMMENT '面试记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `share_token` VARCHAR(64) NOT NULL COMMENT '分享令牌(唯一)',
    `expire_at` DATETIME COMMENT '过期时间(NULL表示永久有效)',
    `is_public` TINYINT DEFAULT 0 COMMENT '是否公开面经: 0-否 1-是',
    `view_count` INT DEFAULT 0 COMMENT '浏览量',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE INDEX `uk_share_token` (`share_token`),
    INDEX `idx_interview_id` (`interview_id`),
    INDEX `idx_is_public` (`is_public`),
    INDEX `idx_public_created` (`is_public`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试分享表';
```

#### 5.1.8 role 表

```sql
CREATE TABLE `role` (
    `id`         BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
    `role_name`  VARCHAR(50)  NOT NULL                   COMMENT '角色标识: admin/user',
    `role_label` VARCHAR(100) NOT NULL                   COMMENT '角色显示名称',
    `status`     TINYINT      DEFAULT 1                  COMMENT '状态: 0-禁用 1-启用',
    `deleted`    TINYINT      DEFAULT 0                  COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX `uk_role_name` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';
```

#### 5.1.9 permission 表

```sql
CREATE TABLE `permission` (
    `id`                BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '权限ID',
    `permission_name`   VARCHAR(100) NOT NULL                   COMMENT '权限标识: user:list',
    `permission_label`  VARCHAR(200) NOT NULL                   COMMENT '权限显示名称',
    `created_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    UNIQUE INDEX `uk_permission_name` (`permission_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';
```

#### 5.1.10 user_role 表

```sql
CREATE TABLE `user_role` (
    `user_id`    BIGINT   NOT NULL COMMENT '用户ID',
    `role_id`    BIGINT   NOT NULL COMMENT '角色ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`user_id`, `role_id`),
    INDEX `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';
```

#### 5.1.11 role_permission 表

```sql
CREATE TABLE `role_permission` (
    `role_id`       BIGINT   NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT   NOT NULL COMMENT '权限ID',
    `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`role_id`, `permission_id`),
    INDEX `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';
```

#### 5.1.12 interview_comment 表

```sql
CREATE TABLE `interview_comment` (
    `id`         BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '评论ID',
    `share_id`   BIGINT       NOT NULL                   COMMENT '面经分享ID',
    `user_id`    BIGINT       NOT NULL                   COMMENT '评论者用户ID',
    `content`    VARCHAR(500) NOT NULL                   COMMENT '评论内容',
    `deleted`    TINYINT      DEFAULT 0                  COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    INDEX `idx_share_id` (`share_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面经评论表';
```

### 5.2 初始化数据

```sql
-- 初始化角色数据
INSERT INTO `role` (`id`, `role_name`, `role_label`) VALUES
(1, 'admin', '管理员'),
(2, 'user', '普通用户');

-- 初始化权限数据
INSERT INTO `permission` (`id`, `permission_name`, `permission_label`) VALUES
(1, 'user:list', '查看用户列表'),
(2, 'user:manage', '管理用户账号'),
(3, 'ai_config:list', '查看AI配置'),
(4, 'ai_config:manage', '管理AI配置'),
(5, 'interview:view_all', '查看所有面试数据'),
(6, 'system:stats', '查看系统统计');

-- 管理员角色拥有所有权限
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6);

-- 初始化管理员账号（密码: admin123，BCrypt 加密）
INSERT INTO `user` (`username`, `password`, `nickname`, `role`)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '管理员', 1);

-- 管理员关联 admin 角色
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- 初始化默认 AI 配置（DeepSeek）
INSERT INTO `ai_config` (`config_name`, `provider`, `model_name`, `api_endpoint`, `config_type`, `is_default`)
VALUES ('DeepSeek 默认', 'deepseek', 'deepseek-chat', 'https://api.deepseek.com/v1', 2, 1);
```

### 5.3 MyBatis-Plus 配置

```yaml
# application.yml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true  # 下划线转驼峰
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 开发环境打印SQL
  global-config:
    db-config:
      id-type: auto  # 自增主键
      logic-delete-field: deleted  # 逻辑删除字段
      logic-delete-value: 1
      logic-not-delete-value: 0
```

---

## 六、数据库运行与维护

### 6.1 备份策略

| 备份类型 | 频率 | 保留时间 | 说明 |
|---------|------|---------|------|
| 全量备份 | 每天凌晨 2:00 | 30 天 | mysqldump 或 xtrabackup |
| 增量备份 | 每小时 | 7 天 | binlog 增量备份 |
| 逻辑备份 | 每周日 | 90 天 | 导出 SQL 文件 |

### 6.2 监控指标

| 监控项 | 阈值 | 告警方式 |
|--------|------|---------|
| 连接数使用率 | > 80% | 邮件+短信 |
| 慢查询数量 | > 10 次/分钟 | 邮件 |
| 主从延迟 | > 5 秒 | 邮件 |
| 磁盘使用率 | > 85% | 邮件+短信 |
| 锁等待时间 | > 10 秒 | 邮件 |

### 6.3 性能优化

**查询优化：**
- 避免 `SELECT *`，只查询需要的字段
- 大文本字段（transcript_text）按需查询，列表页不返回
- 分页查询使用游标分页（基于 id）而非 OFFSET

**写入优化：**
- 批量插入面试问题（MyBatis-Plus 批量插入）
- 异步写入转写文本（先处理，后存储大文本）

**索引优化：**
- 定期分析慢查询日志
- 根据实际查询模式调整索引
- 使用 EXPLAIN 分析查询执行计划

### 6.4 数据归档

| 数据类型 | 归档策略 | 说明 |
|---------|---------|------|
| 已删除数据 | 定期物理删除 | 每月清理 deleted=1 且超过 90 天的数据 |
| 历史面试记录 | 冷热分离 | 超过 1 年的数据迁移到归档表 |
| 音频文件 | 迁移到 OSS | 超过 6 个月的音频迁移到对象存储 |

### 6.5 扩容方案

**读写分离：**
- 主库负责写操作
- 从库负责读操作（面试记录列表、详情查询）
- 使用 MyBatis-Plus 多数据源配置

**分库分表（数据量达到千万级时）：**
- 按 user_id 对 interview 表进行水平分片
- 使用 ShardingSphere 或 MyCAT 实现
- 分片键：user_id

---

## 七、附录

### 7.1 数据字典

| 表名 | 字段名 | 类型 | 必填 | 默认值 | 说明 |
|------|--------|------|------|--------|------|
| user | id | BIGINT | 是 | 自增 | 主键 |
| user | username | VARCHAR(50) | 是 | - | 唯一用户名 |
| user | password | VARCHAR(255) | 是 | - | BCrypt 加密密码 |
| user | nickname | VARCHAR(50) | 否 | NULL | 昵称 |
| user | email | VARCHAR(100) | 是 | - | 邮箱（唯一） |
| user | avatar | VARCHAR(500) | 否 | NULL | 头像URL |
| user | role | TINYINT | 否 | 0 | 角色: 0-普通用户 1-管理员 |
| user | status | TINYINT | 否 | 1 | 状态: 0-禁用 1-正常 |
| user | last_login_at | DATETIME | 否 | NULL | 最后登录时间 |
| user | login_count | INT | 否 | 0 | 登录次数 |
| user | deleted | TINYINT | 否 | 0 | 逻辑删除: 0-未删除 1-已删除 |
| user | created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| user | updated_at | DATETIME | 否 | CURRENT_TIMESTAMP | 更新时间 |
| interview | id | BIGINT | 是 | 自增 | 主键 |
| interview | user_id | BIGINT | 是 | - | 用户ID |
| interview | title | VARCHAR(200) | 否 | NULL | 面试标题 |
| interview | company_name | VARCHAR(100) | 否 | NULL | 公司名称 |
| interview | position_title | VARCHAR(100) | 否 | NULL | 职位名称 |
| interview | industry | VARCHAR(50) | 否 | NULL | 行业分类 |
| interview | interview_type | VARCHAR(30) | 否 | NULL | 面试类型: coding/behavioral/system_design/comprehensive |
| interview | audio_file_path | VARCHAR(500) | 否 | NULL | 音频文件路径 |
| interview | audio_file_size | BIGINT | 否 | NULL | 音频文件大小(字节) |
| interview | audio_duration | INT | 否 | NULL | 音频时长(秒) |
| interview | resume_file_path | VARCHAR(500) | 否 | NULL | 简历PDF文件路径 |
| interview | resume_text | TEXT | 否 | NULL | 简历文本内容(系统从PDF提取) |
| interview | jd_text | TEXT | 否 | NULL | 岗位JD内容 |
| interview | transcript_text | LONGTEXT | 否 | NULL | 完整转写文本 |
| interview | overall_score | DECIMAL(3,1) | 否 | NULL | 整体评分(1-10) |
| interview | dimension_content | DECIMAL(3,1) | 否 | NULL | 内容质量评分 |
| interview | dimension_logic | DECIMAL(3,1) | 否 | NULL | 逻辑性评分 |
| interview | dimension_expression | DECIMAL(3,1) | 否 | NULL | 表达能力评分 |
| interview | dimension_professional | DECIMAL(3,1) | 否 | NULL | 专业度评分 |
| interview | dimension_communication | DECIMAL(3,1) | 否 | NULL | 沟通技巧评分 |
| interview | improvement_summary | TEXT | 否 | NULL | 整体改进建议 |
| interview | strengths | TEXT | 否 | NULL | 优势总结 |
| interview | weaknesses | TEXT | 否 | NULL | 待提升方面 |
| interview | status | TINYINT | 否 | 0 | 状态: 0-处理中 1-已完成 2-失败 |
| interview | processing_step | TINYINT | 否 | 0 | 处理步骤: 0-未开始 1-语音转文字 2-问题识别 3-逐题评分 4-整体评分 |
| interview | error_message | TEXT | 否 | NULL | 失败原因 |
| interview | deleted | TINYINT | 否 | 0 | 逻辑删除 |
| interview | created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| interview | updated_at | DATETIME | 否 | CURRENT_TIMESTAMP | 更新时间 |
| interview_question | id | BIGINT | 是 | 自增 | 主键 |
| interview_question | interview_id | BIGINT | 是 | - | 面试记录ID |
| interview_question | question_index | INT | 是 | - | 问题序号 |
| interview_question | question_text | TEXT | 否 | NULL | 问题内容 |
| interview_question | question_speaker | VARCHAR(20) | 否 | NULL | 提问者角色 |
| interview_question | answer_text | TEXT | 否 | NULL | 回答内容 |
| interview_question | answer_speaker | VARCHAR(20) | 否 | NULL | 回答者角色 |
| interview_question | start_time | INT | 否 | NULL | 开始时间(秒) |
| interview_question | end_time | INT | 否 | NULL | 结束时间(秒) |
| interview_question | score | DECIMAL(3,1) | 否 | NULL | 评分(1-10) |
| interview_question | dimension_content | DECIMAL(3,1) | 否 | NULL | 内容质量评分 |
| interview_question | dimension_logic | DECIMAL(3,1) | 否 | NULL | 逻辑性评分 |
| interview_question | dimension_expression | DECIMAL(3,1) | 否 | NULL | 表达能力评分 |
| interview_question | dimension_professional | DECIMAL(3,1) | 否 | NULL | 专业度评分 |
| interview_question | improvement_tip | TEXT | 否 | NULL | 改进建议 |
| interview_question | reference_answer | TEXT | 否 | NULL | 参考答案 |
| interview_question | deleted | TINYINT | 否 | 0 | 逻辑删除 |
| interview_question | created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| interview_question | updated_at | DATETIME | 否 | CURRENT_TIMESTAMP | 更新时间 |
| interview_note | id | BIGINT | 是 | 自增 | 主键 |
| interview_note | interview_id | BIGINT | 是 | - | 面试记录ID |
| interview_note | user_id | BIGINT | 是 | - | 用户ID |
| interview_note | note_content | LONGTEXT | 否 | NULL | 笔记内容(富文本) |
| interview_note | note_type | VARCHAR(20) | 是 | - | 笔记类型: INTERVIEW/QUESTION |
| interview_note | question_id | BIGINT | 否 | NULL | 问题ID(问题笔记时必填) |
| interview_note | deleted | TINYINT | 否 | 0 | 逻辑删除 |
| interview_note | created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| interview_note | updated_at | DATETIME | 否 | CURRENT_TIMESTAMP | 更新时间 |
| interview_favorite | id | BIGINT | 是 | 自增 | 主键 |
| interview_favorite | user_id | BIGINT | 是 | - | 用户ID |
| interview_favorite | interview_id | BIGINT | 是 | - | 面试记录ID |
| interview_favorite | question_id | BIGINT | 是 | - | 问题ID |
| interview_favorite | remark | VARCHAR(500) | 否 | NULL | 收藏备注 |
| interview_favorite | deleted | TINYINT | 否 | 0 | 逻辑删除 |
| interview_favorite | created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| interview_share | id | BIGINT | 是 | 自增 | 主键 |
| interview_share | interview_id | BIGINT | 是 | - | 面试记录ID |
| interview_share | user_id | BIGINT | 是 | - | 用户ID |
| interview_share | share_token | VARCHAR(64) | 是 | - | 分享令牌(唯一) |
| interview_share | expire_at | DATETIME | 否 | NULL | 过期时间(NULL=永久) |
| interview_share | is_public | TINYINT | 否 | 0 | 是否公开面经: 0-否 1-是 |
| interview_share | view_count | INT | 否 | 0 | 浏览量 |
| interview_share | deleted | TINYINT | 否 | 0 | 逻辑删除 |
| interview_share | created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| ai_config | id | BIGINT | 是 | 自增 | 主键 |
| ai_config | config_name | VARCHAR(100) | 是 | - | 配置名称 |
| ai_config | provider | VARCHAR(50) | 是 | - | 模型提供商 |
| ai_config | model_name | VARCHAR(100) | 是 | - | 模型名称 |
| ai_config | api_key | VARCHAR(500) | 否 | NULL | API密钥(加密) |
| ai_config | api_endpoint | VARCHAR(500) | 否 | NULL | API端点地址 |
| ai_config | config_type | TINYINT | 是 | - | 用途: 1-语音转文字 2-文本分析评分 |
| ai_config | is_default | TINYINT | 否 | 0 | 是否默认: 0-否 1-是 |
| ai_config | sort_order | INT | 否 | 0 | 排序 |
| ai_config | status | TINYINT | 否 | 1 | 状态: 0-禁用 1-启用 |
| ai_config | deleted | TINYINT | 否 | 0 | 逻辑删除 |
| ai_config | created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| ai_config | updated_at | DATETIME | 否 | CURRENT_TIMESTAMP | 更新时间 |
| role | id | BIGINT | 是 | 自增 | 主键 |
| role | role_name | VARCHAR(50) | 是 | - | 角色标识(唯一) |
| role | role_label | VARCHAR(100) | 是 | - | 角色显示名称 |
| role | status | TINYINT | 否 | 1 | 状态: 0-禁用 1-启用 |
| role | deleted | TINYINT | 否 | 0 | 逻辑删除 |
| role | created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| role | updated_at | DATETIME | 否 | CURRENT_TIMESTAMP | 更新时间 |
| permission | id | BIGINT | 是 | 自增 | 主键 |
| permission | permission_name | VARCHAR(100) | 是 | - | 权限标识(唯一) |
| permission | permission_label | VARCHAR(200) | 是 | - | 权限显示名称 |
| permission | created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| user_role | user_id | BIGINT | 是 | - | 用户ID |
| user_role | role_id | BIGINT | 是 | - | 角色ID |
| user_role | created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| role_permission | role_id | BIGINT | 是 | - | 角色ID |
| role_permission | permission_id | BIGINT | 是 | - | 权限ID |
| role_permission | created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| interview_comment | id | BIGINT | 是 | 自增 | 主键 |
| interview_comment | share_id | BIGINT | 是 | - | 面经分享ID |
| interview_comment | user_id | BIGINT | 是 | - | 评论者用户ID |
| interview_comment | content | VARCHAR(500) | 是 | - | 评论内容 |
| interview_comment | deleted | TINYINT | 否 | 0 | 逻辑删除 |
| interview_comment | created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |

### 7.2 状态枚举值

**interview.status:**
| 值 | 说明 |
|----|------|
| 0 | 处理中 (PROCESSING) |
| 1 | 已完成 (COMPLETED) |
| 2 | 失败 (FAILED) |

**interview.processing_step:**
| 值 | 说明 |
|----|------|
| 0 | 未开始 |
| 1 | 语音转文字 |
| 2 | 问题边界识别 |
| 3 | 逐题评分 |
| 4 | 整体评分 |

**interview.interview_type:**
| 值 | 说明 |
|----|------|
| coding | 技术面 |
| behavioral | 行为面 |
| system_design | 系统设计 |
| comprehensive | 综合 |

**interview_note.note_type:**
| 值 | 说明 |
|----|------|
| INTERVIEW | 面试笔记 |
| QUESTION | 问题笔记 |

**user.role:**
| 值 | 说明 |
|----|------|
| 0 | 普通用户 (USER) |
| 1 | 管理员 (ADMIN) |

**user.status:**
| 值 | 说明 |
|----|------|
| 0 | 禁用 (DISABLED) |
| 1 | 正常 (ACTIVE) |

**ai_config.config_type:**
| 值 | 说明 |
|----|------|
| 1 | 语音转文字 (ASR) |
| 2 | 文本分析评分 (LLM) |

### 7.3 完整 SQL 脚本

```sql
-- ============================================================
-- AI面试复盘系统 - 数据库初始化脚本
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `ai_interview`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `ai_interview`;

-- -----------------------------------------------------------
-- 1. 用户表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT          PRIMARY KEY AUTO_INCREMENT  COMMENT '用户ID',
    `username`      VARCHAR(50)     NOT NULL                    COMMENT '用户名',
    `password`      VARCHAR(255)    NOT NULL                    COMMENT '密码(BCrypt加密)',
    `nickname`      VARCHAR(50)     DEFAULT NULL                COMMENT '昵称',
    `email`         VARCHAR(100)    NOT NULL                    COMMENT '邮箱',
    `avatar`        VARCHAR(500)    DEFAULT NULL                COMMENT '头像URL',
    `role`          TINYINT         DEFAULT 0                   COMMENT '角色: 0-普通用户 1-管理员',
    `status`        TINYINT         DEFAULT 1                   COMMENT '状态: 0-禁用 1-正常',
    `last_login_at` DATETIME        DEFAULT NULL                COMMENT '最后登录时间',
    `login_count`   INT             DEFAULT 0                   COMMENT '登录次数',
    `deleted`       TINYINT         DEFAULT 0                   COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at`    DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    `updated_at`    DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX `uk_username` (`username`),
    UNIQUE INDEX `uk_email` (`email`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -----------------------------------------------------------
-- 2. 面试记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview` (
    `id`                      BIGINT          PRIMARY KEY AUTO_INCREMENT  COMMENT '面试记录ID',
    `user_id`                 BIGINT          NOT NULL                    COMMENT '用户ID',
    `title`                   VARCHAR(200)    DEFAULT NULL                COMMENT '面试标题',
    `company_name`            VARCHAR(100)    DEFAULT NULL                COMMENT '公司名称',
    `position_title`          VARCHAR(100)    DEFAULT NULL                COMMENT '职位名称',
    `industry`                VARCHAR(50)     DEFAULT NULL                COMMENT '行业分类',
    `interview_type`          VARCHAR(30)     DEFAULT NULL                COMMENT '面试类型: coding/behavioral/system_design/comprehensive',
    `audio_file_path`         VARCHAR(500)    DEFAULT NULL                COMMENT '音频文件存储路径',
    `audio_file_size`         BIGINT          DEFAULT NULL                COMMENT '音频文件大小(字节)',
    `audio_duration`          INT             DEFAULT NULL                COMMENT '音频时长(秒)',
    `resume_file_path`        VARCHAR(500)    DEFAULT NULL                COMMENT '简历PDF文件存储路径',
    `resume_text`             TEXT            DEFAULT NULL                COMMENT '简历文本内容(系统从PDF提取)',
    `jd_text`                 TEXT            DEFAULT NULL                COMMENT '岗位JD内容',
    `transcript_text`         LONGTEXT        DEFAULT NULL                COMMENT '完整转写文本',
    `overall_score`           DECIMAL(3,1)    DEFAULT NULL                COMMENT '整体评分(1-10)',
    `dimension_content`       DECIMAL(3,1)    DEFAULT NULL                COMMENT '内容质量评分',
    `dimension_logic`         DECIMAL(3,1)    DEFAULT NULL                COMMENT '逻辑性评分',
    `dimension_expression`    DECIMAL(3,1)    DEFAULT NULL                COMMENT '表达能力评分',
    `dimension_professional`  DECIMAL(3,1)    DEFAULT NULL                COMMENT '专业度评分',
    `dimension_communication` DECIMAL(3,1)    DEFAULT NULL                COMMENT '沟通技巧评分',
    `improvement_summary`     TEXT            DEFAULT NULL                COMMENT '整体改进建议',
    `strengths`               TEXT            DEFAULT NULL                COMMENT '优势总结',
    `weaknesses`              TEXT            DEFAULT NULL                COMMENT '待提升方面',
    `status`                  TINYINT         DEFAULT 0                   COMMENT '状态: 0-处理中 1-已完成 2-失败',
    `processing_step`         TINYINT         DEFAULT 0                   COMMENT '处理步骤: 0-未开始 1-语音转文字 2-问题识别 3-逐题评分 4-整体评分',
    `error_message`           TEXT            DEFAULT NULL                COMMENT '失败原因',
    `deleted`                 TINYINT         DEFAULT 0                   COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at`              DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    `updated_at`              DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_created_at` (`created_at`),
    INDEX `idx_company_name` (`company_name`),
    INDEX `idx_industry` (`industry`),
    INDEX `idx_interview_type` (`interview_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试记录表';

-- -----------------------------------------------------------
-- 3. 面试问题表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview_question` (
    `id`                    BIGINT          PRIMARY KEY AUTO_INCREMENT  COMMENT '问题ID',
    `interview_id`          BIGINT          NOT NULL                    COMMENT '面试记录ID',
    `question_index`        INT             NOT NULL                    COMMENT '问题序号',
    `question_text`         TEXT            DEFAULT NULL                COMMENT '问题内容',
    `question_speaker`      VARCHAR(20)     DEFAULT NULL                COMMENT '提问者角色: INTERVIEWER/CANDIDATE',
    `answer_text`           TEXT            DEFAULT NULL                COMMENT '回答内容',
    `answer_speaker`        VARCHAR(20)     DEFAULT NULL                COMMENT '回答者角色: INTERVIEWER/CANDIDATE',
    `start_time`            INT             DEFAULT NULL                COMMENT '开始时间(秒)',
    `end_time`              INT             DEFAULT NULL                COMMENT '结束时间(秒)',
    `score`                 DECIMAL(3,1)    DEFAULT NULL                COMMENT '评分(1-10)',
    `dimension_content`     DECIMAL(3,1)    DEFAULT NULL                COMMENT '内容质量评分',
    `dimension_logic`       DECIMAL(3,1)    DEFAULT NULL                COMMENT '逻辑性评分',
    `dimension_expression`  DECIMAL(3,1)    DEFAULT NULL                COMMENT '表达能力评分',
    `dimension_professional` DECIMAL(3,1)   DEFAULT NULL                COMMENT '专业度评分',
    `improvement_tip`       TEXT            DEFAULT NULL                COMMENT '改进建议',
    `reference_answer`      TEXT            DEFAULT NULL                COMMENT '参考答案',
    `deleted`               TINYINT         DEFAULT 0                   COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at`            DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    `updated_at`            DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_interview_id` (`interview_id`),
    INDEX `idx_question_index` (`interview_id`, `question_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试问题表';

-- -----------------------------------------------------------
-- 4. AI模型配置表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_config` (
    `id`            BIGINT          PRIMARY KEY AUTO_INCREMENT  COMMENT '配置ID',
    `config_name`   VARCHAR(100)    NOT NULL                    COMMENT '配置名称',
    `provider`      VARCHAR(50)     NOT NULL                    COMMENT '模型提供商: openai/deepseek/aliyun/baidu',
    `model_name`    VARCHAR(100)    NOT NULL                    COMMENT '模型名称',
    `api_key`       VARCHAR(500)    DEFAULT NULL                COMMENT 'API密钥(加密存储)',
    `api_endpoint`  VARCHAR(500)    DEFAULT NULL                COMMENT 'API端点地址',
    `config_type`   TINYINT         NOT NULL                    COMMENT '用途: 1-语音转文字 2-文本分析评分',
    `is_default`    TINYINT         DEFAULT 0                   COMMENT '是否默认: 0-否 1-是',
    `sort_order`    INT             DEFAULT 0                   COMMENT '排序',
    `status`        TINYINT         DEFAULT 1                   COMMENT '状态: 0-禁用 1-启用',
    `deleted`       TINYINT         DEFAULT 0                   COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at`    DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    `updated_at`    DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_provider` (`provider`),
    INDEX `idx_config_type` (`config_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型配置表';

-- -----------------------------------------------------------
-- 5. 面试笔记表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview_note` (
    `id`            BIGINT          PRIMARY KEY AUTO_INCREMENT  COMMENT '笔记ID',
    `interview_id`  BIGINT          NOT NULL                    COMMENT '面试记录ID',
    `user_id`       BIGINT          NOT NULL                    COMMENT '用户ID',
    `note_content`  LONGTEXT        DEFAULT NULL                COMMENT '笔记内容(富文本)',
    `note_type`     VARCHAR(20)     NOT NULL                    COMMENT '笔记类型: INTERVIEW-面试笔记 QUESTION-问题笔记',
    `question_id`   BIGINT          DEFAULT NULL                COMMENT '问题ID(问题笔记时必填)',
    `deleted`       TINYINT         DEFAULT 0                   COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at`    DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    `updated_at`    DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX `uk_interview_user` (`interview_id`, `user_id`, `note_type`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试笔记表';

-- -----------------------------------------------------------
-- 6. 问题收藏表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview_favorite` (
    `id`            BIGINT          PRIMARY KEY AUTO_INCREMENT  COMMENT '收藏ID',
    `user_id`       BIGINT          NOT NULL                    COMMENT '用户ID',
    `interview_id`  BIGINT          NOT NULL                    COMMENT '面试记录ID',
    `question_id`   BIGINT          NOT NULL                    COMMENT '问题ID',
    `remark`        VARCHAR(500)    DEFAULT NULL                COMMENT '收藏备注',
    `deleted`       TINYINT         DEFAULT 0                   COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at`    DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    UNIQUE INDEX `uk_user_question` (`user_id`, `question_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_interview_id` (`interview_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题收藏表';

-- -----------------------------------------------------------
-- 7. 面试分享表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview_share` (
    `id`            BIGINT          PRIMARY KEY AUTO_INCREMENT  COMMENT '分享ID',
    `interview_id`  BIGINT          NOT NULL                    COMMENT '面试记录ID',
    `user_id`       BIGINT          NOT NULL                    COMMENT '用户ID',
    `share_token`   VARCHAR(64)     NOT NULL                    COMMENT '分享令牌(唯一)',
    `expire_at`     DATETIME        DEFAULT NULL                COMMENT '过期时间(NULL表示永久有效)',
    `is_public`     TINYINT         DEFAULT 0                   COMMENT '是否公开面经: 0-否 1-是',
    `view_count`    INT             DEFAULT 0                   COMMENT '浏览量',
    `deleted`       TINYINT         DEFAULT 0                   COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at`    DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    UNIQUE INDEX `uk_share_token` (`share_token`),
    INDEX `idx_interview_id` (`interview_id`),
    INDEX `idx_is_public` (`is_public`),
    INDEX `idx_public_created` (`is_public`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试分享表';

-- -----------------------------------------------------------
-- 8. 角色表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role` (
    `id`         BIGINT       PRIMARY KEY AUTO_INCREMENT  COMMENT '角色ID',
    `role_name`  VARCHAR(50)  NOT NULL                    COMMENT '角色标识: admin/user',
    `role_label` VARCHAR(100) NOT NULL                    COMMENT '角色显示名称',
    `status`     TINYINT      DEFAULT 1                   COMMENT '状态: 0-禁用 1-启用',
    `deleted`    TINYINT      DEFAULT 0                   COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX `uk_role_name` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- -----------------------------------------------------------
-- 9. 权限表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `permission` (
    `id`                BIGINT       PRIMARY KEY AUTO_INCREMENT  COMMENT '权限ID',
    `permission_name`   VARCHAR(100) NOT NULL                    COMMENT '权限标识: user:list',
    `permission_label`  VARCHAR(200) NOT NULL                    COMMENT '权限显示名称',
    `created_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    UNIQUE INDEX `uk_permission_name` (`permission_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- -----------------------------------------------------------
-- 10. 用户角色关联表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_role` (
    `user_id`    BIGINT   NOT NULL  COMMENT '用户ID',
    `role_id`    BIGINT   NOT NULL  COMMENT '角色ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`user_id`, `role_id`),
    INDEX `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- -----------------------------------------------------------
-- 11. 角色权限关联表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role_permission` (
    `role_id`       BIGINT   NOT NULL  COMMENT '角色ID',
    `permission_id` BIGINT   NOT NULL  COMMENT '权限ID',
    `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`role_id`, `permission_id`),
    INDEX `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- -----------------------------------------------------------
-- 12. 面经评论表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview_comment` (
    `id`         BIGINT       PRIMARY KEY AUTO_INCREMENT  COMMENT '评论ID',
    `share_id`   BIGINT       NOT NULL                    COMMENT '面经分享ID',
    `user_id`    BIGINT       NOT NULL                    COMMENT '评论者用户ID',
    `content`    VARCHAR(500) NOT NULL                    COMMENT '评论内容',
    `deleted`    TINYINT      DEFAULT 0                   COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    INDEX `idx_share_id` (`share_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面经评论表';

-- -----------------------------------------------------------
-- 初始化数据
-- -----------------------------------------------------------

-- 初始化角色数据
INSERT INTO `role` (`id`, `role_name`, `role_label`) VALUES
(1, 'admin', '管理员'),
(2, 'user', '普通用户');

-- 初始化权限数据
INSERT INTO `permission` (`id`, `permission_name`, `permission_label`) VALUES
(1, 'user:list', '查看用户列表'),
(2, 'user:manage', '管理用户账号'),
(3, 'ai_config:list', '查看AI配置'),
(4, 'ai_config:manage', '管理AI配置'),
(5, 'interview:view_all', '查看所有面试数据'),
(6, 'system:stats', '查看系统统计');

-- 管理员角色拥有所有权限
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6);

-- 初始化管理员账号（密码: admin123，BCrypt 加密）
INSERT INTO `user` (`username`, `password`, `nickname`, `email`, `role`)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '管理员', 'admin@interview.com', 1);

-- 管理员关联 admin 角色
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- 初始化默认 AI 配置（DeepSeek）
INSERT INTO `ai_config` (`config_name`, `provider`, `model_name`, `api_endpoint`, `config_type`, `is_default`)
VALUES ('DeepSeek 默认', 'deepseek', 'deepseek-chat', 'https://api.deepseek.com/v1', 2, 1);
```
