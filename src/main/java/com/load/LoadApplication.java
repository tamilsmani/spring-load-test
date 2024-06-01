package com.load;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@Slf4j
public class LoadApplication {

	public static void main(String[] args) {
	    ConfigurableApplicationContext context =  SpringApplication.run(LoadApplication.class, args);

/*		String[] beans = context.getBeanDefinitionNames();
		for (String bean : beans) {
			System.out.println(bean);
		}*/

		log.info("@Bean Git-DB -{}",context.getBean("git_dbConnection", DB.class) );
		log.info("@Bean Git-SFTP -{}",context.getBean("git_sftpTemplate", SFTP.class) );

		log.info("@Bean Google-DB -{}",context.getBean("google_dbConnection", DB.class) );
		log.info("@Bean Google-SFTP -{}",context.getBean("google_sftpTemplate", SFTP.class) );
	}

	public static void registerBean(ApplicationContext context, String beanName, Class<?> beanClass) {
		BeanDefinitionRegistry registry = (BeanDefinitionRegistry) context.getAutowireCapableBeanFactory();
		GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
		beanDefinition.setBeanClass(beanClass);
		registry.registerBeanDefinition(beanName, beanDefinition);
	}
}
