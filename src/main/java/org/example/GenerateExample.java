package org.example;


import java.util.ArrayList;
import java.util.List;

import org.example.generator.Generator;

public class GenerateExample {
    public static void main(String[] args) {
        var generator = new Generator();
        try {
            var generated = generator.generateValueOfType(ArrayList.class);
            System.out.println(generated);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}