package org.example.generator;

import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.example.classes.BinaryTreeNode;
import org.example.classes.Cart;
import org.example.classes.Example;
import org.example.classes.Product;
import org.example.classes.Rectangle;
import org.example.classes.Shape;
import org.example.classes.Triangle;
import org.example.exc.InstanceGenerationException;
import org.example.generator.TestClasses.A;
import org.example.generator.TestClasses.Animal;
import org.example.generator.TestClasses.Box;
import org.example.generator.TestClasses.Cat;
import org.example.generator.TestClasses.Color;
import org.example.generator.TestClasses.Dog;
import org.example.generator.TestClasses.Empty;
import org.example.generator.TestClasses.Failing;
import org.example.generator.TestClasses.Figure;
import org.example.generator.TestClasses.Inventory;
import org.example.generator.TestClasses.NoPublicConstructor;
import org.example.generator.TestClasses.Person;
import org.example.generator.TestClasses.Point;
import org.example.generator.TestClasses.Sedan;
import org.example.generator.TestClasses.Square;
import org.example.generator.TestClasses.Unannotated;
import org.example.generator.TestClasses.Vehicle;
import org.example.generator.TestClasses.WrongImplementation;
import org.example.generator.TestClasses.X;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorTest {
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

        @Test
        void generatesNonEmptyString() throws Exception {
            var value = generator.generateValueOfType(String.class);

            assertNotNull(value);
            assertFalse(value.isEmpty());
        }
    }

    @Nested
    class Enums {
        @Test
        void generatesEnumConstant() throws Exception {
            var value = generator.generateValueOfType(Color.class);

            assertInstanceOf(Color.class, value);
        }

        @Test
        void generatesNullForEmptyEnum() throws Exception {
            assertNull(generator.generateValueOfType(Empty.class));
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

        @Test
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
        @Test
        void generatesNonEmptyRawList() throws Exception {
            List<?> list = generator.generateValueOfType(List.class);

            assertFalse(list.isEmpty());
            list.forEach(element -> assertInstanceOf(Integer.class, element));
        }

        @Test
        void generatesNonEmptyRawMap() throws Exception {
            Map<?, ?> map = generator.generateValueOfType(Map.class);

            assertFalse(map.isEmpty());
            map.forEach((key, value) -> {
                assertInstanceOf(Integer.class, key);
                assertInstanceOf(Integer.class, value);
            });
        }

        @Test
        void generatesListWithElementTypeFromConstructorParameter() throws Exception {
            var cart = generator.generateValueOfType(Cart.class);

            assertNotNull(cart.getItems());
            assertFalse(cart.getItems().isEmpty());
            cart.getItems().forEach(item -> assertInstanceOf(Product.class, item));
        }

        @Test
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

        @Test
        void generatesProductWithName() throws Exception {
            var product = generator.generateValueOfType(Product.class);

            assertNotNull(product.getName());
        }

        @Test
        void generatesNewClassWithoutSpecialHandling() throws Exception {
            var person = generator.generateValueOfType(Person.class);

            assertNotNull(person.name);
            assertNotNull(person.color);
            assertNotNull(person.tags);
            assertFalse(person.tags.isEmpty());
            person.tags.forEach(tag -> assertInstanceOf(String.class, tag));
        }

        @Test
        void generatesRecord() throws Exception {
            var point = generator.generateValueOfType(Point.class);

            assertNotNull(point);
            assertNotNull(point.label());
            assertNotNull(point.colors());
            assertFalse(point.colors().isEmpty());
            point.colors().forEach(color -> assertInstanceOf(Color.class, color));
        }
    }

    @Nested
    class GeneratableImplementations {
        @Test
        void generatesShapeAsListedImplementation() throws Exception {
            var shape = generator.generateValueOfType(Shape.class);

            assertTrue(shape instanceof Rectangle || shape instanceof Triangle);
        }

        @Test
        void generatesAbstractClassAsListedImplementation() throws Exception {
            var animal = generator.generateValueOfType(Animal.class);

            assertTrue(animal instanceof Cat || animal instanceof Dog);
            assertNotNull(animal.name);
        }

        @Test
        void generatesSingleListedImplementation() throws Exception {
            var figure = generator.generateValueOfType(Figure.class);

            assertEquals(Square.class, figure.getClass());
        }

        @Test
        void followsGeneratableChainToConcreteClass() throws Exception {
            var vehicle = generator.generateValueOfType(Vehicle.class);

            assertEquals(Sedan.class, vehicle.getClass());
        }
    }

    @Nested
    class Recursion {
        private static final int TREE_DEPTH_LIMIT = 3;

        @Test
        void cutsRecursiveTree() throws Exception {
            var root = generator.generateValueOfType(BinaryTreeNode.class);

            assertNotNull(root);
            assertTrue(depth(root) <= TREE_DEPTH_LIMIT);
        }

        @Test
        void generatesLongNonRecursiveChainWithoutNulls() throws Exception {
            var a = generator.generateValueOfType(A.class);

            assertNotNull(a.b);
            assertNotNull(a.b.c);
            assertNotNull(a.b.c.d);
        }

        @Test
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

        @Test
        void failsWhenConstructorThrows() {
            var e = assertThrows(InstanceGenerationException.class,
                    () -> generator.generateValueOfType(Failing.class));

            assertTrue(e.getMessage().contains(Failing.class.getName()));
            assertInstanceOf(IllegalStateException.class, e.getCause());
        }
    }

    private static Class<?> wrap(Class<?> type) {
        return MethodType.methodType(type).wrap().returnType();
    }
}
