package org.example.generator;

import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.example.annotations.Generatable;
import org.example.exc.InstanceGenerationException;

public class Generator {
    private static final int SEQUENCE_LOWER_BOUND = 1;
    private static final int SEQUENCE_UPPER_BOUND = 7;
    private static final int RECURSION_LIMIT = 3;
    private static final Type DEFAULT_TYPE_ARGUMENT = Integer.class;

    private final Random random;
    private final Map<Class<?>, Supplier<?>> baseSamplers;

    public Generator() {
        random = new Random();
        baseSamplers = createPrimitiveSamplers();
    }

    public Generator(int seed) {
        random = new Random(seed);
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
        samplers.put(String.class, () -> new String((char[]) generateArray(char.class, Collections.emptyMap())));
        return samplers;
    }

    public <T> T generateValueOfType(Class<T> clazz) {
        return wrap(clazz).cast(generate(clazz, new HashMap<>()));
    }

    private Object generate(Type type, Map<Class<?>, Integer> typeRepeats) {
        Class<?> clazz;
        Type[] typeArgs;
        switch (type) {
            case WildcardType wildcardType -> {
                return generate(wildcardType.getUpperBounds()[0], typeRepeats);
            }
            case ParameterizedType parameterizedType -> {
                clazz = (Class<?>) parameterizedType.getRawType();
                typeArgs = parameterizedType.getActualTypeArguments();
            }
            case Class<?> c -> {
                clazz = wrap(c);
                typeArgs = new Type[0];
            }
            default -> throw new InstanceGenerationException("Can't generate instance of type " + type);
        }

        if (baseSamplers.containsKey(clazz)) {
            return baseSamplers.get(clazz).get();
        }

        if (clazz.isArray()) {
            return generateArray(clazz.getComponentType(), typeRepeats);
        }

        if (clazz.isEnum()) {
            return generateEnum(clazz);
        }

        if (clazz == List.class) {
            return generateList(getTypeArgument(typeArgs, 0), typeRepeats);
        }

        if (clazz == Map.class) {
            return generateMap(getTypeArgument(typeArgs, 0), getTypeArgument(typeArgs, 1), typeRepeats);
        }

        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
            var impls = getGeneratableImpls(clazz);
            return generate(impls[random.nextInt(impls.length)], typeRepeats);
        }

        return generateObject(clazz, typeRepeats);
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> wrap(Class<T> clazz) {
        return (Class<T>) MethodType.methodType(clazz).wrap().returnType();
    }

    private static Type getTypeArgument(Type[] typeArgs, int index) {
        return index < typeArgs.length ? typeArgs[index] : DEFAULT_TYPE_ARGUMENT;
    }

    private Object generateEnum(Class<?> clazz) {
        var enumConstants = clazz.getEnumConstants();
        if (enumConstants.length == 0) {
            throw new InstanceGenerationException("Can't generate value of empty enum " + clazz.getName());
        }
        return enumConstants[random.nextInt(enumConstants.length)];
    }

    private Object generateObject(Class<?> clazz, Map<Class<?>, Integer> typeRepeats) {
        int repeats = typeRepeats.getOrDefault(clazz, 0);
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
        typeRepeats.put(clazz, repeats + 1);
        try {
            for (var parameter : constructorParameters) {
                try {
                    generatedParameters.add(generate(parameter.getParameterizedType(), typeRepeats));
                } catch (InstanceGenerationException e) {
                    throw new InstanceGenerationException(
                            "Can't generate parameter " + parameter.getName()
                                    + " of type " + parameter.getParameterizedType().getTypeName()
                                    + " for constructor " + randomConstructor.toGenericString(), e);
                }
            }
        } finally {
            typeRepeats.put(clazz, repeats);
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

    private static Class<?>[] getGeneratableImpls(Class<?> clazz) {
        var generatableAnnotation = clazz.getAnnotation(Generatable.class);
        if (generatableAnnotation == null || generatableAnnotation.value().length == 0) {
            throw new InstanceGenerationException("No @Generatable implementations for " + clazz.getName());
        }
        var generatableImpls = generatableAnnotation.value();
        for (var impl : generatableImpls) {
            if (impl == clazz) {
                throw new InstanceGenerationException(
                        "@Generatable on " + clazz.getName() + " references itself");
            }
            if (!clazz.isAssignableFrom(impl)) {
                throw new InstanceGenerationException(
                        "@Generatable implementation " + impl.getName() + " is not a subtype of " + clazz.getName()
                );
            }
        }
        return generatableImpls;
    }

    private List<Object> generateList(Type type, Map<Class<?>, Integer> typeRepeats) {
        int size = random.nextInt(SEQUENCE_LOWER_BOUND, SEQUENCE_UPPER_BOUND);
        return Stream.generate(() -> generate(type, typeRepeats))
                .limit(size)
                .collect(Collectors.toCollection(ArrayList<Object>::new));
    }

    private Object generateArray(Class<?> componentType, Map<Class<?>, Integer> typeRepeats) {
        var elements = generateList(componentType, typeRepeats);
        var array = Array.newInstance(componentType, elements.size());
        for (int i = 0; i < elements.size(); i++) {
            Array.set(array, i, elements.get(i));
        }
        return array;
    }

    private Map<Object, Object> generateMap(Type keyType, Type valueType, Map<Class<?>, Integer> typeRepeats) {
        Map<Object, Object> map = new HashMap<>();
        generateList(keyType, typeRepeats).forEach(key -> map.put(key, generate(valueType, typeRepeats)));
        return map;
    }
}
