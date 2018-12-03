package com.ma.dao;

import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Repository;

@Repository("IndexDao1")
public class IndexDao1 {
	public IndexDao1(){
		System.out.println("IndexDao");
	}
	public void test() {
		System.out.println("IndexDao.test");
	}
}
