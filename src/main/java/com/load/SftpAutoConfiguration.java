package com.load;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.List;

@Slf4j
//@Import({DB.class, SFTP.class})
public class SftpAutoConfiguration {

    @Autowired
    ApplicationContext context;

    @Value("#{'${sftp.services}'.split(',')}")
    private List<String> sftpServices;

    SftpAutoConfiguration() {
        System.out.println("SftpAutoConfiguration-");
    }

    @PostConstruct
    public void init() {

        System.out.println("init");

        sftpServices.forEach(service -> {
            log.info("-----------------------------------------");
            log.info( "Dynamic initalization of 'scope=prototype' bean for service - {}", service);

            System.setProperty("service.name", service);
            //LoadApplication.registerBean(context,service +"DB", DB.class);
            //LoadApplication.registerBean(context,service +"SFTP", SFTP.class);

            context.getBean(service +"DB", DB.class);
            context.getBean(service +"SFTP",SFTP.class);


        });

    }

    //@ConditionalOnProperty(name = "sftp.db.store.enabled", havingValue = "true", matchIfMissing = false)
    class SFTPJdbcConfig {

        SFTPJdbcConfig() {
            log.info("Inside SFTPJdbcConfig");
        }

        @Bean("dbTemplate")
        public String dbTemplate() {
            return "dbTemplate";
        }
    }

}

