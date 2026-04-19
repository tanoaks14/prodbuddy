package com.prodbuddy.tools.codecontext;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class LocalGraphDbServiceTest {

    @Test
    void shouldBuildAndQueryGraphSnapshot() throws IOException {
        Path temp = Files.createTempDirectory("graphdb-build");
        Path dbPath = temp.resolve("graph");
        LocalGraphDbService service = new LocalGraphDbService();

        Map<String, Object> result = service.build(dbPath, snapshot("A"));

        Assertions.assertEquals(1, result.get("classes"));
        Map<String, Object> query = service.query(dbPath, "SELECT COUNT(*) AS cnt FROM ClassNode", 10);
        List<?> rows = (List<?>) query.get("rows");
        Assertions.assertEquals(1, rows.size());
        Assertions.assertEquals(1L, ((Map<?, ?>) rows.get(0)).get("CNT"));
    }

    @Test
    void shouldRejectNonSelectQuery() throws IOException {
        Path temp = Files.createTempDirectory("graphdb-nonselect");
        Path dbPath = temp.resolve("graph");
        LocalGraphDbService service = new LocalGraphDbService();
        service.build(dbPath, snapshot("A"));

        Map<String, Object> result = service.query(dbPath, "DELETE FROM ClassNode", 10);

        Assertions.assertEquals("Only SELECT queries are allowed", result.get("error"));
    }

    @Test
    void shouldLimitRowsBasedOnMaxRows() throws IOException {
        Path temp = Files.createTempDirectory("graphdb-limit");
        Path dbPath = temp.resolve("graph");
        LocalGraphDbService service = new LocalGraphDbService();
        service.build(dbPath, snapshot("A", "B", "C"));

        Map<String, Object> result = service.query(
                dbPath,
                "SELECT name FROM ClassNode ORDER BY name",
                2
        );

        List<?> rows = (List<?>) result.get("rows");
        Assertions.assertEquals(2, rows.size());
    }

    @Test
    void shouldSkipRefreshWhenFingerprintUnchanged() throws IOException {
        Path temp = Files.createTempDirectory("graphdb-skip");
        Path dbPath = temp.resolve("graph");
        LocalGraphDbService service = new LocalGraphDbService();

        Map<String, Object> first = service.refresh(dbPath, snapshot("A"), "fp1", false);
        Map<String, Object> second = service.refresh(dbPath, snapshot("B"), "fp1", false);

        Assertions.assertEquals("rebuilt", first.get("status"));
        Assertions.assertEquals("skipped", second.get("status"));
        Map<String, Object> count = service.query(dbPath, "SELECT COUNT(*) AS cnt FROM ClassNode", 5);
        List<?> rows = (List<?>) count.get("rows");
        Assertions.assertEquals(1L, ((Map<?, ?>) rows.get(0)).get("CNT"));
    }

    @Test
    void shouldForceRefreshEvenWhenFingerprintUnchanged() throws IOException {
        Path temp = Files.createTempDirectory("graphdb-force");
        Path dbPath = temp.resolve("graph");
        LocalGraphDbService service = new LocalGraphDbService();
        service.refresh(dbPath, snapshot("A"), "fp2", false);

        Map<String, Object> refreshed = service.refresh(dbPath, snapshot("B", "C"), "fp2", true);

        Assertions.assertEquals("rebuilt", refreshed.get("status"));
        Assertions.assertEquals(true, refreshed.get("forced"));
        Map<String, Object> count = service.query(dbPath, "SELECT COUNT(*) AS cnt FROM ClassNode", 5);
        List<?> rows = (List<?>) count.get("rows");
        Assertions.assertEquals(2L, ((Map<?, ?>) rows.get(0)).get("CNT"));
    }

    @Test
    void shouldRebuildWhenFingerprintChanges() throws IOException {
        Path temp = Files.createTempDirectory("graphdb-changed");
        Path dbPath = temp.resolve("graph");
        LocalGraphDbService service = new LocalGraphDbService();
        service.refresh(dbPath, snapshot("A"), "fp-old", false);

        Map<String, Object> refreshed = service.refresh(dbPath, snapshot("A", "B"), "fp-new", false);

        Assertions.assertEquals("rebuilt", refreshed.get("status"));
        Assertions.assertEquals("fp-new", refreshed.get("fingerprint"));
    }

    @Test
    void shouldThrowForInvalidSelectStatement() throws IOException {
        Path temp = Files.createTempDirectory("graphdb-invalid");
        Path dbPath = temp.resolve("graph");
        LocalGraphDbService service = new LocalGraphDbService();
        service.build(dbPath, snapshot("A"));

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.query(dbPath, "SELECT * FROM MissingTable", 10)
        );
    }

    private JavaGraphSnapshot snapshot(String... names) {
        List<GraphClassNode> classes = java.util.Arrays.stream(names)
                .map(name -> new GraphClassNode("c-" + name, "sample." + name, name, name + ".java"))
                .toList();
        List<GraphMethodNode> methods = java.util.Arrays.stream(names)
                .map(name -> new GraphMethodNode("m-" + name, "sample." + name, "run", "run()", name + ".java", "", 0, 0))
                .toList();
        List<GraphDefineEdge> defines = java.util.Arrays.stream(names)
                .map(name -> new GraphDefineEdge("c-" + name, "m-" + name))
                .toList();
        return new JavaGraphSnapshot(classes, methods, defines, List.of(), List.of(), List.of());
    }
}
