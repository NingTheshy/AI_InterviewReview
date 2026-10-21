package com.interview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 面试复盘服务的 Spring Boot 启动入口。
 *
 * <p>数据源由 {@link DataSourceConfig} 显式装配，因此排除 Spring Boot 默认数据源自动配置；
 * 同时启用异步执行器以处理音频转写和评分等耗时任务。</p>
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableAsync
@EnableScheduling
@MapperScan("com.interview.**.mapper")
public class AiInterviewApplication {

    /** 启动 Web、认证、面试业务和 AI 模块的统一应用上下文。 */
    public static void main(String[] args) {
        SpringApplication.run(AiInterviewApplication.class, args);
    }
}
