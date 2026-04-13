package com.prodbuddy.tools.codecontext;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class JavaGraphExtractor {

    public JavaGraphSnapshot extract(Path rootPath) {
        List<GraphClassNode> classes = new ArrayList<>();
        List<GraphMethodNode> methods = new ArrayList<>();
        List<GraphDefineEdge> defines = new ArrayList<>();
        List<GraphInheritanceEdge> inherits = new ArrayList<>();
        List<GraphCallEdge> calls = new ArrayList<>();
        try (Stream<Path> files = Files.walk(rootPath)) {
            files.filter(this::isJavaFile).forEach(path -> parseFile(path, classes, methods, defines, inherits, calls));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to extract graph from " + rootPath, exception);
        }
        return new JavaGraphSnapshot(classes, methods, defines, inherits, calls);
    }

    private boolean isJavaFile(Path path) {
        return Files.isRegularFile(path) && path.toString().endsWith(".java");
    }

    private void parseFile(
            Path path,
            List<GraphClassNode> classes,
            List<GraphMethodNode> methods,
            List<GraphDefineEdge> defines,
            List<GraphInheritanceEdge> inherits,
            List<GraphCallEdge> calls
    ) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit unit = parser.parse(path).getResult().orElse(null);
            if (unit == null) {
                return;
            }
            String pkg = unit.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
            unit.findAll(ClassOrInterfaceDeclaration.class).forEach(type
                    -> parseType(type, pkg, path, classes, methods, defines, inherits, calls)
            );
        } catch (IOException exception) {
            // Skip unreadable files.
        }
    }

    private void parseType(
            ClassOrInterfaceDeclaration type,
            String pkg,
            Path path,
            List<GraphClassNode> classes,
            List<GraphMethodNode> methods,
            List<GraphDefineEdge> defines,
            List<GraphInheritanceEdge> inherits,
            List<GraphCallEdge> calls
    ) {
        String classFqn = pkg.isBlank() ? type.getNameAsString() : pkg + "." + type.getNameAsString();
        classes.add(new GraphClassNode(classFqn, classFqn, type.getNameAsString(), path.toString()));
        addInheritance(type, classFqn, inherits);
        Map<String, String> knownMethods = addMethods(type, classFqn, path, methods, defines);
        addCalls(type, knownMethods, calls);
    }

    private void addInheritance(
            ClassOrInterfaceDeclaration type,
            String classFqn,
            List<GraphInheritanceEdge> inherits
    ) {
        type.getExtendedTypes().forEach(ext
                -> inherits.add(new GraphInheritanceEdge(classFqn, ext.getNameAsString(), "extends"))
        );
        type.getImplementedTypes().forEach(impl
                -> inherits.add(new GraphInheritanceEdge(classFqn, impl.getNameAsString(), "implements"))
        );
    }

    private Map<String, String> addMethods(
            ClassOrInterfaceDeclaration type,
            String classFqn,
            Path path,
            List<GraphMethodNode> methods,
            List<GraphDefineEdge> defines
    ) {
        Map<String, String> knownMethods = new HashMap<>();
        type.getMethods().forEach(method
                -> addMethod(method, classFqn, path, methods, defines, knownMethods)
        );
        type.getConstructors().forEach(ctor
                -> addConstructor(ctor, classFqn, path, methods, defines, knownMethods)
        );
        return knownMethods;
    }

    private void addMethod(
            MethodDeclaration method,
            String classFqn,
            Path path,
            List<GraphMethodNode> methods,
            List<GraphDefineEdge> defines,
            Map<String, String> knownMethods
    ) {
        String signature = method.getParameters().stream()
                .map(p -> p.getType().asString())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        String methodId = classFqn + "#" + method.getNameAsString() + "(" + signature + ")";
        methods.add(new GraphMethodNode(methodId, classFqn, method.getNameAsString(), signature, path.toString()));
        defines.add(new GraphDefineEdge(classFqn, methodId));
        knownMethods.put(method.getNameAsString(), methodId);
    }

    private void addConstructor(
            ConstructorDeclaration ctor,
            String classFqn,
            Path path,
            List<GraphMethodNode> methods,
            List<GraphDefineEdge> defines,
            Map<String, String> knownMethods
    ) {
        String signature = ctor.getParameters().stream()
                .map(p -> p.getType().asString())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        String methodId = classFqn + "#" + ctor.getNameAsString() + "(" + signature + ")";
        methods.add(new GraphMethodNode(methodId, classFqn, ctor.getNameAsString(), signature, path.toString()));
        defines.add(new GraphDefineEdge(classFqn, methodId));
        knownMethods.put(ctor.getNameAsString(), methodId);
    }

    private void addCalls(
            ClassOrInterfaceDeclaration type,
            Map<String, String> knownMethods,
            List<GraphCallEdge> calls
    ) {
        type.findAll(MethodDeclaration.class).forEach(method -> {
            String callerId = knownMethods.get(method.getNameAsString());
            if (callerId == null) {
                return;
            }
            method.findAll(MethodCallExpr.class).forEach(call
                    -> calls.add(new GraphCallEdge(callerId, knownMethods.getOrDefault(
                            call.getNameAsString(),
                            "<unresolved>::" + call.getNameAsString()
                    )))
            );
        });
    }
}
