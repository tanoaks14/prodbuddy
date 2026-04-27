package com.prodbuddy.tools.codecontext;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Extracts common read-only analysis queries to keep LocalGraphDbService slim. */
public final class LocalGraphQueries {

    /** Find all MethodNode IDs that define methods in the given class FQN (or name). */
    public List<String> findCallersByClass(final Path dbPath, final String classFqn) {
        String url = "jdbc:h2:file:" + dbPath.toAbsolutePath();
        String sql = "SELECT DISTINCT c.callerMethodId FROM Calls c"
                + " JOIN MethodNode m ON c.calledMethodId = m.id"
                + " WHERE m.classFqn = ? OR m.classFqn LIKE ?";
        try (Connection con = DriverManager.getConnection(url);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, classFqn);
            ps.setString(2, "%" + classFqn + "%");
            return collectSingleColumn(ps.executeQuery());
        } catch (Exception ex) {
            return List.of();
        }
    }

    /** Find all callerMethodIds that call the given methodId directly. */
    public List<String> findCallersByMethodId(final Path dbPath, final String methodId) {
        String url = "jdbc:h2:file:" + dbPath.toAbsolutePath();
        String sql = "SELECT callerMethodId FROM Calls WHERE calledMethodId = ?";
        try (Connection con = DriverManager.getConnection(url);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, methodId);
            return collectSingleColumn(ps.executeQuery());
        } catch (Exception ex) {
            return List.of();
        }
    }

    /** Find all calledMethodIds that the given methodId calls directly. */
    public List<String> findCalleesByMethodId(final Path dbPath, final String methodId) {
        String url = "jdbc:h2:file:" + dbPath.toAbsolutePath();
        String sql = "SELECT calledMethodId FROM Calls WHERE callerMethodId = ?";
        try (Connection con = DriverManager.getConnection(url);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, methodId);
            return collectSingleColumn(ps.executeQuery());
        } catch (Exception ex) {
            return List.of();
        }
    }

    /** Find MethodNode ID by file path and line number. */
    public String findMethodIdByLocation(final Path dbPath, final String filePath, final int line) {
        String url = "jdbc:h2:file:" + dbPath.toAbsolutePath();
        String sql = "SELECT id FROM MethodNode WHERE filePath = ? AND ? BETWEEN startLine AND endLine LIMIT 1";
        try (Connection con = DriverManager.getConnection(url);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, filePath);
            ps.setInt(2, line);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception ex) {
            // Ignore
        }
        return null;
    }

    /** Find detailed MethodNode by its ID. */
    public GraphMethodNode getMethodNode(final Path dbPath, final String methodId) {
        String url = "jdbc:h2:file:" + dbPath.toAbsolutePath();
        String sql = "SELECT * FROM MethodNode WHERE id = ?";
        try (Connection con = DriverManager.getConnection(url);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, methodId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new GraphMethodNode(
                            rs.getString("id"),
                            rs.getString("classFqn"),
                            rs.getString("name"),
                            rs.getString("signature"),
                            rs.getString("filePath"),
                            rs.getString("annotations"),
                            rs.getInt("startLine"),
                            rs.getInt("endLine")
                    );
                }
            }
        } catch (Exception ex) {
            // Log or ignore
        }
        return null;
    }

    /** Returns methods with no inbound call edges. Excludes constructors, main(), and framework entry points. */
    public List<Map<String, Object>> detectDeadCode(final Path dbPath, final int limit) {
        String url = "jdbc:h2:file:" + dbPath.toAbsolutePath();
        String sql = "SELECT m.id, m.name, m.classFqn, m.filePath"
                + " FROM MethodNode m LEFT JOIN Calls c ON m.id = c.calledMethodId"
                + " WHERE c.calledMethodId IS NULL AND m.name NOT IN ('<init>', 'main')"
                + " AND (m.annotations IS NULL OR ("
                + " m.annotations NOT LIKE '%Mapping%' AND"
                + " m.annotations NOT LIKE '%Bean%' AND"
                + " m.annotations NOT LIKE '%EventListener%' AND"
                + " m.annotations NOT LIKE '%Scheduled%' AND"
                + " m.annotations NOT LIKE '%KafkaListener%' AND"
                + " m.annotations NOT LIKE '%SqsListener%' AND"
                + " m.annotations NOT LIKE '%RabbitListener%' AND"
                + " m.annotations NOT LIKE '%PostConstruct%' AND"
                + " m.annotations NOT LIKE '%PreDestroy%' AND"
                + " m.annotations NOT LIKE '%Inject%'"
                + "))"
                + " ORDER BY m.classFqn LIMIT ?";
        try (Connection con = DriverManager.getConnection(url);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            return rows(ps.executeQuery(), limit);
        } catch (Exception ex) {
            return List.of();
        }
    }

    /** Count total MethodNode rows in the database. */
    public int countMethods(final Path dbPath) {
        String url = "jdbc:h2:file:" + dbPath.toAbsolutePath();
        try (Connection con = DriverManager.getConnection(url);
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM MethodNode");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    /** Query ClassMetrics ordered by compositeScore descending. */
    public List<Map<String, Object>> queryComplexityHeatmap(final Path dbPath, final int topN) {
        String url = "jdbc:h2:file:" + dbPath.toAbsolutePath();
        String sql = "SELECT classFqn, filePath, methodCount, inheritanceDepth, compositeScore"
                + " FROM ClassMetrics ORDER BY compositeScore DESC LIMIT ?";
        try (Connection con = DriverManager.getConnection(url);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, topN);
            return rows(ps.executeQuery(), topN);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> collectSingleColumn(final ResultSet rs) throws Exception {
        List<String> result = new ArrayList<>();
        while (rs.next()) {
            result.add(rs.getString(1));
        }
        return result;
    }

    private List<Map<String, Object>> rows(final ResultSet result, final int maxRows)
            throws Exception {
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
