package dev.oreslang;

import dev.oreslang.parser.Parser;
import dev.oreslang.types.OwnershipChecker;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Differential model for queue-local ABA tickets. The Java structures are
 * strictly an oracle; every asserted operation executes in Oreslang/Truffle.
 * This deliberately does not assert cross-queue identity or authorization.
 */
final class LinkedQueueTicketDifferentialTest {
    private static final int CAPACITY = 5;
    private static final Path SOURCE_ROOT = Path.of(
            System.getProperty("oreslang.linkedQueueRoot", "../audit/linked-queue"));

    private record Ticket(int key, int generation, String variable) {}

    private static void expect(StringBuilder src, String expression, boolean expected, int[] id) {
        src.append("  if (").append(expression).append(") != ")
                .append(expected).append(" then\n")
                .append("    return ").append(id[0]++).append(";\n")
                .append("  fi\n");
    }

    private static void expectInt(StringBuilder src, String expression, int expected, int[] id) {
        src.append("  if ").append(expression).append(" != ").append(expected)
                .append(" then\n")
                .append("    return ").append(id[0]++).append(";\n")
                .append("  fi\n");
    }

    private static void checkRemoval(StringBuilder src, String expression,
                                     Integer key, Integer value, int step, int[] id) {
        String result = "removed_" + step;
        src.append("  val ").append(result).append(" = ").append(expression).append(";\n");
        expect(src, result + ".is_some()", key != null, id);
        if (key != null) {
            String entry = "entry_" + step;
            src.append("  val ").append(entry).append(" = ").append(result)
                    .append(".unwrap();\n");
            expectInt(src, entry + ".key", key, id);
            expectInt(src, entry + ".value", value, id);
        }
    }

    private static void snapshot(StringBuilder src, List<Integer> order,
                                 Map<Integer, Integer> values, int[] generations,
                                 List<Ticket> tickets, int[] id) {
        expect(src, "q.validate()", true, id);
        expect(src, "q.is_empty()", order.isEmpty(), id);
        expectInt(src, "q.size()", order.size(), id);
        expectInt(src, "q.ordered_keys().size", order.size(), id);
        for (int index = 0; index < order.size(); index++) {
            expectInt(src, "q.ordered_keys().get(" + index + ")", order.get(index), id);
        }
        if (order.isEmpty()) {
            expect(src, "q.first().is_some()", false, id);
            expect(src, "q.last().is_some()", false, id);
        } else {
            expectInt(src, "q.first().unwrap().key", order.getFirst(), id);
            expectInt(src, "q.last().unwrap().key", order.getLast(), id);
        }
        for (int key = -1; key <= CAPACITY; key++) {
            boolean active = values.containsKey(key);
            expect(src, "q.ticket_for(" + key + ").is_some()", active, id);
            if (active) {
                expectInt(src, "q.ticket_for(" + key + ").unwrap().generation",
                        generations[key], id);
                expectInt(src, "q.get(" + key + ").unwrap()", values.get(key), id);
            }
        }
        for (Ticket ticket : tickets) {
            boolean active = values.containsKey(ticket.key)
                    && generations[ticket.key] == ticket.generation;
            expect(src, "q.contains_ticket(" + ticket.variable + ")", active, id);
            expect(src, "q.get_ticket(" + ticket.variable + ").is_some()", active, id);
            if (active) {
                expectInt(src, "q.get_ticket(" + ticket.variable + ").unwrap()",
                        values.get(ticket.key), id);
            }
        }
    }

    private static void admit(StringBuilder src, int key, int value,
                              int step, int[] generations, List<Ticket> tickets, int[] id) {
        generations[key]++;
        String variable = "ticket_" + step;
        src.append("  val QueueTicket ").append(variable)
                .append(" = q.ticket_for(").append(key).append(").unwrap();\n");
        expectInt(src, variable + ".key", key, id);
        expectInt(src, variable + ".generation", generations[key], id);
        tickets.add(new Ticket(key, generations[key], variable));
    }

    private static String program(String library, int seed, int steps) {
        Random random = new Random(seed);
        List<Integer> order = new ArrayList<>();
        Map<Integer, Integer> values = new HashMap<>();
        List<Ticket> tickets = new ArrayList<>();
        int[] generations = new int[CAPACITY];
        int[] id = {1};
        StringBuilder src = new StringBuilder(library)
                .append("\nfnc run_model() -> int {\n")
                .append("  val IntLinkedQueue q = new IntLinkedQueue(")
                .append(CAPACITY).append(");\n");
        expect(src, "q.initialize()", true, id);
        for (int step = 0; step < steps; step++) {
            int operation = random.nextInt(9);
            int key = random.nextInt(CAPACITY + 2) - 1;
            int anchor = random.nextInt(CAPACITY + 2) - 1;
            int value = random.nextInt(2001) - 1000;
            boolean available = key >= 0 && key < CAPACITY && !values.containsKey(key);
            switch (operation) {
                case 0 -> {
                    expect(src, "q.enqueue(" + key + ", " + value + ")", available, id);
                    if (available) {
                        values.put(key, value);
                        order.add(key);
                        admit(src, key, value, step, generations, tickets, id);
                    }
                }
                case 1 -> {
                    expect(src, "q.add_to_front(" + key + ", " + value + ")", available, id);
                    if (available) {
                        values.put(key, value);
                        order.addFirst(key);
                        admit(src, key, value, step, generations, tickets, id);
                    }
                }
                case 2, 3 -> {
                    boolean ok = available && values.containsKey(anchor);
                    String method = operation == 2 ? "insert_before" : "insert_after";
                    expect(src, "q." + method + "(" + anchor + ", " + key + ", " + value + ")",
                            ok, id);
                    if (ok) {
                        int position = order.indexOf(anchor) + (operation == 3 ? 1 : 0);
                        order.add(position, key);
                        values.put(key, value);
                        admit(src, key, value, step, generations, tickets, id);
                    }
                }
                case 4 -> {
                    Integer old = values.remove(key);
                    if (old != null) order.remove(Integer.valueOf(key));
                    checkRemoval(src, "q.remove(" + key + ")", old == null ? null : key,
                            old, step, id);
                }
                case 5, 6 -> {
                    Integer removedKey = order.isEmpty() ? null
                            : operation == 5 ? order.removeFirst() : order.removeLast();
                    Integer old = removedKey == null ? null : values.remove(removedKey);
                    checkRemoval(src, operation == 5 ? "q.dequeue()" : "q.remove_last()",
                            removedKey, old, step, id);
                }
                case 7 -> {
                    src.append("  q.clear();\n");
                    order.clear();
                    values.clear();
                }
                case 8 -> {
                    if (tickets.isEmpty()) {
                        checkRemoval(src, "q.remove_ticket(new QueueTicket(-1, 1))",
                                null, null, step, id);
                    } else {
                        Ticket ticket = tickets.get(random.nextInt(tickets.size()));
                        boolean active = values.containsKey(ticket.key)
                                && generations[ticket.key] == ticket.generation;
                        Integer old = active ? values.remove(ticket.key) : null;
                        if (active) order.remove(Integer.valueOf(ticket.key));
                        checkRemoval(src, "q.remove_ticket(" + ticket.variable + ")",
                                active ? ticket.key : null, old, step, id);
                    }
                }
                default -> throw new IllegalStateException("operation");
            }
            snapshot(src, order, values, generations, tickets, id);
        }
        src.append("  return 0;\n}\n")
                .append("pub routine main() -> void {\n")
                .append("  stdio.stdout.write(run_model());\n")
                .append("  return;\n}\n");
        return src.toString();
    }

    private static void execute(int seed) throws Exception {
        String library = Files.readString(SOURCE_ROOT.resolve("src/int_linked_queue.ores"));
        String code = program(library, seed, 48);
        var parsed = Parser.parse(code);
        assertDoesNotThrow(() -> TypeChecker.check(parsed), "typing seed " + seed);
        assertDoesNotThrow(() -> OwnershipChecker.check(parsed), "ownership seed " + seed);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder(OresLanguage.ID)
                .allowAllAccess(false).out(output).build()) {
            context.eval(Source.newBuilder(OresLanguage.ID, code,
                    "linked-queue-ticket-model-" + seed + ".ores")
                    .mimeType(OresLanguage.MIME_TYPE).buildLiteral());
        }
        assertEquals("0", output.toString(StandardCharsets.UTF_8),
                "ticket differential disagreement, seed=" + seed);
    }

    @Test void seededTickets1307() throws Exception { execute(1307); }

    @Test void seededTickets7739() throws Exception { execute(7739); }

    @Test void seededTickets20261030() throws Exception { execute(20261030); }
}
