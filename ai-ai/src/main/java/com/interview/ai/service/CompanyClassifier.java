package com.interview.ai.service;

import com.interview.common.constant.CompanyTier;

/**
 * 公司分级服务接口
 * <p>
 * 根据公司名称、行业和岗位 JD 判断企业档次。
 * 采用三级策略：硬编码名单 → AI 缓存 → LLM 调用。
 * </p>
 */
public interface CompanyClassifier {

    /**
     * 分级公司
     *
     * @param companyName 公司名称
     * @param industry    行业
     * @param jdText      岗位 JD
     * @return 公司档次
     */
    CompanyTier classify(String companyName, String industry, String jdText);
}
