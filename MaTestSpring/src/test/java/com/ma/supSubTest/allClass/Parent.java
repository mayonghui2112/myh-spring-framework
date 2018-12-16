package com.ma.supSubTest.allClass;

public class Parent {
	private String str1;
	public void test(){
		System.out.println("++++++++++++++parent");
		this.test1();
	}
	public void test1(){
		System.out.println("++++++++++++++parent1");
	}
}
