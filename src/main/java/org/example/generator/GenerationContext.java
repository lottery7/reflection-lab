package org.example.generator;

import java.util.HashMap;
import java.util.Map;

final class GenerationContext {
    private final int recursionLimit;
    private final Map<Class<?>, Integer> typeRepeats = new HashMap<>();

    GenerationContext(int recursionLimit) {
        this.recursionLimit = recursionLimit;
    }

    boolean tryEnter(Class<?> type) {
        int repeats = typeRepeats.getOrDefault(type, 0);
        if (repeats >= recursionLimit) {
            return false;
        }
        typeRepeats.put(type, repeats + 1);
        return true;
    }

    void leave(Class<?> type) {
        int repeats = typeRepeats.get(type);
        if (repeats == 1) {
            typeRepeats.remove(type);
        } else {
            typeRepeats.put(type, repeats - 1);
        }
    }
}
