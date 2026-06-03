package com.interview.common.constant.config;

/**
 * 跨域配置（已禁用）
 * <p>
 * CORS 已在 SecurityConfig 中统一配置，此处不再重复注册 Bean，
 * 避免响应头出现重复的 Access-Control-Allow-Origin 导致浏览器拒绝请求。
 * </p>
 */
public class CorsConfig {
    // CORS 配置已移至 SecurityConfig.corsConfigurationSource()
}
