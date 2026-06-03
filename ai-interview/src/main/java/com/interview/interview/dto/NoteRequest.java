package com.interview.interview.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 笔记请求 DTO
 * <p>
 * 用于保存面试笔记或问题笔记的请求体。
 * 笔记内容支持富文本格式，采用覆盖式更新策略。
 * </p>
 */
@Data
public class NoteRequest {

    /** 笔记内容，支持富文本格式（标题、加粗、列表等），最大 10000 字符 */
    @Size(max = 10000, message = "笔记内容不能超过 10000 个字符")
    private String content;
}
