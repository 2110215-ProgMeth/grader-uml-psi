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

public class ArgsTest {

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
	void testNoArguments() {
		assertThrows(SecurityException.class, () -> StructureExtractor.main(new String[]{}));
		assertTrue(errContent.toString().contains("Usage: java -jar structure.jar"));
	}

	@Test
	void testEmptyArguments() {
		assertThrows(SecurityException.class, () -> StructureExtractor.main(new String[0]));
		assertTrue(errContent.toString().contains("Usage: java -jar structure.jar"));
	}

	@Test
	void testNullArguments() {
		assertThrows(SecurityException.class, () -> StructureExtractor.main(null));
	}

	@Test
	void testOutputArgumentAlone() {
		assertThrows(SecurityException.class, () -> StructureExtractor.main(new String[]{"-o"}));
		assertTrue(errContent.toString().contains("Error: -o requires a filename"));
	}

	@Test
	void testOutputArgumentWithEmptyFilename() {
		assertThrows(SecurityException.class, () -> StructureExtractor.main(new String[]{"-o", ""}));
		assertTrue(errContent.toString().contains("Error: No input file specified"));
	}

	@Test
	void testOutputArgumentWithWhitespaceFilename() {
		assertThrows(SecurityException.class, () -> StructureExtractor.main(new String[]{"-o", "   "}));
		assertTrue(errContent.toString().contains("Error: No input file specified"));
	}

	@Test
	void testTrueFlagAlone() {
		assertThrows(SecurityException.class, () -> StructureExtractor.main(new String[]{"-t"}));
		assertTrue(errContent.toString().contains("Error: No input file specified"));
	}

	@Test
	void testMultipleTrueFlags() {
		assertThrows(SecurityException.class, () -> StructureExtractor.main(new String[]{"-t", "-t", "-t"}));
		assertTrue(errContent.toString().contains("Error: No input file specified"));
	}

	@Test
	void testOutputAndTrueFlagWithoutInput() {
		assertThrows(SecurityException.class, () -> StructureExtractor.main(new String[]{"-o", "test.json", "-t"}));
		assertTrue(errContent.toString().contains("Error: No input file specified"));
	}

	@Test
	void testTrueFlagAndOutputWithoutInput() {
		assertThrows(SecurityException.class, () -> StructureExtractor.main(new String[]{"-t", "-o", "test.json"}));
		assertTrue(errContent.toString().contains("Error: No input file specified"));
	}

	@Test
	void testNonExistentInputFile() {
		assertThrows(SecurityException.class, () -> StructureExtractor.main(new String[]{"nonexistent.java"}));
		assertTrue(errContent.toString().contains("Error: Input file not found"));
	}

	@Test
	void testNonExistentInputFileWithOutput() {
		assertThrows(SecurityException.class,
				() -> StructureExtractor.main(new String[]{"-o", "out.json", "nonexistent.java"}));
		assertTrue(errContent.toString().contains("Error: Input file not found"));
	}

	@Test
	void testNonExistentInputFileWithTrueFlag() {
		assertThrows(SecurityException.class, () -> StructureExtractor.main(new String[]{"-t", "nonexistent.java"}));
		assertTrue(errContent.toString().contains("Error: Input file not found"));
	}

	@Test
	void testNonExistentInputFileWithAllFlags() {
		assertThrows(SecurityException.class,
				() -> StructureExtractor.main(new String[]{"-o", "out.json", "-t", "nonexistent.java"}));
		assertTrue(errContent.toString().contains("Error: Input file not found"));
	}

	@Test
	void testDirectoryAsInput() throws Exception {
		File directory = tempDir.toFile();
		assertThrows(SecurityException.class, () -> StructureExtractor.main(new String[]{directory.getAbsolutePath()}));
		assertTrue(errContent.toString().contains("Error: Input file not found"));
	}

	@Test
	void testEmptyFilenameAsInput() {
		assertThrows(SecurityException.class, () -> StructureExtractor.main(new String[]{""}));
		assertTrue(errContent.toString().contains("Error: Input file not found"));
	}

	@Test
	void testWhitespaceFilenameAsInput() {
		assertThrows(SecurityException.class, () -> StructureExtractor.main(new String[]{"   "}));
		assertTrue(errContent.toString().contains("Error: Input file not found"));
	}

	@Test
	void testSpecialCharactersInFilename() {
		assertThrows(SecurityException.class, () -> StructureExtractor.main(new String[]{"file@#$%^&*().java"}));
		assertTrue(errContent.toString().contains("Error: Input file not found"));
	}

	@Test
	void testValidInputWithDefaultOutput() throws Exception {
		String javaCode = "public class Test {}";
		File inputFile = createTempJavaFile("Test.java", javaCode);

		StructureExtractor.main(new String[]{inputFile.getAbsolutePath()});

		Path defaultOutput = Path.of("output.json");
		assertTrue(Files.exists(defaultOutput));
		Files.deleteIfExists(defaultOutput);
	}

	@Test
	void testValidInputWithCustomOutput() throws Exception {
		String javaCode = "public class Test {}";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("custom.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		assertTrue(Files.exists(outputFile));
		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Test", result.get("name").asText());
	}

	@Test
	void testValidInputWithTrueFlag() throws Exception {
		String javaCode = "public class Test { private boolean flag; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-t", "-o", outputFile.toString(), inputFile.getAbsolutePath()});

		assertTrue(Files.exists(outputFile));
		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertTrue(result.get("public").asBoolean());
		assertNull(result.get("private"));
	}

	@Test
	void testAllFlagsWithValidInput() throws Exception {
		String javaCode = "public class Test { private boolean flag; }";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), "-t", inputFile.getAbsolutePath()});

		assertTrue(Files.exists(outputFile));
		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertTrue(result.get("public").asBoolean());
		assertNull(result.get("private"));
	}

	@Test
	void testArgumentOrderVariations() throws Exception {
		String javaCode = "public class Test {}";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile1 = tempDir.resolve("output1.json");
		Path outputFile2 = tempDir.resolve("output2.json");
		Path outputFile3 = tempDir.resolve("output3.json");

		StructureExtractor.main(new String[]{"-o", outputFile1.toString(), "-t", inputFile.getAbsolutePath()});
		StructureExtractor.main(new String[]{"-t", "-o", outputFile2.toString(), inputFile.getAbsolutePath()});
		StructureExtractor.main(new String[]{inputFile.getAbsolutePath(), "-o", outputFile3.toString(), "-t"});

		assertTrue(Files.exists(outputFile1));
		assertTrue(Files.exists(outputFile2));
		assertTrue(Files.exists(outputFile3));

		String content1 = Files.readString(outputFile1);
		String content2 = Files.readString(outputFile2);
		String content3 = Files.readString(outputFile3);

		assertEquals(content1, content2);
		assertEquals(content2, content3);
	}

	@Test
	void testInvalidFlagIgnored() throws Exception {
		String javaCode = "public class Test {}";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-invalid", "-o", outputFile.toString(), inputFile.getAbsolutePath()});

		assertTrue(Files.exists(outputFile));
		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Test", result.get("name").asText());
	}

	@Test
	void testLongFilenames() throws Exception {
		String longName = "A".repeat(200) + ".java";
		String javaCode = "public class " + "A".repeat(200) + " {}";
		File inputFile = createTempJavaFile(longName, javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		assertTrue(Files.exists(outputFile));
	}

	@Test
	void testUnicodeFilenames() throws Exception {
		String unicodeName = "测试文件.java";
		String javaCode = "public class UnicodeTest {}";
		File inputFile = createTempJavaFile(unicodeName, javaCode);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		assertTrue(Files.exists(outputFile));
		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("UnicodeTest", result.get("name").asText());
	}

	@Test
	void testOutputFileInNonExistentDirectory() throws Exception {
		String javaCode = "public class Test {}";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path nonExistentDir = tempDir.resolve("nonexistent");
		Path outputFile = nonExistentDir.resolve("output.json");

		assertThrows(SecurityException.class,
				() -> StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()}));
	}

	@Test
	void testOutputFileWithoutExtension() throws Exception {
		String javaCode = "public class Test {}";
		File inputFile = createTempJavaFile("Test.java", javaCode);
		Path outputFile = tempDir.resolve("output_no_ext");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile.getAbsolutePath()});

		assertTrue(Files.exists(outputFile));
		JsonNode result = mapper.readTree(Files.readString(outputFile));
		assertEquals("Test", result.get("name").asText());
	}

	@Test
	void testOutputFileOverwrite() throws Exception {
		String javaCode1 = "public class Test1 {}";
		String javaCode2 = "public class Test2 {}";
		File inputFile1 = createTempJavaFile("Test1.java", javaCode1);
		File inputFile2 = createTempJavaFile("Test2.java", javaCode2);
		Path outputFile = tempDir.resolve("output.json");

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile1.getAbsolutePath()});
		JsonNode result1 = mapper.readTree(Files.readString(outputFile));
		assertEquals("Test1", result1.get("name").asText());

		StructureExtractor.main(new String[]{"-o", outputFile.toString(), inputFile2.getAbsolutePath()});
		JsonNode result2 = mapper.readTree(Files.readString(outputFile));
		assertEquals("Test2", result2.get("name").asText());
	}

	private File createTempJavaFile(String filename, String content) throws Exception {
		Path file = tempDir.resolve(filename);
		Files.writeString(file, content);
		return file.toFile();
	}
}
