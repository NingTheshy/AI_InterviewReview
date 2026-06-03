package com.interview;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

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
