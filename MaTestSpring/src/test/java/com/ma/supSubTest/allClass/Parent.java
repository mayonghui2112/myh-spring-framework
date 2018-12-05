package com.ma.supSubTest.allClass;

public class Parent {
	public void test(){
		System.out.println("++++++++++++++parent");
		this.test1();
	}
	public void test1(){
		System.out.println("++++++++++++++parent1");
	}
}
