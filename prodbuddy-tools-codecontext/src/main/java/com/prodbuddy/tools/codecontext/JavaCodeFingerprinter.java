package com.prodbuddy.tools.codecontext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class JavaCodeFingerprinter {

    String fingerprint(Path projectPath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<Path> files = javaSourceFiles(projectPath);
            for (Path file : files) {
                updateDigest(digest, projectPath, file);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest algorithm not available", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint Java source files", exception);
        }
    }

    private List<Path> javaSourceFiles(Path projectPath) throws IOException {
        try (Stream<Path> stream = Files.walk(projectPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private void updateDigest(MessageDigest digest, Path projectPath, Path file) throws IOException {
        String relative = projectPath.toAbsolutePath().normalize()
                .relativize(file.toAbsolutePath().normalize()).toString();
        digest.update(relative.getBytes(StandardCharsets.UTF_8));
        digest.update(Files.readAllBytes(file));
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
