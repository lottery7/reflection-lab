package org.example.classes;

import org.example.annotations.Generatable;

@Generatable({Rectangle.class, Triangle.class})
public interface Shape {
    double getArea();

    double getPerimeter();
}