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
    private final Map<Class<?>, Supplier<?>> primitiveSamplers;
    private final Map<Class<?>, List<Class<?>>> generatableOverrides;

    public Generator() {
        this(Map.of());
    }

    public Generator(int seed) {
        this(seed, Map.of());
    }

    public Generator(Map<Class<?>, List<Class<?>>> generatableOverrides) {
        this(new Random(), generatableOverrides);
    }

    public Generator(int seed, Map<Class<?>, List<Class<?>>> generatableOverrides) {
        this(new Random(seed), generatableOverrides);
    }

    private Generator(Random random, Map<Class<?>, List<Class<?>>> generatableOverrides) {
        this.random = random;
        this.generatableOverrides = generatableOverrides.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        primitiveSamplers = createPrimitiveSamplers();
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
        return samplers;
    }

    public <T> T generateValueOfType(Class<T> clazz) {
        var context = new GenerationContext(RECURSION_LIMIT);
        return wrap(clazz).cast(generate(clazz, context));
    }

    private Object generate(Type type, GenerationContext context) {
        Class<?> clazz;
        Type[] typeArgs;
        switch (type) {
            case WildcardType wildcardType -> {
                return generate(wildcardType.getUpperBounds()[0], context);
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

        if (primitiveSamplers.containsKey(clazz)) {
            return primitiveSamplers.get(clazz).get();
        }

        if (clazz == String.class) {
            return new String((char[]) generateArray(char.class, context));
        }

        if (clazz.isArray()) {
            return generateArray(clazz.getComponentType(), context);
        }

        if (clazz.isEnum()) {
            return generateEnum(clazz);
        }

        if (clazz == List.class) {
            return generateList(getTypeArgument(typeArgs, 0), context);
        }

        if (clazz == Map.class) {
            return generateMap(getTypeArgument(typeArgs, 0), getTypeArgument(typeArgs, 1), context);
        }

        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
            var impls = getGeneratableImpls(clazz);
            return generate(impls[random.nextInt(impls.length)], context);
        }

        return generateObject(clazz, context);
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

    private Object generateObject(Class<?> clazz, GenerationContext context) {
        if (!context.tryEnter(clazz)) {
            return null;
        }

        try {
            var constructors = clazz.getConstructors();
            if (constructors.length == 0) {
                throw new InstanceGenerationException("Can't find public constructors for class " + clazz.getName());
            }

            var randomConstructor = constructors[random.nextInt(constructors.length)];
            var constructorParameters = randomConstructor.getParameters();
            List<Object> generatedParameters = new ArrayList<>();
            for (var parameter : constructorParameters) {
                try {
                    generatedParameters.add(generate(parameter.getParameterizedType(), context));
                } catch (InstanceGenerationException e) {
                    throw new InstanceGenerationException("Can't generate parameter " + parameter.getName() + " of " +
                            "type " + parameter.getParameterizedType().getTypeName() + " for constructor " + randomConstructor.toGenericString(), e);
                }
            }

            try {
                return randomConstructor.newInstance(generatedParameters.toArray());
            } catch (InvocationTargetException e) {
                throw new InstanceGenerationException("Constructor " + randomConstructor + " threw an exception",
                        e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new InstanceGenerationException("Can't invoke constructor " + randomConstructor, e);
            }
        } finally {
            context.leave(clazz);
        }
    }

    private Class<?>[] getGeneratableImpls(Class<?> clazz) {
        List<Class<?>> overrides = generatableOverrides.get(clazz);
        Class<?>[] generatableImpls;
        if (overrides != null) {
            generatableImpls = overrides.toArray(Class<?>[]::new);
        } else {
            var generatableAnnotation = clazz.getAnnotation(Generatable.class);
            generatableImpls = generatableAnnotation == null ? new Class<?>[0] : generatableAnnotation.value();
        }
        if (generatableImpls.length == 0) {
            throw new InstanceGenerationException("No generatable implementations for " + clazz.getName());
        }
        for (var impl : generatableImpls) {
            if (impl == clazz) {
                throw new InstanceGenerationException("Implementation for " + clazz.getName() + " references itself");
            }
            if (!clazz.isAssignableFrom(impl)) {
                throw new InstanceGenerationException("Implementation " + impl.getName() + " is not a subtype of " + clazz.getName());
            }
        }
        return generatableImpls;
    }

    private List<Object> generateList(Type type, GenerationContext context) {
        int size = random.nextInt(SEQUENCE_LOWER_BOUND, SEQUENCE_UPPER_BOUND);
        return Stream.generate(() -> generate(type, context))
                .limit(size)
                .collect(Collectors.toCollection(ArrayList<Object>::new));
    }

    private Object generateArray(Class<?> componentType, GenerationContext context) {
        var elements = generateList(componentType, context);
        var array = Array.newInstance(componentType, elements.size());
        for (int i = 0; i < elements.size(); i++) {
            Array.set(array, i, elements.get(i));
        }
        return array;
    }

    private Map<Object, Object> generateMap(Type keyType, Type valueType, GenerationContext context) {
        Map<Object, Object> map = new HashMap<>();
        generateList(keyType, context).forEach(key -> map.put(key, generate(valueType, context)));
        return map;
    }
}
