-- 仅对已存在的数据库执行一次；全新数据库直接使用 schema.sql。
ALTER TABLE `interview_question`
    ADD COLUMN `rubric_detail` LONGTEXT NULL COMMENT '评分量表与逐要点比对结果(JSON)' AFTER `reference_answer`;
