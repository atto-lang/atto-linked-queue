package dev.oreslang;

import dev.oreslang.parser.Parser;
import dev.oreslang.types.OwnershipChecker;
import dev.oreslang.types.TypeChecker;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class LinkedQueueIntegrationTest {
    // Configurable for local checkouts; the funded CI mirror uses this default.
    // Do not include Java/host interop in the Oreslang library itself.
    private static final Path QUEUE = Path.of(System.getProperty(
            "oreslang.linkedQueueRoot", "../audit/linked-queue"));
    private static final String LIBRARY = read("src/int_linked_queue.ores");

    private static String read(String filename) {
        try {
            return Files.readString(QUEUE.resolve(filename));
        } catch (Exception problem) {
            throw new IllegalStateException("Queue source missing: " + filename, problem);
        }
    }

    private static String sourceWith(String filename, boolean makeCallableMain) {
        String testSource = read(filename)
                .replaceAll("(?m)^import class [^\\n]*;\\R", "");
        if (makeCallableMain) {
            testSource = testSource
                    .replace("pub routine main() -> int", "fnc queue_case() -> int")
                    .replace("pub fnc main() -> int", "fnc queue_case() -> int");
            if (!testSource.contains("fnc queue_case() -> int")) {
                throw new IllegalStateException("No int-returning queue fixture main: " + filename);
            }
            testSource += """
                    \n
                    pub routine main() -> void {
                      stdio.stdout.write(queue_case());
                      return;
                    }
                    """;
        }
        return LIBRARY + "\n" + testSource;
    }

    @Test
    void queueLibraryMustParseAndTypecheck() {
        assertDoesNotThrow(() -> Parser.parse(LIBRARY), "queue parse");
        assertDoesNotThrow(() -> TypeChecker.check(Parser.parse(LIBRARY)), "queue types");
        assertDoesNotThrow(() -> OwnershipChecker.check(Parser.parse(LIBRARY)), "queue ownership");
    }

    @TestFactory
    Stream<DynamicTest> validFixturesMustExecuteAndReturnZero() {
        return Stream.of("smoke", "stress", "instances", "edge_cases", "relink_matrix",
                        "generation_tickets")
                .map(name -> DynamicTest.dynamicTest(name, () -> {
                    String code = sourceWith("tests/" + name + ".ores", true);
                    var parsed = assertDoesNotThrow(() -> Parser.parse(code), name + " syntax");
                    assertDoesNotThrow(() -> TypeChecker.check(parsed), name + " typing");
                    assertDoesNotThrow(() -> OwnershipChecker.check(parsed), name + " ownership");

                    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                    try (Context context = Context.newBuilder(OresLanguage.ID)
                            .allowAllAccess(false)
                            .out(stdout)
                            .build()) {
                        context.eval(Source.newBuilder(OresLanguage.ID, code, name + ".ores")
                                .mimeType(OresLanguage.MIME_TYPE)
                                .buildLiteral());
                    }
                    assertEquals("0", stdout.toString(StandardCharsets.UTF_8),
                            name + " returned a failure code");
                }));
    }

    @TestFactory
    Stream<DynamicTest> negativesMustBeRejectedByCompiler() {
        return Stream.of("aliasing", "double_mut_borrow", "escaped_mutation",
                        "readonly_binding", "readonly_receiver")
                .map(name -> DynamicTest.dynamicTest(name, () -> {
                    String code = sourceWith("tests/negative/" + name + ".ores", false);
                    var parsed = assertDoesNotThrow(() -> Parser.parse(code),
                            name + " must be syntactically valid so rejection proves semantics");
                    IllegalArgumentException rejection = assertThrows(IllegalArgumentException.class,
                            () -> {
                                TypeChecker.check(parsed);
                                OwnershipChecker.check(parsed);
                            }, name + " must fail compiler ownership/privacy checks");
                    String diagnostic = rejection.getMessage();
                    assertNotNull(diagnostic);
                    assertFalse(diagnostic.isBlank());
                    System.out.println("EXPECTED REJECTION " + name + ": " + diagnostic);
                }));
    }
}
