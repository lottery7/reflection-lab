package org.example.exc;

public class VoidInstantiationException extends RuntimeException {
    public VoidInstantiationException() {
        super("Can't instantiate void type");
    }
}
