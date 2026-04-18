package com.prodbuddy.tools.codecontext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

/** Handles batch insertion of ClassMetrics into the H2 graph database. */
public final class ClassMetricsInserter {

    private static final String MERGE_SQL =
            "MERGE INTO ClassMetrics(classFqn, filePath, methodCount, inheritanceDepth, compositeScore)"
            + " KEY(classFqn) VALUES(?, ?, ?, ?, ?)";

    /** Inserts all metrics in a single JDBC batch. */
    public void insert(Connection connection, List<ClassMetrics> metrics) throws Exception {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        try (PreparedStatement prepared = connection.prepareStatement(MERGE_SQL)) {
            for (ClassMetrics m : metrics) {
                prepared.setString(1, m.classFqn());
                prepared.setString(2, m.filePath());
                prepared.setInt(3, m.methodCount());
                prepared.setInt(4, m.inheritanceDepth());
                prepared.setInt(5, m.compositeScore());
                prepared.addBatch();
            }
            prepared.executeBatch();
        }
    }
}
