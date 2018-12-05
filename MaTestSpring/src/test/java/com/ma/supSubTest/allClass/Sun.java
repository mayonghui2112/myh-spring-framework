package com.ma.supSubTest.allClass;

public class Sun extends Parent {
	public void testSun(){
		System.out.println("++++++++++++testSun");
		super.test();
	}

	@Override
	public void test() {
		System.out.println("++++++++++++++parent1");
	}

	@Override
	public void test1() {
		System.out.println("++++++++++++++parent1");
	}

	public static void main(String[] args) {
		new Sun().testSun();
	}
}
