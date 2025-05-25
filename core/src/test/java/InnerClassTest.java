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

public class InnerClassTest {

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
	void testNoInnerClasses() throws Exception {
		String javaCode = "public class SimpleClass {}";
		File inputFile = createTempJavaFile("SimpleClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(0, inners.size());
	}

	@Test
	void testPublicStaticNestedClass() throws Exception {
		String javaCode = "public class Outer {\n" + "    public static class PublicNested {\n"
				+ "        private String data;\n" + "        public void process() {}\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("Outer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(1, inners.size());

		JsonNode nested = inners.get(0);
		assertEquals("PublicNested", nested.get("name").asText());
		assertEquals("Class", nested.get("kind").asText());
		assertTrue(nested.get("public").asBoolean());
		assertTrue(nested.get("static").asBoolean());
		assertFalse(nested.get("private").asBoolean());
		assertFalse(nested.get("protected").asBoolean());

		JsonNode fields = nested.get("fields");
		assertEquals(1, fields.size());
		assertEquals("data", fields.get(0).get("name").asText());

		JsonNode methods = nested.get("methods");
		assertEquals(1, methods.size());
		assertEquals("process", methods.get(0).get("name").asText());
	}

	@Test
	void testPrivateStaticNestedClass() throws Exception {
		String javaCode = "public class Outer {\n" + "    private static class PrivateNested {}\n" + "}";
		File inputFile = createTempJavaFile("Outer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(1, inners.size());

		JsonNode nested = inners.get(0);
		assertEquals("PrivateNested", nested.get("name").asText());
		assertEquals("Class", nested.get("kind").asText());
		assertTrue(nested.get("private").asBoolean());
		assertTrue(nested.get("static").asBoolean());
		assertFalse(nested.get("public").asBoolean());
		assertFalse(nested.get("protected").asBoolean());
	}

	@Test
	void testProtectedStaticNestedClass() throws Exception {
		String javaCode = "public class Outer {\n" + "    protected static class ProtectedNested {}\n" + "}";
		File inputFile = createTempJavaFile("Outer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(1, inners.size());

		JsonNode nested = inners.get(0);
		assertEquals("ProtectedNested", nested.get("name").asText());
		assertTrue(nested.get("protected").asBoolean());
		assertTrue(nested.get("static").asBoolean());
		assertFalse(nested.get("public").asBoolean());
		assertFalse(nested.get("private").asBoolean());
	}

	@Test
	void testPackagePrivateNestedClass() throws Exception {
		String javaCode = "public class Outer {\n" + "    static class PackageNested {}\n" + "}";
		File inputFile = createTempJavaFile("Outer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(1, inners.size());

		JsonNode nested = inners.get(0);
		assertEquals("PackageNested", nested.get("name").asText());
		assertTrue(nested.get("static").asBoolean());
		assertFalse(nested.get("public").asBoolean());
		assertFalse(nested.get("private").asBoolean());
		assertFalse(nested.get("protected").asBoolean());
	}

	@Test
	void testNonStaticInnerClass() throws Exception {
		String javaCode = "public class Outer {\n" + "    private String outerField;\n" + "    \n"
				+ "    public class InnerClass {\n" + "        private String innerField;\n" + "        \n"
				+ "        public void accessOuter() {\n" + "            outerField = \"accessed\";\n" + "        }\n"
				+ "    }\n" + "}";
		File inputFile = createTempJavaFile("Outer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(1, inners.size());

		JsonNode inner = inners.get(0);
		assertEquals("InnerClass", inner.get("name").asText());
		assertEquals("Class", inner.get("kind").asText());
		assertTrue(inner.get("public").asBoolean());
		assertFalse(inner.get("static").asBoolean());

		JsonNode fields = inner.get("fields");
		assertEquals(1, fields.size());
		assertEquals("innerField", fields.get(0).get("name").asText());

		JsonNode methods = inner.get("methods");
		assertEquals(1, methods.size());
		assertEquals("accessOuter", methods.get(0).get("name").asText());
	}

	@Test
	void testNestedInterface() throws Exception {
		String javaCode = "public class Outer {\n" + "    public interface NestedInterface {\n"
				+ "        void process();\n" + "        default void defaultMethod() {}\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("Outer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(1, inners.size());

		JsonNode nested = inners.get(0);
		assertEquals("NestedInterface", nested.get("name").asText());
		assertEquals("Interface", nested.get("kind").asText());
		assertTrue(nested.get("public").asBoolean());
		assertTrue(nested.get("static").asBoolean());

		JsonNode methods = nested.get("methods");
		assertEquals(2, methods.size());
		assertEquals("process", methods.get(0).get("name").asText());
		assertEquals("defaultMethod", methods.get(1).get("name").asText());
	}

	@Test
	void testNestedEnum() throws Exception {
		String javaCode = "public class Outer {\n" + "    public enum Status {\n" + "        ACTIVE(1), INACTIVE(0);\n"
				+ "        \n" + "        private final int code;\n" + "        \n" + "        Status(int code) {\n"
				+ "            this.code = code;\n" + "        }\n" + "        \n" + "        public int getCode() {\n"
				+ "            return code;\n" + "        }\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("Outer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(1, inners.size());

		JsonNode nested = inners.get(0);
		assertEquals("Status", nested.get("name").asText());
		assertEquals("Enum", nested.get("kind").asText());
		assertTrue(nested.get("public").asBoolean());
		assertTrue(nested.get("static").asBoolean());

		JsonNode fields = nested.get("fields");
		assertEquals(1, fields.size());
		assertEquals("code", fields.get(0).get("name").asText());

		JsonNode constructors = nested.get("constructors");
		assertEquals(1, constructors.size());

		JsonNode methods = nested.get("methods");
		assertEquals(1, methods.size());
		assertEquals("getCode", methods.get(0).get("name").asText());
	}

	@Test
	void testNestedRecord() throws Exception {
		String javaCode = "public class Outer {\n" + "    public record Person(String name, int age) {\n"
				+ "        public String displayName() {\n" + "            return name.toUpperCase();\n" + "        }\n"
				+ "    }\n" + "}";
		File inputFile = createTempJavaFile("Outer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(1, inners.size());

		JsonNode nested = inners.get(0);
		assertEquals("Person", nested.get("name").asText());
		assertEquals("Record", nested.get("kind").asText());
		assertTrue(nested.get("public").asBoolean());
		assertTrue(nested.get("static").asBoolean());

		JsonNode fields = nested.get("fields");
		assertEquals(2, fields.size());
		assertEquals("name", fields.get(0).get("name").asText());
		assertEquals("age", fields.get(1).get("name").asText());

		JsonNode methods = nested.get("methods");
		assertEquals(1, methods.size());
		assertEquals("displayName", methods.get(0).get("name").asText());
	}

	@Test
	void testNestedAnnotation() throws Exception {
		String javaCode = "public class Outer {\n" + "    @interface NestedAnnotation {\n"
				+ "        String value() default \"\";\n" + "        int priority() default 0;\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("Outer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(1, inners.size());

		JsonNode nested = inners.get(0);
		assertEquals("NestedAnnotation", nested.get("name").asText());
		assertEquals("Annotation", nested.get("kind").asText());
		assertFalse(nested.get("public").asBoolean());
		assertTrue(nested.get("static").asBoolean());

		JsonNode methods = nested.get("methods");
		assertEquals(2, methods.size());
		assertEquals("value", methods.get(0).get("name").asText());
		assertEquals("priority", methods.get(1).get("name").asText());
	}

	@Test
	void testMultipleNestedTypes() throws Exception {
		String javaCode = "public class Container {\n" + "    public static class NestedClass {}\n"
				+ "    private class InnerClass {}\n" + "    protected interface NestedInterface {}\n"
				+ "    public enum NestedEnum { A, B, C }\n" + "    @interface NestedAnnotation {}\n"
				+ "    public record NestedRecord(String data) {}\n" + "}";
		File inputFile = createTempJavaFile("Container.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(6, inners.size());

		assertEquals("NestedClass", inners.get(0).get("name").asText());
		assertEquals("Class", inners.get(0).get("kind").asText());
		assertTrue(inners.get(0).get("static").asBoolean());

		assertEquals("InnerClass", inners.get(1).get("name").asText());
		assertEquals("Class", inners.get(1).get("kind").asText());
		assertFalse(inners.get(1).get("static").asBoolean());

		assertEquals("NestedInterface", inners.get(2).get("name").asText());
		assertEquals("Interface", inners.get(2).get("kind").asText());
		assertTrue(inners.get(2).get("static").asBoolean());

		assertEquals("NestedEnum", inners.get(3).get("name").asText());
		assertEquals("Enum", inners.get(3).get("kind").asText());
		assertTrue(inners.get(3).get("static").asBoolean());

		assertEquals("NestedAnnotation", inners.get(4).get("name").asText());
		assertEquals("Annotation", inners.get(4).get("kind").asText());
		assertTrue(inners.get(4).get("static").asBoolean());

		assertEquals("NestedRecord", inners.get(5).get("name").asText());
		assertEquals("Record", inners.get(5).get("kind").asText());
		assertTrue(inners.get(5).get("static").asBoolean());
	}

	@Test
	void testDeeplyNestedClasses() throws Exception {
		String javaCode = "public class Level1 {\n" + "    public static class Level2 {\n"
				+ "        public static class Level3 {\n" + "            public static class Level4 {\n"
				+ "                private String deepData;\n" + "            }\n" + "        }\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("Level1.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode level1Inners = result.get("inners");
		assertEquals(1, level1Inners.size());

		JsonNode level2 = level1Inners.get(0);
		assertEquals("Level2", level2.get("name").asText());
		JsonNode level2Inners = level2.get("inners");
		assertEquals(1, level2Inners.size());

		JsonNode level3 = level2Inners.get(0);
		assertEquals("Level3", level3.get("name").asText());
		JsonNode level3Inners = level3.get("inners");
		assertEquals(1, level3Inners.size());

		JsonNode level4 = level3Inners.get(0);
		assertEquals("Level4", level4.get("name").asText());
		JsonNode level4Fields = level4.get("fields");
		assertEquals(1, level4Fields.size());
		assertEquals("deepData", level4Fields.get(0).get("name").asText());
	}

	@Test
	void testInnerClassWithInheritance() throws Exception {
		String javaCode = "import java.util.ArrayList;\n" + "public class Outer {\n"
				+ "    public static class CustomList extends ArrayList<String> {\n"
				+ "        public void customMethod() {}\n" + "    }\n" + "    \n"
				+ "    public static class SerializableList extends ArrayList<String> implements java.io.Serializable {\n"
				+ "        private static final long serialVersionUID = 1L;\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("Outer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(2, inners.size());

		JsonNode customList = inners.get(0);
		assertEquals("CustomList", customList.get("name").asText());
		JsonNode extendsArray = customList.get("extends");
		assertEquals(1, extendsArray.size());
		assertEquals("ArrayList<String>", extendsArray.get(0).asText());

		JsonNode serializableList = inners.get(1);
		assertEquals("SerializableList", serializableList.get("name").asText());
		JsonNode extendsArray2 = serializableList.get("extends");
		assertEquals(1, extendsArray2.size());
		assertEquals("ArrayList<String>", extendsArray2.get(0).asText());
		JsonNode implementsArray = serializableList.get("implements");
		assertEquals(1, implementsArray.size());
		assertEquals("Serializable", implementsArray.get(0).asText());
	}

	@Test
	void testAnnotatedInnerClasses() throws Exception {
		String javaCode = "public class Outer {\n" + "    @Deprecated\n"
				+ "    public static class DeprecatedNested {}\n" + "    \n" + "    @SuppressWarnings(\"unused\")\n"
				+ "    private class AnnotatedInner {}\n" + "    \n" + "    @Deprecated\n"
				+ "    @SuppressWarnings(\"serial\")\n" + "    protected static class MultiAnnotatedNested {}\n" + "}";
		File inputFile = createTempJavaFile("Outer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(3, inners.size());

		JsonNode deprecated = inners.get(0);
		JsonNode deprecatedAnnotations = deprecated.get("annotations");
		assertEquals(1, deprecatedAnnotations.size());
		assertEquals("Deprecated", deprecatedAnnotations.get(0).asText());

		JsonNode annotatedInner = inners.get(1);
		JsonNode innerAnnotations = annotatedInner.get("annotations");
		assertEquals(1, innerAnnotations.size());
		assertEquals("SuppressWarnings", innerAnnotations.get(0).asText());

		JsonNode multiAnnotated = inners.get(2);
		JsonNode multiAnnotations = multiAnnotated.get("annotations");
		assertEquals(2, multiAnnotations.size());
		assertEquals("Deprecated", multiAnnotations.get(0).asText());
		assertEquals("SuppressWarnings", multiAnnotations.get(1).asText());
	}

	@Test
	void testGenericInnerClass() throws Exception {
		String javaCode = "public class Outer<T> {\n" + "    public static class StaticGeneric<U> {\n"
				+ "        private U data;\n" + "        public void process(U value) {}\n" + "    }\n" + "    \n"
				+ "    public class InnerGeneric<V> {\n" + "        private T outerData;\n"
				+ "        private V innerData;\n" + "        \n" + "        public void combine(T outer, V inner) {}\n"
				+ "    }\n" + "}";
		File inputFile = createTempJavaFile("Outer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(2, inners.size());

		JsonNode staticGeneric = inners.get(0);
		assertEquals("StaticGeneric", staticGeneric.get("name").asText());
		assertTrue(staticGeneric.get("static").asBoolean());

		JsonNode innerGeneric = inners.get(1);
		assertEquals("InnerGeneric", innerGeneric.get("name").asText());
		assertFalse(innerGeneric.get("static").asBoolean());
	}

	@Test
	void testAbstractAndFinalInnerClasses() throws Exception {
		String javaCode = "public class Outer {\n" + "    public abstract static class AbstractNested {\n"
				+ "        public abstract void process();\n" + "        public void concreteMethod() {}\n" + "    }\n"
				+ "    \n" + "    public final static class FinalNested {\n" + "        public void process() {}\n"
				+ "    }\n" + "    \n" + "    public static class ConcreteImpl extends AbstractNested {\n"
				+ "        public void process() {}\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("Outer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(3, inners.size());

		JsonNode abstractNested = inners.get(0);
		assertEquals("AbstractNested", abstractNested.get("name").asText());
		assertTrue(abstractNested.get("abstract").asBoolean());
		assertFalse(abstractNested.get("final").asBoolean());

		JsonNode finalNested = inners.get(1);
		assertEquals("FinalNested", finalNested.get("name").asText());
		assertTrue(finalNested.get("final").asBoolean());
		assertFalse(finalNested.get("abstract").asBoolean());

		JsonNode concreteImpl = inners.get(2);
		assertEquals("ConcreteImpl", concreteImpl.get("name").asText());
		JsonNode extendsArray = concreteImpl.get("extends");
		assertEquals(1, extendsArray.size());
		assertEquals("AbstractNested", extendsArray.get(0).asText());
	}

	@Test
	void testInnerClassWithTrueFlagPruning() throws Exception {
		String javaCode = "public class Outer {\n" + "    private static class PrivateNested {\n"
				+ "        private boolean flag;\n" + "        public String data;\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("Outer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-t", "-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(1, inners.size());

		JsonNode nested = inners.get(0);
		assertEquals("PrivateNested", nested.get("name").asText());
		assertTrue(nested.get("private").asBoolean());
		assertTrue(nested.get("static").asBoolean());
		assertNull(nested.get("public"));
		assertNull(nested.get("protected"));

		JsonNode fields = nested.get("fields");
		assertEquals(2, fields.size());

		JsonNode privateField = fields.get(0);
		assertTrue(privateField.get("private").asBoolean());
		assertNull(privateField.get("public"));

		JsonNode publicField = fields.get(1);
		assertTrue(publicField.get("public").asBoolean());
		assertNull(publicField.get("private"));
	}

	@Test
	void testInnerInterfaceInInterface() throws Exception {
		String javaCode = "public interface OuterInterface {\n" + "    interface NestedInterface {\n"
				+ "        void process();\n" + "    }\n" + "    \n"
				+ "    class NestedClass implements NestedInterface {\n" + "        public void process() {}\n"
				+ "    }\n" + "}";
		File inputFile = createTempJavaFile("OuterInterface.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Interface", result.get("kind").asText());

		JsonNode inners = result.get("inners");
		assertEquals(2, inners.size());

		JsonNode nestedInterface = inners.get(0);
		assertEquals("NestedInterface", nestedInterface.get("name").asText());
		assertEquals("Interface", nestedInterface.get("kind").asText());
		assertTrue(nestedInterface.get("static").asBoolean());

		JsonNode nestedClass = inners.get(1);
		assertEquals("NestedClass", nestedClass.get("name").asText());
		assertEquals("Class", nestedClass.get("kind").asText());
		assertTrue(nestedClass.get("static").asBoolean());
		JsonNode implementsArray = nestedClass.get("implements");
		assertEquals(1, implementsArray.size());
		assertEquals("NestedInterface", implementsArray.get(0).asText());
	}

	@Test
	void testComplexNestedStructure() throws Exception {
		String javaCode = "public class ComplexContainer {\n" + "    private String containerData;\n" + "    \n"
				+ "    public static class UtilityClass {\n" + "        public static void staticMethod() {}\n"
				+ "        \n" + "        public enum Priority { HIGH, MEDIUM, LOW }\n" + "        \n"
				+ "        public interface Processor {\n" + "            void process();\n" + "        }\n" + "    }\n"
				+ "    \n" + "    public class DataProcessor implements UtilityClass.Processor {\n"
				+ "        private String processorData;\n" + "        \n" + "        public void process() {\n"
				+ "            containerData = \"processed\";\n" + "        }\n" + "        \n"
				+ "        public class InnerProcessor {\n" + "            public void innerProcess() {\n"
				+ "                processorData = \"inner processed\";\n" + "            }\n" + "        }\n"
				+ "    }\n" + "}";
		File inputFile = createTempJavaFile("ComplexContainer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(2, inners.size());

		JsonNode utilityClass = inners.get(0);
		assertEquals("UtilityClass", utilityClass.get("name").asText());
		assertTrue(utilityClass.get("static").asBoolean());
		JsonNode utilityInners = utilityClass.get("inners");
		assertEquals(2, utilityInners.size());
		assertEquals("Priority", utilityInners.get(0).get("name").asText());
		assertEquals("Processor", utilityInners.get(1).get("name").asText());

		JsonNode dataProcessor = inners.get(1);
		assertEquals("DataProcessor", dataProcessor.get("name").asText());
		assertFalse(dataProcessor.get("static").asBoolean());
		JsonNode dataProcessorInners = dataProcessor.get("inners");
		assertEquals(1, dataProcessorInners.size());
		assertEquals("InnerProcessor", dataProcessorInners.get(0).get("name").asText());
	}

	private File createTempJavaFile(String filename, String content) throws Exception {
		Path file = tempDir.resolve(filename);
		Files.writeString(file, content);
		return file.toFile();
	}
}
