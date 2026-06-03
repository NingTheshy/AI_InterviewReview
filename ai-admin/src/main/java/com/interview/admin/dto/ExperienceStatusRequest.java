package com.interview.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 面经状态更新请求
 * <p>
 * 管理员下架或恢复面经时使用的请求体。
 * </p>
 */
@Data
public class ExperienceStatusRequest {

    /**
     * 公开状态：0=下架（私有），1=上架（公开）
     */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态值无效，0=下架, 1=上架")
    @Max(value = 1, message = "状态值无效，0=下架, 1=上架")
    private Integer isPublic;
}
