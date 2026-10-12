package dev.oreslang;

import dev.oreslang.parser.Parser;
import dev.oreslang.types.TypeChecker;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Independent Java reference model; the system under test remains the actual
 * Oreslang queue source executed by GraalVM, never a Java queue implementation.
 */
final class LinkedQueueDifferentialTest {
    private static final int CAPACITY = 8;
    private static final Path SOURCE_ROOT = Path.of(
            System.getProperty("oreslang.linkedQueueRoot", "../audit/linked-queue"));

    private static void expect(StringBuilder code, String predicate, boolean expected, int[] id) {
        code.append("  if (").append(predicate).append(") != ")
                .append(expected).append(" then\n")
                .append("    return ").append(id[0]++).append(";\n")
                .append("  fi\n");
    }

    private static void expectInt(StringBuilder code, String expression, int expected, int[] id) {
        code.append("  if ").append(expression).append(" != ").append(expected).append(" then\n")
                .append("    return ").append(id[0]++).append(";\n")
                .append("  fi\n");
    }

    private static void verifyState(StringBuilder code, List<Integer> order,
                                    Map<Integer, Integer> values, int[] id) {
        expect(code, "q.validate()", true, id);
        expectInt(code, "q.size()", order.size(), id);
        expect(code, "q.is_empty()", order.isEmpty(), id);
        expectInt(code, "q.ordered_keys().size", order.size(), id);

        if (order.isEmpty()) {
            expect(code, "q.first().is_some()", false, id);
            expect(code, "q.last().is_some()", false, id);
        } else {
            int first = order.getFirst();
            int last = order.getLast();
            expectInt(code, "q.first().unwrap().key", first, id);
            expectInt(code, "q.last().unwrap().key", last, id);
            expectInt(code, "q.first().unwrap().value", values.get(first), id);
            expectInt(code, "q.last().unwrap().value", values.get(last), id);
        }
        for (int i = 0; i < order.size(); i++) {
            expectInt(code, "q.ordered_keys().get(" + i + ")", order.get(i), id);
        }
        for (int key = -1; key <= CAPACITY; key++) {
            expect(code, "q.contains(" + key + ")", values.containsKey(key), id);
            expect(code, "q.get(" + key + ").is_some()", values.containsKey(key), id);
            if (values.containsKey(key)) {
                expectInt(code, "q.get(" + key + ").unwrap()", values.get(key), id);
            }
        }
    }

    private static void verifyRemoval(StringBuilder code, String removalExpression,
                                      Integer key, Integer value, int step, int[] id) {
        String variable = "removed_" + step;
        code.append("  val ").append(variable).append(" = ").append(removalExpression).append(";\n");
        expect(code, variable + ".is_some()", key != null, id);
        if (key != null) {
            expectInt(code, variable + ".unwrap().key", key, id);
            expectInt(code, variable + ".unwrap().value", value, id);
        }
    }

    private static String program(String library, int seed, int steps) {
        Random rng = new Random(seed);
        List<Integer> order = new ArrayList<>();
        Map<Integer, Integer> values = new HashMap<>();
        int[] id = {1};
        StringBuilder code = new StringBuilder(library)
                .append("\nfnc run_model() -> int {\n")
                .append("  val IntLinkedQueue q = new IntLinkedQueue(").append(CAPACITY).append(");\n");
        expect(code, "q.initialize()", true, id);

        for (int step = 0; step < steps; step++) {
            int operation = rng.nextInt(9);
            int key = rng.nextInt(CAPACITY + 2) - 1; // include invalid -1 and capacity
            int anchor = rng.nextInt(CAPACITY + 2) - 1;
            int value = rng.nextInt(20001) - 10000;
            boolean valid = key >= 0 && key < CAPACITY;
            boolean absent = !values.containsKey(key);

            switch (operation) {
                case 0 -> {
                    boolean ok = valid && absent;
                    expect(code, "q.enqueue(" + key + ", " + value + ")", ok, id);
                    if (ok) { values.put(key, value); order.add(key); }
                }
                case 1 -> {
                    boolean ok = valid && absent;
                    expect(code, "q.add_to_front(" + key + ", " + value + ")", ok, id);
                    if (ok) { values.put(key, value); order.addFirst(key); }
                }
                case 2 -> {
                    boolean ok = valid && absent && values.containsKey(anchor);
                    expect(code, "q.insert_before(" + anchor + ", " + key + ", " + value + ")", ok, id);
                    if (ok) {
                        order.add(order.indexOf(anchor), key);
                        values.put(key, value);
                    }
                }
                case 3 -> {
                    boolean ok = valid && absent && values.containsKey(anchor);
                    expect(code, "q.insert_after(" + anchor + ", " + key + ", " + value + ")", ok, id);
                    if (ok) {
                        order.add(order.indexOf(anchor) + 1, key);
                        values.put(key, value);
                    }
                }
                case 4 -> {
                    Integer removed = values.remove(key);
                    if (removed != null) order.remove(Integer.valueOf(key));
                    verifyRemoval(code, "q.remove(" + key + ")", removed == null ? null : key,
                            removed, step, id);
                }
                case 5 -> {
                    Integer removedKey = order.isEmpty() ? null : order.removeFirst();
                    Integer removedValue = removedKey == null ? null : values.remove(removedKey);
                    verifyRemoval(code, "q.dequeue()", removedKey, removedValue, step, id);
                }
                case 6 -> {
                    Integer removedKey = order.isEmpty() ? null : order.removeLast();
                    Integer removedValue = removedKey == null ? null : values.remove(removedKey);
                    verifyRemoval(code, "q.remove_last()", removedKey, removedValue, step, id);
                }
                case 7 -> {
                    code.append("  q.clear();\n");
                    order.clear();
                    values.clear();
                }
                case 8 -> {
                    expect(code, "q.get(" + key + ").is_some()", values.containsKey(key), id);
                    if (values.containsKey(key))
                        expectInt(code, "q.get(" + key + ").unwrap()", values.get(key), id);
                }
                default -> throw new IllegalStateException("Unexpected operation " + operation);
            }
            verifyState(code, order, values, id);
        }

        code.append("  return 0;\n}\n")
                .append("pub routine main() -> void {\n")
                .append("  stdio.stdout.write(run_model());\n")
                .append("  return;\n}\n");
        return code.toString();
    }

    private static void executeSeed(int seed) throws Exception {
        String library = Files.readString(SOURCE_ROOT.resolve("src/int_linked_queue.ores"));
        String generated = program(library, seed, 64);
        var parsed = Parser.parse(generated);
        assertDoesNotThrow(() -> TypeChecker.check(parsed), "typecheck seed " + seed);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder(OresLanguage.ID)
                .allowAllAccess(false).out(stdout).build()) {
            context.eval(Source.newBuilder(OresLanguage.ID, generated, "linked-queue-model-" + seed + ".ores")
                    .mimeType(OresLanguage.MIME_TYPE).buildLiteral());
        }
        assertEquals("0", stdout.toString(StandardCharsets.UTF_8),
                "reference-model disagreement, seed=" + seed);
    }

    @Test void deterministicSeed1307() throws Exception { executeSeed(1307); }

    @Test void deterministicSeed7739() throws Exception { executeSeed(7739); }
}
