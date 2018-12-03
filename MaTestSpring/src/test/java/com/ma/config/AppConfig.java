package com.ma.config;

import com.ma.dao.IndexDao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
@Configuration
@ComponentScan("com.ma")
@Import(IndexDao.class)
public class AppConfig {
}
