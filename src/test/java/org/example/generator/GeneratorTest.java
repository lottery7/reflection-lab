package org.example.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.example.classes.BinaryTreeNode;
import org.example.classes.Cart;
import org.example.classes.Example;
import org.example.classes.Product;
import org.example.classes.Rectangle;
import org.example.classes.Shape;
import org.example.classes.Triangle;
import org.example.exc.InstanceGenerationException;
import org.example.fixtures.Fixtures.A;
import org.example.fixtures.Fixtures.Animal;
import org.example.fixtures.Fixtures.Box;
import org.example.fixtures.Fixtures.Cat;
import org.example.fixtures.Fixtures.Color;
import org.example.fixtures.Fixtures.Dog;
import org.example.fixtures.Fixtures.Figure;
import org.example.fixtures.Fixtures.Inventory;
import org.example.fixtures.Fixtures.NoPublicConstructor;
import org.example.fixtures.Fixtures.Person;
import org.example.fixtures.Fixtures.Sedan;
import org.example.fixtures.Fixtures.Square;
import org.example.fixtures.Fixtures.Unannotated;
import org.example.fixtures.Fixtures.Vehicle;
import org.example.fixtures.Fixtures.WrongImplementation;
import org.example.fixtures.Fixtures.X;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class GeneratorTest {
    private static final int REPEATS = 50;

    private final Generator generator = new Generator();

    static Stream<Arguments> primitiveAndWrapperTypes() {
        return Stream.of(
                Arguments.of(boolean.class, Boolean.class),
                Arguments.of(byte.class, Byte.class),
                Arguments.of(short.class, Short.class),
                Arguments.of(char.class, Character.class),
                Arguments.of(int.class, Integer.class),
                Arguments.of(long.class, Long.class),
                Arguments.of(float.class, Float.class),
                Arguments.of(double.class, Double.class),
                Arguments.of(Boolean.class, Boolean.class),
                Arguments.of(Byte.class, Byte.class),
                Arguments.of(Short.class, Short.class),
                Arguments.of(Character.class, Character.class),
                Arguments.of(Integer.class, Integer.class),
                Arguments.of(Long.class, Long.class),
                Arguments.of(Float.class, Float.class),
                Arguments.of(Double.class, Double.class)
        );
    }

    @Nested
    class Primitives {
        @ParameterizedTest
        @MethodSource("org.example.generator.GeneratorTest#primitiveAndWrapperTypes")
        void generatesWrapperInstance(Class<?> requested, Class<?> expectedWrapper) throws Exception {
            var value = generator.generateValueOfType(requested);

            assertInstanceOf(expectedWrapper, value);
        }

        @RepeatedTest(REPEATS)
        void generatesNonEmptyString() throws Exception {
            var value = generator.generateValueOfType(String.class);

            assertNotNull(value);
            assertFalse(value.isEmpty());
        }
    }

    @Nested
    class Enums {
        @Test
        void generatesEveryEnumConstant() throws Exception {
            Set<Color> seen = EnumSet.noneOf(Color.class);
            for (int i = 0; i < 200; i++) {
                var value = generator.generateValueOfType(Color.class);
                assertNotNull(value);
                seen.add(value);
            }

            assertEquals(EnumSet.allOf(Color.class), seen);
        }
    }

    @Nested
    class ArrayTypes {
        @ParameterizedTest
        @ValueSource(classes = {int[].class, String[].class, Color[].class, Rectangle[].class})
        void generatesNonEmptyArrayOfComponentType(Class<?> arrayType) throws Exception {
            var array = (Object) generator.generateValueOfType(arrayType);

            assertNotNull(array);
            int length = Array.getLength(array);
            assertTrue(length > 0);
            var wrappedComponent = wrap(arrayType.getComponentType());
            for (int i = 0; i < length; i++) {
                assertInstanceOf(wrappedComponent, Array.get(array, i));
            }
        }

        @RepeatedTest(REPEATS)
        void generatesNonEmptyNestedArrays() throws Exception {
            var array = generator.generateValueOfType(int[][].class);

            assertTrue(array.length > 0);
            for (var inner : array) {
                assertNotNull(inner);
                assertTrue(inner.length > 0);
            }
        }
    }

    @Nested
    class ListsAndMaps {
        @RepeatedTest(REPEATS)
        void generatesNonEmptyRawList() throws Exception {
            List<?> list = generator.generateValueOfType(List.class);

            assertFalse(list.isEmpty());
            list.forEach(element -> assertInstanceOf(Integer.class, element));
        }

        @RepeatedTest(REPEATS)
        void generatesNonEmptyRawMap() throws Exception {
            Map<?, ?> map = generator.generateValueOfType(Map.class);

            assertFalse(map.isEmpty());
            map.forEach((key, value) -> {
                assertInstanceOf(Integer.class, key);
                assertInstanceOf(Integer.class, value);
            });
        }

        @RepeatedTest(REPEATS)
        void generatesListWithElementTypeFromConstructorParameter() throws Exception {
            var cart = generator.generateValueOfType(Cart.class);

            assertNotNull(cart.getItems());
            assertFalse(cart.getItems().isEmpty());
            cart.getItems().forEach(item -> assertInstanceOf(Product.class, item));
        }

        @RepeatedTest(REPEATS)
        void generatesMapWithKeyAndValueTypesFromConstructorParameter() throws Exception {
            var inventory = generator.generateValueOfType(Inventory.class);

            assertNotNull(inventory.stock);
            assertFalse(inventory.stock.isEmpty());
            inventory.stock.forEach((key, value) -> {
                assertInstanceOf(String.class, key);
                assertInstanceOf(Color.class, value);
            });
        }
    }

    @Nested
    class UserClasses {
        @ParameterizedTest
        @ValueSource(classes = {Rectangle.class, Triangle.class, Product.class, Example.class})
        void generatesTemplateClass(Class<?> type) throws Exception {
            var value = generator.generateValueOfType(type);

            assertInstanceOf(type, value);
        }

        @RepeatedTest(REPEATS)
        void generatesProductWithName() throws Exception {
            var product = generator.generateValueOfType(Product.class);

            assertNotNull(product.getName());
        }

        @RepeatedTest(REPEATS)
        void generatesNewClassWithoutSpecialHandling() throws Exception {
            var person = generator.generateValueOfType(Person.class);

            assertNotNull(person.name);
            assertNotNull(person.color);
            assertNotNull(person.tags);
            assertFalse(person.tags.isEmpty());
            person.tags.forEach(tag -> assertInstanceOf(String.class, tag));
        }
    }

    @Nested
    class GeneratableImplementations {
        @Test
        void generatesShapeAsEveryListedImplementation() throws Exception {
            Set<Class<?>> seen = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                var shape = generator.generateValueOfType(Shape.class);
                assertInstanceOf(Shape.class, shape);
                seen.add(shape.getClass());
            }

            assertEquals(Set.of(Rectangle.class, Triangle.class), seen);
        }

        @RepeatedTest(REPEATS)
        void generatesAbstractClassAsListedImplementation() throws Exception {
            var animal = generator.generateValueOfType(Animal.class);

            assertTrue(animal instanceof Cat || animal instanceof Dog);
            assertNotNull(animal.name);
        }

        @RepeatedTest(REPEATS)
        void generatesSingleListedImplementation() throws Exception {
            var figure = generator.generateValueOfType(Figure.class);

            assertEquals(Square.class, figure.getClass());
        }

        @RepeatedTest(REPEATS)
        void followsGeneratableChainToConcreteClass() throws Exception {
            var vehicle = generator.generateValueOfType(Vehicle.class);

            assertEquals(Sedan.class, vehicle.getClass());
        }
    }

    @Nested
    class Recursion {
        private static final int TREE_DEPTH_LIMIT = 3;

        @RepeatedTest(REPEATS)
        void cutsRecursiveTree() throws Exception {
            var root = generator.generateValueOfType(BinaryTreeNode.class);

            assertNotNull(root);
            assertTrue(depth(root) <= TREE_DEPTH_LIMIT);
        }

        @RepeatedTest(REPEATS)
        void generatesLongNonRecursiveChainWithoutNulls() throws Exception {
            var a = generator.generateValueOfType(A.class);

            assertNotNull(a.b);
            assertNotNull(a.b.c);
            assertNotNull(a.b.c.d);
        }

        @RepeatedTest(REPEATS)
        void terminatesOnMutualRecursion() throws Exception {
            var x = generator.generateValueOfType(X.class);

            assertNotNull(x);
            assertNotNull(x.y);
        }

        private int depth(BinaryTreeNode node) {
            if (node == null) {
                return 0;
            }
            return 1 + Math.max(depth(node.getLeft()), depth(node.getRight()));
        }
    }

    @Nested
    class Errors {
        @Test
        void failsOnInterfaceWithoutGeneratable() {
            var e = assertThrows(InstanceGenerationException.class,
                    () -> generator.generateValueOfType(Unannotated.class));

            assertTrue(e.getMessage().contains(Unannotated.class.getName()));
        }

        @Test
        void failsOnGeneratableWithNonSubtype() {
            var e = assertThrows(InstanceGenerationException.class,
                    () -> generator.generateValueOfType(WrongImplementation.class));

            assertTrue(e.getMessage().contains(WrongImplementation.class.getName()));
            assertTrue(e.getMessage().contains(String.class.getName()));
        }

        @Test
        void failsOnClassWithoutPublicConstructors() {
            var e = assertThrows(InstanceGenerationException.class,
                    () -> generator.generateValueOfType(NoPublicConstructor.class));

            assertTrue(e.getMessage().contains(NoPublicConstructor.class.getName()));
        }

        @ParameterizedTest
        @ValueSource(classes = {void.class, Void.class})
        void failsOnVoid(Class<?> type) {
            assertThrows(InstanceGenerationException.class, () -> generator.generateValueOfType(type));
        }

        @Test
        void failsOnTypeVariable() {
            assertThrows(InstanceGenerationException.class, () -> generator.generateValueOfType(Box.class));
        }
    }

    private static Class<?> wrap(Class<?> type) {
        return MethodType.methodType(type).wrap().returnType();
    }
}
