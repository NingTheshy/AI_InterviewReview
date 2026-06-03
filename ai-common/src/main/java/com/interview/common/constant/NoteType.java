package com.interview.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 笔记类型枚举
 * <p>
 * 区分面试笔记和问题笔记，用于面经分享模块的笔记分类。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum NoteType {

    INTERVIEW("INTERVIEW", "面试笔记"),
    QUESTION("QUESTION", "问题笔记");

    private final String code;
    private final String description;
}
