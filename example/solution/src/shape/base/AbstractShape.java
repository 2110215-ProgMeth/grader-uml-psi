package shape.base;

import shape.core.Shape;

public abstract class AbstractShape implements Shape {
    private String name;

    public AbstractShape(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}