package com.load;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class SFTP {

    @Value("${${service.name}.host}")
    String sftpHost;

    String serviceName;

    SFTP() {
        log.info("{} - sftp constructor" , System.getProperty("service.name"));
        this.serviceName = System.getProperty("service.name");
    }

    @Autowired
    @Qualifier("dbTemplate")
    String dbTemplate;

    @Bean("${service.name}_sftpTemplate")
    public String sftpConnectionAtBean(@Value("${service.name}") String serviceName) {
        log.info("{} - @Bean called", serviceName);
        return serviceName + "_sftpTemplate";
    }


    @PostConstruct
    public void init() {
        Assert.notNull(dbTemplate, "DB Template is null");
        Assert.notNull(sftpHost, "SFTP host is null");
        Assert.isTrue(sftpHost.equals(serviceName + "_localhost"), "@value value is not matchec");
        log.info("{} - sftp service initalized with host - {}", serviceName, sftpHost);
    }
}
