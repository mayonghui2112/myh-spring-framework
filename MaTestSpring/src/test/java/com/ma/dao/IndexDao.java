package com.ma.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation5.MyRepository;
import org.springframework.stereotype.Component;
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
