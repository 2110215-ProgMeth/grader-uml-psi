package shape.impl.round;

import shape.base.AbstractShape;

public class Oval extends AbstractShape {
    private double majorAxis;
    private double minorAxis;

    public Oval(String name, double majorAxis, double minorAxis) {
        super(name);
        this.majorAxis = majorAxis;
        this.minorAxis = minorAxis;
    }

    public double getMajorAxis() {
        return majorAxis;
    }

    public void setMajorAxis(double majorAxis) {
        this.majorAxis = majorAxis;
    }

    public double getMinorAxis() {
        return minorAxis;
    }

    public void setMinorAxis(double minorAxis) {
        this.minorAxis = minorAxis;
    }

    public double calculateArea() {
        return Math.PI * majorAxis * minorAxis;
    }

    public double calculatePerimeter() {
        double a = majorAxis;
        double b = minorAxis;
        return Math.PI * (3*(a + b) - Math.sqrt((3*a + b)*(a + 3*b)));
    }
}