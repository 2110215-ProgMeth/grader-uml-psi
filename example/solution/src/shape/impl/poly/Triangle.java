package shape.impl.poly;

import shape.base.AbstractShape;

public class Triangle extends AbstractShape {
    private double a, b, c;

    public Triangle(String name, double a, double b, double c) {
        super(name);
        this.a = a;
        this.b = b;
        this.c = c;
    }

    public double getA() {
        return a;
    }

    public void setA(double a) {
        this.a = a;
    }

    public double getB() {
        return b;
    }

    public void setB(double b) {
        this.b = b;
    }

    public double getC() {
        return c;
    }

    public void setC(double c) {
        this.c = c;
    }

    public double calculatePerimeter() {
        return a + b + c;
    }

    public double calculateArea() {
        double s = calculatePerimeter() / 2;
        return Math.sqrt(s * (s - a) * (s - b) * (s - c));
    }
}