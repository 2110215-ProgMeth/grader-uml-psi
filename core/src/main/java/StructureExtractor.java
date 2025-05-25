import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StructureExtractor {
    public static void main(String[] args) {
        try {
            if (args == null) {
                System.err.println("Error: Null arguments provided");
                throw new SecurityException("Null arguments provided");
            }

            if (args.length < 1) {
                System.err.println("Usage: java -jar structure.jar [-o out.json] [-t] <input.java>");
                throw new SecurityException("No arguments provided");
            }

            String outputPath = "output.json";
            boolean onlyTrue = false;
            String inputPath = null;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-o":
                        if (i + 1 < args.length) {
                            outputPath = args[++i];
                        } else {
                            System.err.println("Error: -o requires a filename");
                            throw new SecurityException("-o requires a filename");
                        }
                        break;
                    case "-t":
                        onlyTrue = true;
                        break;
                    default:
                        inputPath = args[i];
                }
            }

            if (inputPath == null) {
                System.err.println("Error: No input file specified");
                throw new SecurityException("No input file specified");
            }

            File javaFile = new File(inputPath);
            if (!javaFile.exists() || !javaFile.isFile()) {
                System.err.println("Error: Input file not found");
                throw new SecurityException("Input file not found");
            }

            configureSymbolSolver(javaFile.getParentFile());
            String code = Files.readString(javaFile.toPath(), StandardCharsets.UTF_8);
            CompilationUnit cu = StaticJavaParser.parse(code);

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            ArrayNode typesArray = rootNode.putArray("types");

            cu.getPackageDeclaration().ifPresent(pd -> rootNode.put("package", pd.getNameAsString()));

            for (TypeDeclaration<?> type : cu.getTypes()) {
                ObjectNode typeNode = extractTypeStructure(type, mapper);
                if (onlyTrue) {
                    pruneEmpty(typeNode);
                }
                typesArray.add(typeNode);
            }

            String output;
            if (!typesArray.isEmpty()) {
                if (typesArray.size() == 1) {
                    output = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(typesArray.get(0));
                } else {
                    TypeDeclaration<?> mainType = cu.getTypes().stream()
                            .filter(TypeDeclaration::isPublic)
                            .findFirst()
                            .orElse(cu.getTypes().get(cu.getTypes().size() - 1));
                    ObjectNode mainTypeNode = null;
                    for (int i = 0; i < typesArray.size(); i++) {
                        if (typesArray.get(i).get("name").asText().equals(mainType.getNameAsString())) {
                            mainTypeNode = (ObjectNode) typesArray.get(i);
                            break;
                        }
                    }
                    output = mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(mainTypeNode != null ? mainTypeNode : typesArray.get(0));
                }
            } else {
                output = "{}";
            }
            Files.writeString(Paths.get(outputPath), output, StandardCharsets.UTF_8);

        } catch (IOException | UnsolvedSymbolException e) {
            System.err.println("Error processing file: " + e.getMessage());
            e.printStackTrace();
            throw new SecurityException("Error processing file: " + e.getMessage());
        }
    }

    private static void pruneEmpty(ObjectNode node) {
        List<String> toRemove = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();
            JsonNode child = entry.getValue();
            if (child.isBoolean() && !child.booleanValue()) {
                toRemove.add(fieldName);
            } else if (child.isArray()) {
                ArrayNode array = (ArrayNode) child;
                for (JsonNode elem : array) {
                    if (elem.isObject()) {
                        pruneEmpty((ObjectNode) elem);
                    }
                }
                if (array.size() == 0) {
                    toRemove.add(fieldName);
                }
            } else if (child.isObject()) {
                pruneEmpty((ObjectNode) child);
                if (!child.fieldNames().hasNext()) {
                    toRemove.add(fieldName);
                }
            }
        }
        toRemove.forEach(node::remove);
    }

    private static void configureSymbolSolver(File sourceRoot) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        if (sourceRoot != null && sourceRoot.exists()) {
            typeSolver.add(new JavaParserTypeSolver(sourceRoot));
        }
        typeSolver.add(new ReflectionTypeSolver());
        StaticJavaParser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(LanguageLevel.JAVA_17);
    }

    private static ObjectNode extractTypeStructure(TypeDeclaration<?> typeDecl, ObjectMapper mapper) {
        ObjectNode typeNode = mapper.createObjectNode();
        typeNode.put("name", typeDecl.getNameAsString());
        if (typeDecl.isClassOrInterfaceDeclaration()) {
            ClassOrInterfaceDeclaration ci = typeDecl.asClassOrInterfaceDeclaration();
            typeNode.put("kind", ci.isInterface() ? "Interface" : "Class");
        } else if (typeDecl.isEnumDeclaration()) {
            typeNode.put("kind", "Enum");
        } else if (typeDecl.isAnnotationDeclaration()) {
            typeNode.put("kind", "Annotation");
        } else if (typeDecl.isRecordDeclaration()) {
            typeNode.put("kind", "Record");
        } else {
            typeNode.put("kind", "Unknown");
        }
        boolean isInterface = typeDecl.isClassOrInterfaceDeclaration()
                && typeDecl.asClassOrInterfaceDeclaration().isInterface();
        typeNode.put("public", typeDecl.hasModifier(Modifier.Keyword.PUBLIC));
        typeNode.put("protected", typeDecl.hasModifier(Modifier.Keyword.PROTECTED));
        typeNode.put("private", typeDecl.hasModifier(Modifier.Keyword.PRIVATE));
        boolean explicitAbstract = typeDecl.hasModifier(Modifier.Keyword.ABSTRACT);
        boolean hasAbstractMethods = typeDecl.getMethods().stream()
                .anyMatch(m -> m.hasModifier(Modifier.Keyword.ABSTRACT));
        typeNode.put("abstract", explicitAbstract || hasAbstractMethods);
        boolean explicitFinal = typeDecl.hasModifier(Modifier.Keyword.FINAL);
        boolean implicitFinal = typeDecl.isRecordDeclaration();
        typeNode.put("final", explicitFinal || implicitFinal);
        boolean isNested = typeDecl.getParentNode().filter(n -> n instanceof TypeDeclaration).isPresent();
        boolean explicitStatic = typeDecl.hasModifier(Modifier.Keyword.STATIC);
        boolean implicitStatic = (isInterface)
                || typeDecl.isEnumDeclaration()
                || typeDecl.isAnnotationDeclaration()
                || typeDecl.isRecordDeclaration()
                || typeDecl.getParentNode()
                        .filter(n -> n instanceof ClassOrInterfaceDeclaration
                                && ((ClassOrInterfaceDeclaration) n).isInterface())
                        .isPresent();
        typeNode.put("static", explicitStatic || (isNested && implicitStatic));

        ArrayNode extendsNode = mapper.createArrayNode();
        ArrayNode implNode = mapper.createArrayNode();
        if (typeDecl.isClassOrInterfaceDeclaration()) {
            ClassOrInterfaceDeclaration ci = typeDecl.asClassOrInterfaceDeclaration();
            for (ClassOrInterfaceType t : ci.getExtendedTypes()) {
                String name = t.getName().asString();
                if (t.getTypeArguments().isPresent()) {
                    String args = t.getTypeArguments().get().stream().map(Object::toString)
                            .collect(Collectors.joining(","));
                    name += "<" + args + ">";
                }
                extendsNode.add(name);
            }
            for (ClassOrInterfaceType t : ci.getImplementedTypes()) {
                String name = t.getName().asString();
                if (t.getTypeArguments().isPresent()) {
                    String args = t.getTypeArguments().get().stream().map(Object::toString)
                            .collect(Collectors.joining(","));
                    name += "<" + args + ">";
                }
                implNode.add(name);
            }
        } else if (typeDecl.isRecordDeclaration()) {
            RecordDeclaration rec = typeDecl.asRecordDeclaration();
            for (ClassOrInterfaceType t : rec.getImplementedTypes()) {
                String name = t.getName().asString();
                if (t.getTypeArguments().isPresent()) {
                    String args = t.getTypeArguments().get().stream().map(Object::toString)
                            .collect(Collectors.joining(","));
                    name += "<" + args + ">";
                }
                implNode.add(name);
            }
        }
        typeNode.set("extends", extendsNode);
        typeNode.set("implements", implNode);

        ArrayNode annNode = mapper.createArrayNode();
        for (AnnotationExpr ann : typeDecl.getAnnotations()) {
            annNode.add(ann.getName().getIdentifier());
        }
        typeNode.set("annotations", annNode);

        ArrayNode fieldsNode = mapper.createArrayNode();
        if (typeDecl.isRecordDeclaration()) {
            RecordDeclaration rec = typeDecl.asRecordDeclaration();
            for (Parameter comp : rec.getParameters()) {
                ObjectNode f = mapper.createObjectNode();
                f.put("name", comp.getNameAsString());
                f.put("type", comp.getType().asString());
                f.put("public", false);
                f.put("protected", false);
                f.put("private", true);
                f.put("static", false);
                f.put("final", true);
                fieldsNode.add(f);
            }
        }
        for (FieldDeclaration field : typeDecl.getFields()) {
            for (VariableDeclarator var : field.getVariables()) {
                ObjectNode f = mapper.createObjectNode();
                f.put("name", var.getNameAsString());
                f.put("type", var.getType().asString());
                f.put("public", isInterface || field.hasModifier(Modifier.Keyword.PUBLIC));
                f.put("protected", field.hasModifier(Modifier.Keyword.PROTECTED));
                f.put("private", field.hasModifier(Modifier.Keyword.PRIVATE));
                f.put("static", isInterface || field.hasModifier(Modifier.Keyword.STATIC));
                f.put("final", isInterface || field.hasModifier(Modifier.Keyword.FINAL));
                f.put("transient", field.hasModifier(Modifier.Keyword.TRANSIENT));
                f.put("volatile", field.hasModifier(Modifier.Keyword.VOLATILE));
                fieldsNode.add(f);
            }
        }
        typeNode.set("fields", fieldsNode);

        ArrayNode constructorsNode = mapper.createArrayNode();
        for (ConstructorDeclaration ctor : typeDecl.getConstructors()) {
            ObjectNode c = mapper.createObjectNode();
            c.put("name", ctor.getNameAsString());
            c.put("public", ctor.hasModifier(Modifier.Keyword.PUBLIC));
            c.put("protected", ctor.hasModifier(Modifier.Keyword.PROTECTED));
            c.put("private", ctor.hasModifier(Modifier.Keyword.PRIVATE));
            ArrayNode pNode = mapper.createArrayNode();
            for (Parameter p : ctor.getParameters()) {
                String repr = p.getType().asString();
                if (p.isVarArgs())
                    repr += "...";
                pNode.add(repr);
            }
            c.set("params", pNode);
            ArrayNode throwsNode = mapper.createArrayNode();
            ctor.getThrownExceptions().forEach(te -> throwsNode.add(te.asString()));
            c.set("throws", throwsNode);
            constructorsNode.add(c);
        }
        typeNode.set("constructors", constructorsNode);

        ArrayNode methodsNode = mapper.createArrayNode();
        if (typeDecl.isAnnotationDeclaration()) {
            AnnotationDeclaration ad = typeDecl.asAnnotationDeclaration();
            for (BodyDeclaration<?> member : ad.getMembers()) {
                if (member instanceof AnnotationMemberDeclaration) {
                    AnnotationMemberDeclaration amd = (AnnotationMemberDeclaration) member;
                    ObjectNode m = mapper.createObjectNode();
                    m.put("name", amd.getNameAsString());
                    m.put("returnType", amd.getType().asString());
                    m.put("default", amd.getDefaultValue().isPresent());
                    methodsNode.add(m);
                }
            }
        } else {
            for (MethodDeclaration method : typeDecl.getMethods()) {
                ObjectNode m = mapper.createObjectNode();
                m.put("name", method.getNameAsString());
                m.put("returnType", method.getType().asString());
                m.put("public", isInterface || method.hasModifier(Modifier.Keyword.PUBLIC));
                m.put("protected", method.hasModifier(Modifier.Keyword.PROTECTED));
                m.put("private", method.hasModifier(Modifier.Keyword.PRIVATE));
                boolean ifaceAbstract = isInterface && !method.hasModifier(Modifier.Keyword.DEFAULT)
                        && !method.hasModifier(Modifier.Keyword.STATIC);
                m.put("abstract", ifaceAbstract || method.hasModifier(Modifier.Keyword.ABSTRACT));
                m.put("static", method.hasModifier(Modifier.Keyword.STATIC));
                m.put("final", method.hasModifier(Modifier.Keyword.FINAL));
                m.put("synchronized", method.hasModifier(Modifier.Keyword.SYNCHRONIZED));
                m.put("native", method.hasModifier(Modifier.Keyword.NATIVE));
                m.put("default", method.hasModifier(Modifier.Keyword.DEFAULT));
                ArrayNode paramsNode = mapper.createArrayNode();
                for (Parameter p : method.getParameters()) {
                    String repr = p.getType().asString();
                    if (p.isVarArgs())
                        repr += "...";
                    paramsNode.add(repr);
                }
                m.set("params", paramsNode);
                ArrayNode throwsNode = mapper.createArrayNode();
                method.getThrownExceptions().forEach(te -> throwsNode.add(te.asString()));
                m.set("throws", throwsNode);
                methodsNode.add(m);
            }
        }
        typeNode.set("methods", methodsNode);

        ArrayNode innersNode = mapper.createArrayNode();
        for (BodyDeclaration<?> member : typeDecl.getMembers()) {
            if (member.isTypeDeclaration()) {
                innersNode.add(extractTypeStructure(member.asTypeDeclaration(), mapper));
            }
        }
        typeNode.set("inners", innersNode);

        return typeNode;
    }
}