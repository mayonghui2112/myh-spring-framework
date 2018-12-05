package com.ma.springFramworkTest.config;

import com.ma.springFramworkTest.dao.IndexDao;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
@Configuration
@ComponentScan("com.ma")
@Import(IndexDao.class)
public class AppConfig {
}
