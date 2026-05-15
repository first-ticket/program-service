package com.firstticket.programservice.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = {
    "com.firstticket.programservice",
    "com.firstticket.common.messaging"
})
@EnableJpaRepositories(basePackages = {
    "com.firstticket.programservice",
    "com.firstticket.common.messaging"
})
public class JpaConfig {}
