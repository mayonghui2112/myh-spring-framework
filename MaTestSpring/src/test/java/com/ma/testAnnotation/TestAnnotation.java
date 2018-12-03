package com.ma.testAnnotation;

import com.ma.config.AppConfig;
import com.ma.dao.IndexDao;
import com.ma.postProcessors.MyBeanDefinitionRegistryPostProcessor;
import com.ma.postProcessors.MyBeanDefinitionRegistryPostProcessor1;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

public class TestAnnotation {
	public static void main(String[] args) {
		//初始化spring上下文环境
		AnnotationConfigApplicationContext annotationConfigApplicationContext=new AnnotationConfigApplicationContext();
		annotationConfigApplicationContext.register(AppConfig.class);
		annotationConfigApplicationContext.register(IndexDao.class);
		annotationConfigApplicationContext.addBeanFactoryPostProcessor(new MyBeanDefinitionRegistryPostProcessor());
		annotationConfigApplicationContext.addBeanFactoryPostProcessor(new MyBeanDefinitionRegistryPostProcessor1());
		annotationConfigApplicationContext.refresh();
		IndexDao indexDao = (IndexDao)annotationConfigApplicationContext.getBean("IndexDao");
		indexDao.test();
		System.out.println(indexDao.getClass());

	}
}
