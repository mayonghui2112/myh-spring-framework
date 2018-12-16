package com.ma.springFramworkTest.config;

import com.ma.springFramworkTest.dao.IndexDao;
import com.ma.springFramworkTest.postProcessors.myImportSelected;
import org.springframework.context.annotation.*;

@Configuration
//@ComponentScan(basePackages = "com.ma",excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,pattern = "com.ma.springFramworkTest.postProcessors.myImportSelected"))
@ComponentScan(basePackages = "com.ma")
@Import(myImportSelected.class)
public class AppConfig {
	@Bean
	public IndexDao getIndexDao(){
		System.out.println("@bean indexDao");
		return new IndexDao();
	}
}

