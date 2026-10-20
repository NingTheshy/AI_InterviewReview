-- ============================================================
-- AI 面试复盘系统 - v1.5 完整 RBAC 升级脚本
-- 适用范围: 已使用旧版 user.role 的 MySQL 8 数据库
-- 执行前请备份数据库；此脚本仅执行一次。
-- ============================================================

USE `ai_interview`;
SET NAMES utf8mb4;

-- 使用动态 DDL 兼容首次执行和中断后的续跑。
SET @has_is_system = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'role' AND column_name = 'is_system'
);
SET @ddl = IF(@has_is_system = 0,
    'ALTER TABLE `role` ADD COLUMN `is_system` TINYINT DEFAULT 0 COMMENT ''是否系统内置角色: 0-否 1-是'' AFTER `role_label`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `role`
SET `is_system` = 1, `status` = 1, `deleted` = 0
WHERE `role_name` IN ('user', 'admin');

INSERT INTO `role` (`role_name`, `role_label`, `is_system`, `status`, `deleted`)
VALUES ('admin', '管理员', 1, 1, 0), ('user', '普通用户', 1, 1, 0)
ON DUPLICATE KEY UPDATE
    `role_label` = IF(`role_label` IS NULL OR `role_label` = '', VALUES(`role_label`), `role_label`),
    `is_system` = 1,
    `status` = 1,
    `deleted` = 0;

CREATE TABLE IF NOT EXISTS `permission_audit_log` (
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
ON DUPLICATE KEY UPDATE `permission_label` = VALUES(`permission_label`);

-- 迁移旧单角色字段：所有账号至少获得 user，原管理员再获得 admin。
INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT u.`id`, r.`id`
FROM `user` u
JOIN `role` r ON r.`role_name` = 'user';

SET @has_legacy_user_role = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'role'
);
SET @migrate_admin = IF(@has_legacy_user_role > 0,
    'INSERT IGNORE INTO `user_role` (`user_id`, `role_id`) SELECT u.`id`, r.`id` FROM `user` u JOIN `role` r ON r.`role_name` = ''admin'' WHERE u.`role` = 1',
    'SELECT 1');
PREPARE stmt FROM @migrate_admin;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DELETE rp
FROM `role_permission` rp
JOIN `role` r ON r.`id` = rp.`role_id`
WHERE r.`role_name` IN ('user', 'admin');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p ON p.`permission_name` IN (
    'interview:create', 'interview:read', 'interview:manage', 'note:manage',
    'favorite:manage', 'share:manage', 'comment:manage_own'
)
WHERE r.`role_name` = 'user';

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p ON p.`permission_name` IN (
    'user:read', 'user:status:update', 'user:role:assign', 'interview:read_all',
    'experience:read_all', 'experience:manage', 'comment:read_all', 'comment:manage',
    'ai_config:read', 'ai_config:manage', 'system:stats:read',
    'system:monitor:read', 'audit:read'
)
WHERE r.`role_name` = 'admin';

DELETE FROM `permission`
WHERE `permission_name` IN (
    'user:list', 'user:manage', 'ai_config:list', 'interview:view_all', 'system:stats'
);

SET @drop_legacy_role = IF(@has_legacy_user_role > 0,
    'ALTER TABLE `user` DROP COLUMN `role`',
    'SELECT 1');
PREPARE stmt FROM @drop_legacy_role;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
