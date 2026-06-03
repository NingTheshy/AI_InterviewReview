package com.interview.common.result;

import lombok.Data;

import java.util.List;

/**
 * 分页查询结果封装
 * <p>
 * 包含分页数据列表、总记录数、当前页码和每页大小。
 * </p>
 *
 * @param <T> 记录数据类型
 */
@Data
public class PageResult<T> {

    private List<T> records;
    private long total;
    private int page;
    private int size;

    public PageResult(List<T> records, long total, int page, int size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        return new PageResult<>(records, total, page, size);
    }
}
