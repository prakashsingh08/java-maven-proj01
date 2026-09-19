package com.prakash.learning;

public class App {

    public int add(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) {
        App app = new App();
        System.out.println("Hello, Maven! welcome scenario A");
        System.out.println("2 + 3 = " + app.add(2, 3));
    }
}
