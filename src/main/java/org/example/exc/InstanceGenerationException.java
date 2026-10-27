package org.example.exc;

public class InstanceGenerationException extends RuntimeException {
    public InstanceGenerationException(String msg) {
        super(msg);
    }

    public InstanceGenerationException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
