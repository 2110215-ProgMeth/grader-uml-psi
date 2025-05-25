import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ConstructorTest {

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void testDefaultConstructor() throws Exception {
        String javaCode = "public class Test {}";
        File inputFile = createTempJavaFile("Test.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode constructors = result.get("constructors");
        assertEquals(0, constructors.size());
    }

    @Test
    void testPublicConstructor() throws Exception {
        String javaCode = "public class Test { public Test() {} }";
        File inputFile = createTempJavaFile("Test.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode constructor = constructors.get(0);
        assertEquals("Test", constructor.get("name").asText());
        assertTrue(constructor.get("public").asBoolean());
        assertFalse(constructor.get("protected").asBoolean());
        assertFalse(constructor.get("private").asBoolean());
        assertEquals(0, constructor.get("params").size());
        assertEquals(0, constructor.get("throws").size());
    }

    @Test
    void testPrivateConstructor() throws Exception {
        String javaCode = "public class Test { private Test() {} }";
        File inputFile = createTempJavaFile("Test.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode constructor = constructors.get(0);
        assertEquals("Test", constructor.get("name").asText());
        assertFalse(constructor.get("public").asBoolean());
        assertFalse(constructor.get("protected").asBoolean());
        assertTrue(constructor.get("private").asBoolean());
    }

    @Test
    void testProtectedConstructor() throws Exception {
        String javaCode = "public class Test { protected Test() {} }";
        File inputFile = createTempJavaFile("Test.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode constructor = constructors.get(0);
        assertEquals("Test", constructor.get("name").asText());
        assertFalse(constructor.get("public").asBoolean());
        assertTrue(constructor.get("protected").asBoolean());
        assertFalse(constructor.get("private").asBoolean());
    }

    @Test
    void testPackagePrivateConstructor() throws Exception {
        String javaCode = "public class Test { Test() {} }";
        File inputFile = createTempJavaFile("Test.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode constructor = constructors.get(0);
        assertEquals("Test", constructor.get("name").asText());
        assertFalse(constructor.get("public").asBoolean());
        assertFalse(constructor.get("protected").asBoolean());
        assertFalse(constructor.get("private").asBoolean());
    }

    @Test
    void testConstructorWithSingleParameter() throws Exception {
        String javaCode = "public class Test { public Test(String name) {} }";
        File inputFile = createTempJavaFile("Test.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode constructor = constructors.get(0);
        JsonNode params = constructor.get("params");
        assertEquals(1, params.size());
        assertEquals("String", params.get(0).asText());
    }

    @Test
    void testConstructorWithMultipleParameters() throws Exception {
        String javaCode = "public class Test { public Test(String name, int age, boolean active) {} }";
        File inputFile = createTempJavaFile("Test.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode constructor = constructors.get(0);
        JsonNode params = constructor.get("params");
        assertEquals(3, params.size());
        assertEquals("String", params.get(0).asText());
        assertEquals("int", params.get(1).asText());
        assertEquals("boolean", params.get(2).asText());
    }

    @Test
    void testConstructorWithGenericParameter() throws Exception {
        String javaCode = "import java.util.List; public class Test { public Test(List<String> items) {} }";
        File inputFile = createTempJavaFile("Test.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode constructor = constructors.get(0);
        JsonNode params = constructor.get("params");
        assertEquals(1, params.size());
        assertEquals("List<String>", params.get(0).asText());
    }

    @Test
    void testConstructorWithArrayParameter() throws Exception {
        String javaCode = "public class Test { public Test(String[] args) {} }";
        File inputFile = createTempJavaFile("Test.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode constructor = constructors.get(0);
        JsonNode params = constructor.get("params");
        assertEquals(1, params.size());
        assertEquals("String[]", params.get(0).asText());
    }

    @Test
    void testConstructorWithVarArgs() throws Exception {
        String javaCode = "public class Test { public Test(String... args) {} }";
        File inputFile = createTempJavaFile("Test.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode constructor = constructors.get(0);
        JsonNode params = constructor.get("params");
        assertEquals(1, params.size());
        assertTrue(params.get(0).asText().equals("String...") || params.get(0).asText().equals("String[]"));
    }

    @Test
    void testConstructorWithSingleException() throws Exception {
        String javaCode = "public class Test { public Test() throws Exception {} }";
        File inputFile = createTempJavaFile("Test.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode constructor = constructors.get(0);
        JsonNode throwsNode = constructor.get("throws");
        assertEquals(1, throwsNode.size());
        assertEquals("Exception", throwsNode.get(0).asText());
    }

    @Test
    void testConstructorWithMultipleExceptions() throws Exception {
        String javaCode = "import java.io.IOException; public class Test { public Test() throws IOException, RuntimeException {} }";
        File inputFile = createTempJavaFile("Test.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode constructor = constructors.get(0);
        JsonNode throwsNode = constructor.get("throws");
        assertEquals(2, throwsNode.size());
        assertEquals("IOException", throwsNode.get(0).asText());
        assertEquals("RuntimeException", throwsNode.get(1).asText());
    }

    @Test
    void testMultipleConstructors() throws Exception {
        String javaCode = "public class Test {\n" + "    public Test() {}\n" + "    public Test(String name) {}\n"
                + "    private Test(int id) {}\n" + "    protected Test(String name, int id) throws Exception {}\n"
                + "}";
        File inputFile = createTempJavaFile("Test.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode constructors = result.get("constructors");
        assertEquals(4, constructors.size());

        JsonNode ctor1 = constructors.get(0);
        assertTrue(ctor1.get("public").asBoolean());
        assertEquals(0, ctor1.get("params").size());

        JsonNode ctor2 = constructors.get(1);
        assertTrue(ctor2.get("public").asBoolean());
        assertEquals(1, ctor2.get("params").size());
        assertEquals("String", ctor2.get("params").get(0).asText());

        JsonNode ctor3 = constructors.get(2);
        assertTrue(ctor3.get("private").asBoolean());
        assertEquals(1, ctor3.get("params").size());
        assertEquals("int", ctor3.get("params").get(0).asText());

        JsonNode ctor4 = constructors.get(3);
        assertTrue(ctor4.get("protected").asBoolean());
        assertEquals(2, ctor4.get("params").size());
        assertEquals("String", ctor4.get("params").get(0).asText());
        assertEquals("int", ctor4.get("params").get(1).asText());
        assertEquals(1, ctor4.get("throws").size());
        assertEquals("Exception", ctor4.get("throws").get(0).asText());
    }

    @Test
    void testConstructorWithTrueFlagPruning() throws Exception {
        String javaCode = "public class Test { private Test() {} }";
        File inputFile = createTempJavaFile("Test.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-t", "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode constructor = constructors.get(0);
        assertTrue(constructor.get("private").asBoolean());
        assertNull(constructor.get("public"));
        assertNull(constructor.get("protected"));
    }

    @Test
    void testEnumConstructor() throws Exception {
        String javaCode = "public enum Status { ACTIVE, INACTIVE; private Status() {} }";
        File inputFile = createTempJavaFile("Status.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("Enum", result.get("kind").asText());
        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode constructor = constructors.get(0);
        assertEquals("Status", constructor.get("name").asText());
        assertTrue(constructor.get("private").asBoolean());
    }

    @Test
    void testConstructorWithCustomObjectParameter() throws Exception {
        String javaCode = "class Address {\n" + "    String street;\n" + "}\n" + "public class Person {\n"
                + "    public Person(Address address) {}\n" + "}";
        File inputFile = createTempJavaFile("Person.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode constructor = constructors.get(0);
        JsonNode params = constructor.get("params");
        assertEquals(1, params.size());
        assertEquals("Address", params.get(0).asText());
    }

    private File createTempJavaFile(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content);
        return file.toFile();
    }
}
