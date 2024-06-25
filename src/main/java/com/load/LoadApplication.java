package com.load;

import com.load.sftp.EmbeddedSftpServer;
import com.load.sftp.SFTPInMemoryConfig;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

import java.util.concurrent.TimeUnit;

@SpringBootApplication
@Slf4j
@Import(value = {EmbeddedSftpServer.class, SFTPInMemoryConfig.class})
public class LoadApplication {

	@SneakyThrows
	public static void main(String[] args) {
		System.setProperty("sftp.serviceName", "service1");
		ConfigurableApplicationContext context = SpringApplication.run(LoadApplication.class, args);
		SFTPInMemoryConfig sftpInMemoryConfig = context.getBean(SFTPInMemoryConfig.class);

		EmbeddedSftpServer embeddedSftpServer = context.getBean(EmbeddedSftpServer.class);

		/*log.info("Stopping SFTP service....in 5 seconds.");
		TimeUnit.SECONDS.sleep(5);
		sftpInMemoryConfig.stopSftpPolling();

		log.info("Start SFTP service....in 5 seconds.");
		TimeUnit.SECONDS.sleep(5);
		sftpInMemoryConfig.startSftpPolling();*/

		log.info("publishing files in 2 seconds .....");
		TimeUnit.SECONDS.sleep(2);

		embeddedSftpServer.copyFileToSFTPInboundDirectory("dummy.txt");
		//TimeUnit.SECONDS.sleep(10);

		//TimeUnit.SECONDS.sleep(5);
		//embeddedSftpServer.copyFileToSFTPInboundDirectory("dummy.txt");

		TimeUnit.SECONDS.sleep(10);
		log.info("publishing again in 2 seconds .....");

		TimeUnit.SECONDS.sleep(2);
		embeddedSftpServer.copyFileToSFTPInboundDirectory("dummy.txt");

//		TimeUnit.SECONDS.sleep(10);
	}

}

/*		String[] beans = context.getBeanDefinitionNames();
		for (String bean : beans) {
			System.out.println(bean);
		}*/

		/*System.out.println(context.getBean("custommsg", Message1.class));
		System.out.println(context.getBean("custommsg", Message1.class));

		System.setProperty("my.data","1");
		context.getBean(MyData.class);
		context.getBean(MyData.class);

		String[] beans = context.getBeanDefinitionNames();

		for (String bean : beans) {
			//System.out.println(bean);
		}
		//System.out.println(context.getBean("1-mydata", MyData.class));
		System.setProperty("my.data","2");

		//System.out.println(context.getBean("2-mydata", MyData.class));


		*//**//*log.info("@Bean Git-DB -{}",context.getBean("git_dbConnection", DB.class) );
		log.info("@Bean Git-SFTP -{}",context.getBean("git_sftpTemplate", SFTP.class) );

		log.info("@Bean Google-DB -{}",context.getBean("google_dbConnection", DB.class) );
		log.info("@Bean Google-SFTP -{}",context.getBean("google_sftpTemplate", SFTP.class) );*//**//*
	}
*//*
	public static void registerBean(ApplicationContext context, String beanName, Class<?> beanClass) {
		BeanDefinitionRegistry registry = (BeanDefinitionRegistry) context.getAutowireCapableBeanFactory();
		GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
		beanDefinition.setBeanClass(beanClass);
		registry.registerBeanDefinition(beanName, beanDefinition);
	}

	@Bean("#{systemProperties['my.data']}-mydata")
	// 	//@Value("${#{systemProperties['sftp.serviceName']}.sftp.port}")
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public MyData getData() {
		return new MyData();
	}*/


