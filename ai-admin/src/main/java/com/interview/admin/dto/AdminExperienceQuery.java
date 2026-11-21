package com.interview.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/** 管理员已发布面经列表筛选条件。 */
@Data
public class AdminExperienceQuery {
    private int page = 1;
    private int size = 10;
    private String title;
    private String author;
    private String company;
    private String position;
    private String moderation;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime to;
}
