-- ============================================================
-- AI 面试复盘系统 - 初始化数据脚本
-- 版本: 当前完整基线
-- 日期: 2026-10-21
-- 说明: 请在 schema.sql 之后执行；脚本可重复执行。
-- ============================================================

USE `ai_interview`;
SET NAMES utf8mb4;
START TRANSACTION;

-- -----------------------------------------------------------
-- 1. 预置角色
-- -----------------------------------------------------------
INSERT INTO `role` (`role_name`, `role_label`, `is_system`, `status`, `deleted`) VALUES
('admin', '管理员', 1, 1, 0),
('user', '普通用户', 1, 1, 0)
ON DUPLICATE KEY UPDATE
    `role_label` = VALUES(`role_label`),
    `is_system` = VALUES(`is_system`),
    `status` = VALUES(`status`),
    `deleted` = VALUES(`deleted`);

-- -----------------------------------------------------------
-- 2. 预置权限
-- -----------------------------------------------------------
INSERT INTO `permission` (`permission_name`, `permission_label`) VALUES
('interview:create', '创建面试复盘'),
('interview:read', '查看本人面试复盘'),
('interview:manage', '管理本人面试复盘'),
('note:manage', '管理本人笔记'),
('favorite:manage', '管理本人收藏'),
('share:manage', '管理本人分享'),
('comment:manage_own', '发表和管理本人评论'),
('user:read', '查看用户'),
('user:status:update', '更新用户状态'),
('user:role:assign', '分配用户角色'),
('interview:read_all', '查看全部面试数据'),
('experience:read_all', '查看全部面经'),
('experience:manage', '管理面经'),
('comment:read_all', '查看全部评论'),
('comment:manage', '管理任意评论'),
('ai_config:read', '查看AI配置'),
('ai_config:manage', '管理AI配置'),
('system:stats:read', '查看系统统计'),
('system:monitor:read', '查看系统监控'),
('audit:read', '查看审计日志')
ON DUPLICATE KEY UPDATE
    `permission_label` = VALUES(`permission_label`);

-- -----------------------------------------------------------
-- 3. 角色权限关系
-- -----------------------------------------------------------
-- 普通用户角色拥有用户侧权限。
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p ON p.`permission_name` IN (
    'interview:create', 'interview:read', 'interview:manage', 'note:manage',
    'favorite:manage', 'share:manage', 'comment:manage_own'
)
WHERE r.`role_name` = 'user';

-- 管理员角色只声明管理侧增量权限；管理员账号同时关联 user 与 admin，权限取并集。
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p ON p.`permission_name` IN (
    'user:read', 'user:status:update', 'user:role:assign', 'interview:read_all',
    'experience:read_all', 'experience:manage', 'comment:read_all', 'comment:manage',
    'ai_config:read', 'ai_config:manage', 'system:stats:read',
    'system:monitor:read', 'audit:read'
)
WHERE r.`role_name` = 'admin';

-- 不在 SQL 中内置固定管理员密码。应用在无有效权限管理者时读取
-- RBAC_BOOTSTRAP_ADMIN_* 环境变量创建首个同时拥有 user、admin 角色的账号。

-- -----------------------------------------------------------
-- 4. 默认 AI 配置
-- -----------------------------------------------------------
INSERT INTO `ai_config`
    (`config_name`, `provider`, `model_name`, `api_endpoint`, `config_type`, `is_default`, `test_status`, `sort_order`)
SELECT 'DeepSeek 文本分析', 'deepseek', 'deepseek-chat', 'https://api.deepseek.com/v1', 2, 0, 0, 1
WHERE NOT EXISTS (
    SELECT 1 FROM `ai_config` WHERE `config_name` = 'DeepSeek 文本分析' AND `deleted` = 0
);

INSERT INTO `ai_config`
    (`config_name`, `provider`, `model_name`, `api_endpoint`, `config_type`, `is_default`, `test_status`, `sort_order`)
SELECT 'FunASR 语音识别', 'funasr', 'fun-asr', 'https://dashscope.aliyuncs.com/api/v1', 1, 0, 0, 1
WHERE NOT EXISTS (
    SELECT 1 FROM `ai_config` WHERE `config_name` = 'FunASR 语音识别' AND `deleted` = 0
);

INSERT INTO `ai_config`
    (`config_name`, `provider`, `model_name`, `api_endpoint`, `config_type`, `is_default`, `test_status`, `sort_order`)
SELECT '小米 MIMO-V2.5 文本分析', 'xiaomi', 'mimo-v2.5', 'https://token-plan-cn.xiaomimimo.com', 2, 0, 0, 2
WHERE NOT EXISTS (
    SELECT 1 FROM `ai_config` WHERE `config_name` = '小米 MIMO-V2.5 文本分析' AND `deleted` = 0
);

COMMIT;
