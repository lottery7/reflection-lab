package org.example.generator;

import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import org.example.annotations.Generatable;
import org.example.exc.InstanceGenerationException;

public class Generator {
    private static final int SEQUENCE_LOWER_BOUND = 1;
    private static final int SEQUENCE_UPPER_BOUND = 5;
    private static final int RECURSION_LIMIT = 3;

    private final Random random;
    private final Map<Class<?>, Supplier<?>> baseSamplers;

    public Generator() {
        random = new Random();
        baseSamplers = createPrimitiveSamplers();
    }

    private Map<Class<?>, Supplier<?>> createPrimitiveSamplers() {
        Map<Class<?>, Supplier<?>> samplers = new HashMap<>();
        samplers.put(Void.class, () -> {
            throw new InstanceGenerationException("Can't generate object of void type");
        });
        samplers.put(Boolean.class, random::nextBoolean);
        samplers.put(Byte.class, () -> (byte) random.nextInt());
        samplers.put(Short.class, () -> (short) random.nextInt());
        samplers.put(Character.class, () -> (char) ('a' + random.nextInt(26)));
        samplers.put(Integer.class, random::nextInt);
        samplers.put(Long.class, random::nextLong);
        samplers.put(Float.class, random::nextFloat);
        samplers.put(Double.class, random::nextDouble);
        samplers.put(String.class, this::generateString);
        samplers.put(List.class, () -> this.generateList(Integer.class, new HashMap<>()));
        samplers.put(Map.class, () -> this.generateMap(Integer.class, Integer.class, new HashMap<>()));
        return samplers;
    }

    public <T> T generateValueOfType(Class<T> clazz) {
        return generateValueOfType(clazz, new HashMap<>());
    }

    @SuppressWarnings("unchecked")
    private <T> T generateValueOfType(Class<T> clazz, Map<Class<?>, Integer> path) {
        var wrappedClass = (Class<T>) MethodType.methodType(clazz).wrap().returnType();
        return wrappedClass.cast(generateByClass(wrappedClass, path));
    }

    private Object generateByType(Type type, Map<Class<?>, Integer> path) {
        if (type instanceof ParameterizedType parameterizedType) {
            var clazz = (Class<?>) parameterizedType.getRawType();
            var typeArgs = parameterizedType.getActualTypeArguments();
            if (clazz == List.class) {
                return generateList(typeArgs[0], path);
            }
            if (clazz == Map.class) {
                return generateMap(typeArgs[0], typeArgs[1], path);
            }
            return generateValueOfType(clazz, path);
        }

        if (type instanceof WildcardType w) {
            return generateByType(w.getUpperBounds()[0], path);
        }

        if (type instanceof Class<?> clazz) {
            return generateValueOfType(clazz, path);
        }

        throw new InstanceGenerationException("Can't generate instance of type " + type);
    }

    private Object generateByClass(Class<?> clazz, Map<Class<?>, Integer> path) {
        if (baseSamplers.containsKey(clazz)) {
            return baseSamplers.get(clazz).get();
        }

        if (clazz.isArray()) {
            return generateArray(clazz, path);
        }

        if (clazz.isEnum()) {
            var enumConstants = clazz.getEnumConstants();
            if (enumConstants.length == 0) {
                return null;
            }
            return enumConstants[random.nextInt(enumConstants.length)];
        }

        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
            var generatableImplementations = getGeneratableImplementations(clazz);
            var randomImplementation = generatableImplementations[random.nextInt(generatableImplementations.length)];
            return generateByClass(randomImplementation, path);
        }

        int repeats = path.getOrDefault(clazz, 0);
        if (repeats >= RECURSION_LIMIT) {
            return null;
        }

        var constructors = clazz.getConstructors();
        if (constructors.length == 0) {
            throw new InstanceGenerationException("Can't find public constructors for class " + clazz);
        }

        var randomConstructor = constructors[random.nextInt(constructors.length)];
        var constructorParameters = randomConstructor.getParameters();
        List<Object> generatedParameters = new ArrayList<>();
        path.put(clazz, repeats + 1);
        try {
            for (var parameter : constructorParameters) {
                generatedParameters.add(generateByType(parameter.getParameterizedType(), path));
            }
        } finally {
            path.put(clazz, repeats);
        }

        try {
            return randomConstructor.newInstance(generatedParameters.toArray());
        } catch (InvocationTargetException e) {
            throw new InstanceGenerationException("Constructor " + randomConstructor + " threw an exception",
                    e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new InstanceGenerationException("Can't invoke constructor " + randomConstructor, e);
        }
    }

    private static Class<?>[] getGeneratableImplementations(Class<?> clazz) {
        var generatableAnnotation = clazz.getAnnotation(Generatable.class);
        if (generatableAnnotation == null || generatableAnnotation.value().length == 0) {
            throw new InstanceGenerationException("No @Generatable implementations for " + clazz.getName());
        }
        var generatableImplementations = generatableAnnotation.value();
        for (var implementation : generatableImplementations) {
            if (!clazz.isAssignableFrom(implementation)) {
                throw new InstanceGenerationException("@Generatable implementation " + implementation.getName()
                        + " is not a subtype of " + clazz.getName());
            }
        }
        return generatableImplementations;
    }

    private int generateSequenceSize() {
        return random.nextInt(SEQUENCE_LOWER_BOUND, SEQUENCE_UPPER_BOUND + 1);
    }

    private String generateString() {
        int strLength = generateSequenceSize();
        var stringBuilder = new StringBuilder();
        var charSampler = baseSamplers.get(Character.class);
        for (int i = 0; i < strLength; i++) {
            stringBuilder.append(charSampler.get());
        }
        return stringBuilder.toString();
    }

    private Object generateArray(Class<?> clazz, Map<Class<?>, Integer> path) {
        if (!clazz.isArray()) {
            return generateValueOfType(clazz, path);
        }
        int size = generateSequenceSize();
        var array = Array.newInstance(clazz.getComponentType(), size);
        for (int i = 0; i < size; i++) {
            Array.set(array, i, generateArray(clazz.getComponentType(), path));
        }
        return array;
    }

    private List<?> generateList(Type type, Map<Class<?>, Integer> path) {
        int size = generateSequenceSize();
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(generateByType(type, path));
        }
        return list;
    }

    private Map<Object, Object> generateMap(Type keyType, Type valueType, Map<Class<?>, Integer> path) {
        int size = generateSequenceSize();
        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            map.put(generateByType(keyType, path), generateByType(valueType, path));
        }
        return map;
    }
}
