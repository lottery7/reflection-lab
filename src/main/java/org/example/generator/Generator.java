package org.example.generator;

import org.example.exc.VoidInstantiationException;

import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

public class Generator {
    private static final int SEQUENCE_LOWER_BOUND = 1;
    private static final int SEQUENCE_UPPER_BOUND = 5;

    private final Random random;
    private final Map<Class<?>, Supplier<?>> baseSamplers;

    public Generator() {
        random = new Random();
        baseSamplers = createPrimitiveSamplers();
    }

    private Map<Class<?>, Supplier<?>> createPrimitiveSamplers() {
        return Map.of(
                Void.class, () -> {
                    throw new VoidInstantiationException();
                },
                Boolean.class, random::nextBoolean,
                Byte.class, () -> (byte) random.nextInt(),
                Short.class, () -> (short) random.nextInt(),
                Character.class, () -> (char) ('a' + random.nextInt(26)),
                Integer.class, random::nextInt,
                Long.class, random::nextLong,
                Float.class, random::nextFloat,
                Double.class, random::nextDouble,
                String.class, this::generateRandomString
        );
    }

    public <T> T generateValueOfType(Class<T> clazz) throws InvocationTargetException, InstantiationException, IllegalAccessException, VoidInstantiationException {
        return clazz.cast(generate(clazz));
    }

    private int generateSequenceSize() {
        return random.nextInt(SEQUENCE_LOWER_BOUND, SEQUENCE_UPPER_BOUND + 1);
    }

    @SuppressWarnings("unchecked")
    private Object generate(Class<?> clazz) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        if (clazz.isArray()) {
            return clazz.cast(generateArray(clazz));
        }
        clazz = MethodType.methodType(clazz).wrap().returnType();
        var sampler = baseSamplers.get(clazz);
        if (sampler != null) {
            return clazz.cast(sampler.get());
        }

        Constructor<?>[] constructors = clazz.getDeclaredConstructors();

        int randomConstructorIndex = new Random().nextInt(constructors.length);
        Constructor<?> randomConstructor = constructors[randomConstructorIndex];
        return (T) randomConstructor.newInstance(111);
    }

    private String generateRandomString() {
        int strLength = generateSequenceSize();
        var stringBuilder = new StringBuilder();
        var charSampler = baseSamplers.get(Character.class);
        for (int i = 0; i < strLength; i++) {
            stringBuilder.append(charSampler.get());
        }
        return stringBuilder.toString();
    }

    private Object generateArray(Class<?> clazz) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        if (!clazz.isArray()) {
            return generateValueOfType(clazz);
        }
        int size = generateSequenceSize();
        var array = Array.newInstance(clazz.getComponentType(), size);
        for (int i = 0; i < size; i++) {
            Array.set(array, i, generateArray(clazz.getComponentType()));
        }
        return array;
    }
}
