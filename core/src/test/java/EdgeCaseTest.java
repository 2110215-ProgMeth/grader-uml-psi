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

public class EdgeCaseTest {

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
    void testUnicodeClassName() throws Exception {
        String javaCode = "public class 测试类 { private String 姓名; public void 处理() {} }";
        File inputFile = createTempJavaFile("测试类.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("测试类", result.get("name").asText());

        JsonNode fields = result.get("fields");
        assertEquals(1, fields.size());
        assertEquals("姓名", fields.get(0).get("name").asText());

        JsonNode methods = result.get("methods");
        assertEquals(1, methods.size());
        assertEquals("处理", methods.get(0).get("name").asText());
    }

    @Test
    void testComplexGenericWildcards() throws Exception {
        String javaCode = "import java.util.*; " +
                "public class ComplexGenerics<T extends Number & Comparable<T>> { " +
                "private Map<? super T, List<? extends Collection<String>>> data; " +
                "public <U extends T> void process(Map<String, ? super U> input, List<? extends Map<String, ?>> output) {} "
                +
                "}";
        File inputFile = createTempJavaFile("ComplexGenerics.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("ComplexGenerics", result.get("name").asText());

        JsonNode fields = result.get("fields");
        assertEquals(1, fields.size());
        assertTrue(fields.get(0).get("type").asText().contains("Map"));

        JsonNode methods = result.get("methods");
        assertEquals(1, methods.size());
        assertEquals(2, methods.get(0).get("params").size());
    }

    @Test
    void testSelfReferencingType() throws Exception {
        String javaCode = "public class SelfRef<T extends SelfRef<T>> { " +
                "private T self; " +
                "public T getSelf() { return self; } " +
                "public void setSelf(T self) { this.self = self; } " +
                "}";
        File inputFile = createTempJavaFile("SelfRef.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("SelfRef", result.get("name").asText());

        JsonNode fields = result.get("fields");
        assertEquals(1, fields.size());
        assertEquals("T", fields.get(0).get("type").asText());
    }

    @Test
    void testMultipleTypesInSameFile() throws Exception {
        String javaCode = "class First {} " +
                "interface Second {} " +
                "enum Third { A, B } " +
                "@interface Fourth {} " +
                "record Fifth(String data) {} " +
                "public class Main {}";
        File inputFile = createTempJavaFile("Multiple.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("Main", result.get("name").asText());
        assertEquals("Class", result.get("kind").asText());
    }

    @Test
    void testClassWithNoPublicType() throws Exception {
        String javaCode = "class First {} class Second {} class Third {}";
        File inputFile = createTempJavaFile("NoPublic.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("Third", result.get("name").asText());
    }

    @Test
    void testDeeplyNestedGenerics() throws Exception {
        String javaCode = "import java.util.*; " +
                "public class DeepNested { " +
                "private Map<String, List<Set<Map<Integer, List<String>>>>> deepData; " +
                "public Optional<Map<String, List<Optional<String>>>> getDeepOptional() { return null; } " +
                "}";
        File inputFile = createTempJavaFile("DeepNested.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode fields = result.get("fields");
        assertEquals(1, fields.size());
        assertTrue(fields.get(0).get("type").asText().contains("Map"));

        JsonNode methods = result.get("methods");
        assertEquals(1, methods.size());
        assertTrue(methods.get(0).get("returnType").asText().contains("Optional"));
    }

    @Test
    void testMethodWithManyParameters() throws Exception {
        StringBuilder params = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            if (i > 0)
                params.append(", ");
            params.append("String param").append(i);
        }

        String javaCode = "public class ManyParams { " +
                "public void method(" + params + ") {} " +
                "}";
        File inputFile = createTempJavaFile("ManyParams.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode methods = result.get("methods");
        assertEquals(1, methods.size());
        assertEquals(50, methods.get(0).get("params").size());
    }

    @Test
    void testMethodWithManyExceptions() throws Exception {
        String javaCode = "import java.io.*; " +
                "public class ManyExceptions { " +
                "public void dangerous() throws IOException, RuntimeException, Exception, " +
                "IllegalArgumentException, NullPointerException, ClassNotFoundException, " +
                "InterruptedException, InstantiationException {} " +
                "}";
        File inputFile = createTempJavaFile("ManyExceptions.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode methods = result.get("methods");
        assertEquals(1, methods.size());
        assertEquals(8, methods.get(0).get("throws").size());
    }

    @Test
    void testComplexAnnotationValues() throws Exception {
        String javaCode = "@interface ComplexAnno { " +
                "String[] values() default {\"a\", \"b\", \"c\"}; " +
                "Class<?>[] types() default {String.class, Integer.class}; " +
                "int[][] matrix() default {{1, 2}, {3, 4}}; " +
                "} " +
                "@ComplexAnno(values = {\"x\", \"y\"}, types = {Object.class}) " +
                "public class AnnotatedComplex {}";
        File inputFile = createTempJavaFile("AnnotatedComplex.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode annotations = result.get("annotations");
        assertEquals(1, annotations.size());
        assertEquals("ComplexAnno", annotations.get(0).asText());
    }

    @Test
    void testVeryDeeplyNestedClass() throws Exception {
        StringBuilder javaCode = new StringBuilder("public class Level0 {");
        for (int i = 1; i <= 20; i++) {
            javaCode.append(" public static class Level").append(i).append(" {");
        }
        javaCode.append(" private String deepestField;");
        for (int i = 20; i >= 0; i--) {
            javaCode.append(" }");
        }

        File inputFile = createTempJavaFile("DeepNesting.java", javaCode.toString());
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("Level0", result.get("name").asText());

        JsonNode current = result;
        for (int i = 1; i <= 20; i++) {
            JsonNode inners = current.get("inners");
            assertEquals(1, inners.size());
            current = inners.get(0);
            assertEquals("Level" + i, current.get("name").asText());
        }

        JsonNode deepestFields = current.get("fields");
        assertEquals(1, deepestFields.size());
        assertEquals("deepestField", deepestFields.get(0).get("name").asText());
    }

    @Test
    void testCircularGenericConstraints() throws Exception {
        String javaCode = "class A<T extends B<T>> {} " +
                "class B<U extends A<U>> {} " +
                "public class CircularGeneric extends A<CircularGeneric> {}";
        File inputFile = createTempJavaFile("CircularGeneric.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("CircularGeneric", result.get("name").asText());
        JsonNode extendsArray = result.get("extends");
        assertEquals(1, extendsArray.size());
        assertTrue(extendsArray.get(0).asText().contains("A"));
    }

    @Test
    void testMultiDimensionalArrays() throws Exception {
        String javaCode = "public class MultiArrays { " +
                "private int[][][] cube; " +
                "private String[][][][][][] hyperCube; " +
                "public void process(int[][][] input, String[][][][] output) {} " +
                "}";
        File inputFile = createTempJavaFile("MultiArrays.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode fields = result.get("fields");
        assertEquals(2, fields.size());
        assertEquals("int[][][]", fields.get(0).get("type").asText());
        assertEquals("String[][][][][][]", fields.get(1).get("type").asText());

        JsonNode methods = result.get("methods");
        assertEquals(1, methods.size());
        JsonNode params = methods.get(0).get("params");
        assertEquals(2, params.size());
        assertEquals("int[][][]", params.get(0).asText());
        assertEquals("String[][][][]", params.get(1).asText());
    }

    @Test
    void testExtremelyLongMethodName() throws Exception {
        String longMethodName = "veryLongMethodName" + "AndEvenLonger".repeat(50);
        String javaCode = "public class LongMethod { " +
                "public void " + longMethodName + "() {} " +
                "}";
        File inputFile = createTempJavaFile("LongMethod.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode methods = result.get("methods");
        assertEquals(1, methods.size());
        assertEquals(longMethodName, methods.get(0).get("name").asText());
    }

    @Test
    void testEmptyRecord() throws Exception {
        String javaCode = "public record EmptyRecord() {}";
        File inputFile = createTempJavaFile("EmptyRecord.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("EmptyRecord", result.get("name").asText());
        assertEquals("Record", result.get("kind").asText());
        JsonNode fields = result.get("fields");
        assertEquals(0, fields.size());
    }

    @Test
    void testEmptyEnum() throws Exception {
        String javaCode = "public enum EmptyEnum {}";
        File inputFile = createTempJavaFile("EmptyEnum.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("EmptyEnum", result.get("name").asText());
        assertEquals("Enum", result.get("kind").asText());
    }

    @Test
    void testEmptyInterface() throws Exception {
        String javaCode = "public interface EmptyInterface {}";
        File inputFile = createTempJavaFile("EmptyInterface.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("EmptyInterface", result.get("name").asText());
        assertEquals("Interface", result.get("kind").asText());
        JsonNode methods = result.get("methods");
        assertEquals(0, methods.size());
    }

    @Test
    void testEmptyAnnotation() throws Exception {
        String javaCode = "@interface EmptyAnnotation {}";
        File inputFile = createTempJavaFile("EmptyAnnotation.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("EmptyAnnotation", result.get("name").asText());
        assertEquals("Annotation", result.get("kind").asText());
        JsonNode methods = result.get("methods");
        assertEquals(0, methods.size());
    }

    @Test
    void testSpecialCharactersInStringLiterals() throws Exception {
        String javaCode = "public class SpecialChars { " +
                "private String special = \"\\n\\t\\r\\\\\\\"\"; " +
                "private String unicode = \"\\u0041\\u0042\\u0043\"; " +
                "}";
        File inputFile = createTempJavaFile("SpecialChars.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode fields = result.get("fields");
        assertEquals(2, fields.size());
        assertEquals("special", fields.get(0).get("name").asText());
        assertEquals("unicode", fields.get(1).get("name").asText());
    }

    @Test
    void testMixedStaticAndNonStaticWithSameName() throws Exception {
        String javaCode = "public class MixedStatic { " +
                "public static void process() {} " +
                "public void process(String input) {} " +
                "public static class Inner {} " +
                "public class Inner2 {} " +
                "}";
        File inputFile = createTempJavaFile("MixedStatic.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode methods = result.get("methods");
        assertEquals(2, methods.size());
        assertEquals("process", methods.get(0).get("name").asText());
        assertEquals("process", methods.get(1).get("name").asText());
        assertTrue(methods.get(0).get("static").asBoolean());
        assertFalse(methods.get(1).get("static").asBoolean());

        JsonNode inners = result.get("inners");
        assertEquals(2, inners.size());
        assertTrue(inners.get(0).get("static").asBoolean());
        assertFalse(inners.get(1).get("static").asBoolean());
    }

    @Test
    void testMaximalModifierCombinations() throws Exception {
        String javaCode = "public abstract class MaxModifiers { " +
                "public static final synchronized native void nativeMethod(); " +
                "private static final transient volatile String field = \"test\"; " +
                "protected abstract void abstractMethod(); " +
                "}";
        File inputFile = createTempJavaFile("MaxModifiers.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertTrue(result.get("abstract").asBoolean());

        JsonNode methods = result.get("methods");
        assertEquals(2, methods.size());

        JsonNode nativeMethod = methods.get(0);
        assertTrue(nativeMethod.get("public").asBoolean());
        assertTrue(nativeMethod.get("static").asBoolean());
        assertTrue(nativeMethod.get("final").asBoolean());
        assertTrue(nativeMethod.get("synchronized").asBoolean());
        assertTrue(nativeMethod.get("native").asBoolean());

        JsonNode fields = result.get("fields");
        assertEquals(1, fields.size());
        JsonNode field = fields.get(0);
        assertTrue(field.get("private").asBoolean());
        assertTrue(field.get("static").asBoolean());
        assertTrue(field.get("final").asBoolean());
        assertTrue(field.get("transient").asBoolean());
        assertTrue(field.get("volatile").asBoolean());
    }

    @Test
    void testSealedClassHierarchy() throws Exception {
        String javaCode = "public sealed class Shape permits Circle, Rectangle, Triangle {" +
                "    protected abstract double area();" +
                "}" +
                "final class Circle extends Shape {" +
                "    private double radius;" +
                "    protected double area() { return Math.PI * radius * radius; }" +
                "}" +
                "non-sealed class Rectangle extends Shape {" +
                "    protected double area() { return 0; }" +
                "}" +
                "sealed class Triangle extends Shape permits EquilateralTriangle {" +
                "    protected double area() { return 0; }" +
                "}" +
                "final class EquilateralTriangle extends Triangle {" +
                "    protected double area() { return 0; }" +
                "}";
        File inputFile = createTempJavaFile("Shape.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("Shape", result.get("name").asText());
        assertEquals("Class", result.get("kind").asText());
        assertTrue(result.get("abstract").asBoolean());
        assertTrue(result.get("public").asBoolean());
    }

    @Test
    void testAnonymousClassInField() throws Exception {
        String javaCode = "public class Test {" +
                "    private Runnable task = new Runnable() {" +
                "        private String name = \"anonymous\";" +
                "        public void run() {" +
                "            System.out.println(name);" +
                "        }" +
                "        private void helper() {}" +
                "    };" +
                "    private Comparable<String> comp = new Comparable<String>() {" +
                "        public int compareTo(String o) { return 0; }" +
                "    };" +
                "}";
        File inputFile = createTempJavaFile("Test.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode fields = result.get("fields");
        assertEquals(2, fields.size());
        assertEquals("task", fields.get(0).get("name").asText());
        assertEquals("Runnable", fields.get(0).get("type").asText());
        assertEquals("comp", fields.get(1).get("name").asText());
        assertEquals("Comparable<String>", fields.get(1).get("type").asText());
    }

    @Test
    void testLocalClassInMethod() throws Exception {
        String javaCode = "public class OuterClass {" +
                "    public void outerMethod() {" +
                "        class LocalClass {" +
                "            private int localField;" +
                "            public void localMethod() {}" +
                "            class NestedLocal {" +
                "                void deepMethod() {}" +
                "            }" +
                "        }" +
                "        LocalClass local = new LocalClass();" +
                "    }" +
                "    private class RegularInner {" +
                "        void regularMethod() {}" +
                "    }" +
                "}";
        File inputFile = createTempJavaFile("OuterClass.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("OuterClass", result.get("name").asText());

        JsonNode methods = result.get("methods");
        assertEquals(1, methods.size());
        assertEquals("outerMethod", methods.get(0).get("name").asText());

        JsonNode inners = result.get("inners");
        assertEquals(1, inners.size());
        assertEquals("RegularInner", inners.get(0).get("name").asText());
    }

    @Test
    void testTextBlocksInFields() throws Exception {
        String javaCode = "public class TextBlockTest {" +
                "    private String query = \"\"\"" +
                "        SELECT id, name" +
                "        FROM users" +
                "        WHERE active = true" +
                "        \"\"\";" +
                "    private String json = \"\"\"" +
                "        {" +
                "          \"name\": \"John\"," +
                "          \"age\": 30" +
                "        }" +
                "        \"\"\";" +
                "    public String getFormattedText() {" +
                "        return \"\"\"" +
                "            Hello %s," +
                "            Welcome to our service!" +
                "            \"\"\".formatted(\"User\");" +
                "    }" +
                "}";
        File inputFile = createTempJavaFile("TextBlockTest.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode fields = result.get("fields");
        assertEquals(2, fields.size());
        assertEquals("query", fields.get(0).get("name").asText());
        assertEquals("json", fields.get(1).get("name").asText());

        JsonNode methods = result.get("methods");
        assertEquals(1, methods.size());
        assertEquals("getFormattedText", methods.get(0).get("name").asText());
    }

    @Test
    void testRecordWithComplexComponents() throws Exception {
        String javaCode = "import java.util.*;" +
                "public record ComplexRecord(" +
                "    String name," +
                "    List<Map<String, Integer>> data," +
                "    Object... extras" +
                ") implements Comparable<ComplexRecord> {" +
                "    public ComplexRecord {" +
                "        Objects.requireNonNull(name);" +
                "        if (data == null) data = List.of();" +
                "    }" +
                "    public ComplexRecord(String name) {" +
                "        this(name, List.of());" +
                "    }" +
                "    public int compareTo(ComplexRecord other) {" +
                "        return name.compareTo(other.name);" +
                "    }" +
                "    public static ComplexRecord empty() {" +
                "        return new ComplexRecord(\"\");" +
                "    }" +
                "    private static final String DEFAULT_NAME = \"default\";" +
                "}";
        File inputFile = createTempJavaFile("ComplexRecord.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("ComplexRecord", result.get("name").asText());
        assertEquals("Record", result.get("kind").asText());
        assertTrue(result.get("final").asBoolean());

        JsonNode fields = result.get("fields");
        assertEquals(4, fields.size());
        assertEquals("name", fields.get(0).get("name").asText());
        assertEquals("data", fields.get(1).get("name").asText());
        assertEquals("extras", fields.get(2).get("name").asText());
        assertEquals("DEFAULT_NAME", fields.get(3).get("name").asText());

        JsonNode implements_ = result.get("implements");
        assertEquals(1, implements_.size());
        assertEquals("Comparable<ComplexRecord>", implements_.get(0).asText());

        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());

        JsonNode methods = result.get("methods");
        assertEquals(2, methods.size());
    }

    @Test
    void testEnumWithComplexConstructors() throws Exception {
        String javaCode = "public enum HttpStatus {" +
                "    OK(200, \"OK\", true)," +
                "    NOT_FOUND(404, \"Not Found\", false) {" +
                "        @Override" +
                "        public String getCustomMessage() {" +
                "            return \"Resource not found\";" +
                "        }" +
                "    }," +
                "    INTERNAL_ERROR(500, \"Internal Server Error\", false) {" +
                "        @Override" +
                "        public String getCustomMessage() {" +
                "            return \"Server error occurred\";" +
                "        }" +
                "        private void logError() {" +
                "            System.err.println(\"Error logged\");" +
                "        }" +
                "    };" +
                "    " +
                "    private final int code;" +
                "    private final String message;" +
                "    private final boolean success;" +
                "    " +
                "    HttpStatus(int code, String message, boolean success) {" +
                "        this.code = code;" +
                "        this.message = message;" +
                "        this.success = success;" +
                "    }" +
                "    " +
                "    public int getCode() { return code; }" +
                "    public String getMessage() { return message; }" +
                "    public boolean isSuccess() { return success; }" +
                "    public String getCustomMessage() { return message; }" +
                "    " +
                "    public static HttpStatus fromCode(int code) {" +
                "        for (HttpStatus status : values()) {" +
                "            if (status.code == code) return status;" +
                "        }" +
                "        return null;" +
                "    }" +
                "}";
        File inputFile = createTempJavaFile("HttpStatus.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("HttpStatus", result.get("name").asText());
        assertEquals("Enum", result.get("kind").asText());

        JsonNode fields = result.get("fields");
        assertEquals(3, fields.size());
        assertEquals("code", fields.get(0).get("name").asText());
        assertEquals("message", fields.get(1).get("name").asText());
        assertEquals("success", fields.get(2).get("name").asText());

        JsonNode constructors = result.get("constructors");
        assertEquals(1, constructors.size());
        assertEquals(3, constructors.get(0).get("params").size());

        JsonNode methods = result.get("methods");
        assertEquals(5, methods.size());
    }

    @Test
    void testInterfaceWithDefaultAndStaticMethods() throws Exception {
        String javaCode = "public interface AdvancedInterface<T extends Comparable<T>> {" +
                "    T process(T input);" +
                "    " +
                "    default T processWithDefault(T input) {" +
                "        System.out.println(\"Processing: \" + input);" +
                "        return process(input);" +
                "    }" +
                "    " +
                "    default void log(String message) {" +
                "        System.out.println(getClass().getSimpleName() + \": \" + message);" +
                "    }" +
                "    " +
                "    static <U> void staticHelper(U item) {" +
                "        System.out.println(\"Static helper: \" + item);" +
                "    }" +
                "    " +
                "    static String getVersion() {" +
                "        return \"1.0\";" +
                "    }" +
                "    " +
                "    private void privateHelper() {" +
                "        System.out.println(\"Private helper\");" +
                "    }" +
                "    " +
                "    private static final String CONSTANT = \"INTERFACE_CONSTANT\";" +
                "    " +
                "    interface NestedInterface {" +
                "        void nestedMethod();" +
                "    }" +
                "    " +
                "    class NestedClass implements NestedInterface {" +
                "        public void nestedMethod() {}" +
                "    }" +
                "}";
        File inputFile = createTempJavaFile("AdvancedInterface.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("AdvancedInterface", result.get("name").asText());
        assertEquals("Interface", result.get("kind").asText());

        JsonNode methods = result.get("methods");
        assertEquals(6, methods.size());

        boolean hasAbstract = false, hasDefault = false, hasStatic = false, hasPrivate = false;
        for (JsonNode method : methods) {
            if (method.get("abstract").asBoolean())
                hasAbstract = true;
            if (method.get("default").asBoolean())
                hasDefault = true;
            if (method.get("static").asBoolean())
                hasStatic = true;
            if (method.get("private").asBoolean())
                hasPrivate = true;
        }
        assertTrue(hasAbstract);
        assertTrue(hasDefault);
        assertTrue(hasStatic);
        assertTrue(hasPrivate);

        JsonNode inners = result.get("inners");
        assertEquals(2, inners.size());
    }

    @Test
    void testAnnotationWithComplexDefaultValues() throws Exception {
        String javaCode = "import java.lang.annotation.*;" +
                "@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})" +
                "@Retention(RetentionPolicy.RUNTIME)" +
                "@Repeatable(ConfigList.class)" +
                "public @interface Config {" +
                "    String name() default \"\";" +
                "    String[] values() default {};" +
                "    int priority() default 0;" +
                "    Class<?>[] types() default {Object.class};" +
                "    ElementType target() default ElementType.TYPE;" +
                "    boolean enabled() default true;" +
                "    long timeout() default 5000L;" +
                "    double ratio() default 1.0;" +
                "    char separator() default ',';" +
                "    NestedEnum level() default NestedEnum.INFO;" +
                "    " +
                "    enum NestedEnum {" +
                "        DEBUG, INFO, WARN, ERROR;" +
                "        private String description;" +
                "        public String getDescription() { return description; }" +
                "    }" +
                "    " +
                "    @interface NestedAnnotation {" +
                "        String value() default \"nested\";" +
                "    }" +
                "}" +
                "@Target(ElementType.TYPE)" +
                "@Retention(RetentionPolicy.RUNTIME)" +
                "@interface ConfigList {" +
                "    Config[] value();" +
                "}";
        File inputFile = createTempJavaFile("Config.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("Config", result.get("name").asText());
        assertEquals("Annotation", result.get("kind").asText());

        JsonNode annotations = result.get("annotations");
        assertEquals(3, annotations.size());

        JsonNode methods = result.get("methods");
        assertEquals(10, methods.size());

        for (JsonNode method : methods) {
            assertTrue(method.has("default"));
        }

        JsonNode inners = result.get("inners");
        assertEquals(2, inners.size());
        assertEquals("NestedEnum", inners.get(0).get("name").asText());
        assertEquals("NestedAnnotation", inners.get(1).get("name").asText());
    }

    @Test
    void testComplexGenericBounds() throws Exception {
        String javaCode = "import java.io.Serializable;" +
                "import java.util.*;" +
                "public class GenericBounds<" +
                "    T extends Number & Comparable<T> & Serializable," +
                "    U extends List<? super T>," +
                "    V extends Map<String, ? extends Collection<T>>," +
                "    W extends GenericBounds<T, U, V, W>" +
                "> implements Comparable<GenericBounds<T, U, V, W>> {" +
                "    " +
                "    private T numericValue;" +
                "    private U listValue;" +
                "    private V mapValue;" +
                "    private W selfReference;" +
                "    " +
                "    public <X extends T, Y extends Collection<? super X>> " +
                "    void complexMethod(X item, Y collection, Map<? extends String, ? super Y> map) {}" +
                "    " +
                "    public <A, B extends A, C extends List<B>> " +
                "    C chainedGenerics(A a, B b) { return null; }" +
                "    " +
                "    public int compareTo(GenericBounds<T, U, V, W> other) { return 0; }" +
                "    " +
                "    public static class Builder<" +
                "        T extends Number & Comparable<T> & Serializable," +
                "        U extends List<? super T>," +
                "        V extends Map<String, ? extends Collection<T>>," +
                "        W extends GenericBounds<T, U, V, W>" +
                "    > {" +
                "        public GenericBounds<T, U, V, W> build() { return null; }" +
                "    }" +
                "}";
        File inputFile = createTempJavaFile("GenericBounds.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("GenericBounds", result.get("name").asText());
        assertEquals("Class", result.get("kind").asText());

        JsonNode fields = result.get("fields");
        assertEquals(4, fields.size());
        assertEquals("T", fields.get(0).get("type").asText());
        assertEquals("U", fields.get(1).get("type").asText());
        assertEquals("V", fields.get(2).get("type").asText());
        assertEquals("W", fields.get(3).get("type").asText());

        JsonNode methods = result.get("methods");
        assertEquals(3, methods.size());

        JsonNode inners = result.get("inners");
        assertEquals(1, inners.size());
        assertEquals("Builder", inners.get(0).get("name").asText());
    }

    @Test
    void testVarArgsWithGenerics() throws Exception {
        String javaCode = "import java.util.*;" +
                "public class VarArgsTest {" +
                "    @SafeVarargs" +
                "    public final <T> void safeVarArgs(T... items) {}" +
                "    " +
                "    @SafeVarargs" +
                "    public static <T> List<T> listOf(T... elements) {" +
                "        return Arrays.asList(elements);" +
                "    }" +
                "    " +
                "    public <T extends Number> void numbersOnly(T... numbers) {}" +
                "    " +
                "    public void mixedParams(String prefix, int count, Object... extras) {}" +
                "    " +
                "    public <T> void genericArrays(T[] array, T... varargs) {}" +
                "    " +
                "    @SafeVarargs" +
                "    public static <T> void deepVarArgs(List<T>... lists) {}" +
                "}";
        File inputFile = createTempJavaFile("VarArgsTest.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode methods = result.get("methods");
        assertEquals(6, methods.size());

        for (JsonNode method : methods) {
            JsonNode params = method.get("params");
            if (params.size() > 0) {
                String lastParam = params.get(params.size() - 1).asText();
                if (method.get("name").asText().contains("VarArgs") ||
                        method.get("name").asText().equals("mixedParams") ||
                        method.get("name").asText().equals("genericArrays")) {
                    assertTrue(lastParam.contains("...") || lastParam.contains("[]"));
                }
            }
        }
    }

    @Test
    void testSwitchExpressions() throws Exception {
        String javaCode = "public class SwitchTest {" +
                "    public String processDay(int day) {" +
                "        return switch (day) {" +
                "            case 1, 2, 3, 4, 5 -> \"Weekday\";" +
                "            case 6, 7 -> \"Weekend\";" +
                "            default -> \"Invalid day\";" +
                "        };" +
                "    }" +
                "    " +
                "    public int calculate(String operation, int a, int b) {" +
                "        return switch (operation) {" +
                "            case \"add\" -> a + b;" +
                "            case \"subtract\" -> a - b;" +
                "            case \"multiply\" -> {" +
                "                System.out.println(\"Multiplying\");" +
                "                yield a * b;" +
                "            }" +
                "            default -> throw new IllegalArgumentException(\"Unknown operation\");" +
                "        };" +
                "    }" +
                "}";
        File inputFile = createTempJavaFile("SwitchTest.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode methods = result.get("methods");
        assertEquals(2, methods.size());
        assertEquals("processDay", methods.get(0).get("name").asText());
        assertEquals("calculate", methods.get(1).get("name").asText());
    }

    @Test
    void testTypeInferenceWithVar() throws Exception {
        String javaCode = "import java.util.*;" +
                "public class VarTest {" +
                "    public void demonstrateVar() {" +
                "        var list = new ArrayList<String>();" +
                "        var map = Map.of(\"key\", \"value\");" +
                "        var stream = list.stream();" +
                "        " +
                "        for (var item : list) {" +
                "            System.out.println(item);" +
                "        }" +
                "        " +
                "        try (var scanner = new Scanner(System.in)) {" +
                "            var input = scanner.nextLine();" +
                "        }" +
                "    }" +
                "    " +
                "    private Map<String, Integer> processData() {" +
                "        var result = new HashMap<String, Integer>();" +
                "        return result;" +
                "    }" +
                "}";
        File inputFile = createTempJavaFile("VarTest.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");

        StructureExtractor.main(new String[] { "-o", outputFile.toString(), inputFile.getAbsolutePath() });

        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode methods = result.get("methods");
        assertEquals(2, methods.size());
        assertEquals("demonstrateVar", methods.get(0).get("name").asText());
        assertEquals("processData", methods.get(1).get("name").asText());
        assertEquals("Map<String,Integer>", methods.get(1).get("returnType").asText());
    }

    private File createTempJavaFile(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content);
        return file.toFile();
    }
}