package com.prodbuddy.tools.codecontext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LocalGraphDbService {

    public Map<String, Object> build(Path dbPath, JavaGraphSnapshot snapshot) {
        ensureParentExists(dbPath);
        String jdbcUrl = "jdbc:h2:file:" + dbPath.toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            createSchema(connection);
            clearTables(connection);
            insertClasses(connection, snapshot.classes());
            insertMethods(connection, snapshot.methods());
            insertDefines(connection, snapshot.defines());
            insertInherits(connection, snapshot.inherits());
            insertCalls(connection, snapshot.calls());
            new ClassMetricsInserter().insert(connection, snapshot.metrics());
            return buildResult(dbPath, snapshot);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to build local graph DB", exception);
        }
    }

    public Map<String, Object> refresh(Path dbPath, JavaGraphSnapshot snapshot, String codeFingerprint, boolean forceRefresh) {
        ensureParentExists(dbPath);
        String jdbcUrl = "jdbc:h2:file:" + dbPath.toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            createSchema(connection);
            String normalizedFingerprint = normalizeFingerprint(codeFingerprint);
            String existingFingerprint = readFingerprint(connection);
            if (!forceRefresh && isUnchanged(existingFingerprint, normalizedFingerprint)) {
                return Map.of(
                        "dbPath", dbPath.toAbsolutePath().toString(),
                        "status", "skipped",
                        "reason", "no_code_changes",
                        "fingerprint", normalizedFingerprint
                );
            }
            clearTables(connection);
            insertClasses(connection, snapshot.classes());
            insertMethods(connection, snapshot.methods());
            insertDefines(connection, snapshot.defines());
            insertInherits(connection, snapshot.inherits());
            insertCalls(connection, snapshot.calls());
            new ClassMetricsInserter().insert(connection, snapshot.metrics());
            if (!normalizedFingerprint.isBlank()) {
                writeFingerprint(connection, normalizedFingerprint);
            }
            return refreshedResult(dbPath, snapshot, normalizedFingerprint, forceRefresh);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to refresh local graph DB", exception);
        }
    }

    public Map<String, Object> query(Path dbPath, String sql, int maxRows) {
        if (!isReadOnly(sql)) {
            return Map.of("error", "Only SELECT queries are allowed");
        }
        String jdbcUrl = "jdbc:h2:file:" + dbPath.toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(jdbcUrl); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            return Map.of("rows", rows(result, maxRows), "dbPath", dbPath.toAbsolutePath().toString());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to query local graph DB", exception);
        }
    }

    private boolean isReadOnly(String sql) {
        return sql != null && sql.trim().toLowerCase().startsWith("select");
    }

    private void ensureParentExists(Path dbPath) {
        Path parent = dbPath.toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create DB directory", exception);
        }
    }

    private void createSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS ClassNode(id VARCHAR PRIMARY KEY, fqn VARCHAR, name VARCHAR, filePath VARCHAR)");
            statement.execute("CREATE TABLE IF NOT EXISTS MethodNode(id VARCHAR PRIMARY KEY, classFqn VARCHAR, name VARCHAR, signature VARCHAR, filePath VARCHAR, annotations VARCHAR, startLine INT, endLine INT)");
            statement.execute("CREATE TABLE IF NOT EXISTS Defines(classId VARCHAR, methodId VARCHAR)");
            statement.execute("CREATE TABLE IF NOT EXISTS Inherits(childId VARCHAR, parentId VARCHAR, relationType VARCHAR)");
            statement.execute("CREATE TABLE IF NOT EXISTS Calls(callerMethodId VARCHAR, calledMethodId VARCHAR)");
            statement.execute("CREATE TABLE IF NOT EXISTS GraphMeta(metaKey VARCHAR PRIMARY KEY, metaValue VARCHAR)");
            statement.execute("CREATE TABLE IF NOT EXISTS ClassMetrics(classFqn VARCHAR PRIMARY KEY, filePath VARCHAR, methodCount INT, inheritanceDepth INT, compositeScore INT)");
        }
    }

    private void clearTables(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM Defines");
            statement.execute("DELETE FROM Inherits");
            statement.execute("DELETE FROM Calls");
            statement.execute("DELETE FROM MethodNode");
            statement.execute("DELETE FROM ClassNode");
            statement.execute("DELETE FROM GraphMeta");
            statement.execute("DELETE FROM ClassMetrics");
        }
    }

    private String readFingerprint(Connection connection) throws Exception {
        try (PreparedStatement prepared = connection.prepareStatement(
                "SELECT metaValue FROM GraphMeta WHERE metaKey = ?"
        )) {
            prepared.setString(1, "codeFingerprint");
            try (ResultSet result = prepared.executeQuery()) {
                if (result.next()) {
                    return result.getString(1);
                }
                return "";
            }
        }
    }

    private void writeFingerprint(Connection connection, String codeFingerprint) throws Exception {
        try (PreparedStatement prepared = connection.prepareStatement(
                "INSERT INTO GraphMeta(metaKey, metaValue) VALUES(?, ?)"
        )) {
            prepared.setString(1, "codeFingerprint");
            prepared.setString(2, codeFingerprint);
            prepared.executeUpdate();
        }
    }

    private Map<String, Object> buildResult(Path dbPath, JavaGraphSnapshot snapshot) {
        return Map.of(
                "dbPath", dbPath.toAbsolutePath().toString(),
                "classes", snapshot.classes().size(),
                "methods", snapshot.methods().size(),
                "defines", snapshot.defines().size(),
                "inherits", snapshot.inherits().size(),
                "calls", snapshot.calls().size()
        );
    }

    private Map<String, Object> refreshedResult(
            Path dbPath,
            JavaGraphSnapshot snapshot,
            String codeFingerprint,
            boolean forceRefresh
    ) {
        Map<String, Object> base = new LinkedHashMap<>(buildResult(dbPath, snapshot));
        base.put("status", "rebuilt");
        base.put("fingerprint", codeFingerprint);
        base.put("forced", forceRefresh);
        return base;
    }

    private boolean isUnchanged(String existingFingerprint, String newFingerprint) {
        return !newFingerprint.isBlank() && newFingerprint.equals(existingFingerprint);
    }

    private String normalizeFingerprint(String codeFingerprint) {
        return codeFingerprint == null ? "" : codeFingerprint.trim();
    }

    private void insertClasses(Connection connection, List<GraphClassNode> nodes) throws Exception {
        try (PreparedStatement prepared = connection.prepareStatement(
                "MERGE INTO ClassNode(id, fqn, name, filePath) KEY(id) VALUES(?, ?, ?, ?)"
        )) {
            for (GraphClassNode node : nodes) {
                prepared.setString(1, node.id());
                prepared.setString(2, node.fqn());
                prepared.setString(3, node.name());
                prepared.setString(4, node.filePath());
                prepared.addBatch();
            }
            prepared.executeBatch();
        }
    }

    private void insertMethods(Connection connection, List<GraphMethodNode> nodes) throws Exception {
        try (PreparedStatement prepared = connection.prepareStatement(
                "MERGE INTO MethodNode(id, classFqn, name, signature, filePath, annotations, startLine, endLine) KEY(id) VALUES(?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            for (GraphMethodNode node : nodes) {
                prepared.setString(1, node.id());
                prepared.setString(2, node.classFqn());
                prepared.setString(3, node.name());
                prepared.setString(4, node.signature());
                prepared.setString(5, node.filePath());
                prepared.setString(6, node.annotations());
                prepared.setInt(7, node.startLine());
                prepared.setInt(8, node.endLine());
                prepared.addBatch();
            }
            prepared.executeBatch();
        }
    }

    private void insertDefines(Connection connection, List<GraphDefineEdge> edges) throws Exception {
        try (PreparedStatement prepared = connection.prepareStatement(
                "INSERT INTO Defines(classId, methodId) VALUES(?, ?)"
        )) {
            for (GraphDefineEdge edge : edges) {
                prepared.setString(1, edge.classId());
                prepared.setString(2, edge.methodId());
                prepared.addBatch();
            }
            prepared.executeBatch();
        }
    }

    private void insertInherits(Connection connection, List<GraphInheritanceEdge> edges) throws Exception {
        try (PreparedStatement prepared = connection.prepareStatement(
                "INSERT INTO Inherits(childId, parentId, relationType) VALUES(?, ?, ?)"
        )) {
            for (GraphInheritanceEdge edge : edges) {
                prepared.setString(1, edge.childId());
                prepared.setString(2, edge.parentId());
                prepared.setString(3, edge.relationType());
                prepared.addBatch();
            }
            prepared.executeBatch();
        }
    }

    private void insertCalls(Connection connection, List<GraphCallEdge> edges) throws Exception {
        try (PreparedStatement prepared = connection.prepareStatement(
                "INSERT INTO Calls(callerMethodId, calledMethodId) VALUES(?, ?)"
        )) {
            for (GraphCallEdge edge : edges) {
                prepared.setString(1, edge.callerMethodId());
                prepared.setString(2, edge.calledMethodId());
                prepared.addBatch();
            }
            prepared.executeBatch();
        }
    }

    private List<Map<String, Object>> rows(ResultSet result, int maxRows) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData meta = result.getMetaData();
        int cols = meta.getColumnCount();
        while (result.next() && rows.size() < maxRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                row.put(meta.getColumnLabel(i), result.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }
}
