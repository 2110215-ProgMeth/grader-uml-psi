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

public class TypeTest {

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
    void testBasicClass() throws Exception {
        String javaCode = "public class BasicClass {}";
        File inputFile = createTempJavaFile("BasicClass.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("BasicClass", result.get("name").asText());
        assertEquals("Class", result.get("kind").asText());
        assertTrue(result.get("public").asBoolean());
        assertFalse(result.get("abstract").asBoolean());
        assertFalse(result.get("final").asBoolean());
        assertFalse(result.get("static").asBoolean());
    }

    @Test
    void testInterface() throws Exception {
        String javaCode = "public interface TestInterface {}";
        File inputFile = createTempJavaFile("TestInterface.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("TestInterface", result.get("name").asText());
        assertEquals("Interface", result.get("kind").asText());
        assertTrue(result.get("public").asBoolean());
        assertFalse(result.get("abstract").asBoolean());
        assertFalse(result.get("final").asBoolean());
    }

    @Test
    void testEnum() throws Exception {
        String javaCode = "public enum Status { ACTIVE, INACTIVE, PENDING }";
        File inputFile = createTempJavaFile("Status.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("Status", result.get("name").asText());
        assertEquals("Enum", result.get("kind").asText());
        assertTrue(result.get("public").asBoolean());
        assertFalse(result.get("abstract").asBoolean());
        assertFalse(result.get("final").asBoolean());
    }

    @Test
    void testAnnotation() throws Exception {
        String javaCode = "public @interface TestAnnotation { String value() default \"\"; }";
        File inputFile = createTempJavaFile("TestAnnotation.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("TestAnnotation", result.get("name").asText());
        assertEquals("Annotation", result.get("kind").asText());
        assertTrue(result.get("public").asBoolean());
    }

    @Test
    void testRecord() throws Exception {
        String javaCode = "public record Person(String name, int age) {}";
        File inputFile = createTempJavaFile("Person.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("Person", result.get("name").asText());
        assertEquals("Record", result.get("kind").asText());
        assertTrue(result.get("public").asBoolean());
        assertFalse(result.get("abstract").asBoolean());
        assertTrue(result.get("final").asBoolean());
    }

    @Test
    void testAbstractClass() throws Exception {
        String javaCode = "public abstract class AbstractBase { public abstract void process(); }";
        File inputFile = createTempJavaFile("AbstractBase.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("AbstractBase", result.get("name").asText());
        assertEquals("Class", result.get("kind").asText());
        assertTrue(result.get("public").asBoolean());
        assertTrue(result.get("abstract").asBoolean());
        assertFalse(result.get("final").asBoolean());
    }

    @Test
    void testFinalClass() throws Exception {
        String javaCode = "public final class FinalClass {}";
        File inputFile = createTempJavaFile("FinalClass.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("FinalClass", result.get("name").asText());
        assertEquals("Class", result.get("kind").asText());
        assertTrue(result.get("public").asBoolean());
        assertFalse(result.get("abstract").asBoolean());
        assertTrue(result.get("final").asBoolean());
    }

    @Test
    void testPackagePrivateClass() throws Exception {
        String javaCode = "class PackageClass {}";
        File inputFile = createTempJavaFile("PackageClass.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("PackageClass", result.get("name").asText());
        assertEquals("Class", result.get("kind").asText());
        assertFalse(result.get("public").asBoolean());
        assertFalse(result.get("protected").asBoolean());
        assertFalse(result.get("private").asBoolean());
    }

    @Test
    void testStaticNestedClass() throws Exception {
        String javaCode = "public class Outer { public static class StaticNested {} }";
        File inputFile = createTempJavaFile("Outer.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("Outer", result.get("name").asText());
        assertEquals("Class", result.get("kind").asText());

        JsonNode inners = result.get("inners");
        assertEquals(1, inners.size());

        JsonNode nested = inners.get(0);
        assertEquals("StaticNested", nested.get("name").asText());
        assertEquals("Class", nested.get("kind").asText());
        assertTrue(nested.get("public").asBoolean());
        assertTrue(nested.get("static").asBoolean());
    }

    @Test
    void testInnerClass() throws Exception {
        String javaCode = "public class Outer { private class Inner {} }";
        File inputFile = createTempJavaFile("Outer.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode inners = result.get("inners");
        assertEquals(1, inners.size());

        JsonNode inner = inners.get(0);
        assertEquals("Inner", inner.get("name").asText());
        assertEquals("Class", inner.get("kind").asText());
        assertTrue(inner.get("private").asBoolean());
        assertFalse(inner.get("static").asBoolean());
    }

    @Test
    void testNestedInterface() throws Exception {
        String javaCode = "public class Outer { public interface NestedInterface {} }";
        File inputFile = createTempJavaFile("Outer.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode inners = result.get("inners");
        assertEquals(1, inners.size());

        JsonNode nested = inners.get(0);
        assertEquals("NestedInterface", nested.get("name").asText());
        assertEquals("Interface", nested.get("kind").asText());
        assertTrue(nested.get("public").asBoolean());
        assertTrue(nested.get("static").asBoolean());
    }

    @Test
    void testNestedEnum() throws Exception {
        String javaCode = "public class Outer { public enum NestedEnum { VALUE1, VALUE2 } }";
        File inputFile = createTempJavaFile("Outer.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode inners = result.get("inners");
        assertEquals(1, inners.size());

        JsonNode nested = inners.get(0);
        assertEquals("NestedEnum", nested.get("name").asText());
        assertEquals("Enum", nested.get("kind").asText());
        assertTrue(nested.get("public").asBoolean());
        assertTrue(nested.get("static").asBoolean());
    }

    @Test
    void testMultipleNestedTypes() throws Exception {
        String javaCode = "public class Container {\n" + "    public static class StaticClass {}\n"
                + "    private class InnerClass {}\n" + "    protected interface InnerInterface {}\n"
                + "    public enum InnerEnum { A, B }\n" + "    @interface InnerAnnotation {}\n" + "}";
        File inputFile = createTempJavaFile("Container.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode inners = result.get("inners");
        assertEquals(5, inners.size());

        assertEquals("StaticClass", inners.get(0).get("name").asText());
        assertEquals("Class", inners.get(0).get("kind").asText());

        assertEquals("InnerClass", inners.get(1).get("name").asText());
        assertEquals("Class", inners.get(1).get("kind").asText());

        assertEquals("InnerInterface", inners.get(2).get("name").asText());
        assertEquals("Interface", inners.get(2).get("kind").asText());

        assertEquals("InnerEnum", inners.get(3).get("name").asText());
        assertEquals("Enum", inners.get(3).get("kind").asText());

        assertEquals("InnerAnnotation", inners.get(4).get("name").asText());
        assertEquals("Annotation", inners.get(4).get("kind").asText());
    }

    @Test
    void testGenericClass() throws Exception {
        String javaCode = "public class GenericClass<T, U extends Number> {}";
        File inputFile = createTempJavaFile("GenericClass.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("GenericClass", result.get("name").asText());
        assertEquals("Class", result.get("kind").asText());
        assertTrue(result.get("public").asBoolean());
    }

    @Test
    void testGenericInterface() throws Exception {
        String javaCode = "public interface Comparable<T> { int compareTo(T other); }";
        File inputFile = createTempJavaFile("Comparable.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("Comparable", result.get("name").asText());
        assertEquals("Interface", result.get("kind").asText());
        assertTrue(result.get("public").asBoolean());
    }

    @Test
    void testComplexRecord() throws Exception {
        String javaCode = "public record ComplexRecord(String name, int value) {\n" + "    public ComplexRecord {\n"
                + "        if (value < 0) throw new IllegalArgumentException();\n" + "    }\n"
                + "    public String displayName() { return name.toUpperCase(); }\n" + "}";
        File inputFile = createTempJavaFile("ComplexRecord.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("ComplexRecord", result.get("name").asText());
        assertEquals("Record", result.get("kind").asText());

        JsonNode fields = result.get("fields");
        assertEquals(2, fields.size());
        assertEquals("name", fields.get(0).get("name").asText());
        assertEquals("value", fields.get(1).get("name").asText());

        JsonNode methods = result.get("methods");
        assertEquals(1, methods.size());
        assertEquals("displayName", methods.get(0).get("name").asText());
    }

    @Test
    void testEnumWithConstructorAndMethods() throws Exception {
        String javaCode = "public enum Planet {\n" + "    MERCURY(3.303e+23, 2.4397e6),\n"
                + "    VENUS(4.869e+24, 6.0518e6);\n" + "    \n" + "    private final double mass;\n"
                + "    private final double radius;\n" + "    \n" + "    Planet(double mass, double radius) {\n"
                + "        this.mass = mass;\n" + "        this.radius = radius;\n" + "    }\n" + "    \n"
                + "    public double getMass() { return mass; }\n" + "}";
        File inputFile = createTempJavaFile("Planet.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("Planet", result.get("name").asText());
        assertEquals("Enum", result.get("kind").asText());

        JsonNode fields = result.get("fields");
        assertEquals(2, fields.size());

        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode methods = result.get("methods");
        assertEquals(1, methods.size());
        assertEquals("getMass", methods.get(0).get("name").asText());
    }

    @Test
    void testAnnotationWithElements() throws Exception {
        String javaCode = "@interface ComplexAnnotation {\n" + "    String value() default \"\";\n"
                + "    int priority() default 0;\n" + "    Class<?>[] types() default {};\n" + "}";
        File inputFile = createTempJavaFile("ComplexAnnotation.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("ComplexAnnotation", result.get("name").asText());
        assertEquals("Annotation", result.get("kind").asText());

        JsonNode methods = result.get("methods");
        assertEquals(3, methods.size());
        assertEquals("value", methods.get(0).get("name").asText());
        assertEquals("priority", methods.get(1).get("name").asText());
        assertEquals("types", methods.get(2).get("name").asText());
    }

    @Test
    void testTypeWithTrueFlagPruning() throws Exception {
        String javaCode = "public class TestClass { private boolean flag; }";
        File inputFile = createTempJavaFile("TestClass.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-t", "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertTrue(result.get("public").asBoolean());
        assertNull(result.get("private"));
        assertNull(result.get("protected"));
        assertNull(result.get("abstract"));
        assertNull(result.get("final"));
        assertNull(result.get("static"));
    }

    private File createTempJavaFile(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content);
        return file.toFile();
    }
}
