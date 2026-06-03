package com.interview.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 公司档次枚举
 * <p>
 * 将企业按规模和影响力分为五个档次，用于面试评分时的难度校准。
 * 超大厂和大厂的面试评分标准相对更严格。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum CompanyTier {

    TIER_1(1, "超大厂",
            "全球/全国龙头企业。判定标准：市值/营收行业前3、全球品牌知名度、技术影响力（如开源项目、专利数）、员工规模>1万、行业标准制定者"),
    TIER_2(2, "大厂",
            "国内知名大型企业。判定标准：上市公司或估值>10亿美元、行业前10、较强品牌影响力、员工规模>3000、技术博客/开源活跃"),
    TIER_3(3, "中厂",
            "区域知名中型企业。判定标准：B轮以上融资或盈利稳定、细分领域头部、员工500-3000、有一定行业影响力"),
    TIER_4(4, "小厂",
            "小型企业。判定标准：A轮及以下融资、员工50-500、业务模式验证中、区域性影响力"),
    TIER_5(5, "初创",
            "初创企业。判定标准：天使轮/种子轮、员工<50、产品MVP阶段、创始人背景");

    private final int code;
    private final String name;
    private final String description;

    public static CompanyTier fromCode(int code) {
        for (CompanyTier tier : values()) {
            if (tier.code == code) {
                return tier;
            }
        }
        return TIER_3;
    }
}
