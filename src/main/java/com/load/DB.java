package com.load;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.util.Assert;

@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class DB {

    @Value("${${service.name}.host}")
    String dbHost;

    String serviceName;

    DB() {
        log.info("{} - DB Constructor ",System.getProperty("service.name"));
        this.serviceName = System.getProperty("service.name");
    }

    @Autowired
    @Qualifier("dbTemplate")
    String dbTemplate;

    @Bean("${service.name}_dbConnection")
    public String dbConnectionAtBean(@Value("${service.name}") String serviceName) {
        log.info("{} - @Bean called", serviceName);
        return serviceName + "_dbConnection";
    }

    @PostConstruct
    public void init() {
        Assert.notNull(dbTemplate, "DB Template is null");
        Assert.notNull(dbHost, "DB host is null");
        Assert.isTrue(dbHost.equals(serviceName + "_localhost"), "@value value is not matchec");
        log.info("{} - db service initalized with host - {}", serviceName, dbHost);
        //System.out.println(dbHost.equals("git_localhost"));
    }
}
