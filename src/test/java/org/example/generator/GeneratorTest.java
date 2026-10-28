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
import org.example.generator.TestClasses.AbstractRoot;
import org.example.generator.TestClasses.Animal;
import org.example.generator.TestClasses.Box;
import org.example.generator.TestClasses.Cat;
import org.example.generator.TestClasses.Color;
import org.example.generator.TestClasses.CorrectImplementation;
import org.example.generator.TestClasses.Dog;
import org.example.generator.TestClasses.Empty;
import org.example.generator.TestClasses.EmptyImplementations;
import org.example.generator.TestClasses.Failing;
import org.example.generator.TestClasses.Inventory;
import org.example.generator.TestClasses.NoPublicConstructor;
import org.example.generator.TestClasses.Person;
import org.example.generator.TestClasses.Point;
import org.example.generator.TestClasses.Sedan;
import org.example.generator.TestClasses.SelfReferencing;
import org.example.generator.TestClasses.Unannotated;
import org.example.generator.TestClasses.UnannotatedImplementation;
import org.example.generator.TestClasses.Vehicle;
import org.example.generator.TestClasses.WrongImplementation;
import org.example.generator.TestClasses.X;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorTest {
    private final Generator generator = new Generator();

    static Stream<Arguments> primitiveTypes() {
        return Stream.of(
                Arguments.of(int.class, Integer.class),
                Arguments.of(char.class, Character.class),
                Arguments.of(boolean.class, Boolean.class),
                Arguments.of(double.class, Double.class),
                Arguments.of(Integer.class, Integer.class),
                Arguments.of(String.class, String.class),
                Arguments.of(Color.class, Color.class)
        );
    }

    @ParameterizedTest
    @MethodSource("primitiveTypes")
    void generatesPrimitiveTypes(Class<?> requested, Class<?> expected) {
        var value = generator.generateValueOfType(requested);
        assertInstanceOf(expected, value);
        if (value instanceof String string) {
            assertFalse(string.isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(
            classes = {int[].class, String[].class, Color[].class, Rectangle[].class, int[][][][].class, Shape[].class}
    )
    void generatesArrays(Class<?> arrayType) {
        Object array = generator.generateValueOfType(arrayType);
        var componentType = MethodType.methodType(arrayType.getComponentType()).wrap().returnType();
        assertTrue(Array.getLength(array) > 0);
        for (int i = 0; i < Array.getLength(array); i++) {
            assertInstanceOf(componentType, Array.get(array, i));
        }
    }

    @Test
    void generatesRawListAndMap() {
        assertFalse(generator.generateValueOfType(List.class).isEmpty());
        assertFalse(generator.generateValueOfType(Map.class).isEmpty());
    }

    @Test
    void usesGenericTypesOfConstructorParameters() {
        var cart = generator.generateValueOfType(Cart.class);
        var inventory = generator.generateValueOfType(Inventory.class);
        cart.getItems().forEach(item -> assertInstanceOf(Product.class, item));
        inventory.stock.forEach((key, value) -> {
            assertInstanceOf(String.class, key);
            assertInstanceOf(Color.class, value);
        });
    }

    @ParameterizedTest
    @ValueSource(classes = {Rectangle.class, Triangle.class, Product.class, Example.class, Person.class, Point.class})
    void generatesUserClasses(Class<?> type) {
        assertInstanceOf(type, generator.generateValueOfType(type));
    }

    @Test
    void generatesGeneratableImplementations() {
        var shape = generator.generateValueOfType(Shape.class);
        var animal = generator.generateValueOfType(Animal.class);
        assertTrue(shape instanceof Rectangle || shape instanceof Triangle);
        assertTrue(animal instanceof Cat || animal instanceof Dog);
        assertInstanceOf(Sedan.class, generator.generateValueOfType(Vehicle.class));
    }

    @Test
    void overridesGeneratable() {
        var generator = new Generator(Map.of(
                Unannotated.class, List.of(UnannotatedImplementation.class),
                WrongImplementation.class, List.of(CorrectImplementation.class)));
        assertInstanceOf(UnannotatedImplementation.class, generator.generateValueOfType(Unannotated.class));
        assertInstanceOf(CorrectImplementation.class, generator.generateValueOfType(WrongImplementation.class));
    }

    @Test
    void generatesLongNonRecursiveChain() {
        var a = generator.generateValueOfType(A.class);
        assertNotNull(a.b.c.d);
    }

    @Test
    void cutsRecursion() {
        assertNotNull(generator.generateValueOfType(BinaryTreeNode.class));
        assertNotNull(generator.generateValueOfType(X.class).y);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            Unannotated.class, EmptyImplementations.class, AbstractRoot.class, WrongImplementation.class,
            SelfReferencing.class, NoPublicConstructor.class, void.class, Void.class, Box.class, Empty.class
    })
    void failsOnUnsupportedTypes(Class<?> type) {
        assertThrows(InstanceGenerationException.class, () -> generator.generateValueOfType(type));
    }

    @Test
    void failsOnInvalidOverride() {
        var empty = new Generator(Map.of(Animal.class, List.of()));
        var notSubtype = new Generator(Map.of(Unannotated.class, List.of(String.class)));
        var self = new Generator(Map.of(Unannotated.class, List.of(Unannotated.class)));

        assertThrows(InstanceGenerationException.class, () -> empty.generateValueOfType(Animal.class));
        assertThrows(InstanceGenerationException.class, () -> notSubtype.generateValueOfType(Unannotated.class));
        assertThrows(InstanceGenerationException.class, () -> self.generateValueOfType(Unannotated.class));
    }

    @Test
    void keepsConstructorExceptionAsCause() {
        var e = assertThrows(InstanceGenerationException.class, () -> generator.generateValueOfType(Failing.class));
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }
}
