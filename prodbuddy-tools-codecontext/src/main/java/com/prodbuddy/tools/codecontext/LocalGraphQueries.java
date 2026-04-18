package com.prodbuddy.tools.codecontext;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Extracts common read-only analysis queries to keep LocalGraphDbService slim. */
public final class LocalGraphQueries {

    /** Find all MethodNode IDs that define methods in the given class FQN (or name). */
    public List<String> findCallersByClass(Path dbPath, String classFqn) {
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
    public List<String> findCallersByMethodId(Path dbPath, String methodId) {
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

    /** Returns methods with no inbound call edges. Excludes constructors and main(). */
    public List<Map<String, Object>> detectDeadCode(Path dbPath, int limit) {
        String url = "jdbc:h2:file:" + dbPath.toAbsolutePath();
        String sql = "SELECT m.id, m.name, m.classFqn, m.filePath"
                + " FROM MethodNode m LEFT JOIN Calls c ON m.id = c.calledMethodId"
                + " WHERE c.calledMethodId IS NULL AND m.name NOT IN ('<init>', 'main')"
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
    public int countMethods(Path dbPath) {
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
    public List<Map<String, Object>> queryComplexityHeatmap(Path dbPath, int topN) {
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

    private List<String> collectSingleColumn(ResultSet rs) throws Exception {
        List<String> result = new ArrayList<>();
        while (rs.next()) {
            result.add(rs.getString(1));
        }
        return result;
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
