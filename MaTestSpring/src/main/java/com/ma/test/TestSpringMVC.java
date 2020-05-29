package com.ma.test;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

public class TestSpringMVC {
	public static void main(String[] args) {
		ApplicationContext context = new ClassPathXmlApplicationContext("services.xml", "daos.xml");
//		new AnnotationConfigApplicationContext("cn.net.mayh");
		Object petStoreServiceImpl = context.getBean("petStoreServiceImpl");
	}
}
