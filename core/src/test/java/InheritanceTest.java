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

public class InheritanceTest {
    
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
    void testNoInheritance() throws Exception {
        String javaCode = "public class SimpleClass {}";
        File inputFile = createTempJavaFile("SimpleClass.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode extendsArray = result.get("extends");
        JsonNode implementsArray = result.get("implements");
        
        assertEquals(0, extendsArray.size());
        assertEquals(0, implementsArray.size());
    }

    @Test
    void testSimpleClassInheritance() throws Exception {
        String javaCode = "class BaseClass {}\npublic class DerivedClass extends BaseClass {}";
        File inputFile = createTempJavaFile("DerivedClass.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("DerivedClass", result.get("name").asText());
        
        JsonNode extendsArray = result.get("extends");
        assertEquals(1, extendsArray.size());
        assertEquals("BaseClass", extendsArray.get(0).asText());
        
        JsonNode implementsArray = result.get("implements");
        assertEquals(0, implementsArray.size());
    }

    @Test
    void testSingleInterfaceImplementation() throws Exception {
        String javaCode = "interface TestInterface {}\npublic class TestClass implements TestInterface {}";
        File inputFile = createTempJavaFile("TestClass.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("TestClass", result.get("name").asText());
        
        JsonNode extendsArray = result.get("extends");
        assertEquals(0, extendsArray.size());
        
        JsonNode implementsArray = result.get("implements");
        assertEquals(1, implementsArray.size());
        assertEquals("TestInterface", implementsArray.get(0).asText());
    }

    @Test
    void testMultipleInterfaceImplementation() throws Exception {
        String javaCode = "interface FirstInterface {}\n" +
            "interface SecondInterface {}\n" +
            "interface ThirdInterface {}\n" +
            "public class MultiImpl implements FirstInterface, SecondInterface, ThirdInterface {}";
        File inputFile = createTempJavaFile("MultiImpl.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("MultiImpl", result.get("name").asText());
        
        JsonNode implementsArray = result.get("implements");
        assertEquals(3, implementsArray.size());
        assertEquals("FirstInterface", implementsArray.get(0).asText());
        assertEquals("SecondInterface", implementsArray.get(1).asText());
        assertEquals("ThirdInterface", implementsArray.get(2).asText());
    }

    @Test
    void testClassInheritanceWithInterfaceImplementation() throws Exception {
        String javaCode = "class BaseClass {}\n" +
            "interface FirstInterface {}\n" +
            "interface SecondInterface {}\n" +
            "public class ComplexClass extends BaseClass implements FirstInterface, SecondInterface {}";
        File inputFile = createTempJavaFile("ComplexClass.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("ComplexClass", result.get("name").asText());
        
        JsonNode extendsArray = result.get("extends");
        assertEquals(1, extendsArray.size());
        assertEquals("BaseClass", extendsArray.get(0).asText());
        
        JsonNode implementsArray = result.get("implements");
        assertEquals(2, implementsArray.size());
        assertEquals("FirstInterface", implementsArray.get(0).asText());
        assertEquals("SecondInterface", implementsArray.get(1).asText());
    }

    @Test
    void testInterfaceExtendingInterface() throws Exception {
        String javaCode = "interface BaseInterface {}\npublic interface ExtendedInterface extends BaseInterface {}";
        File inputFile = createTempJavaFile("ExtendedInterface.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("ExtendedInterface", result.get("name").asText());
        assertEquals("Interface", result.get("kind").asText());
        
        JsonNode extendsArray = result.get("extends");
        assertEquals(1, extendsArray.size());
        assertEquals("BaseInterface", extendsArray.get(0).asText());
        
        JsonNode implementsArray = result.get("implements");
        assertEquals(0, implementsArray.size());
    }

    @Test
    void testInterfaceExtendingMultipleInterfaces() throws Exception {
        String javaCode = "interface FirstInterface {}\n" +
            "interface SecondInterface {}\n" +
            "public interface MultiExtendedInterface extends FirstInterface, SecondInterface {}";
        File inputFile = createTempJavaFile("MultiExtendedInterface.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("MultiExtendedInterface", result.get("name").asText());
        assertEquals("Interface", result.get("kind").asText());
        
        JsonNode extendsArray = result.get("extends");
        assertEquals(2, extendsArray.size());
        assertEquals("FirstInterface", extendsArray.get(0).asText());
        assertEquals("SecondInterface", extendsArray.get(1).asText());
    }

    @Test
    void testGenericClassInheritance() throws Exception {
        String javaCode = "import java.util.ArrayList;\npublic class StringList extends ArrayList<String> {}";
        File inputFile = createTempJavaFile("StringList.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("StringList", result.get("name").asText());
        
        JsonNode extendsArray = result.get("extends");
        assertEquals(1, extendsArray.size());
        assertEquals("ArrayList<String>", extendsArray.get(0).asText());
    }

    @Test
    void testGenericInterfaceImplementation() throws Exception {
        String javaCode = "import java.util.List;\npublic class CustomList implements List<Integer> {\n" +
            "    public int size() { return 0; }\n" +
            "    public boolean isEmpty() { return true; }\n" +
            "    public boolean contains(Object o) { return false; }\n" +
            "    public java.util.Iterator<Integer> iterator() { return null; }\n" +
            "    public Object[] toArray() { return new Object[0]; }\n" +
            "    public <T> T[] toArray(T[] a) { return a; }\n" +
            "    public boolean add(Integer integer) { return false; }\n" +
            "    public boolean remove(Object o) { return false; }\n" +
            "    public boolean containsAll(java.util.Collection<?> c) { return false; }\n" +
            "    public boolean addAll(java.util.Collection<? extends Integer> c) { return false; }\n" +
            "    public boolean addAll(int index, java.util.Collection<? extends Integer> c) { return false; }\n" +
            "    public boolean removeAll(java.util.Collection<?> c) { return false; }\n" +
            "    public boolean retainAll(java.util.Collection<?> c) { return false; }\n" +
            "    public void clear() {}\n" +
            "    public Integer get(int index) { return null; }\n" +
            "    public Integer set(int index, Integer element) { return null; }\n" +
            "    public void add(int index, Integer element) {}\n" +
            "    public Integer remove(int index) { return null; }\n" +
            "    public int indexOf(Object o) { return -1; }\n" +
            "    public int lastIndexOf(Object o) { return -1; }\n" +
            "    public java.util.ListIterator<Integer> listIterator() { return null; }\n" +
            "    public java.util.ListIterator<Integer> listIterator(int index) { return null; }\n" +
            "    public List<Integer> subList(int fromIndex, int toIndex) { return null; }\n" +
            "}";
        File inputFile = createTempJavaFile("CustomList.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("CustomList", result.get("name").asText());
        
        JsonNode implementsArray = result.get("implements");
        assertEquals(1, implementsArray.size());
        assertEquals("List<Integer>", implementsArray.get(0).asText());
    }

    @Test
    void testComplexGenericInheritance() throws Exception {
        String javaCode = "import java.util.Map;\n" +
            "import java.util.HashMap;\n" +
            "public class StringIntMap extends HashMap<String, Integer> implements Map<String, Integer> {}";
        File inputFile = createTempJavaFile("StringIntMap.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("StringIntMap", result.get("name").asText());
        
        JsonNode extendsArray = result.get("extends");
        assertEquals(1, extendsArray.size());
        assertEquals("HashMap<String,Integer>", extendsArray.get(0).asText());
        
        JsonNode implementsArray = result.get("implements");
        assertEquals(1, implementsArray.size());
        assertEquals("Map<String,Integer>", implementsArray.get(0).asText());
    }

    @Test
    void testAbstractClassInheritance() throws Exception {
        String javaCode = "abstract class AbstractBase {\n" +
            "    public abstract void process();\n" +
            "}\n" +
            "public class ConcreteImpl extends AbstractBase {\n" +
            "    public void process() {}\n" +
            "}";
        File inputFile = createTempJavaFile("ConcreteImpl.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("ConcreteImpl", result.get("name").asText());
        
        JsonNode extendsArray = result.get("extends");
        assertEquals(1, extendsArray.size());
        assertEquals("AbstractBase", extendsArray.get(0).asText());
    }

    @Test
    void testInheritanceWithJavaLangClasses() throws Exception {
        String javaCode = "public class CustomException extends RuntimeException {\n" +
            "    public CustomException(String message) {\n" +
            "        super(message);\n" +
            "    }\n" +
            "}";
        File inputFile = createTempJavaFile("CustomException.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("CustomException", result.get("name").asText());
        
        JsonNode extendsArray = result.get("extends");
        assertEquals(1, extendsArray.size());
        assertEquals("RuntimeException", extendsArray.get(0).asText());
    }

    @Test
    void testSerializableImplementation() throws Exception {
        String javaCode = "import java.io.Serializable;\npublic class SerializableClass implements Serializable {\n" +
            "    private static final long serialVersionUID = 1L;\n" +
            "    private String data;\n" +
            "}";
        File inputFile = createTempJavaFile("SerializableClass.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("SerializableClass", result.get("name").asText());
        
        JsonNode implementsArray = result.get("implements");
        assertEquals(1, implementsArray.size());
        assertEquals("Serializable", implementsArray.get(0).asText());
    }

    @Test
    void testFunctionalInterfaceImplementation() throws Exception {
        String javaCode = "import java.util.function.Function;\n" +
            "public class StringProcessor implements Function<String, String> {\n" +
            "    public String apply(String input) {\n" +
            "        return input.toUpperCase();\n" +
            "    }\n" +
            "}";
        File inputFile = createTempJavaFile("StringProcessor.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("StringProcessor", result.get("name").asText());
        
        JsonNode implementsArray = result.get("implements");
        assertEquals(1, implementsArray.size());
        assertEquals("Function<String,String>", implementsArray.get(0).asText());
    }

    @Test
    void testNestedClassInheritance() throws Exception {
        String javaCode = "public class Outer {\n" +
            "    static class BaseNested {}\n" +
            "    public static class DerivedNested extends BaseNested {}\n" +
            "}";
        File inputFile = createTempJavaFile("Outer.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode inners = result.get("inners");
        assertEquals(2, inners.size());
        
        JsonNode derivedNested = inners.get(1);
        assertEquals("DerivedNested", derivedNested.get("name").asText());
        
        JsonNode extendsArray = derivedNested.get("extends");
        assertEquals(1, extendsArray.size());
        assertEquals("BaseNested", extendsArray.get(0).asText());
    }

    @Test
    void testWildcardGenericInheritance() throws Exception {
        String javaCode = "import java.util.List;\n" +
            "abstract class BaseProcessor {\n" +
            "    abstract void process(List<?> items);\n" +
            "}\n" +
            "public class ConcreteProcessor extends BaseProcessor {\n" +
            "    void process(List<?> items) {}\n" +
            "}";
        File inputFile = createTempJavaFile("ConcreteProcessor.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("ConcreteProcessor", result.get("name").asText());
        
        JsonNode extendsArray = result.get("extends");
        assertEquals(1, extendsArray.size());
        assertEquals("BaseProcessor", extendsArray.get(0).asText());
    }

    @Test
    void testInheritanceWithTrueFlagPruning() throws Exception {
        String javaCode = "interface TestInterface {}\n" +
            "public class TestClass implements TestInterface {}";
        File inputFile = createTempJavaFile("TestClass.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-t", "-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        JsonNode implementsArray = result.get("implements");
        assertEquals(1, implementsArray.size());
        assertEquals("TestInterface", implementsArray.get(0).asText());
    }

    @Test
    void testEnumInheritance() throws Exception {
        String javaCode = "public enum Status { ACTIVE, INACTIVE }";
        File inputFile = createTempJavaFile("Status.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("Status", result.get("name").asText());
        assertEquals("Enum", result.get("kind").asText());
        
        JsonNode extendsArray = result.get("extends");
        JsonNode implementsArray = result.get("implements");
        assertEquals(0, extendsArray.size());
        assertEquals(0, implementsArray.size());
    }

    @Test
    void testRecordInheritance() throws Exception {
        String javaCode = "import java.io.Serializable;\n" +
            "public record PersonRecord(String name, int age) implements Serializable {}";
        File inputFile = createTempJavaFile("PersonRecord.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("PersonRecord", result.get("name").asText());
        assertEquals("Record", result.get("kind").asText());
        
        JsonNode extendsArray = result.get("extends");
        JsonNode implementsArray = result.get("implements");
        assertEquals(0, extendsArray.size());
        assertEquals(1, implementsArray.size());
        assertEquals("Serializable", implementsArray.get(0).asText());
    }

    @Test
    void testComplexInheritanceHierarchy() throws Exception {
        String javaCode = "import java.io.Serializable;\n" +
            "import java.util.List;\n" +
            "abstract class AbstractProcessor implements Serializable {\n" +
            "    abstract void process();\n" +
            "}\n" +
            "interface Configurable {\n" +
            "    void configure();\n" +
            "}\n" +
            "interface Monitorable {\n" +
            "    void monitor();\n" +
            "}\n" +
            "public class DataProcessor extends AbstractProcessor implements Configurable, Monitorable {\n" +
            "    void process() {}\n" +
            "    public void configure() {}\n" +
            "    public void monitor() {}\n" +
            "}";
        File inputFile = createTempJavaFile("DataProcessor.java", javaCode);
        Path outputFile = tempDir.resolve("output.json");
        
        StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});
        
        JsonNode result = mapper.readTree(Files.readString(outputFile));
        assertEquals("DataProcessor", result.get("name").asText());
        
        JsonNode extendsArray = result.get("extends");
        assertEquals(1, extendsArray.size());
        assertEquals("AbstractProcessor", extendsArray.get(0).asText());
        
        JsonNode implementsArray = result.get("implements");
        assertEquals(2, implementsArray.size());
        assertEquals("Configurable", implementsArray.get(0).asText());
        assertEquals("Monitorable", implementsArray.get(1).asText());
    }

    private File createTempJavaFile(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content);
        return file.toFile();
    }
}