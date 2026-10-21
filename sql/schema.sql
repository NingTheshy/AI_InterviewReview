-- ============================================================
-- AI 面试复盘系统 - 数据库结构脚本
-- 版本: 当前完整基线
-- 日期: 2026-10-21
-- 说明: 本文件仅维护数据库与表结构；初始化数据见 data.sql。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ai_interview` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `ai_interview`;

-- -----------------------------------------------------------
-- 1. 用户表
-- -----------------------------------------------------------
CREATE TABLE `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    `nickname` VARCHAR(50) COMMENT '昵称',
    `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
    `avatar` VARCHAR(500) COMMENT '头像URL',
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

-- -----------------------------------------------------------
-- 2. 用户简历版本表
-- -----------------------------------------------------------
CREATE TABLE `user_resume` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '简历版本ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_path` VARCHAR(500) NOT NULL COMMENT '存储文件名',
    `file_size` BIGINT NOT NULL COMMENT '文件大小(字节)',
    `content_type` VARCHAR(100) COMMENT 'MIME类型',
    `content_hash` CHAR(64) NOT NULL COMMENT '文件SHA-256',
    `parsed_text` LONGTEXT NOT NULL COMMENT '解析后的不可变简历正文',
    `parse_status` TINYINT NOT NULL DEFAULT 0 COMMENT '解析状态: 0-处理中 1-成功 2-失败',
    `parse_error` VARCHAR(500) COMMENT '解析失败原因',
    `processing_token` VARCHAR(64) COMMENT '当前解析任务令牌',
    `lease_expires_at` DATETIME COMMENT '解析任务租约到期时间',
    `heartbeat_at` DATETIME COMMENT '解析任务最后心跳时间',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '解析重试次数',
    `deleted` TINYINT DEFAULT 0 COMMENT '从简历库隐藏: 0-可用 1-隐藏',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_resume_user` (`user_id`, `deleted`, `created_at`),
    INDEX `idx_resume_hash` (`user_id`, `content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户可复用简历版本表';

-- -----------------------------------------------------------
-- 3. 面试记录表
-- -----------------------------------------------------------
CREATE TABLE `interview` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '面试记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `title` VARCHAR(200) COMMENT '面试标题',
    `company_name` VARCHAR(100) COMMENT '公司名称',
    `position_title` VARCHAR(100) COMMENT '职位名称',
    `industry` VARCHAR(50) COMMENT '行业分类',
    `company_tier` TINYINT COMMENT '公司分级: 1-超大厂 2-大厂 3-中厂 4-小厂 5-初创（内部使用，前端不展示）',
    `interview_type` VARCHAR(30) COMMENT '面试类型: coding/behavioral/system_design/comprehensive',
    `resume_id` BIGINT COMMENT '绑定的用户简历版本ID；历史数据清理后置空',
    `audio_file_path` VARCHAR(500) COMMENT '音频文件存储路径',
    `audio_file_size` BIGINT COMMENT '音频文件大小(字节)',
    `audio_duration` INT COMMENT '音频时长(秒)',
    `resume_file_path` VARCHAR(500) COMMENT '简历PDF文件存储路径',
    `resume_text` TEXT COMMENT '简历文本内容(系统从PDF提取)',
    `jd_text` TEXT COMMENT '岗位JD内容',
    `transcript_text` LONGTEXT COMMENT '完整转写文本',
    `transcript_segments` LONGTEXT COMMENT '句子级转写结果(JSON，含speakerId、speakerRole和毫秒时间戳)',
    `overall_score` INT COMMENT '整体评分(0-100)',
    `dimension_content` INT COMMENT '内容质量评分(0-100)',
    `dimension_logic` INT COMMENT '逻辑性评分(0-100)',
    `dimension_expression` INT COMMENT '表达能力评分(0-100)',
    `dimension_professional` INT COMMENT '专业度评分(0-100)',
    `dimension_communication` INT COMMENT '沟通技巧评分(0-100)',
    `improvement_summary` TEXT COMMENT '整体改进建议',
    `strengths` TEXT COMMENT '优势总结',
    `weaknesses` TEXT COMMENT '待提升方面',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-处理中 1-已完成 2-失败 3-已取消',
    `processing_step` TINYINT DEFAULT 0 COMMENT '处理步骤: 0-未开始 1-FunASR转写 2-标准生成与比对 3-计分保存 4-整体总结',
    `processing_token` VARCHAR(64) COMMENT '当前工作线程令牌',
    `lease_expires_at` DATETIME COMMENT '任务租约到期时间',
    `heartbeat_at` DATETIME COMMENT '任务最后心跳时间',
    `asr_config_snapshot` LONGTEXT COMMENT '加密ASR配置快照',
    `llm_config_snapshot` LONGTEXT COMMENT '加密LLM配置快照',
    `last_error_code` VARCHAR(64) COMMENT '稳定失败错误码',
    `reanalysis_status` VARCHAR(20) COMMENT '角色校正重分析状态',
    `reanalysis_error` VARCHAR(500) COMMENT '角色校正重分析失败原因',
    `error_message` TEXT COMMENT '失败原因',
    `deleted_at` DATETIME COMMENT '进入回收站时间',
    `purge_after` DATETIME COMMENT '允许恢复截止时间',
    `purged_at` DATETIME COMMENT '敏感业务数据物理清理时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `processing_guard` TINYINT GENERATED ALWAYS AS (CASE WHEN `status`=0 AND `deleted`=0 THEN 1 ELSE NULL END) STORED,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_created_at` (`created_at`),
    INDEX `idx_company_name` (`company_name`),
    INDEX `idx_industry` (`industry`),
    INDEX `idx_interview_type` (`interview_type`)
    ,UNIQUE INDEX `uk_user_active_processing` (`user_id`, `processing_guard`)
    ,INDEX `idx_purge_after` (`deleted`, `purged_at`, `purge_after`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试记录表';

-- -----------------------------------------------------------
-- 4. 面试问题表
-- -----------------------------------------------------------
CREATE TABLE `interview_question` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '问题ID',
    `interview_id` BIGINT NOT NULL COMMENT '面试记录ID',
    `question_index` INT NOT NULL COMMENT '问题序号',
    `question_text` TEXT COMMENT '问题内容',
    `question_type` VARCHAR(20) COMMENT '题型: TECHNICAL/OBJECTIVE/PERSONAL/PROJECT/BEHAVIORAL',
    `recognition_confidence` VARCHAR(10) COMMENT '问答边界置信度: HIGH/LOW',
    `included_in_score` TINYINT NOT NULL DEFAULT 1 COMMENT '是否参与汇总评分',
    `question_speaker` VARCHAR(20) COMMENT '提问者角色: INTERVIEWER/CANDIDATE',
    `answer_text` TEXT COMMENT '回答内容',
    `answer_speaker` VARCHAR(20) COMMENT '回答者角色: INTERVIEWER/CANDIDATE',
    `start_time` INT COMMENT '开始时间(秒)',
    `end_time` INT COMMENT '结束时间(秒)',
    `score` INT COMMENT '评分(0-100)',
    `dimension_content` INT COMMENT '内容质量评分(0-100)',
    `dimension_logic` INT COMMENT '逻辑性评分(0-100)',
    `dimension_expression` INT COMMENT '表达能力评分(0-100)',
    `dimension_professional` INT COMMENT '专业度评分(0-100)',
    `improvement_tip` TEXT COMMENT '改进建议',
    `reference_answer` TEXT COMMENT '参考答案',
    `answer_difference` TEXT COMMENT '用户答案与满分标准的关键差距',
    `rubric_detail` LONGTEXT COMMENT '评分量表与逐要点比对结果(JSON)',
    `question_fingerprint` CHAR(64) COMMENT '规范化题目与时间范围指纹',
    `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否为当前有效问题: 0-归档 1-有效',
    `archived_at` DATETIME COMMENT '问题归档时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_interview_id` (`interview_id`),
    INDEX `idx_question_index` (`interview_id`, `question_index`),
    INDEX `idx_question_active` (`interview_id`, `is_active`),
    INDEX `idx_question_fingerprint` (`interview_id`, `question_fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试问题表';

-- -----------------------------------------------------------
-- 5. 面试笔记表
-- -----------------------------------------------------------
CREATE TABLE `interview_note` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '笔记ID',
    `interview_id` BIGINT NOT NULL COMMENT '面试记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `note_content` LONGTEXT COMMENT '笔记内容(富文本)',
    `note_type` VARCHAR(20) NOT NULL COMMENT '笔记类型: INTERVIEW-面试笔记 QUESTION-问题笔记',
    `question_id` BIGINT NOT NULL DEFAULT 0 COMMENT '问题ID(问题笔记时为真实ID，面试笔记为0)',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX `uk_note_scope` (`interview_id`, `user_id`, `note_type`, `question_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试笔记表';

-- -----------------------------------------------------------
-- 6. 问题收藏表
-- -----------------------------------------------------------
CREATE TABLE `interview_favorite` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '收藏ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `interview_id` BIGINT NOT NULL COMMENT '面试记录ID',
    `question_id` BIGINT NOT NULL COMMENT '问题ID',
    `remark` VARCHAR(500) COMMENT '收藏备注',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX `uk_favorite_user_question` (`user_id`, `question_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_interview_id` (`interview_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题收藏表';

-- -----------------------------------------------------------
-- 7. 面经发布表
-- -----------------------------------------------------------
CREATE TABLE `interview_experience` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '面经ID',
    `interview_id` BIGINT NOT NULL COMMENT '面试记录ID',
    `user_id` BIGINT NOT NULL COMMENT '发布者用户ID',
    `summary_markdown` LONGTEXT COMMENT '发布者经验总结Markdown，最多10000字',
    `publish_status` VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' COMMENT '用户发布状态: PUBLISHED/WITHDRAWN',
    `moderation_status` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '治理状态: NORMAL/BLOCKED',
    `published_at` DATETIME COMMENT '最近发布时间',
    `view_count` INT DEFAULT 0 COMMENT '浏览量',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE INDEX `uk_experience_interview` (`interview_id`),
    INDEX `idx_experience_user` (`user_id`),
    INDEX `idx_experience_visible` (`publish_status`, `moderation_status`, `published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公开面经发布表';

-- -----------------------------------------------------------
-- 8. 面经评论表
-- -----------------------------------------------------------
CREATE TABLE `interview_comment` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评论ID',
    `experience_id` BIGINT NOT NULL COMMENT '公开面经ID',
    `user_id` BIGINT NOT NULL COMMENT '评论者用户ID',
    `content` VARCHAR(500) NOT NULL COMMENT '评论内容',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_experience_id` (`experience_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面经评论表';

-- -----------------------------------------------------------
-- 9. 标准答案缓存表
-- -----------------------------------------------------------
CREATE TABLE `question_standard_cache` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标准答案缓存ID',
    `cache_key` CHAR(64) NOT NULL COMMENT '规范化上下文SHA-256',
    `scope_type` VARCHAR(20) NOT NULL COMMENT '作用域: GENERIC/PERSONAL',
    `question_text` TEXT NOT NULL COMMENT '规范化前题目',
    `context_digest` CHAR(64) NOT NULL COMMENT '岗位公司简历等上下文摘要',
    `user_id` BIGINT COMMENT '个性化缓存所属用户',
    `interview_id` BIGINT COMMENT '个性化缓存所属面试',
    `resume_id` BIGINT COMMENT '个性化缓存绑定简历版本',
    `provider` VARCHAR(50) NOT NULL COMMENT '模型提供商',
    `model_name` VARCHAR(100) NOT NULL COMMENT '模型名称',
    `config_version` VARCHAR(100) NOT NULL COMMENT '配置版本摘要',
    `prompt_version` VARCHAR(50) NOT NULL COMMENT '提示词版本',
    `reference_answer` LONGTEXT NOT NULL COMMENT '标准满分答案',
    `rubric_detail` LONGTEXT NOT NULL COMMENT '评分量表JSON',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX `uk_standard_cache_key` (`cache_key`),
    INDEX `idx_standard_scope` (`scope_type`, `user_id`, `interview_id`, `resume_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标准答案与评分量表缓存表';

-- -----------------------------------------------------------
-- 10. AI 模型配置表
-- -----------------------------------------------------------
CREATE TABLE `ai_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '配置ID',
    `config_name` VARCHAR(100) NOT NULL COMMENT '配置名称',
    `provider` VARCHAR(50) NOT NULL COMMENT '模型提供商: openai/deepseek/aliyun/baidu',
    `model_name` VARCHAR(100) NOT NULL COMMENT '模型名称',
    `api_key` VARCHAR(4096) COMMENT 'API密钥(AES-256-GCM加密存储)',
    `api_endpoint` VARCHAR(500) COMMENT 'API端点地址',
    `config_type` TINYINT NOT NULL COMMENT '用途: 1-语音转文字 2-文本分析评分',
    `is_default` TINYINT DEFAULT 0 COMMENT '是否默认: 0-否 1-是',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `test_status` TINYINT DEFAULT 0 COMMENT '连接测试: 0-未测试 1-成功 2-失败',
    `test_fingerprint` CHAR(64) COMMENT '测试成功时的运行参数指纹',
    `last_tested_at` DATETIME COMMENT '最后测试时间',
    `last_test_message` VARCHAR(500) COMMENT '最后测试结果',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_provider` (`provider`),
    INDEX `idx_config_type` (`config_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型配置表';

-- -----------------------------------------------------------
-- 11. 角色表
-- -----------------------------------------------------------
CREATE TABLE `role` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
    `role_name` VARCHAR(50) NOT NULL COMMENT '角色标识: admin/user',
    `role_label` VARCHAR(100) NOT NULL COMMENT '角色显示名称',
    `is_system` TINYINT DEFAULT 0 COMMENT '是否系统内置角色: 0-否 1-是',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX `uk_role_name` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- -----------------------------------------------------------
-- 12. 权限表
-- -----------------------------------------------------------
CREATE TABLE `permission` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '权限ID',
    `permission_name` VARCHAR(100) NOT NULL COMMENT '权限标识: user:read',
    `permission_label` VARCHAR(200) NOT NULL COMMENT '权限显示名称',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE INDEX `uk_permission_name` (`permission_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- -----------------------------------------------------------
-- 13. 用户角色关联表
-- -----------------------------------------------------------
CREATE TABLE `user_role` (
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`user_id`, `role_id`),
    INDEX `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- -----------------------------------------------------------
-- 14. 角色权限关联表
-- -----------------------------------------------------------
CREATE TABLE `role_permission` (
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`role_id`, `permission_id`),
    INDEX `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- -----------------------------------------------------------
-- 15. 权限与高风险操作审计日志表
-- -----------------------------------------------------------
CREATE TABLE `permission_audit_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '审计日志ID',
    `operator_user_id` BIGINT COMMENT '操作人用户ID；系统引导操作可为空',
    `action` VARCHAR(100) NOT NULL COMMENT '操作类型',
    `target_type` VARCHAR(50) NOT NULL COMMENT '目标资源类型',
    `target_id` VARCHAR(100) COMMENT '目标资源标识',
    `before_data` LONGTEXT COMMENT '操作前非敏感数据(JSON)',
    `after_data` LONGTEXT COMMENT '操作后非敏感数据(JSON)',
    `source_ip` VARCHAR(64) COMMENT '来源IP',
    `success` TINYINT NOT NULL COMMENT '是否成功: 0-否 1-是',
    `failure_reason` VARCHAR(500) COMMENT '失败原因',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_operator_user_id` (`operator_user_id`),
    INDEX `idx_action` (`action`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限与高风险操作审计日志表';
