-- ============================================================
-- AI 面试复盘系统 - 数据库建表脚本
-- 版本: v1.6 RBAC + P1 安全加固
-- 日期: 2026-10-20
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
-- 2. 面试记录表
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
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-处理中 1-已完成 2-失败',
    `processing_step` TINYINT DEFAULT 0 COMMENT '处理步骤: 0-未开始 1-FunASR转写 2-标准生成与比对 3-计分保存 4-整体总结',
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

-- -----------------------------------------------------------
-- 3. 面试问题表
-- -----------------------------------------------------------
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
    `score` INT COMMENT '评分(0-100)',
    `dimension_content` INT COMMENT '内容质量评分(0-100)',
    `dimension_logic` INT COMMENT '逻辑性评分(0-100)',
    `dimension_expression` INT COMMENT '表达能力评分(0-100)',
    `dimension_professional` INT COMMENT '专业度评分(0-100)',
    `improvement_tip` TEXT COMMENT '改进建议',
    `reference_answer` TEXT COMMENT '参考答案',
    `rubric_detail` LONGTEXT COMMENT '评分量表与逐要点比对结果(JSON)',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_interview_id` (`interview_id`),
    INDEX `idx_question_index` (`interview_id`, `question_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试问题表';

-- -----------------------------------------------------------
-- 4. 面试笔记表
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
-- 5. 问题收藏表
-- -----------------------------------------------------------
CREATE TABLE `interview_favorite` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '收藏ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `interview_id` BIGINT NOT NULL COMMENT '面试记录ID',
    `question_id` BIGINT COMMENT '问题ID(为NULL时表示收藏整个面试)',
    `remark` VARCHAR(500) COMMENT '收藏备注',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_interview_id` (`interview_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题收藏表';

-- -----------------------------------------------------------
-- 6. 面试分享表
-- -----------------------------------------------------------
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

-- -----------------------------------------------------------
-- 7. 面经评论表
-- -----------------------------------------------------------
CREATE TABLE `interview_comment` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评论ID',
    `share_id` BIGINT NOT NULL COMMENT '面经分享ID',
    `user_id` BIGINT NOT NULL COMMENT '评论者用户ID',
    `content` VARCHAR(500) NOT NULL COMMENT '评论内容',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_share_id` (`share_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面经评论表';

-- -----------------------------------------------------------
-- 8. AI 模型配置表
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
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_provider` (`provider`),
    INDEX `idx_config_type` (`config_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型配置表';

-- -----------------------------------------------------------
-- 9. 角色表
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
-- 10. 权限表
-- -----------------------------------------------------------
CREATE TABLE `permission` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '权限ID',
    `permission_name` VARCHAR(100) NOT NULL COMMENT '权限标识: user:read',
    `permission_label` VARCHAR(200) NOT NULL COMMENT '权限显示名称',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE INDEX `uk_permission_name` (`permission_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- -----------------------------------------------------------
-- 11. 用户角色关联表
-- -----------------------------------------------------------
CREATE TABLE `user_role` (
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`user_id`, `role_id`),
    INDEX `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- -----------------------------------------------------------
-- 12. 角色权限关联表
-- -----------------------------------------------------------
CREATE TABLE `role_permission` (
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`role_id`, `permission_id`),
    INDEX `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- -----------------------------------------------------------
-- 13. 权限与高风险操作审计日志表
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

-- ============================================================
-- 初始化数据
-- ============================================================

-- 初始化角色数据
INSERT INTO `role` (`id`, `role_name`, `role_label`, `is_system`) VALUES
(1, 'admin', '管理员', 1),
(2, 'user', '普通用户', 1);

-- 初始化权限数据
INSERT INTO `permission` (`id`, `permission_name`, `permission_label`) VALUES
(1, 'interview:create', '创建面试复盘'),
(2, 'interview:read', '查看本人面试复盘'),
(3, 'interview:manage', '管理本人面试复盘'),
(4, 'note:manage', '管理本人笔记'),
(5, 'favorite:manage', '管理本人收藏'),
(6, 'share:manage', '管理本人分享'),
(7, 'comment:manage_own', '发表和管理本人评论'),
(8, 'user:read', '查看用户'),
(9, 'user:status:update', '更新用户状态'),
(10, 'user:role:assign', '分配用户角色'),
(11, 'interview:read_all', '查看全部面试数据'),
(12, 'experience:read_all', '查看全部面经'),
(13, 'experience:manage', '管理面经'),
(14, 'comment:read_all', '查看全部评论'),
(15, 'comment:manage', '管理任意评论'),
(16, 'ai_config:read', '查看AI配置'),
(17, 'ai_config:manage', '管理AI配置'),
(18, 'system:stats:read', '查看系统统计'),
(19, 'system:monitor:read', '查看系统监控'),
(20, 'audit:read', '查看审计日志');

-- 普通用户角色拥有用户侧权限
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
(2, 1), (2, 2), (2, 3), (2, 4), (2, 5), (2, 6), (2, 7);

-- 管理员角色只声明管理侧增量权限；管理员账号同时关联 user 与 admin，权限取并集
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
(1, 8), (1, 9), (1, 10), (1, 11), (1, 12), (1, 13), (1, 14),
(1, 15), (1, 16), (1, 17), (1, 18), (1, 19), (1, 20);

-- 不在 SQL 中内置固定管理员密码。应用在无有效权限管理者时读取
-- RBAC_BOOTSTRAP_ADMIN_* 环境变量创建首个同时拥有 user、admin 角色的账号。

-- 初始化默认 AI 配置（DeepSeek 文本分析）
INSERT INTO `ai_config` (`config_name`, `provider`, `model_name`, `api_endpoint`, `config_type`, `is_default`, `sort_order`)
VALUES ('DeepSeek 文本分析', 'deepseek', 'deepseek-chat', 'https://api.deepseek.com/v1', 2, 1, 1);

-- 初始化默认 AI 配置（Whisper 语音转文字）
INSERT INTO `ai_config` (`config_name`, `provider`, `model_name`, `api_endpoint`, `config_type`, `is_default`, `sort_order`)
VALUES ('Whisper 语音转文字', 'openai', 'whisper-1', 'https://api.openai.com/v1', 1, 0, 2);

-- 初始化默认 AI 配置（FunASR 非实时语音识别）
INSERT INTO `ai_config` (`config_name`, `provider`, `model_name`, `api_endpoint`, `config_type`, `is_default`, `sort_order`)
VALUES ('FunASR 语音识别', 'funasr', 'paraformer-v2', 'https://dashscope.aliyuncs.com/api/v1', 1, 1, 1);

-- 初始化默认 AI 配置（小米 MIMO-V2.5 文本分析）
INSERT INTO `ai_config` (`config_name`, `provider`, `model_name`, `api_endpoint`, `config_type`, `is_default`, `sort_order`)
VALUES ('小米 MIMO-V2.5 文本分析', 'xiaomi', 'mimo-v2.5', 'https://token-plan-cn.xiaomimimo.com/v1', 2, 0, 2);
