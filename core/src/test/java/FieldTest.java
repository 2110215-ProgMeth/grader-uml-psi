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

public class FieldTest {

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
	void testNoFields() throws Exception {
		String javaCode = "public class Test {}";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(0, fields.size());
	}

	@Test
	void testPublicField() throws Exception {
		String javaCode = "public class Test { public String name; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("name", field.get("name").asText());
		assertEquals("String", field.get("type").asText());
		assertTrue(field.get("public").asBoolean());
		assertFalse(field.get("protected").asBoolean());
		assertFalse(field.get("private").asBoolean());
		assertFalse(field.get("static").asBoolean());
		assertFalse(field.get("final").asBoolean());
	}

	@Test
	void testPrivateField() throws Exception {
		String javaCode = "public class Test { private int age; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("age", field.get("name").asText());
		assertEquals("int", field.get("type").asText());
		assertFalse(field.get("public").asBoolean());
		assertFalse(field.get("protected").asBoolean());
		assertTrue(field.get("private").asBoolean());
	}

	@Test
	void testProtectedField() throws Exception {
		String javaCode = "public class Test { protected boolean active; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("active", field.get("name").asText());
		assertEquals("boolean", field.get("type").asText());
		assertFalse(field.get("public").asBoolean());
		assertTrue(field.get("protected").asBoolean());
		assertFalse(field.get("private").asBoolean());
	}

	@Test
	void testPackagePrivateField() throws Exception {
		String javaCode = "public class Test { double value; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("value", field.get("name").asText());
		assertEquals("double", field.get("type").asText());
		assertFalse(field.get("public").asBoolean());
		assertFalse(field.get("protected").asBoolean());
		assertFalse(field.get("private").asBoolean());
	}

	@Test
	void testStaticField() throws Exception {
		String javaCode = "public class Test { public static String VERSION = \"1.0\"; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("VERSION", field.get("name").asText());
		assertEquals("String", field.get("type").asText());
		assertTrue(field.get("public").asBoolean());
		assertTrue(field.get("static").asBoolean());
		assertFalse(field.get("final").asBoolean());
	}

	@Test
	void testFinalField() throws Exception {
		String javaCode = "public class Test { public final int MAX_SIZE = 100; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("MAX_SIZE", field.get("name").asText());
		assertEquals("int", field.get("type").asText());
		assertTrue(field.get("public").asBoolean());
		assertTrue(field.get("final").asBoolean());
		assertFalse(field.get("static").asBoolean());
	}

	@Test
	void testStaticFinalField() throws Exception {
		String javaCode = "public class Test { public static final String CONSTANT = \"value\"; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("CONSTANT", field.get("name").asText());
		assertEquals("String", field.get("type").asText());
		assertTrue(field.get("public").asBoolean());
		assertTrue(field.get("static").asBoolean());
		assertTrue(field.get("final").asBoolean());
	}

	@Test
	void testTransientField() throws Exception {
		String javaCode = "public class Test { private transient String temp; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("temp", field.get("name").asText());
		assertEquals("String", field.get("type").asText());
		assertTrue(field.get("private").asBoolean());
		assertTrue(field.get("transient").asBoolean());
		assertFalse(field.get("volatile").asBoolean());
	}

	@Test
	void testVolatileField() throws Exception {
		String javaCode = "public class Test { private volatile boolean flag; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("flag", field.get("name").asText());
		assertEquals("boolean", field.get("type").asText());
		assertTrue(field.get("private").asBoolean());
		assertTrue(field.get("volatile").asBoolean());
		assertFalse(field.get("transient").asBoolean());
	}

	@Test
	void testArrayField() throws Exception {
		String javaCode = "public class Test { private String[] items; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("items", field.get("name").asText());
		assertEquals("String[]", field.get("type").asText());
	}

	@Test
	void testMultiDimensionalArrayField() throws Exception {
		String javaCode = "public class Test { private int[][] matrix; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("matrix", field.get("name").asText());
		assertEquals("int[][]", field.get("type").asText());
	}

	@Test
	void testGenericField() throws Exception {
		String javaCode = "import java.util.List; public class Test { private List<String> names; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("names", field.get("name").asText());
		assertEquals("List<String>", field.get("type").asText());
	}

	@Test
	void testComplexGenericField() throws Exception {
		String javaCode = "import java.util.Map; public class Test { private Map<String, Integer> counts; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("counts", field.get("name").asText());
		assertEquals("Map<String,Integer>", field.get("type").asText());
	}

	@Test
	void testWildcardGenericField() throws Exception {
		String javaCode = "import java.util.List; public class Test { private List<?> items; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("items", field.get("name").asText());
		assertEquals("List<?>", field.get("type").asText());
	}

	@Test
	void testMultipleFieldsOnSameLine() throws Exception {
		String javaCode = "public class Test { private int x, y, z; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(3, fields.size());

		assertEquals("x", fields.get(0).get("name").asText());
		assertEquals("int", fields.get(0).get("type").asText());
		assertTrue(fields.get(0).get("private").asBoolean());

		assertEquals("y", fields.get(1).get("name").asText());
		assertEquals("int", fields.get(1).get("type").asText());
		assertTrue(fields.get(1).get("private").asBoolean());

		assertEquals("z", fields.get(2).get("name").asText());
		assertEquals("int", fields.get(2).get("type").asText());
		assertTrue(fields.get(2).get("private").asBoolean());
	}

	@Test
	void testMixedAccessModifierFields() throws Exception {
		String javaCode = "public class Test {\n" + "    public String publicField;\n"
				+ "    protected int protectedField;\n" + "    private boolean privateField;\n"
				+ "    String packageField;\n" + "    public static final String CONSTANT = \"value\";\n"
				+ "    private volatile transient Object temp;\n" + "}";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(6, fields.size());

		JsonNode publicField = fields.get(0);
		assertEquals("publicField", publicField.get("name").asText());
		assertTrue(publicField.get("public").asBoolean());

		JsonNode protectedField = fields.get(1);
		assertEquals("protectedField", protectedField.get("name").asText());
		assertTrue(protectedField.get("protected").asBoolean());

		JsonNode privateField = fields.get(2);
		assertEquals("privateField", privateField.get("name").asText());
		assertTrue(privateField.get("private").asBoolean());

		JsonNode packageField = fields.get(3);
		assertEquals("packageField", packageField.get("name").asText());
		assertFalse(packageField.get("public").asBoolean());
		assertFalse(packageField.get("protected").asBoolean());
		assertFalse(packageField.get("private").asBoolean());

		JsonNode constantField = fields.get(4);
		assertEquals("CONSTANT", constantField.get("name").asText());
		assertTrue(constantField.get("public").asBoolean());
		assertTrue(constantField.get("static").asBoolean());
		assertTrue(constantField.get("final").asBoolean());

		JsonNode tempField = fields.get(5);
		assertEquals("temp", tempField.get("name").asText());
		assertTrue(tempField.get("private").asBoolean());
		assertTrue(tempField.get("volatile").asBoolean());
		assertTrue(tempField.get("transient").asBoolean());
	}

	@Test
	void testFieldsWithTrueFlagPruning() throws Exception {
		String javaCode = "public class Test { private boolean flag; public String name; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-t", "-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(2, fields.size());

		JsonNode privateField = fields.get(0);
		assertEquals("flag", privateField.get("name").asText());
		assertTrue(privateField.get("private").asBoolean());
		assertNull(privateField.get("public"));
		assertNull(privateField.get("protected"));
		assertNull(privateField.get("static"));
		assertNull(privateField.get("final"));

		JsonNode publicField = fields.get(1);
		assertEquals("name", publicField.get("name").asText());
		assertTrue(publicField.get("public").asBoolean());
		assertNull(publicField.get("private"));
		assertNull(publicField.get("protected"));
	}

	@Test
	void testEnumFields() throws Exception {
		String javaCode = "public enum Status { ACTIVE, INACTIVE; private String description; }";
		File inputFile = createTempJavaFile("Status.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Enum", result.get("kind").asText());
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("description", field.get("name").asText());
		assertEquals("String", field.get("type").asText());
		assertTrue(field.get("private").asBoolean());
	}

	@Test
	void testRecordFields() throws Exception {
		String javaCode = "public record Person(String name, int age) { private static int count; }";
		File inputFile = createTempJavaFile("Person.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Record", result.get("kind").asText());
		JsonNode fields = result.get("fields");
		assertEquals(3, fields.size());

		JsonNode nameField = fields.get(0);
		assertEquals("name", nameField.get("name").asText());
		assertEquals("String", nameField.get("type").asText());
		assertTrue(nameField.get("private").asBoolean());
		assertTrue(nameField.get("final").asBoolean());

		JsonNode ageField = fields.get(1);
		assertEquals("age", ageField.get("name").asText());
		assertEquals("int", ageField.get("type").asText());
		assertTrue(ageField.get("private").asBoolean());
		assertTrue(ageField.get("final").asBoolean());

		JsonNode countField = fields.get(2);
		assertEquals("count", countField.get("name").asText());
		assertEquals("int", countField.get("type").asText());
		assertTrue(countField.get("private").asBoolean());
		assertTrue(countField.get("static").asBoolean());
	}

	@Test
	void testCustomObjectField() throws Exception {
		String javaCode = "class Address {\n" + "    String street;\n" + "}\n" + "public class Person {\n"
				+ "    private Address address;\n" + "}";
		File inputFile = createTempJavaFile("Person.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(1, fields.size());

		JsonNode field = fields.get(0);
		assertEquals("address", field.get("name").asText());
		assertEquals("Address", field.get("type").asText());
		assertTrue(field.get("private").asBoolean());
	}

	@Test
	void testPrimitiveTypeFields() throws Exception {
		String javaCode = "public class Test {\n" + "    byte byteField;\n" + "    short shortField;\n"
				+ "    int intField;\n" + "    long longField;\n" + "    float floatField;\n"
				+ "    double doubleField;\n" + "    boolean booleanField;\n" + "    char charField;\n" + "}";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		JsonNode result = mapper.readTree(Files.readString(outputFile));
		JsonNode fields = result.get("fields");
		assertEquals(8, fields.size());

		assertEquals("byte", fields.get(0).get("type").asText());
		assertEquals("short", fields.get(1).get("type").asText());
		assertEquals("int", fields.get(2).get("type").asText());
		assertEquals("long", fields.get(3).get("type").asText());
		assertEquals("float", fields.get(4).get("type").asText());
		assertEquals("double", fields.get(5).get("type").asText());
		assertEquals("boolean", fields.get(6).get("type").asText());
		assertEquals("char", fields.get(7).get("type").asText());
	}

	private File createTempJavaFile(String filename, String content) throws Exception {
		Path file = tempDir.resolve(filename);
		Files.writeString(file, content);
		return file.toFile();
	}
}
