-- FunASR 句子级时间戳、说话人角色与问题笔记唯一范围升级

ALTER TABLE `interview`
    ADD COLUMN `transcript_segments` LONGTEXT NULL
        COMMENT '句子级转写结果(JSON，含speakerId、speakerRole和毫秒时间戳)'
        AFTER `transcript_text`;

UPDATE `interview_note`
SET `question_id` = 0
WHERE `question_id` IS NULL;

ALTER TABLE `interview_note`
    DROP INDEX `uk_interview_user`,
    MODIFY COLUMN `question_id` BIGINT NOT NULL DEFAULT 0
        COMMENT '问题ID(问题笔记时为真实ID，面试笔记为0)',
    ADD UNIQUE INDEX `uk_note_scope` (`interview_id`, `user_id`, `note_type`, `question_id`);
