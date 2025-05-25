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

public class AbstractTest {

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
	void testBasicAbstractClass() throws Exception {
		String javaCode = "public abstract class AbstractBase {}";
		File inputFile = createTempJavaFile("AbstractBase.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("AbstractBase", result.get("name").asText());
		assertEquals("Class", result.get("kind").asText());
		assertTrue(result.get("public").asBoolean());
		assertTrue(result.get("abstract").asBoolean());
		assertFalse(result.get("final").asBoolean());
		assertFalse(result.get("static").asBoolean());
	}

	@Test
	void testAbstractClassWithAbstractMethods() throws Exception {
		String javaCode = "public abstract class Processor {\n" + "    public abstract void process();\n"
				+ "    public abstract String getName();\n" + "    protected abstract int calculate(int value);\n"
				+ "}";
		File inputFile = createTempJavaFile("Processor.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertTrue(result.get("abstract").asBoolean());

		JsonNode methods = result.get("methods");
		assertEquals(3, methods.size());

		JsonNode processMethod = methods.get(0);
		assertEquals("process", processMethod.get("name").asText());
		assertEquals("void", processMethod.get("returnType").asText());
		assertTrue(processMethod.get("public").asBoolean());
		assertTrue(processMethod.get("abstract").asBoolean());

		JsonNode getNameMethod = methods.get(1);
		assertEquals("getName", getNameMethod.get("name").asText());
		assertEquals("String", getNameMethod.get("returnType").asText());
		assertTrue(getNameMethod.get("abstract").asBoolean());

		JsonNode calculateMethod = methods.get(2);
		assertEquals("calculate", calculateMethod.get("name").asText());
		assertEquals("int", calculateMethod.get("returnType").asText());
		assertTrue(calculateMethod.get("protected").asBoolean());
		assertTrue(calculateMethod.get("abstract").asBoolean());
	}

	@Test
	void testAbstractClassWithConcreteAndAbstractMethods() throws Exception {
		String javaCode = "public abstract class MixedProcessor {\n" + "    public abstract void process();\n"
				+ "    \n" + "    public void initialize() {\n" + "        System.out.println(\"Initializing\");\n"
				+ "    }\n" + "    \n" + "    protected final String getVersion() {\n" + "        return \"1.0\";\n"
				+ "    }\n" + "    \n" + "    public static void staticMethod() {}\n" + "}";
		File inputFile = createTempJavaFile("MixedProcessor.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertTrue(result.get("abstract").asBoolean());

		JsonNode methods = result.get("methods");
		assertEquals(4, methods.size());

		JsonNode abstractMethod = methods.get(0);
		assertEquals("process", abstractMethod.get("name").asText());
		assertTrue(abstractMethod.get("abstract").asBoolean());

		JsonNode concreteMethod = methods.get(1);
		assertEquals("initialize", concreteMethod.get("name").asText());
		assertFalse(concreteMethod.get("abstract").asBoolean());

		JsonNode finalMethod = methods.get(2);
		assertEquals("getVersion", finalMethod.get("name").asText());
		assertTrue(finalMethod.get("final").asBoolean());
		assertFalse(finalMethod.get("abstract").asBoolean());

		JsonNode staticMethod = methods.get(3);
		assertEquals("staticMethod", staticMethod.get("name").asText());
		assertTrue(staticMethod.get("static").asBoolean());
		assertFalse(staticMethod.get("abstract").asBoolean());
	}

	@Test
	void testAbstractClassWithFields() throws Exception {
		String javaCode = "public abstract class DataProcessor {\n" + "    protected String name;\n"
				+ "    private int version = 1;\n" + "    public static final String DEFAULT_NAME = \"processor\";\n"
				+ "    protected static int count;\n" + "    \n" + "    public abstract void process();\n" + "}";
		File inputFile = createTempJavaFile("DataProcessor.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertTrue(result.get("abstract").asBoolean());

		JsonNode fields = result.get("fields");
		assertEquals(4, fields.size());

		JsonNode nameField = fields.get(0);
		assertEquals("name", nameField.get("name").asText());
		assertEquals("String", nameField.get("type").asText());
		assertTrue(nameField.get("protected").asBoolean());

		JsonNode versionField = fields.get(1);
		assertEquals("version", versionField.get("name").asText());
		assertEquals("int", versionField.get("type").asText());
		assertTrue(versionField.get("private").asBoolean());

		JsonNode constantField = fields.get(2);
		assertEquals("DEFAULT_NAME", constantField.get("name").asText());
		assertTrue(constantField.get("public").asBoolean());
		assertTrue(constantField.get("static").asBoolean());
		assertTrue(constantField.get("final").asBoolean());

		JsonNode countField = fields.get(3);
		assertEquals("count", countField.get("name").asText());
		assertTrue(countField.get("protected").asBoolean());
		assertTrue(countField.get("static").asBoolean());
	}

	@Test
	void testAbstractClassWithConstructors() throws Exception {
		String javaCode = "public abstract class AbstractService {\n" + "    private String serviceName;\n"
				+ "    protected int timeout;\n" + "    \n" + "    public AbstractService() {\n"
				+ "        this(\"default\", 30);\n" + "    }\n" + "    \n"
				+ "    protected AbstractService(String name) {\n" + "        this(name, 60);\n" + "    }\n" + "    \n"
				+ "    private AbstractService(String name, int timeout) {\n" + "        this.serviceName = name;\n"
				+ "        this.timeout = timeout;\n" + "    }\n" + "    \n"
				+ "    public abstract void start() throws Exception;\n" + "}";
		File inputFile = createTempJavaFile("AbstractService.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertTrue(result.get("abstract").asBoolean());

		JsonNode constructors = result.get("constructors");
		assertEquals(3, constructors.size());

		JsonNode publicCtor = constructors.get(0);
		assertEquals("AbstractService", publicCtor.get("name").asText());
		assertTrue(publicCtor.get("public").asBoolean());
		assertEquals(0, publicCtor.get("params").size());

		JsonNode protectedCtor = constructors.get(1);
		assertTrue(protectedCtor.get("protected").asBoolean());
		assertEquals(1, protectedCtor.get("params").size());
		assertEquals("String", protectedCtor.get("params").get(0).asText());

		JsonNode privateCtor = constructors.get(2);
		assertTrue(privateCtor.get("private").asBoolean());
		assertEquals(2, privateCtor.get("params").size());
		assertEquals("String", privateCtor.get("params").get(0).asText());
		assertEquals("int", privateCtor.get("params").get(1).asText());
	}

	@Test
	void testAbstractClassInheritance() throws Exception {
		String javaCode = "import java.io.Serializable;\n" + "abstract class BaseProcessor {\n"
				+ "    public abstract void init();\n" + "}\n" + "\n"
				+ "public abstract class AdvancedProcessor extends BaseProcessor implements Serializable {\n"
				+ "    private static final long serialVersionUID = 1L;\n" + "    \n"
				+ "    public abstract void process();\n" + "    \n" + "    public void init() {\n"
				+ "        System.out.println(\"Initialized\");\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("AdvancedProcessor.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("AdvancedProcessor", result.get("name").asText());
		assertTrue(result.get("abstract").asBoolean());

		JsonNode extendsArray = result.get("extends");
		assertEquals(1, extendsArray.size());
		assertEquals("BaseProcessor", extendsArray.get(0).asText());

		JsonNode implementsArray = result.get("implements");
		assertEquals(1, implementsArray.size());
		assertEquals("Serializable", implementsArray.get(0).asText());

		JsonNode methods = result.get("methods");
		assertEquals(2, methods.size());

		JsonNode processMethod = methods.get(0);
		assertEquals("process", processMethod.get("name").asText());
		assertTrue(processMethod.get("abstract").asBoolean());

		JsonNode initMethod = methods.get(1);
		assertEquals("init", initMethod.get("name").asText());
		assertFalse(initMethod.get("abstract").asBoolean());
	}

	@Test
	void testAbstractClassWithGenericTypes() throws Exception {
		String javaCode = "import java.util.List;\n" + "import java.util.Map;\n" + "\n"
				+ "public abstract class GenericProcessor<T, R> {\n" + "    protected List<T> inputData;\n"
				+ "    private Map<String, R> results;\n" + "    \n" + "    public abstract R process(T input);\n"
				+ "    public abstract List<R> processAll(List<T> inputs);\n" + "    \n"
				+ "    public void setInputData(List<T> data) {\n" + "        this.inputData = data;\n" + "    }\n"
				+ "    \n" + "    protected Map<String, R> getResults() {\n" + "        return results;\n" + "    }\n"
				+ "}";
		File inputFile = createTempJavaFile("GenericProcessor.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("GenericProcessor", result.get("name").asText());
		assertTrue(result.get("abstract").asBoolean());

		JsonNode fields = result.get("fields");
		assertEquals(2, fields.size());
		assertEquals("List<T>", fields.get(0).get("type").asText());
		assertEquals("Map<String,R>", fields.get(1).get("type").asText());

		JsonNode methods = result.get("methods");
		assertEquals(4, methods.size());

		JsonNode abstractMethod1 = methods.get(0);
		assertEquals("process", abstractMethod1.get("name").asText());
		assertTrue(abstractMethod1.get("abstract").asBoolean());
		assertEquals("R", abstractMethod1.get("returnType").asText());

		JsonNode abstractMethod2 = methods.get(1);
		assertEquals("processAll", abstractMethod2.get("name").asText());
		assertTrue(abstractMethod2.get("abstract").asBoolean());
		assertEquals("List<R>", abstractMethod2.get("returnType").asText());
	}

	@Test
	void testNestedAbstractClass() throws Exception {
		String javaCode = "public class OuterClass {\n" + "    public abstract static class AbstractNested {\n"
				+ "        protected String data;\n" + "        \n" + "        public AbstractNested(String data) {\n"
				+ "            this.data = data;\n" + "        }\n" + "        \n"
				+ "        public abstract void process();\n" + "        \n" + "        public String getData() {\n"
				+ "            return data;\n" + "        }\n" + "    }\n" + "    \n"
				+ "    private abstract class AbstractInner {\n" + "        public abstract void innerProcess();\n"
				+ "    }\n" + "}";
		File inputFile = createTempJavaFile("OuterClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode inners = result.get("inners");
		assertEquals(2, inners.size());

		JsonNode abstractNested = inners.get(0);
		assertEquals("AbstractNested", abstractNested.get("name").asText());
		assertEquals("Class", abstractNested.get("kind").asText());
		assertTrue(abstractNested.get("abstract").asBoolean());
		assertTrue(abstractNested.get("static").asBoolean());

		JsonNode nestedFields = abstractNested.get("fields");
		assertEquals(1, nestedFields.size());
		assertEquals("data", nestedFields.get(0).get("name").asText());

		JsonNode nestedMethods = abstractNested.get("methods");
		assertEquals(2, nestedMethods.size());
		assertTrue(nestedMethods.get(0).get("abstract").asBoolean());
		assertFalse(nestedMethods.get(1).get("abstract").asBoolean());

		JsonNode abstractInner = inners.get(1);
		assertEquals("AbstractInner", abstractInner.get("name").asText());
		assertTrue(abstractInner.get("abstract").asBoolean());
		assertFalse(abstractInner.get("static").asBoolean());
	}

	@Test
	void testAbstractClassWithMultipleInterfaces() throws Exception {
		String javaCode = "import java.io.Serializable;\n" + "import java.util.Comparator;\n" + "\n"
				+ "interface Configurable {\n" + "    void configure();\n" + "}\n" + "\n" + "interface Monitorable {\n"
				+ "    void startMonitoring();\n" + "    void stopMonitoring();\n" + "}\n" + "\n"
				+ "public abstract class ComplexProcessor implements Serializable, Configurable, Monitorable {\n"
				+ "    private static final long serialVersionUID = 1L;\n" + "    \n"
				+ "    public abstract void process();\n" + "    \n" + "    public void configure() {\n"
				+ "        System.out.println(\"Configured\");\n" + "    }\n" + "    \n"
				+ "    public void startMonitoring() {\n" + "        System.out.println(\"Monitoring started\");\n"
				+ "    }\n" + "    \n" + "    public abstract void stopMonitoring();\n" + "}";
		File inputFile = createTempJavaFile("ComplexProcessor.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("ComplexProcessor", result.get("name").asText());
		assertTrue(result.get("abstract").asBoolean());

		JsonNode implementsArray = result.get("implements");
		assertEquals(3, implementsArray.size());
		assertEquals("Serializable", implementsArray.get(0).asText());
		assertEquals("Configurable", implementsArray.get(1).asText());
		assertEquals("Monitorable", implementsArray.get(2).asText());

		JsonNode methods = result.get("methods");
		assertEquals(4, methods.size());

		for (JsonNode method : methods) {
			if (method.get("abstract").asBoolean()) {
				assertTrue(method.get("name").asText().equals("process")
						|| method.get("name").asText().equals("stopMonitoring"));
			}
		}
	}

	@Test
	void testProtectedAbstractClass() throws Exception {
		String javaCode = "class OuterPackage {\n" + "    protected abstract static class ProtectedAbstract {\n"
				+ "        public abstract void execute();\n" + "        \n" + "        protected void helper() {\n"
				+ "            System.out.println(\"Helper method\");\n" + "        }\n" + "    }\n" + "}\n" + "\n"
				+ "public class MainClass extends OuterPackage.ProtectedAbstract {\n" + "    public void execute() {\n"
				+ "        System.out.println(\"Executed\");\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("MainClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("MainClass", result.get("name").asText());
		assertFalse(result.get("abstract").asBoolean());

		JsonNode extendsArray = result.get("extends");
		assertEquals(1, extendsArray.size());
		assertTrue(extendsArray.get(0).asText().contains("ProtectedAbstract"));
	}

	@Test
	void testAbstractClassWithStaticMembers() throws Exception {
		String javaCode = "public abstract class StaticMembersAbstract {\n"
				+ "    public static final String CONSTANT = \"VALUE\";\n" + "    private static int counter = 0;\n"
				+ "    protected static String sharedData;\n" + "    \n" + "    static {\n" + "        counter = 1;\n"
				+ "        sharedData = \"initialized\";\n" + "    }\n" + "    \n"
				+ "    public static void incrementCounter() {\n" + "        counter++;\n" + "    }\n" + "    \n"
				+ "    public static int getCounter() {\n" + "        return counter;\n" + "    }\n" + "    \n"
				+ "    public abstract void instanceMethod();\n" + "    \n"
				+ "    protected static abstract class NestedAbstract {\n"
				+ "        public abstract void nestedMethod();\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("StaticMembersAbstract.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertTrue(result.get("abstract").asBoolean());

		JsonNode fields = result.get("fields");
		assertEquals(3, fields.size());
		for (JsonNode field : fields) {
			assertTrue(field.get("static").asBoolean());
		}

		JsonNode methods = result.get("methods");
		assertEquals(3, methods.size());

		JsonNode staticMethod1 = methods.get(0);
		assertEquals("incrementCounter", staticMethod1.get("name").asText());
		assertTrue(staticMethod1.get("static").asBoolean());
		assertFalse(staticMethod1.get("abstract").asBoolean());

		JsonNode staticMethod2 = methods.get(1);
		assertEquals("getCounter", staticMethod2.get("name").asText());
		assertTrue(staticMethod2.get("static").asBoolean());

		JsonNode abstractMethod = methods.get(2);
		assertEquals("instanceMethod", abstractMethod.get("name").asText());
		assertTrue(abstractMethod.get("abstract").asBoolean());
		assertFalse(abstractMethod.get("static").asBoolean());

		JsonNode inners = result.get("inners");
		assertEquals(1, inners.size());
		JsonNode nestedAbstract = inners.get(0);
		assertTrue(nestedAbstract.get("abstract").asBoolean());
		assertTrue(nestedAbstract.get("static").asBoolean());
	}

	@Test
	void testAbstractClassWithExceptions() throws Exception {
		String javaCode = "import java.io.IOException;\n" + "import java.sql.SQLException;\n" + "\n"
				+ "public abstract class ExceptionProcessor {\n"
				+ "    public abstract void processData() throws IOException, SQLException;\n" + "    \n"
				+ "    public abstract String processString(String input) throws IllegalArgumentException;\n" + "    \n"
				+ "    public void safeProcess() {\n" + "        try {\n" + "            processData();\n"
				+ "        } catch (Exception e) {\n" + "            handleError(e);\n" + "        }\n" + "    }\n"
				+ "    \n" + "    protected abstract void handleError(Exception e);\n" + "}";
		File inputFile = createTempJavaFile("ExceptionProcessor.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertTrue(result.get("abstract").asBoolean());

		JsonNode methods = result.get("methods");
		assertEquals(4, methods.size());

		JsonNode method1 = methods.get(0);
		assertEquals("processData", method1.get("name").asText());
		assertTrue(method1.get("abstract").asBoolean());
		JsonNode throws1 = method1.get("throws");
		assertEquals(2, throws1.size());
		assertEquals("IOException", throws1.get(0).asText());
		assertEquals("SQLException", throws1.get(1).asText());

		JsonNode method2 = methods.get(1);
		assertEquals("processString", method2.get("name").asText());
		assertTrue(method2.get("abstract").asBoolean());
		JsonNode throws2 = method2.get("throws");
		assertEquals(1, throws2.size());
		assertEquals("IllegalArgumentException", throws2.get(0).asText());

		JsonNode method3 = methods.get(2);
		assertEquals("safeProcess", method3.get("name").asText());
		assertFalse(method3.get("abstract").asBoolean());

		JsonNode method4 = methods.get(3);
		assertEquals("handleError", method4.get("name").asText());
		assertTrue(method4.get("abstract").asBoolean());
		assertTrue(method4.get("protected").asBoolean());
	}

	@Test
	void testAbstractClassWithTrueFlagPruning() throws Exception {
		String javaCode = "public abstract class TestAbstract {\n" + "    private boolean flag;\n"
				+ "    public String data;\n" + "    public abstract void process();\n" + "}";
		File inputFile = createTempJavaFile("TestAbstract.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-t", "-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertTrue(result.get("public").asBoolean());
		assertTrue(result.get("abstract").asBoolean());
		assertNull(result.get("private"));
		assertNull(result.get("protected"));
		assertNull(result.get("final"));
		assertNull(result.get("static"));

		JsonNode fields = result.get("fields");
		assertEquals(2, fields.size());

		JsonNode privateField = fields.get(0);
		assertTrue(privateField.get("private").asBoolean());
		assertNull(privateField.get("public"));

		JsonNode publicField = fields.get(1);
		assertTrue(publicField.get("public").asBoolean());
		assertNull(publicField.get("private"));
	}

	@Test
	void testPackagePrivateAbstractClass() throws Exception {
		String javaCode = "abstract class PackageAbstract {\n" + "    abstract void packageMethod();\n" + "    \n"
				+ "    public void concreteMethod() {\n" + "        System.out.println(\"Concrete\");\n" + "    }\n"
				+ "}";
		File inputFile = createTempJavaFile("PackageAbstract.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("PackageAbstract", result.get("name").asText());
		assertTrue(result.get("abstract").asBoolean());
		assertFalse(result.get("public").asBoolean());
		assertFalse(result.get("private").asBoolean());
		assertFalse(result.get("protected").asBoolean());

		JsonNode methods = result.get("methods");
		assertEquals(2, methods.size());

		JsonNode abstractMethod = methods.get(0);
		assertEquals("packageMethod", abstractMethod.get("name").asText());
		assertTrue(abstractMethod.get("abstract").asBoolean());
		assertFalse(abstractMethod.get("public").asBoolean());

		JsonNode concreteMethod = methods.get(1);
		assertEquals("concreteMethod", concreteMethod.get("name").asText());
		assertFalse(concreteMethod.get("abstract").asBoolean());
		assertTrue(concreteMethod.get("public").asBoolean());
	}

	private File createTempJavaFile(String filename, String content) throws Exception {
		Path file = tempDir.resolve(filename);
		Files.writeString(file, content);
		return file.toFile();
	}
}
