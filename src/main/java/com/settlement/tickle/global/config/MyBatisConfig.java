package com.settlement.tickle.global.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.settlement.tickle.domain.settlement.batch.mapper")
public class MyBatisConfig {
}
