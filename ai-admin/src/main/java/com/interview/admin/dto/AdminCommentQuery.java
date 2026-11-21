package com.interview.admin.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** 管理端评论列表的组合筛选条件。 */
@Data
public class AdminCommentQuery {
    private int page = 1;
    private int size = 10;
    private String keyword;
    private String author;
    private String experience;
    private Long userId;
    /** 0 正常，1 已删除；默认只查看正常评论。 */
    private Integer status = 0;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
