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

public class InterfaceTest {

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
	void testBasicInterface() throws Exception {
		String javaCode = "public interface BasicInterface {}";
		File inputFile = createTempJavaFile("BasicInterface.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("BasicInterface", result.get("name").asText());
		assertEquals("Interface", result.get("kind").asText());
		assertTrue(result.get("public").asBoolean());
		assertFalse(result.get("abstract").asBoolean());
		assertFalse(result.get("final").asBoolean());
		assertFalse(result.get("static").asBoolean());
		assertEquals(0, result.get("methods").size());
		assertEquals(0, result.get("fields").size());
	}

	@Test
	void testInterfaceWithAbstractMethods() throws Exception {
		String javaCode = "public interface Processor {\n" + "    void process();\n" + "    String getName();\n"
				+ "    int calculate(int value);\n" + "    boolean isValid(String input, int threshold);\n" + "}";
		File inputFile = createTempJavaFile("Processor.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Interface", result.get("kind").asText());

		JsonNode methods = result.get("methods");
		assertEquals(4, methods.size());

		JsonNode processMethod = methods.get(0);
		assertEquals("process", processMethod.get("name").asText());
		assertEquals("void", processMethod.get("returnType").asText());
		assertTrue(processMethod.get("public").asBoolean());
		assertTrue(processMethod.get("abstract").asBoolean());
		assertFalse(processMethod.get("default").asBoolean());
		assertFalse(processMethod.get("static").asBoolean());
		assertEquals(0, processMethod.get("params").size());

		JsonNode getNameMethod = methods.get(1);
		assertEquals("getName", getNameMethod.get("name").asText());
		assertEquals("String", getNameMethod.get("returnType").asText());
		assertTrue(getNameMethod.get("abstract").asBoolean());

		JsonNode calculateMethod = methods.get(2);
		assertEquals("calculate", calculateMethod.get("name").asText());
		assertEquals("int", calculateMethod.get("returnType").asText());
		assertEquals(1, calculateMethod.get("params").size());
		assertEquals("int", calculateMethod.get("params").get(0).asText());

		JsonNode isValidMethod = methods.get(3);
		assertEquals("isValid", isValidMethod.get("name").asText());
		assertEquals("boolean", isValidMethod.get("returnType").asText());
		assertEquals(2, isValidMethod.get("params").size());
		assertEquals("String", isValidMethod.get("params").get(0).asText());
		assertEquals("int", isValidMethod.get("params").get(1).asText());
	}

	@Test
	void testInterfaceWithDefaultMethods() throws Exception {
		String javaCode = "public interface DefaultMethodInterface {\n" + "    void abstractMethod();\n" + "    \n"
				+ "    default void defaultMethod() {\n" + "        System.out.println(\"Default implementation\");\n"
				+ "    }\n" + "    \n" + "    default String getDefaultValue() {\n" + "        return \"default\";\n"
				+ "    }\n" + "    \n" + "    default int calculate(int a, int b) {\n" + "        return a + b;\n"
				+ "    }\n" + "}";
		File inputFile = createTempJavaFile("DefaultMethodInterface.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Interface", result.get("kind").asText());

		JsonNode methods = result.get("methods");
		assertEquals(4, methods.size());

		JsonNode abstractMethod = methods.get(0);
		assertEquals("abstractMethod", abstractMethod.get("name").asText());
		assertTrue(abstractMethod.get("abstract").asBoolean());
		assertFalse(abstractMethod.get("default").asBoolean());

		JsonNode defaultMethod1 = methods.get(1);
		assertEquals("defaultMethod", defaultMethod1.get("name").asText());
		assertTrue(defaultMethod1.get("default").asBoolean());
		assertFalse(defaultMethod1.get("abstract").asBoolean());
		assertEquals("void", defaultMethod1.get("returnType").asText());

		JsonNode defaultMethod2 = methods.get(2);
		assertEquals("getDefaultValue", defaultMethod2.get("name").asText());
		assertTrue(defaultMethod2.get("default").asBoolean());
		assertEquals("String", defaultMethod2.get("returnType").asText());

		JsonNode defaultMethod3 = methods.get(3);
		assertEquals("calculate", defaultMethod3.get("name").asText());
		assertTrue(defaultMethod3.get("default").asBoolean());
		assertEquals("int", defaultMethod3.get("returnType").asText());
		assertEquals(2, defaultMethod3.get("params").size());
	}

	@Test
	void testInterfaceWithStaticMethods() throws Exception {
		String javaCode = "public interface StaticMethodInterface {\n" + "    void instanceMethod();\n" + "    \n"
				+ "    static void staticMethod() {\n" + "        System.out.println(\"Static method\");\n" + "    }\n"
				+ "    \n" + "    static String getVersion() {\n" + "        return \"1.0\";\n" + "    }\n" + "    \n"
				+ "    static int add(int a, int b) {\n" + "        return a + b;\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("StaticMethodInterface.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Interface", result.get("kind").asText());

		JsonNode methods = result.get("methods");
		assertEquals(4, methods.size());

		JsonNode instanceMethod = methods.get(0);
		assertEquals("instanceMethod", instanceMethod.get("name").asText());
		assertTrue(instanceMethod.get("abstract").asBoolean());
		assertFalse(instanceMethod.get("static").asBoolean());

		JsonNode staticMethod1 = methods.get(1);
		assertEquals("staticMethod", staticMethod1.get("name").asText());
		assertTrue(staticMethod1.get("static").asBoolean());
		assertFalse(staticMethod1.get("abstract").asBoolean());
		assertFalse(staticMethod1.get("default").asBoolean());

		JsonNode staticMethod2 = methods.get(2);
		assertEquals("getVersion", staticMethod2.get("name").asText());
		assertTrue(staticMethod2.get("static").asBoolean());
		assertEquals("String", staticMethod2.get("returnType").asText());

		JsonNode staticMethod3 = methods.get(3);
		assertEquals("add", staticMethod3.get("name").asText());
		assertTrue(staticMethod3.get("static").asBoolean());
		assertEquals("int", staticMethod3.get("returnType").asText());
		assertEquals(2, staticMethod3.get("params").size());
	}

	@Test
	void testInterfaceWithConstants() throws Exception {
		String javaCode = "public interface Constants {\n" + "    String DEFAULT_NAME = \"default\";\n"
				+ "    int MAX_SIZE = 100;\n" + "    boolean DEBUG_MODE = true;\n" + "    double PI = 3.14159;\n"
				+ "    \n" + "    void process();\n" + "}";
		File inputFile = createTempJavaFile("Constants.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Interface", result.get("kind").asText());

		JsonNode fields = result.get("fields");
		assertEquals(4, fields.size());

		for (JsonNode field : fields) {
			assertTrue(field.get("public").asBoolean());
			assertTrue(field.get("static").asBoolean());
			assertTrue(field.get("final").asBoolean());
		}

		assertEquals("DEFAULT_NAME", fields.get(0).get("name").asText());
		assertEquals("String", fields.get(0).get("type").asText());

		assertEquals("MAX_SIZE", fields.get(1).get("name").asText());
		assertEquals("int", fields.get(1).get("type").asText());

		assertEquals("DEBUG_MODE", fields.get(2).get("name").asText());
		assertEquals("boolean", fields.get(2).get("type").asText());

		assertEquals("PI", fields.get(3).get("name").asText());
		assertEquals("double", fields.get(3).get("type").asText());

		JsonNode methods = result.get("methods");
		assertEquals(1, methods.size());
		assertEquals("process", methods.get(0).get("name").asText());
	}

	@Test
	void testInterfaceInheritance() throws Exception {
		String javaCode = "interface BaseInterface {\n" + "    void baseMethod();\n" + "}\n" + "\n"
				+ "interface SecondaryInterface {\n" + "    void secondaryMethod();\n" + "}\n" + "\n"
				+ "public interface ExtendedInterface extends BaseInterface, SecondaryInterface {\n"
				+ "    void extendedMethod();\n" + "    \n" + "    default void defaultImplementation() {\n"
				+ "        System.out.println(\"Extended default\");\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("ExtendedInterface.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("ExtendedInterface", result.get("name").asText());
		assertEquals("Interface", result.get("kind").asText());

		JsonNode extendsArray = result.get("extends");
		assertEquals(2, extendsArray.size());
		assertEquals("BaseInterface", extendsArray.get(0).asText());
		assertEquals("SecondaryInterface", extendsArray.get(1).asText());

		JsonNode implementsArray = result.get("implements");
		assertEquals(0, implementsArray.size());

		JsonNode methods = result.get("methods");
		assertEquals(2, methods.size());
		assertEquals("extendedMethod", methods.get(0).get("name").asText());
		assertEquals("defaultImplementation", methods.get(1).get("name").asText());
		assertTrue(methods.get(1).get("default").asBoolean());
	}

	@Test
	void testFunctionalInterface() throws Exception {
		String javaCode = "@FunctionalInterface\n" + "public interface Processor<T, R> {\n"
				+ "    R process(T input);\n" + "    \n" + "    default void preProcess() {\n"
				+ "        System.out.println(\"Pre-processing\");\n" + "    }\n" + "    \n"
				+ "    default void postProcess() {\n" + "        System.out.println(\"Post-processing\");\n"
				+ "    }\n" + "    \n" + "    static void staticUtility() {\n"
				+ "        System.out.println(\"Utility method\");\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("Processor.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Processor", result.get("name").asText());
		assertEquals("Interface", result.get("kind").asText());

		JsonNode annotations = result.get("annotations");
		assertEquals(1, annotations.size());
		assertEquals("FunctionalInterface", annotations.get(0).asText());

		JsonNode methods = result.get("methods");
		assertEquals(4, methods.size());

		JsonNode abstractMethod = methods.get(0);
		assertEquals("process", abstractMethod.get("name").asText());
		assertTrue(abstractMethod.get("abstract").asBoolean());
		assertEquals("R", abstractMethod.get("returnType").asText());
		assertEquals(1, abstractMethod.get("params").size());
		assertEquals("T", abstractMethod.get("params").get(0).asText());

		JsonNode defaultMethod1 = methods.get(1);
		assertEquals("preProcess", defaultMethod1.get("name").asText());
		assertTrue(defaultMethod1.get("default").asBoolean());

		JsonNode defaultMethod2 = methods.get(2);
		assertEquals("postProcess", defaultMethod2.get("name").asText());
		assertTrue(defaultMethod2.get("default").asBoolean());

		JsonNode staticMethod = methods.get(3);
		assertEquals("staticUtility", staticMethod.get("name").asText());
		assertTrue(staticMethod.get("static").asBoolean());
	}

	@Test
	void testGenericInterface() throws Exception {
		String javaCode = "import java.util.List;\n" + "import java.util.Map;\n" + "\n"
				+ "public interface GenericProcessor<T extends Number, R, E extends Exception> {\n"
				+ "    R process(T input) throws E;\n" + "    \n" + "    List<R> processAll(List<T> inputs) throws E;\n"
				+ "    \n" + "    Map<String, R> processMap(Map<String, T> inputMap);\n" + "    \n"
				+ "    default boolean canProcess(T input) {\n" + "        return input != null;\n" + "    }\n"
				+ "    \n" + "    static <U> boolean isValid(U value) {\n" + "        return value != null;\n"
				+ "    }\n" + "}";
		File inputFile = createTempJavaFile("GenericProcessor.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("GenericProcessor", result.get("name").asText());
		assertEquals("Interface", result.get("kind").asText());

		JsonNode methods = result.get("methods");
		assertEquals(5, methods.size());

		JsonNode processMethod = methods.get(0);
		assertEquals("process", processMethod.get("name").asText());
		assertEquals("R", processMethod.get("returnType").asText());
		assertEquals(1, processMethod.get("params").size());
		assertEquals("T", processMethod.get("params").get(0).asText());
		JsonNode throws1 = processMethod.get("throws");
		assertEquals(1, throws1.size());
		assertEquals("E", throws1.get(0).asText());

		JsonNode processAllMethod = methods.get(1);
		assertEquals("processAll", processAllMethod.get("name").asText());
		assertEquals("List<R>", processAllMethod.get("returnType").asText());
		assertEquals(1, processAllMethod.get("params").size());
		assertEquals("List<T>", processAllMethod.get("params").get(0).asText());

		JsonNode processMapMethod = methods.get(2);
		assertEquals("processMap", processMapMethod.get("name").asText());
		assertEquals("Map<String,R>", processMapMethod.get("returnType").asText());
		assertEquals("Map<String,T>", processMapMethod.get("params").get(0).asText());

		JsonNode defaultMethod = methods.get(3);
		assertEquals("canProcess", defaultMethod.get("name").asText());
		assertTrue(defaultMethod.get("default").asBoolean());

		JsonNode staticMethod = methods.get(4);
		assertEquals("isValid", staticMethod.get("name").asText());
		assertTrue(staticMethod.get("static").asBoolean());
	}

	@Test
	void testNestedInterfaces() throws Exception {
		String javaCode = "public class ContainerClass {\n" + "    public interface PublicNestedInterface {\n"
				+ "        void publicMethod();\n" + "    }\n" + "    \n"
				+ "    protected interface ProtectedNestedInterface {\n" + "        String getValue();\n"
				+ "        default void defaultMethod() {}\n" + "    }\n" + "    \n"
				+ "    private interface PrivateNestedInterface {\n" + "        int calculate();\n" + "    }\n"
				+ "    \n" + "    interface PackageNestedInterface {\n" + "        boolean isValid();\n" + "    }\n"
				+ "}";
		File inputFile = createTempJavaFile("ContainerClass.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Class", result.get("kind").asText());

		JsonNode inners = result.get("inners");
		assertEquals(4, inners.size());

		JsonNode publicInterface = inners.get(0);
		assertEquals("PublicNestedInterface", publicInterface.get("name").asText());
		assertEquals("Interface", publicInterface.get("kind").asText());
		assertTrue(publicInterface.get("public").asBoolean());
		assertTrue(publicInterface.get("static").asBoolean());

		JsonNode protectedInterface = inners.get(1);
		assertEquals("ProtectedNestedInterface", protectedInterface.get("name").asText());
		assertEquals("Interface", protectedInterface.get("kind").asText());
		assertTrue(protectedInterface.get("protected").asBoolean());
		assertTrue(protectedInterface.get("static").asBoolean());

		JsonNode privateInterface = inners.get(2);
		assertEquals("PrivateNestedInterface", privateInterface.get("name").asText());
		assertEquals("Interface", privateInterface.get("kind").asText());
		assertTrue(privateInterface.get("private").asBoolean());
		assertTrue(privateInterface.get("static").asBoolean());

		JsonNode packageInterface = inners.get(3);
		assertEquals("PackageNestedInterface", packageInterface.get("name").asText());
		assertEquals("Interface", packageInterface.get("kind").asText());
		assertFalse(packageInterface.get("public").asBoolean());
		assertFalse(packageInterface.get("protected").asBoolean());
		assertFalse(packageInterface.get("private").asBoolean());
		assertTrue(packageInterface.get("static").asBoolean());
	}

	@Test
	void testInterfaceWithComplexMethods() throws Exception {
		String javaCode = "import java.io.IOException;\n" + "import java.util.concurrent.CompletableFuture;\n" + "\n"
				+ "public interface ComplexInterface {\n" + "    void simpleMethod();\n" + "    \n"
				+ "    <T> T genericMethod(T input);\n" + "    \n"
				+ "    CompletableFuture<String> asyncMethod(String input) throws IOException;\n" + "    \n"
				+ "    void varArgsMethod(String... args);\n" + "    \n"
				+ "    int[] arrayMethod(int[] input, String[][] matrix);\n" + "    \n"
				+ "    default void multipleExceptionsMethod() throws IOException, IllegalArgumentException, RuntimeException {\n"
				+ "        throw new RuntimeException(\"Not implemented\");\n" + "    }\n" + "    \n"
				+ "    static synchronized void synchronizedStaticMethod() {\n"
				+ "        System.out.println(\"Synchronized\");\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("ComplexInterface.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Interface", result.get("kind").asText());

		JsonNode methods = result.get("methods");
		assertEquals(7, methods.size());

		JsonNode varArgsMethod = methods.get(3);
		assertEquals("varArgsMethod", varArgsMethod.get("name").asText());
		assertEquals(1, varArgsMethod.get("params").size());
		String paramType = varArgsMethod.get("params").get(0).asText();
		assertTrue(paramType.equals("String...") || paramType.equals("String[]"));

		JsonNode arrayMethod = methods.get(4);
		assertEquals("arrayMethod", arrayMethod.get("name").asText());
		assertEquals("int[]", arrayMethod.get("returnType").asText());
		assertEquals(2, arrayMethod.get("params").size());
		assertEquals("int[]", arrayMethod.get("params").get(0).asText());
		assertEquals("String[][]", arrayMethod.get("params").get(1).asText());

		JsonNode exceptionsMethod = methods.get(5);
		assertEquals("multipleExceptionsMethod", exceptionsMethod.get("name").asText());
		assertTrue(exceptionsMethod.get("default").asBoolean());
		JsonNode throws1 = exceptionsMethod.get("throws");
		assertEquals(3, throws1.size());
		assertEquals("IOException", throws1.get(0).asText());
		assertEquals("IllegalArgumentException", throws1.get(1).asText());
		assertEquals("RuntimeException", throws1.get(2).asText());

		JsonNode syncMethod = methods.get(6);
		assertEquals("synchronizedStaticMethod", syncMethod.get("name").asText());
		assertTrue(syncMethod.get("static").asBoolean());
		assertTrue(syncMethod.get("synchronized").asBoolean());
	}

	@Test
	void testMarkerInterface() throws Exception {
		String javaCode = "public interface MarkerInterface {}";
		File inputFile = createTempJavaFile("MarkerInterface.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("MarkerInterface", result.get("name").asText());
		assertEquals("Interface", result.get("kind").asText());
		assertEquals(0, result.get("methods").size());
		assertEquals(0, result.get("fields").size());
		assertEquals(0, result.get("extends").size());
		assertEquals(0, result.get("implements").size());
	}

	@Test
	void testPackagePrivateInterface() throws Exception {
		String javaCode = "interface PackageInterface {\n" + "    void packageMethod();\n" + "    \n"
				+ "    default String getDefaultValue() {\n" + "        return \"package\";\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("PackageInterface.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("PackageInterface", result.get("name").asText());
		assertEquals("Interface", result.get("kind").asText());
		assertFalse(result.get("public").asBoolean());
		assertFalse(result.get("private").asBoolean());
		assertFalse(result.get("protected").asBoolean());

		JsonNode methods = result.get("methods");
		assertEquals(2, methods.size());
		assertEquals("packageMethod", methods.get(0).get("name").asText());
		assertEquals("getDefaultValue", methods.get(1).get("name").asText());
	}

	@Test
	void testInterfaceInInterface() throws Exception {
		String javaCode = "public interface OuterInterface {\n" + "    void outerMethod();\n" + "    \n"
				+ "    interface NestedInterface {\n" + "        void nestedMethod();\n" + "        \n"
				+ "        default void defaultNested() {\n" + "            System.out.println(\"Nested default\");\n"
				+ "        }\n" + "        \n" + "        interface DeeplyNestedInterface {\n"
				+ "            void deepMethod();\n" + "        }\n" + "    }\n" + "    \n"
				+ "    class NestedClass implements NestedInterface {\n" + "        public void nestedMethod() {\n"
				+ "            System.out.println(\"Implemented\");\n" + "        }\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("OuterInterface.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("OuterInterface", result.get("name").asText());
		assertEquals("Interface", result.get("kind").asText());

		JsonNode inners = result.get("inners");
		assertEquals(2, inners.size());

		JsonNode nestedInterface = inners.get(0);
		assertEquals("NestedInterface", nestedInterface.get("name").asText());
		assertEquals("Interface", nestedInterface.get("kind").asText());
		assertTrue(nestedInterface.get("static").asBoolean());

		JsonNode nestedInterfaceInners = nestedInterface.get("inners");
		assertEquals(1, nestedInterfaceInners.size());
		JsonNode deeplyNested = nestedInterfaceInners.get(0);
		assertEquals("DeeplyNestedInterface", deeplyNested.get("name").asText());
		assertEquals("Interface", deeplyNested.get("kind").asText());

		JsonNode nestedClass = inners.get(1);
		assertEquals("NestedClass", nestedClass.get("name").asText());
		assertEquals("Class", nestedClass.get("kind").asText());
		JsonNode implementsArray = nestedClass.get("implements");
		assertEquals(1, implementsArray.size());
		assertEquals("NestedInterface", implementsArray.get(0).asText());
	}

	@Test
	void testInterfaceWithAnnotations() throws Exception {
		String javaCode = "import java.lang.annotation.Target;\n" + "import java.lang.annotation.ElementType;\n" + "\n"
				+ "@FunctionalInterface\n" + "@Target(ElementType.TYPE)\n" + "@Deprecated\n"
				+ "public interface AnnotatedInterface<T> {\n" + "    @Deprecated\n"
				+ "    T process(@Deprecated T input);\n" + "    \n" + "    @SuppressWarnings(\"unchecked\")\n"
				+ "    default void defaultMethod() {\n" + "        System.out.println(\"Default\");\n" + "    }\n"
				+ "}";
		File inputFile = createTempJavaFile("AnnotatedInterface.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("AnnotatedInterface", result.get("name").asText());
		assertEquals("Interface", result.get("kind").asText());

		JsonNode annotations = result.get("annotations");
		assertEquals(3, annotations.size());
		assertEquals("FunctionalInterface", annotations.get(0).asText());
		assertEquals("Target", annotations.get(1).asText());
		assertEquals("Deprecated", annotations.get(2).asText());

		JsonNode methods = result.get("methods");
		assertEquals(2, methods.size());
		assertEquals("process", methods.get(0).get("name").asText());
		assertEquals("defaultMethod", methods.get(1).get("name").asText());
	}

	@Test
	void testInterfaceWithTrueFlagPruning() throws Exception {
		String javaCode = "public interface TestInterface {\n" + "    String CONSTANT = \"value\";\n"
				+ "    void abstractMethod();\n" + "    default void defaultMethod() {}\n" + "}";
		File inputFile = createTempJavaFile("TestInterface.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-t", "-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Interface", result.get("kind").asText());
		assertTrue(result.get("public").asBoolean());
		assertNull(result.get("private"));
		assertNull(result.get("protected"));
		assertNull(result.get("abstract"));
		assertNull(result.get("final"));
		assertNull(result.get("static"));

		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());
		JsonNode field = fields.get(0);
		assertTrue(field.get("public").asBoolean());
		assertTrue(field.get("static").asBoolean());
		assertTrue(field.get("final").asBoolean());
		assertNull(field.get("private"));
		assertNull(field.get("protected"));

		JsonNode methods = result.get("methods");
		assertEquals(2, methods.size());

		JsonNode abstractMethod = methods.get(0);
		assertTrue(abstractMethod.get("public").asBoolean());
		assertTrue(abstractMethod.get("abstract").asBoolean());
		assertNull(abstractMethod.get("private"));
		assertNull(abstractMethod.get("protected"));
		assertNull(abstractMethod.get("static"));

		JsonNode defaultMethod = methods.get(1);
		assertTrue(defaultMethod.get("default").asBoolean());
		assertNull(defaultMethod.get("abstract"));
	}

	@Test
	void testSerializableAndCloneableInterfaces() throws Exception {
		String javaCode = "import java.io.Serializable;\n" + "\n"
				+ "public interface CustomSerializable extends Serializable, Cloneable {\n"
				+ "    long getSerialVersionUID();\n" + "    \n" + "    default Object deepClone() {\n"
				+ "        try {\n" + "            return super.clone();\n" + "        } catch (Exception e) {\n"
				+ "            return null;\n" + "        }\n" + "    }\n" + "}";
		File inputFile = createTempJavaFile("CustomSerializable.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("CustomSerializable", result.get("name").asText());
		assertEquals("Interface", result.get("kind").asText());

		JsonNode extendsArray = result.get("extends");
		assertEquals(2, extendsArray.size());
		assertEquals("Serializable", extendsArray.get(0).asText());
		assertEquals("Cloneable", extendsArray.get(1).asText());

		JsonNode methods = result.get("methods");
		assertEquals(2, methods.size());
		assertEquals("getSerialVersionUID", methods.get(0).get("name").asText());
		assertEquals("deepClone", methods.get(1).get("name").asText());
		assertTrue(methods.get(1).get("default").asBoolean());
	}

	private File createTempJavaFile(String filename, String content) throws Exception {
		Path file = tempDir.resolve(filename);
		Files.writeString(file, content);
		return file.toFile();
	}
}
