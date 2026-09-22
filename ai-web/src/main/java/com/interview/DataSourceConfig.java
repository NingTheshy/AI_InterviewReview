package com.interview;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 数据源装配配置。
 *
 * <p>显式使用 {@link DataSourceProperties} 解析环境变量和 profile 配置，再创建 Druid 数据源，
 * 使 Spring Boot 3 的属性绑定与项目既有 Druid 连接池保持一致。</p>
 */
@Configuration
public class DataSourceConfig {

    /** 提供可被环境变量覆盖的标准 Spring 数据源属性。 */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    /** 根据已解析的属性创建主数据源；连接池细节由 application.yml 的 Druid 配置补充。 */
    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        DruidDataSource ds = new DruidDataSource();
        ds.setUrl(properties.determineUrl());
        ds.setUsername(properties.determineUsername());
        ds.setPassword(properties.determinePassword());
        ds.setDriverClassName(properties.determineDriverClassName());
        return ds;
    }
}
