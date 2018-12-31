package com.ma.springFramworkTest.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository("IndexDao1")
public class IndexDao1 {
	@Autowired
	public IndexDao indexDao;
	public IndexDao1(){
		System.out.println("IndexDao");
	}
	public void test() {
		System.out.println("IndexDao.test");
	}
}
