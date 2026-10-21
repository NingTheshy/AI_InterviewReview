# AI 面试复盘系统 - 数据库设计文档

| 文档属性 | 内容 |
| --- | --- |
| 数据库 | MySQL 8.x |
| 字符集 | utf8mb4 |
| 存储引擎 | InnoDB |
| 文档版本 | V2.0 |
| 当前基线日期 | 2026-10-21 |
| 结构事实来源 | sql/schema.sql |
| 初始化数据事实来源 | sql/data.sql |

## 1. 设计范围

本文描述当前可运行版本的数据库结构。仓库只维护 sql/schema.sql 和 sql/data.sql 两个数据库脚本，不在文档中复制可执行建表语句，避免脚本和文档形成多个事实来源。

当前结构覆盖：

- 用户、角色、权限及高风险操作审计；
- 用户级不可变简历版本和异步解析任务；
- 面试、FunASR 转写、问答识别、评分、重评、回收站和任务租约；
- 面试总笔记、逐问题笔记和问题收藏；
- 数字 ID 公开面经、发布治理及评论；
- 通用/个性化标准答案缓存；
- ASR 与文本模型动态配置。

系统不再使用 interview_share、分享 Token、Whisper 或 MIMO-ASR。公开内容统一由 interview_experience 表表达，ASR 仅使用 FunASR。

## 2. 表清单

| 业务域 | 表名 | 说明 |
| --- | --- | --- |
| 用户权限 | user | 用户账户与状态 |
| 用户权限 | role | 角色定义，当前预置 user、admin |
| 用户权限 | permission | 权限码定义 |
| 用户权限 | user_role | 用户与角色多对多关系 |
| 用户权限 | role_permission | 角色与权限多对多关系 |
| 用户权限 | permission_audit_log | 权限及高风险操作审计 |
| 简历 | user_resume | 用户可复用、不可变简历版本及解析任务状态 |
| 面试 | interview | 面试聚合根、AI 状态机、配置快照和删除恢复 |
| 面试 | interview_question | 识别出的问答、评分、Rubric 和归档状态 |
| 用户内容 | interview_note | 面试总笔记和逐问题笔记 |
| 用户内容 | interview_favorite | 问题收藏 |
| 面经 | interview_experience | 每场面试唯一的公开面经发布记录 |
| 面经 | interview_comment | 面经评论 |
| AI | question_standard_cache | 标准答案和评分量表缓存 |
| AI | ai_config | ASR/LLM 动态配置、测试状态和密钥密文 |

## 3. 核心关系

    user
     ├─< user_resume
     ├─< interview >─ user_resume
     │    ├─< interview_question
     │    ├─< interview_note
     │    ├─< interview_favorite >─ interview_question
     │    └─0..1 interview_experience ─< interview_comment
     ├─< user_role >─ role ─< role_permission >─ permission
     └─< permission_audit_log

    question_standard_cache  按 GENERIC/PERSONAL 作用域隔离
    ai_config                 独立保存模型运行配置

当前脚本不声明数据库级外键。关联完整性、资源归属、删除顺序和历史引用由服务层事务、唯一索引及定时清理任务共同保证。

## 4. 表结构说明

### 4.1 user

| 字段组 | 字段 | 约束与用途 |
| --- | --- | --- |
| 标识 | id | 自增主键 |
| 登录 | username、password、email | 用户名、邮箱唯一；密码保存 BCrypt 密文 |
| 展示 | nickname、avatar | 公开面经可展示昵称和头像；昵称为空时不回退用户名 |
| 状态 | status、deleted | 启禁用与逻辑删除 |
| 审计 | last_login_at、login_count、created_at、updated_at | 登录及时间审计 |

### 4.2 user_resume

| 字段组 | 字段 | 约束与用途 |
| --- | --- | --- |
| 归属 | id、user_id | 简历版本主键及所有者 |
| 文件 | original_name、file_path、file_size、content_type、content_hash | PDF 文件元数据；SHA-256 用于同用户内容去重 |
| 解析 | parsed_text、parse_status、parse_error | 0 PROCESSING / 1 SUCCESS / 2 FAILED |
| 任务 | processing_token、lease_expires_at、heartbeat_at、retry_count | 异步解析租约、接管和重试 |
| 生命周期 | deleted、created_at、updated_at | deleted=1 表示从简历库隐藏，不影响历史面试快照 |

索引 (user_id, deleted, created_at) 支持简历库查询；(user_id, content_hash) 支持相同文件复用。

### 4.3 interview

| 字段组 | 字段 | 约束与用途 |
| --- | --- | --- |
| 归属 | id、user_id | 面试主键与所有者 |
| 基本信息 | title、company_name、position_title、industry、company_tier、interview_type | 类型限定为 coding、behavioral、system_design、comprehensive |
| 简历/JD | resume_id、resume_file_path、resume_text、jd_text | resume_id 绑定简历版本；正文和路径作为历史不可变快照 |
| 音频 | audio_file_path、audio_file_size、audio_duration | MP3/WAV，最大 2GB、最长 2 小时 |
| 转写 | transcript_text、transcript_segments | 完整文本及句子级 JSON；保存毫秒时间戳、speakerId 和角色 |
| 评分 | overall_score、五个 dimension_*、improvement_summary、strengths、weaknesses | 面试汇总结果 |
| 状态机 | status、processing_step、last_error_code、error_message | 状态：处理中、完成、失败、取消；阶段：未开始、转写、问答评分、保存、总结 |
| 任务可靠性 | processing_token、lease_expires_at、heartbeat_at | 防重复、续租、接管和旧线程回写保护 |
| 模型快照 | asr_config_snapshot、llm_config_snapshot | 每次任务启动时保存加密完整配置快照 |
| 重评 | reanalysis_status、reanalysis_error | 重新分析状态；失败时保留旧结果 |
| 删除恢复 | deleted_at、purge_after、purged_at、deleted | 7 天回收站与到期清理 |

processing_guard 是生成列。唯一索引 (user_id, processing_guard) 保证同一用户最多只有一场未删除且处理中的面试。

### 4.4 interview_question

| 字段组 | 字段 | 约束与用途 |
| --- | --- | --- |
| 定位 | id、interview_id、question_index | 面试内问题序号 |
| 问答 | question_text、answer_text、question_speaker、answer_speaker、start_time、end_time | 完整问答和秒级范围 |
| 识别 | question_type、recognition_confidence、included_in_score | 题型、置信度及是否计分 |
| 评分 | score、五个 dimension_*、improvement_tip | 逐题及维度评分 |
| 对比 | reference_answer、answer_difference、rubric_detail | 满分答案、关键差距和 Rubric JSON |
| 稳定关联 | question_fingerprint、is_active、archived_at | 重评时匹配原问题；未匹配问题归档以保留笔记和收藏 |

### 4.5 interview_note

note_type=INTERVIEW 时 question_id=0，每场面试保存一条 Markdown 总笔记；note_type=QUESTION 时使用真实问题 ID，同场不同问题分别保存一条 Markdown 笔记。

唯一索引 (interview_id, user_id, note_type, question_id) 保证每个笔记作用域只有一条当前记录，但一场面试可以拥有多个问题笔记。

### 4.6 interview_favorite

收藏只面向问题。question_id 必填，唯一索引 (user_id, question_id) 防止同一用户重复收藏同一问题。interview_id 保留来源面试上下文，不设置每用户数量上限。

### 4.7 interview_experience

| 字段 | 说明 |
| --- | --- |
| interview_id | 唯一索引；每场面试最多一篇面经 |
| user_id | 发布者 |
| summary_markdown | 最多 10,000 字的经验总结源文本 |
| publish_status | 用户状态：PUBLISHED / WITHDRAWN |
| moderation_status | 管理状态：NORMAL / BLOCKED |
| published_at、view_count | 最近发布时间和浏览量 |

面经仅在 publish_status=PUBLISHED 且 moderation_status=NORMAL 时公开。管理员下架后，用户不能自行解除 BLOCKED。面试删除或发起重新分析时自动撤回，恢复或重评完成后不自动重新发布。

### 4.8 interview_comment

评论通过 experience_id 关联面经，内容最大 500 字。面经撤回或下架时评论记录保留但不公开；面经重新发布或管理员解除限制后恢复显示。

### 4.9 question_standard_cache

| 字段组 | 字段 | 说明 |
| --- | --- | --- |
| 缓存键 | cache_key、context_digest | 规范化题目及上下文 SHA-256 |
| 作用域 | scope_type、user_id、interview_id、resume_id | GENERIC 可复用；PERSONAL 必须绑定用户、面试和简历版本 |
| 模型版本 | provider、model_name、config_version、prompt_version | 模型或提示词变化后自然隔离 |
| 内容 | reference_answer、rubric_detail | 标准答案和评分量表 JSON |

技术/客观题不写入简历上下文；个人、项目和行为题不得跨用户、面试或简历版本复用。

### 4.10 ai_config

保存 provider、model_name、api_endpoint、config_type、启停及默认状态。api_key 使用 AES-256-GCM 密文保存；数据库没有密钥时才允许读取对应环境变量。

test_status、test_fingerprint、last_tested_at、last_test_message 保存连接测试结果。只有启用、测试成功且测试指纹与当前配置一致的配置才能设为默认。

初始化数据只包含：

- FunASR 语音识别配置；
- DeepSeek 文本模型配置；
- MIMO 文本模型配置。

脚本不包含真实 API Key。

### 4.11 RBAC 与审计表

- role：角色标识、显示名、系统角色标记和启停状态；
- permission：唯一权限码及显示名称；
- user_role：复合主键 (user_id, role_id)；管理员可同时拥有 USER 与 ADMIN 角色，权限取并集；
- role_permission：复合主键 (role_id, permission_id)；管理员也必须通过该表获得每项权限；
- permission_audit_log：记录操作人、动作、目标、非敏感前后数据、来源 IP、结果和失败原因。

新注册账户自动关联 USER。角色变更后若账号将无角色，业务层自动补回 USER；最后管理者保护防止系统失去有效权限管理员。所有非公开业务接口遵循“身份认证 + 权限码 + 资源归属/业务规则，未明确授权则拒绝”。

## 5. 生命周期与一致性

### 5.1 面试任务

1. 创建记录时申请用户级处理中唯一槽位并生成任务令牌；
2. 工作线程只在令牌仍匹配时写入阶段结果，并定期续租；
3. 租约过期后新任务可以接管，旧线程因令牌失效不能继续写入；
4. 失败或取消后从最后完整阶段重试；完整转写存在时不重复调用 FunASR；
5. 重评先保存新结果，事务校验完成后原子替换当前结果；未匹配旧问题转为归档。

### 5.2 删除与清理

- 删除面试：设置 deleted_at、purge_after 和逻辑删除标志，并撤回公开面经；
- 7 天内恢复：原样恢复音频、转写、问题、评分和用户内容，不调用 AI；
- 到期清理：事务内清理音频、转写、问题、笔记、收藏、评论、面经和配置快照，仅保留最小删除审计信息；
- 删除简历：仅从简历库隐藏；历史面试继续读取快照；隐藏简历在不存在面试引用后才允许物理清理。

## 6. 索引与查询约束

- 面试列表使用 user_id、状态、创建时间、公司、行业和面试类型索引；
- 面试问题使用 (interview_id, question_index)、(interview_id, is_active) 和指纹索引；
- 面经列表使用 (publish_status, moderation_status, published_at)；
- 评论使用 experience_id；
- 收藏使用用户、来源面试及用户-问题唯一索引；
- 分页接口必须限制页码、每页大小和合法排序字段，不能直接拼接未经白名单校验的排序字段。

## 7. 初始化与验收

数据库重建顺序：

1. 执行 sql/schema.sql；
2. 执行 sql/data.sql；
3. 配置 RBAC_BOOTSTRAP_ADMIN_* 引导首个管理员；
4. 在管理端录入或通过环境变量提供 AI 密钥，并完成连接测试后设置默认模型。

验收必须确认：

- 两个 SQL 文件可以从空库完整执行；
- 表、字段和初始化权限与当前实体及 Mapper 一致；
- 同一用户不能同时存在两场处理中面试；
- 面试、简历、问题、收藏和面经的唯一索引能阻止重复数据；
- 日志、接口响应和文档不泄露 API Key、JWT 密钥、简历正文或配置快照明文。
