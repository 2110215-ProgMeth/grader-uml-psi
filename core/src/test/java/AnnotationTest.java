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

public class AnnotationTest {

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
	void testNoAnnotations() throws Exception {
		String javaCode = "public class PlainClass {}";
		File inputFile = createTempJavaFile("PlainClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode annotations = result.get("annotations");
		assertEquals(0, annotations.size());
	}

	@Test
	void testSingleAnnotation() throws Exception {
		String javaCode = "@Deprecated\npublic class DeprecatedClass {}";
		File inputFile = createTempJavaFile("DeprecatedClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode annotations = result.get("annotations");
		assertEquals(1, annotations.size());
		assertEquals("Deprecated", annotations.get(0).asText());
	}

	@Test
	void testMultipleAnnotations() throws Exception {
		String javaCode = "@Deprecated\n@SuppressWarnings(\"unchecked\")\npublic class MultiAnnotatedClass {}";
		File inputFile = createTempJavaFile("MultiAnnotatedClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode annotations = result.get("annotations");
		assertEquals(2, annotations.size());
		assertEquals("Deprecated", annotations.get(0).asText());
		assertEquals("SuppressWarnings", annotations.get(1).asText());
	}

	@Test
	void testCustomAnnotation() throws Exception {
		String javaCode = "@interface Author {\n" + "    String name();\n" + "    String version() default \"1.0\";\n"
				+ "}\n" + "@Author(name = \"John Doe\")\n" + "public class AuthorClass {}";
		File inputFile = createTempJavaFile("AuthorClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode annotations = result.get("annotations");
		assertEquals(1, annotations.size());
		assertEquals("Author", annotations.get(0).asText());
	}

	@Test
	void testAnnotationWithSingleValue() throws Exception {
		String javaCode = "@SuppressWarnings(\"unused\")\npublic class SingleValueAnnotatedClass {}";
		File inputFile = createTempJavaFile("SingleValueAnnotatedClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode annotations = result.get("annotations");
		assertEquals(1, annotations.size());
		assertEquals("SuppressWarnings", annotations.get(0).asText());
	}

	@Test
	void testAnnotationWithMultipleValues() throws Exception {
		String javaCode = "@interface Config {\n" + "    String name();\n" + "    int priority();\n"
				+ "    boolean enabled() default true;\n" + "}\n" + "@Config(name = \"test\", priority = 5)\n"
				+ "public class ConfiguredClass {}";
		File inputFile = createTempJavaFile("ConfiguredClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode annotations = result.get("annotations");
		assertEquals(1, annotations.size());
		assertEquals("Config", annotations.get(0).asText());
	}

	@Test
	void testAnnotationWithArrayValue() throws Exception {
		String javaCode = "@SuppressWarnings({\"unused\", \"unchecked\"})\npublic class ArrayAnnotatedClass {}";
		File inputFile = createTempJavaFile("ArrayAnnotatedClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode annotations = result.get("annotations");
		assertEquals(1, annotations.size());
		assertEquals("SuppressWarnings", annotations.get(0).asText());
	}

	@Test
	void testJavaLangAnnotations() throws Exception {
		String javaCode = "@Override\n@Deprecated\n@SafeVarargs\n@FunctionalInterface\n"
				+ "public interface TestInterface<T> {\n" + "    @SuppressWarnings(\"unchecked\")\n"
				+ "    default void process(T... items) {}\n" + "}";
		File inputFile = createTempJavaFile("TestInterface.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode annotations = result.get("annotations");
		assertEquals(4, annotations.size());
		assertEquals("Override", annotations.get(0).asText());
		assertEquals("Deprecated", annotations.get(1).asText());
		assertEquals("SafeVarargs", annotations.get(2).asText());
		assertEquals("FunctionalInterface", annotations.get(3).asText());
	}

	@Test
	void testMetaAnnotations() throws Exception {
		String javaCode = "import java.lang.annotation.Target;\n" + "import java.lang.annotation.ElementType;\n"
				+ "import java.lang.annotation.Retention;\n" + "import java.lang.annotation.RetentionPolicy;\n"
				+ "@Target(ElementType.TYPE)\n" + "@Retention(RetentionPolicy.RUNTIME)\n"
				+ "@interface MetaAnnotated {\n" + "    String value();\n" + "}";
		File inputFile = createTempJavaFile("MetaAnnotated.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode annotations = result.get("annotations");
		assertEquals(2, annotations.size());
		assertEquals("Target", annotations.get(0).asText());
		assertEquals("Retention", annotations.get(1).asText());
	}

	@Test
	void testAnnotationOnEnum() throws Exception {
		String javaCode = "@Deprecated\npublic enum Status {\n" + "    @Deprecated ACTIVE,\n" + "    INACTIVE\n" + "}";
		File inputFile = createTempJavaFile("Status.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Enum", result.get("kind").asText());
		JsonNode annotations = result.get("annotations");
		assertEquals(1, annotations.size());
		assertEquals("Deprecated", annotations.get(0).asText());
	}

	@Test
	void testAnnotationOnRecord() throws Exception {
		String javaCode = "import java.io.Serializable;\n" + "@Deprecated\n"
				+ "public record Person(@Deprecated String name, int age) implements Serializable {}";
		File inputFile = createTempJavaFile("Person.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Record", result.get("kind").asText());
		JsonNode annotations = result.get("annotations");
		assertEquals(1, annotations.size());
		assertEquals("Deprecated", annotations.get(0).asText());
	}

	@Test
	void testNestedAnnotatedClass() throws Exception {
		String javaCode = "public class Outer {\n" + "    @Deprecated\n"
				+ "    public static class NestedAnnotated {}\n" + "    \n" + "    @SuppressWarnings(\"unused\")\n"
				+ "    private class InnerAnnotated {}\n" + "}";
		File inputFile = createTempJavaFile("Outer.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(2, inners.size());

		JsonNode nestedAnnotated = inners.get(0);
		JsonNode nestedAnnotations = nestedAnnotated.get("annotations");
		assertEquals(1, nestedAnnotations.size());
		assertEquals("Deprecated", nestedAnnotations.get(0).asText());

		JsonNode innerAnnotated = inners.get(1);
		JsonNode innerAnnotations = innerAnnotated.get("annotations");
		assertEquals(1, innerAnnotations.size());
		assertEquals("SuppressWarnings", innerAnnotations.get(0).asText());
	}

	@Test
	void testAnnotationOnAbstractClass() throws Exception {
		String javaCode = "@Deprecated\n@SuppressWarnings(\"unused\")\n" + "public abstract class AbstractClass {\n"
				+ "    public abstract void process();\n" + "}";
		File inputFile = createTempJavaFile("AbstractClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertTrue(result.get("abstract").asBoolean());
		JsonNode annotations = result.get("annotations");
		assertEquals(2, annotations.size());
		assertEquals("Deprecated", annotations.get(0).asText());
		assertEquals("SuppressWarnings", annotations.get(1).asText());
	}

	@Test
	void testAnnotationOnFinalClass() throws Exception {
		String javaCode = "@Deprecated\npublic final class FinalClass {}";
		File inputFile = createTempJavaFile("FinalClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertTrue(result.get("final").asBoolean());
		JsonNode annotations = result.get("annotations");
		assertEquals(1, annotations.size());
		assertEquals("Deprecated", annotations.get(0).asText());
	}

	@Test
	void testComplexCustomAnnotation() throws Exception {
		String javaCode = "import java.lang.annotation.ElementType;\n" + "import java.lang.annotation.Target;\n"
				+ "@interface ComplexAnnotation {\n" + "    String name();\n"
				+ "    int[] values() default {1, 2, 3};\n" + "    Class<?> type() default Object.class;\n"
				+ "    ElementType target() default ElementType.TYPE;\n" + "}\n" + "@ComplexAnnotation(\n"
				+ "    name = \"test\",\n" + "    values = {10, 20},\n" + "    type = String.class,\n"
				+ "    target = ElementType.METHOD\n" + ")\n" + "public class ComplexAnnotatedClass {}";
		File inputFile = createTempJavaFile("ComplexAnnotatedClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode annotations = result.get("annotations");
		assertEquals(1, annotations.size());
		assertEquals("ComplexAnnotation", annotations.get(0).asText());
	}

	@Test
	void testAnnotationOnGenericClass() throws Exception {
		String javaCode = "@Deprecated\n@SuppressWarnings(\"unchecked\")\n"
				+ "public class GenericClass<T, U extends Number> {\n" + "    private T value;\n"
				+ "    private U number;\n" + "}";
		File inputFile = createTempJavaFile("GenericClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode annotations = result.get("annotations");
		assertEquals(2, annotations.size());
		assertEquals("Deprecated", annotations.get(0).asText());
		assertEquals("SuppressWarnings", annotations.get(1).asText());
	}

	@Test
	void testAnnotationOnPackagePrivateClass() throws Exception {
		String javaCode = "@Deprecated\nclass PackageClass {}";
		File inputFile = createTempJavaFile("PackageClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertFalse(result.get("public").asBoolean());
		JsonNode annotations = result.get("annotations");
		assertEquals(1, annotations.size());
		assertEquals("Deprecated", annotations.get(0).asText());
	}

	@Test
	void testQualifiedAnnotationName() throws Exception {
		String javaCode = "@java.lang.Deprecated\n@java.lang.SuppressWarnings(\"unused\")\n"
				+ "public class QualifiedAnnotatedClass {}";
		File inputFile = createTempJavaFile("QualifiedAnnotatedClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode annotations = result.get("annotations");
		assertEquals(2, annotations.size());
		assertEquals("Deprecated", annotations.get(0).asText());
		assertEquals("SuppressWarnings", annotations.get(1).asText());
	}

	@Test
	void testAnnotationWithTrueFlagPruning() throws Exception {
		String javaCode = "@Deprecated\npublic class DeprecatedClass {}";
		File inputFile = createTempJavaFile("DeprecatedClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-t", "-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode annotations = result.get("annotations");
		assertEquals(1, annotations.size());
		assertEquals("Deprecated", annotations.get(0).asText());
	}

	@Test
	void testJavaUtilAnnotations() throws Exception {
		String javaCode = "import java.util.concurrent.ThreadSafe;\n" + "import java.util.concurrent.GuardedBy;\n"
				+ "public class AnnotationTest {\n" + "    @Deprecated\n" + "    public void oldMethod() {}\n" + "}";
		File inputFile = createTempJavaFile("AnnotationTest.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode annotations = result.get("annotations");
		assertEquals(0, annotations.size());
	}

	@Test
	void testRepeatableAnnotations() throws Exception {
		String javaCode = "import java.lang.annotation.Repeatable;\n" + "import java.lang.annotation.Retention;\n"
				+ "import java.lang.annotation.RetentionPolicy;\n" + "@Retention(RetentionPolicy.RUNTIME)\n"
				+ "@interface Author {\n" + "    String name();\n" + "}\n" + "@Retention(RetentionPolicy.RUNTIME)\n"
				+ "@Repeatable(Authors.class)\n" + "@interface Authors {\n" + "    Author[] value();\n" + "}\n"
				+ "@Author(name = \"John\")\n" + "@Author(name = \"Jane\")\n" + "public class MultiAuthorClass {}";
		File inputFile = createTempJavaFile("MultiAuthorClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode annotations = result.get("annotations");
		assertEquals(2, annotations.size());
		assertEquals("Author", annotations.get(0).asText());
		assertEquals("Author", annotations.get(1).asText());
	}

	@Test
	void testAnnotationOnInheritedClass() throws Exception {
		String javaCode = "class BaseClass {}\n" + "@Deprecated\n" + "@SuppressWarnings(\"serial\")\n"
				+ "public class DerivedClass extends BaseClass {}";
		File inputFile = createTempJavaFile("DerivedClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode extendsArray = result.get("extends");
		assertEquals(1, extendsArray.size());
		assertEquals("BaseClass", extendsArray.get(0).asText());

		JsonNode annotations = result.get("annotations");
		assertEquals(2, annotations.size());
		assertEquals("Deprecated", annotations.get(0).asText());
		assertEquals("SuppressWarnings", annotations.get(1).asText());
	}

	private File createTempJavaFile(String filename, String content) throws Exception {
		Path file = tempDir.resolve(filename);
		Files.writeString(file, content);
		return file.toFile();
	}
}
