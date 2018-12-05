package com.ma.springFramworkTest.dao;

import org.springframework.stereotype.Repository;
@Repository("IndexDao")
public class IndexDao {
	public IndexDao(){
		System.out.println("IndexDao");
	}
	public void test() {
		System.out.println("IndexDao.test");
	}
}
