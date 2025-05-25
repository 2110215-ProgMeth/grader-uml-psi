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

public class MethodTest {

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
	void testNoMethods() throws Exception {
		String javaCode = "public class Test {}";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(0, methods.size());
	}

	@Test
	void testPublicMethod() throws Exception {
		String javaCode = "public class Test { public void doSomething() {} }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		assertEquals("doSomething", method.get("name").asText());
		assertEquals("void", method.get("returnType").asText());
		assertTrue(method.get("public").asBoolean());
		assertFalse(method.get("protected").asBoolean());
		assertFalse(method.get("private").asBoolean());
		assertFalse(method.get("static").asBoolean());
		assertFalse(method.get("final").asBoolean());
		assertEquals(0, method.get("params").size());
		assertEquals(0, method.get("throws").size());
	}

	@Test
	void testPrivateMethod() throws Exception {
		String javaCode = "public class Test { private String getName() { return \"test\"; } }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		assertEquals("getName", method.get("name").asText());
		assertEquals("String", method.get("returnType").asText());
		assertFalse(method.get("public").asBoolean());
		assertFalse(method.get("protected").asBoolean());
		assertTrue(method.get("private").asBoolean());
	}

	@Test
	void testProtectedMethod() throws Exception {
		String javaCode = "public class Test { protected int calculate() { return 42; } }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		assertEquals("calculate", method.get("name").asText());
		assertEquals("int", method.get("returnType").asText());
		assertFalse(method.get("public").asBoolean());
		assertTrue(method.get("protected").asBoolean());
		assertFalse(method.get("private").asBoolean());
	}

	@Test
	void testPackagePrivateMethod() throws Exception {
		String javaCode = "public class Test { boolean isValid() { return true; } }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		assertEquals("isValid", method.get("name").asText());
		assertEquals("boolean", method.get("returnType").asText());
		assertFalse(method.get("public").asBoolean());
		assertFalse(method.get("protected").asBoolean());
		assertFalse(method.get("private").asBoolean());
	}

	@Test
	void testStaticMethod() throws Exception {
		String javaCode = "public class Test { public static void main(String[] args) {} }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		assertEquals("main", method.get("name").asText());
		assertEquals("void", method.get("returnType").asText());
		assertTrue(method.get("public").asBoolean());
		assertTrue(method.get("static").asBoolean());
		assertFalse(method.get("final").asBoolean());
	}

	@Test
	void testFinalMethod() throws Exception {
		String javaCode = "public class Test { public final String getValue() { return \"final\"; } }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		assertEquals("getValue", method.get("name").asText());
		assertEquals("String", method.get("returnType").asText());
		assertTrue(method.get("public").asBoolean());
		assertTrue(method.get("final").asBoolean());
		assertFalse(method.get("static").asBoolean());
	}

	@Test
	void testAbstractMethod() throws Exception {
		String javaCode = "public abstract class Test { public abstract void process(); }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		assertEquals("process", method.get("name").asText());
		assertEquals("void", method.get("returnType").asText());
		assertTrue(method.get("public").asBoolean());
		assertTrue(method.get("abstract").asBoolean());
		assertFalse(method.get("final").asBoolean());
	}

	@Test
	void testSynchronizedMethod() throws Exception {
		String javaCode = "public class Test { public synchronized void sync() {} }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		assertEquals("sync", method.get("name").asText());
		assertTrue(method.get("synchronized").asBoolean());
		assertFalse(method.get("native").asBoolean());
	}

	@Test
	void testNativeMethod() throws Exception {
		String javaCode = "public class Test { public native void nativeMethod(); }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		assertEquals("nativeMethod", method.get("name").asText());
		assertTrue(method.get("native").asBoolean());
		assertFalse(method.get("synchronized").asBoolean());
	}

	@Test
	void testDefaultInterfaceMethod() throws Exception {
		String javaCode = "public interface Test { default void defaultMethod() {} }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		assertEquals("defaultMethod", method.get("name").asText());
		assertTrue(method.get("default").asBoolean());
		assertFalse(method.get("abstract").asBoolean());
	}

	@Test
	void testMethodWithSingleParameter() throws Exception {
		String javaCode = "public class Test { public void setName(String name) {} }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		JsonNode params = method.get("params");
		assertEquals(1, params.size());
		assertEquals("String", params.get(0).asText());
	}

	@Test
	void testMethodWithMultipleParameters() throws Exception {
		String javaCode = "public class Test { public void update(String name, int age, boolean active) {} }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		JsonNode params = method.get("params");
		assertEquals(3, params.size());
		assertEquals("String", params.get(0).asText());
		assertEquals("int", params.get(1).asText());
		assertEquals("boolean", params.get(2).asText());
	}

	@Test
	void testMethodWithGenericParameter() throws Exception {
		String javaCode = "import java.util.List; public class Test { public void process(List<String> items) {} }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		JsonNode params = method.get("params");
		assertEquals(1, params.size());
		assertEquals("List<String>", params.get(0).asText());
	}

	@Test
	void testMethodWithVarArgs() throws Exception {
		String javaCode = "public class Test { public void print(String... messages) {} }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		JsonNode params = method.get("params");
		assertEquals(1, params.size());
		assertTrue(params.get(0).asText().equals("String...") || params.get(0).asText().equals("String[]"));
	}

	@Test
	void testMethodWithArrayParameter() throws Exception {
		String javaCode = "public class Test { public void process(int[] numbers) {} }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		JsonNode params = method.get("params");
		assertEquals(1, params.size());
		assertEquals("int[]", params.get(0).asText());
	}

	@Test
	void testMethodWithSingleException() throws Exception {
		String javaCode = "public class Test { public void read() throws Exception {} }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		JsonNode throwsNode = method.get("throws");
		assertEquals(1, throwsNode.size());
		assertEquals("Exception", throwsNode.get(0).asText());
	}

	@Test
	void testMethodWithMultipleExceptions() throws Exception {
		String javaCode = "import java.io.IOException; public class Test { public void process() throws IOException, RuntimeException {} }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		JsonNode throwsNode = method.get("throws");
		assertEquals(2, throwsNode.size());
		assertEquals("IOException", throwsNode.get(0).asText());
		assertEquals("RuntimeException", throwsNode.get(1).asText());
	}

	@Test
	void testMethodReturnTypes() throws Exception {
		String javaCode = "import java.util.List; public class Test {\n" + "    public int getInt() { return 0; }\n"
				+ "    public String getString() { return null; }\n"
				+ "    public List<String> getList() { return null; }\n"
				+ "    public int[] getArray() { return null; }\n" + "    public void doNothing() {}\n" + "}";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(5, methods.size());

		assertEquals("int", methods.get(0).get("returnType").asText());
		assertEquals("String", methods.get(1).get("returnType").asText());
		assertEquals("List<String>", methods.get(2).get("returnType").asText());
		assertEquals("int[]", methods.get(3).get("returnType").asText());
		assertEquals("void", methods.get(4).get("returnType").asText());
	}

	@Test
	void testOverloadedMethods() throws Exception {
		String javaCode = "public class Test {\n" + "    public void process() {}\n"
				+ "    public void process(String input) {}\n" + "    public void process(String input, int count) {}\n"
				+ "    public int process(int value) { return value; }\n" + "}";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(4, methods.size());

		assertEquals("process", methods.get(0).get("name").asText());
		assertEquals(0, methods.get(0).get("params").size());
		assertEquals("void", methods.get(0).get("returnType").asText());

		assertEquals("process", methods.get(1).get("name").asText());
		assertEquals(1, methods.get(1).get("params").size());
		assertEquals("void", methods.get(1).get("returnType").asText());

		assertEquals("process", methods.get(2).get("name").asText());
		assertEquals(2, methods.get(2).get("params").size());
		assertEquals("void", methods.get(2).get("returnType").asText());

		assertEquals("process", methods.get(3).get("name").asText());
		assertEquals(1, methods.get(3).get("params").size());
		assertEquals("int", methods.get(3).get("returnType").asText());
	}

	@Test
	void testMethodsWithTrueFlagPruning() throws Exception {
		String javaCode = "public class Test { private void hidden() {} public void visible() {} }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-t", "-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(2, methods.size());

		JsonNode privateMethod = methods.get(0);
		assertEquals("hidden", privateMethod.get("name").asText());
		assertTrue(privateMethod.get("private").asBoolean());
		assertNull(privateMethod.get("public"));
		assertNull(privateMethod.get("protected"));

		JsonNode publicMethod = methods.get(1);
		assertEquals("visible", publicMethod.get("name").asText());
		assertTrue(publicMethod.get("public").asBoolean());
		assertNull(publicMethod.get("private"));
		assertNull(publicMethod.get("protected"));
	}

	@Test
	void testInterfaceMethods() throws Exception {
		String javaCode = "public interface Service {\n" + "    void abstractMethod();\n"
				+ "    default void defaultMethod() {}\n" + "    static void staticMethod() {}\n" + "}";
		File inputFile = createTempJavaFile("Service.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Interface", result.get("kind").asText());
		JsonNode methods = result.get("methods");
		assertEquals(3, methods.size());

		JsonNode abstractMethod = methods.get(0);
		assertEquals("abstractMethod", abstractMethod.get("name").asText());
		assertTrue(abstractMethod.get("public").asBoolean());
		assertTrue(abstractMethod.get("abstract").asBoolean());

		JsonNode defaultMethod = methods.get(1);
		assertEquals("defaultMethod", defaultMethod.get("name").asText());
		assertTrue(defaultMethod.get("default").asBoolean());

		JsonNode staticMethod = methods.get(2);
		assertEquals("staticMethod", staticMethod.get("name").asText());
		assertTrue(staticMethod.get("static").asBoolean());
	}

	@Test
	void testComplexMethodSignature() throws Exception {
		String javaCode = "import java.util.Map; import java.io.IOException; " + "public class Test { "
				+ "public static final synchronized <T> Map<String, T> complexMethod(String key, T value, int... options) throws IOException, RuntimeException { return null; } "
				+ "}";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());

		JsonNode method = methods.get(0);
		assertEquals("complexMethod", method.get("name").asText());
		assertTrue(method.get("public").asBoolean());
		assertTrue(method.get("static").asBoolean());
		assertTrue(method.get("final").asBoolean());
		assertTrue(method.get("synchronized").asBoolean());

		JsonNode params = method.get("params");
		assertEquals(3, params.size());
		assertEquals("String", params.get(0).asText());

		JsonNode throwsNode = method.get("throws");
		assertEquals(2, throwsNode.size());
		assertEquals("IOException", throwsNode.get(0).asText());
		assertEquals("RuntimeException", throwsNode.get(1).asText());
	}

	private File createTempJavaFile(String filename, String content) throws Exception {
		Path file = tempDir.resolve(filename);
		Files.writeString(file, content);
		return file.toFile();
	}
}
