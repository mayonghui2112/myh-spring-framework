package com.ma.springFramworkTest.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
@Repository("IndexDao")
public class IndexDao {
	@Autowired
	public IndexDao1 indexDao1;

	public IndexDao() {
	}
	public IndexDao(String str){
		System.out.println("IndexDao"+str);
	}
	public void test() {
		System.out.println("IndexDao.test");
	}
}
