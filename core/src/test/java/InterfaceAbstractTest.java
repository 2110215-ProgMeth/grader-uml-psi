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

public class InterfaceAbstractTest {

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
	void testInterfaceMethodsAreAbstract() throws Exception {
		String javaCode = "public interface Mergeable {\n" + "    boolean merge(String source, String target);\n"
				+ "    void resolveConflict(String path, String resolution);\n" + "}";
		File inputFile = createTempJavaFile("Mergeable.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Interface", result.get("kind").asText());

		JsonNode methods = result.get("methods");
		assertEquals(2, methods.size());

		for (JsonNode method : methods) {
			assertTrue(method.get("abstract").asBoolean(),
					"Interface method '" + method.get("name").asText() + "' should be abstract");
			assertFalse(method.get("default").asBoolean());
			assertFalse(method.get("static").asBoolean());
		}
	}

	@Test
	void testInterfaceWithDefaultMethodsNotAbstract() throws Exception {
		String javaCode = "public interface Service {\n" + "    void process();\n"
				+ "    default void initialize() { System.out.println(\"init\"); }\n"
				+ "    static void utility() { System.out.println(\"util\"); }\n" + "}";
		File inputFile = createTempJavaFile("Service.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(3, methods.size());

		JsonNode processMethod = methods.get(0);
		assertEquals("process", processMethod.get("name").asText());
		assertTrue(processMethod.get("abstract").asBoolean());
		assertFalse(processMethod.get("default").asBoolean());
		assertFalse(processMethod.get("static").asBoolean());

		JsonNode defaultMethod = methods.get(1);
		assertEquals("initialize", defaultMethod.get("name").asText());
		assertFalse(defaultMethod.get("abstract").asBoolean());
		assertTrue(defaultMethod.get("default").asBoolean());
		assertFalse(defaultMethod.get("static").asBoolean());

		JsonNode staticMethod = methods.get(2);
		assertEquals("utility", staticMethod.get("name").asText());
		assertFalse(staticMethod.get("abstract").asBoolean());
		assertFalse(staticMethod.get("default").asBoolean());
		assertTrue(staticMethod.get("static").asBoolean());
	}

	@Test
	void testClassMethodsNotAbstractByDefault() throws Exception {
		String javaCode = "public class ConcreteClass {\n" + "    public void regularMethod() {}\n"
				+ "    private String getInfo() { return \"info\"; }\n" + "}";
		File inputFile = createTempJavaFile("ConcreteClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Class", result.get("kind").asText());

		JsonNode methods = result.get("methods");
		assertEquals(2, methods.size());

		for (JsonNode method : methods) {
			assertFalse(method.get("abstract").asBoolean(),
					"Regular class method '" + method.get("name").asText() + "' should not be abstract");
		}
	}

	@Test
	void testAbstractClassWithAbstractMethods() throws Exception {
		String javaCode = "public abstract class AbstractProcessor {\n" + "    public abstract void process();\n"
				+ "    public void concreteMethod() {}\n" + "}";
		File inputFile = createTempJavaFile("AbstractProcessor.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Class", result.get("kind").asText());
		assertTrue(result.get("abstract").asBoolean());

		JsonNode methods = result.get("methods");
		assertEquals(2, methods.size());

		JsonNode abstractMethod = methods.get(0);
		assertEquals("process", abstractMethod.get("name").asText());
		assertTrue(abstractMethod.get("abstract").asBoolean());

		JsonNode concreteMethod = methods.get(1);
		assertEquals("concreteMethod", concreteMethod.get("name").asText());
		assertFalse(concreteMethod.get("abstract").asBoolean());
	}

	@Test
	void testAllMethodsHaveThrowsArray() throws Exception {
		String javaCode = "import java.io.IOException;\n" + "public interface FileProcessor {\n"
				+ "    void processFile(String path) throws IOException;\n" + "    void simpleMethod();\n"
				+ "    default void defaultMethod() throws RuntimeException {}\n"
				+ "    static void staticMethod() {}\n" + "}";
		File inputFile = createTempJavaFile("FileProcessor.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode methods = result.get("methods");
		assertEquals(4, methods.size());

		for (JsonNode method : methods) {
			assertTrue(method.has("throws"), "Method '" + method.get("name").asText() + "' must have 'throws' array");
			assertTrue(method.get("throws").isArray(),
					"'throws' must be an array for method '" + method.get("name").asText() + "'");
		}

		JsonNode methodWithException = methods.get(0);
		assertEquals("processFile", methodWithException.get("name").asText());
		assertEquals(1, methodWithException.get("throws").size());
		assertEquals("IOException", methodWithException.get("throws").get(0).asText());

		JsonNode methodNoException = methods.get(1);
		assertEquals("simpleMethod", methodNoException.get("name").asText());
		assertEquals(0, methodNoException.get("throws").size());

		JsonNode defaultMethodWithException = methods.get(2);
		assertEquals("defaultMethod", defaultMethodWithException.get("name").asText());
		assertEquals(1, defaultMethodWithException.get("throws").size());
		assertEquals("RuntimeException", defaultMethodWithException.get("throws").get(0).asText());

		JsonNode staticMethodNoException = methods.get(3);
		assertEquals("staticMethod", staticMethodNoException.get("name").asText());
		assertEquals(0, staticMethodNoException.get("throws").size());
	}

	@Test
	void testConstructorsHaveThrowsArray() throws Exception {
		String javaCode = "import java.io.IOException;\n" + "public class FileHandler {\n"
				+ "    public FileHandler() {}\n" + "    public FileHandler(String path) throws IOException {}\n"
				+ "    private FileHandler(String path, boolean create) throws IOException, IllegalArgumentException {}\n"
				+ "}";
		File inputFile = createTempJavaFile("FileHandler.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode constructors = result.get("constructors");
		assertEquals(3, constructors.size());

		for (JsonNode constructor : constructors) {
			assertTrue(constructor.has("throws"), "Constructor must have 'throws' array");
			assertTrue(constructor.get("throws").isArray(), "'throws' must be an array for constructor");
		}

		JsonNode defaultConstructor = constructors.get(0);
		assertEquals(0, defaultConstructor.get("throws").size());

		JsonNode singleExceptionConstructor = constructors.get(1);
		assertEquals(1, singleExceptionConstructor.get("throws").size());
		assertEquals("IOException", singleExceptionConstructor.get("throws").get(0).asText());

		JsonNode multiExceptionConstructor = constructors.get(2);
		assertEquals(2, multiExceptionConstructor.get("throws").size());
		assertEquals("IOException", multiExceptionConstructor.get("throws").get(0).asText());
		assertEquals("IllegalArgumentException", multiExceptionConstructor.get("throws").get(1).asText());
	}

	@Test
	void testComplexInterfaceScenario() throws Exception {
		String javaCode = "import java.util.List;\n" + "import java.util.function.Consumer;\n"
				+ "public interface Trackable {\n"
				+ "    void trackFile(String path) throws Exception, java.io.IOException;\n"
				+ "    void untrackFile(String path);\n" + "    List<String> getTrackedFiles();\n"
				+ "    void addFileChangeListener(Consumer<String> listener);\n"
				+ "    default void log(String message) {\n" + "        System.out.println(message);\n" + "    }\n"
				+ "    static boolean isValidPath(String path) {\n"
				+ "        return path != null && !path.isEmpty();\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("Trackable.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Interface", result.get("kind").asText());

		JsonNode methods = result.get("methods");
		assertEquals(6, methods.size());

		JsonNode trackFileMethod = methods.get(0);
		assertEquals("trackFile", trackFileMethod.get("name").asText());
		assertTrue(trackFileMethod.get("abstract").asBoolean());
		assertFalse(trackFileMethod.get("default").asBoolean());
		assertFalse(trackFileMethod.get("static").asBoolean());
		assertEquals(2, trackFileMethod.get("throws").size());
		assertEquals("Exception", trackFileMethod.get("throws").get(0).asText());
		assertEquals("IOException", trackFileMethod.get("throws").get(1).asText());

		JsonNode untrackFileMethod = methods.get(1);
		assertEquals("untrackFile", untrackFileMethod.get("name").asText());
		assertTrue(untrackFileMethod.get("abstract").asBoolean());
		assertEquals(0, untrackFileMethod.get("throws").size());

		JsonNode getTrackedFilesMethod = methods.get(2);
		assertEquals("getTrackedFiles", getTrackedFilesMethod.get("name").asText());
		assertTrue(getTrackedFilesMethod.get("abstract").asBoolean());
		assertEquals("List<String>", getTrackedFilesMethod.get("returnType").asText());

		JsonNode addListenerMethod = methods.get(3);
		assertEquals("addFileChangeListener", addListenerMethod.get("name").asText());
		assertTrue(addListenerMethod.get("abstract").asBoolean());
		assertEquals("Consumer<String>", addListenerMethod.get("params").get(0).asText());

		JsonNode logMethod = methods.get(4);
		assertEquals("log", logMethod.get("name").asText());
		assertFalse(logMethod.get("abstract").asBoolean());
		assertTrue(logMethod.get("default").asBoolean());
		assertFalse(logMethod.get("static").asBoolean());

		JsonNode staticMethod = methods.get(5);
		assertEquals("isValidPath", staticMethod.get("name").asText());
		assertFalse(staticMethod.get("abstract").asBoolean());
		assertFalse(staticMethod.get("default").asBoolean());
		assertTrue(staticMethod.get("static").asBoolean());
	}

	@Test
	void testEmptyInterface() throws Exception {
		String javaCode = "public interface EmptyMarker {}";
		File inputFile = createTempJavaFile("EmptyMarker.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Interface", result.get("kind").asText());

		JsonNode methods = result.get("methods");
		assertEquals(0, methods.size());

		JsonNode fields = result.get("fields");
		assertEquals(0, fields.size());
	}

	@Test
	void testInterfaceWithConstants() throws Exception {
		String javaCode = "public interface Constants {\n" + "    String DEFAULT_NAME = \"test\";\n"
				+ "    int MAX_SIZE = 100;\n" + "    void process();\n" + "}";
		File inputFile = createTempJavaFile("Constants.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Interface", result.get("kind").asText());

		JsonNode fields = result.get("fields");
		assertEquals(2, fields.size());

		for (JsonNode field : fields) {
			assertTrue(field.get("public").asBoolean());
			assertTrue(field.get("static").asBoolean());
			assertTrue(field.get("final").asBoolean());
		}

		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());
		JsonNode processMethod = methods.get(0);
		assertTrue(processMethod.get("abstract").asBoolean());
		assertTrue(processMethod.has("throws"));
		assertEquals(0, processMethod.get("throws").size());
	}

	private File createTempJavaFile(String filename, String content) throws Exception {
		Path file = tempDir.resolve(filename);
		Files.writeString(file, content);
		return file.toFile();
	}
}