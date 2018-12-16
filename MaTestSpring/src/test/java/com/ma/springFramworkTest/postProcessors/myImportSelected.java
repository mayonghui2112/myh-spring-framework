package com.ma.springFramworkTest.postProcessors;

import com.ma.springFramworkTest.dao.IndexDao;
import com.ma.springFramworkTest.dao.IndexDao1;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.stereotype.Component;

@Component("myImportSelected")
public class myImportSelected implements ImportSelector {
	public void test(){
		System.out.println("myImportSelected");
	}

	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		String imports[]=new String[2];
		imports[0]=IndexDao.class.getName();
		imports[1]=IndexDao1.class.getName();
		return new String[0];
	}
}
