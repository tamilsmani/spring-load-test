package com.load;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DynamicStringMakerBeanDefinitionRegistrarConfiguration {

  private ApplicationContext applicationContext;

  @Bean("dbTemplate")
  public String dbTemplate() {
    return "dbTemplate";
  }

  @Bean("dynamicBean")
  public BeanDefinitionRegistryPostProcessor beanDefinitionRegistryPostProcessor() {
    return new BeanDefinitionRegistryPostProcessor() {
      @Override
      public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        AnnotatedGenericBeanDefinition beanDefinition = new AnnotatedGenericBeanDefinition(SftpAutoConfiguration.class);
        registry.registerBeanDefinition("sftpAutoConfiguration", beanDefinition);
      }

      @Override
      public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // Add necessary post-processors
        AutowiredAnnotationBeanPostProcessor autowiredProcessor = new AutowiredAnnotationBeanPostProcessor();
        autowiredProcessor.setBeanFactory(beanFactory);
        beanFactory.addBeanPostProcessor(autowiredProcessor);

       // ((GenericApplicationContext) applicationContext).refresh();

        /*ValueAnnotationBeanPostProcessor valueProcessor = new ValueAnnotationBeanPostProcessor();
        valueProcessor.setBeanFactory(beanFactory);
        beanFactory.addBeanPostProcessor(valueProcessor);*/
      }
    };
  }
}