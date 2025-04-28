package com.newrelic.labs.sample;

public class SimpleApp {
    public static void main(String[] args) {
        SimpleApp app = new SimpleApp();
        app.sayHello();

        double num1 = 10.0;
        double num2 = 20.9;

        app.performCalculations(num1, num2);
    }

    public void sayHello() {
        System.out.println("Hello from SimpleApp!");
    }

    public void performCalculations(double a, double b) {
        Calculator calc = new Calculator();

        System.out.println("Performing calculations with numbers: " + a + " and " + b);
        System.out.println("Addition: " + calc.add(a, b));
        System.out.println("Subtraction: " + calc.subtract(a, b));
        System.out.println("Multiplication: " + calc.multiply(a, b));

        try {
            System.out.println("Division: " + calc.divide(a, b));
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}