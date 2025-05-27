# Java Structure Extractor

A tool for extracting structural information from Java source files and converting them to JSON format for analysis and autograding purposes.

## Quick Start

### Download Latest Release
See the Release section for pre-build jarfile (Java 21)
```bash
wget https://github.com/your-repo/java-structure-extractor/releases/latest/download/structure-extractor-<version>.jar
java -jar structure-extractor-<version>.jar MyClass.java
```

### Build Your Own
Using Docker:
```bash
python scripts/build.py [--java <docker-image>]
```

Using Maven:
```bash
cd core
mvn clean package assembly:single
```

## Overview

This tool parses Java source code and extracts structural elements including:
- Class and interface declarations
- Method signatures and modifiers
- Field declarations and types
- Constructor parameters
- Inheritance relationships
- Generic type parameters
- Annotations
- Nested type definitions

## Installation

### Prerequisites
- Java 21+
- Python 3.8+ (for main script)
- Docker (for automated grading)

### Setup
```bash
git clone https://github.com/your-repo/java-structure-extractor
cd java-structure-extractor
pip install -r requirements.txt
```

### Building from Source
```bash
cd core
mvn clean package assembly:single
```

## Usage

### Main Script (Recommended)
For automated grading workflows, use the main script:

```bash
python main.py --input_dir submissions --ref_dir solution
```

**Main Script Flags:**
- `--input_dir`: Directory containing student JAR submissions (required)
- `--ref_dir`: Reference/solution directory (default: `solution`)
- `--java`: Java Docker image to use (default: `openjdk:21-jdk-slim`)
- `--output_dir`: Output directory for results (default: `umls`)

### Structure Extractor (Manual Use)
For individual file processing, use the structure extractor directly:

```bash
java -jar structure-extractor.jar [flags] input.java
```

**Structure Extractor Flags:**
- `-o <filename>`: Specify output JSON file (default: `output.json`)
- `-t`: Prune false boolean values from output for cleaner JSON
- `input.java`: Java source file to analyze (required)

### Manual Scripts
Located in the `scripts/` directory for individual operations:

#### Building the JAR
```bash
python scripts/build.py [--java <docker-image>]
```

#### Processing Multiple Files
```bash
python scripts/generate_structure.py <input_dir> <output.json>
```

#### Grading Student Submissions
```bash
python scripts/grade_uml_structure.py <submissions_dir> <solution.json>
```

## Configuration

### Target Files
Create a `files.txt` file listing the Java files to process:
```
MyClass.java
AnotherClass.java
```

### Reference Solution
Place reference Java files in the `solution/` directory or specify with `--ref_dir`.

## Output Format

The tool generates JSON structures containing:

```json
{
  "name": "ClassName",
  "kind": "Class|Interface|Enum|Record|Annotation",
  "public": true,
  "protected": false,
  "private": false,
  "abstract": false,
  "final": false,
  "static": false,
  "extends": ["ParentClass"],
  "implements": ["Interface1", "Interface2"],
  "annotations": ["Annotation1"],
  "fields": [
    {
      "name": "fieldName",
      "type": "String",
      "public": false,
      "private": true,
      "static": false,
      "final": false
    }
  ],
  "constructors": [
    {
      "name": "ClassName",
      "public": true,
      "params": ["String", "int"],
      "throws": ["Exception"]
    }
  ],
  "methods": [
    {
      "name": "methodName",
      "returnType": "void",
      "public": true,
      "abstract": false,
      "static": false,
      "params": ["String"],
      "throws": ["IOException"]
    }
  ],
  "inners": []
}
```

## Autograding

The main script provides automated grading capabilities:

1. **Submission Processing**: Extracts and analyzes JAR files from student submissions
2. **Structure Comparison**: Compares student code structure against reference solution
3. **Report Generation**: Creates CSV files with grading results and detailed feedback
4. **Docker Isolation**: Runs grading in isolated Docker containers for security

### Grading Workflow
```bash
# 1. Prepare reference solution in solution/ directory
# 2. Place student JAR files in submissions/ directory  
# 3. Run automated grading
python main.py --input_dir submissions --ref_dir solution --output_dir results

# 4. Check results in results/ directory:
#    - uml-score.csv: Pass/fail results
#    - uml-comments.csv: Detailed feedback
#    - json/: Individual student analyses
```

### Grading Criteria
The system compares:
- Class structure and modifiers
- Method signatures and access levels
- Field declarations and types
- Inheritance relationships
- Constructor parameters
- Exception specifications

## Development

### Running Tests
```bash
cd core
mvn test
```

### Code Quality Checks
```bash
mvn spotless:check
mvn checkstyle:check
mvn spotbugs:check
mvn pmd:check
```

### Supported Java Versions
- Java 11
- Java 17  
- Java 21
- Java 24

## Docker Support

The tool includes Docker integration for consistent environments:

```bash
# Build using Docker
python scripts/build.py

# Run grading in Docker containers
python main.py --input_dir submissions --java openjdk:21-jdk-slim
```

## Project Structure

```
java-structure-extractor/
├── core/                    # Java source and Maven project
│   ├── src/main/java/      # Structure extractor implementation
│   ├── src/test/java/      # Test suite
│   └── pom.xml             # Maven configuration
├── scripts/                # Manual operation scripts
│   ├── build.py            # Build JAR with Docker
│   ├── generate_structure.py # Process multiple files
│   └── grade_uml_structure.py # Grade submissions
├── example/                # Example Java files
├── main.py                 # Main grading script
├── files.txt              # Target files configuration
└── requirements.txt        # Python dependencies
```

## Contributing

Contribution to this project is encouraged. Feel free to create issues and pull requests for your invaluable contributions.