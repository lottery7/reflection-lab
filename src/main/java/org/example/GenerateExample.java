package org.example;


import org.example.classes.Example;
import org.example.generator.Generator;

import java.util.Arrays;

public class GenerateExample {
    public static void main(String[] args) {
        var gen = new Generator();
        try {
            var generated = gen.generateValueOfType(int[][][].class);
            System.out.println(Arrays.toString(generated));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}