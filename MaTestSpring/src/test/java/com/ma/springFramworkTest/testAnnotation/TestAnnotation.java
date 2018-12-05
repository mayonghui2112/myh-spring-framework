package com.ma.springFramworkTest.testAnnotation;

import com.ma.springFramworkTest.config.AppConfig;
import com.ma.springFramworkTest.dao.IndexDao;
import com.ma.springFramworkTest.postProcessors.MyBeanDefinitionRegistryPostProcessor;
import com.ma.springFramworkTest.postProcessors.MyBeanDefinitionRegistryPostProcessor1;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

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
