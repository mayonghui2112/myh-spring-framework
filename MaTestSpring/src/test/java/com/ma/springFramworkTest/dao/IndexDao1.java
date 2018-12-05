package com.ma.springFramworkTest.dao;

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
