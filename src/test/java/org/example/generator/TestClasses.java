package org.example.generator;

import java.util.List;
import java.util.Map;

import org.example.annotations.Generatable;

public final class TestClasses {
    public enum Color {
        RED, GREEN, BLUE
    }

    public enum Empty {
    }

    public record Point(double x, double y, String label, List<Color> colors) {
    }

    public static class Inventory {
        public final Map<String, Color> stock;

        public Inventory(Map<String, Color> stock) {
            this.stock = stock;
        }
    }

    public static class Person {
        public final String name;
        public final int age;
        public final Color color;
        public final List<String> tags;

        public Person(String name, int age, Color color, List<String> tags) {
            this.name = name;
            this.age = age;
            this.color = color;
            this.tags = tags;
        }
    }

    @Generatable({Cat.class, Dog.class})
    public abstract static class Animal {
        public final String name;

        protected Animal(String name) {
            this.name = name;
        }
    }

    public static class Cat extends Animal {
        public Cat(String name) {
            super(name);
        }
    }

    public static class Dog extends Animal {
        public Dog(String name) {
            super(name);
        }
    }

    @Generatable(Square.class)
    public interface Figure {
    }

    public static class Square implements Figure {
        public final double side;

        public Square(double side) {
            this.side = side;
        }
    }

    @Generatable(Car.class)
    public interface Vehicle {
    }

    @Generatable(Sedan.class)
    public interface Car extends Vehicle {
    }

    public static class Sedan implements Car {
    }

    public static class A {
        public final B b;

        public A(B b) {
            this.b = b;
        }
    }

    public static class B {
        public final C c;

        public B(C c) {
            this.c = c;
        }
    }

    public static class C {
        public final D d;

        public C(D d) {
            this.d = d;
        }
    }

    public static class D {
        public final int value;

        public D(int value) {
            this.value = value;
        }
    }

    public static class X {
        public final Y y;

        public X(Y y) {
            this.y = y;
        }
    }

    public static class Y {
        public final X x;

        public Y(X x) {
            this.x = x;
        }
    }

    public interface Unannotated {
    }

    @Generatable({})
    public interface EmptyImplementations {
    }

    @Generatable(UnannotatedAbstract.class)
    public abstract static class AbstractRoot {
    }

    public abstract static class UnannotatedAbstract extends AbstractRoot {
    }

    @Generatable(String.class)
    public interface WrongImplementation {
    }

    @Generatable(SelfReferencing.class)
    public interface SelfReferencing {
    }

    public static class NoPublicConstructor {
        private NoPublicConstructor() {
        }
    }

    public static class Failing {
        public Failing() {
            throw new IllegalStateException("Failing constructor");
        }
    }

    public static class Box<T> {
        public final T value;

        public Box(T value) {
            this.value = value;
        }
    }
}
