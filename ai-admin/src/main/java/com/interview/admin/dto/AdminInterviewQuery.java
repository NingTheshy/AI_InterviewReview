package com.interview.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/** 管理员面试列表筛选条件。 */
@Data
public class AdminInterviewQuery {
    private int page = 1;
    private int size = 10;
    private String title;
    private String userKeyword;
    private String company;
    private String position;
    private Integer status;
    private Integer deleted;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime to;
}
