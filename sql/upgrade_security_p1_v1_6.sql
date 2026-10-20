-- ============================================================
-- AI 面试复盘系统 - v1.6 P1 安全加固升级脚本
-- 日期: 2026-10-20
-- 说明: 扩充 AI API Key 密文字段；历史明文由应用启动迁移器加密。
-- ============================================================

USE `ai_interview`;
SET NAMES utf8mb4;

ALTER TABLE `ai_config`
    MODIFY COLUMN `api_key` VARCHAR(4096)
    COMMENT 'API密钥(AES-256-GCM加密存储)';

-- DDL 完成后启动应用。AiConfigSecretMigrationInitializer 会在同一事务中把所有
-- 非空且不带 enc:v1: 前缀的历史值转换为 AES-256-GCM 密文。
