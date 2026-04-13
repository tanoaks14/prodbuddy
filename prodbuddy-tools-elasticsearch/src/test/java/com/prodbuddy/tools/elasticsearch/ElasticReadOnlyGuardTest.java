package com.prodbuddy.tools.elasticsearch;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ElasticReadOnlyGuardTest {

    @Test
    void shouldAllowReadEndpoints() {
        ElasticReadOnlyGuard guard = new ElasticReadOnlyGuard();

        Assertions.assertTrue(guard.isAllowed("_search", "POST"));
        Assertions.assertTrue(guard.isAllowed("_count", "POST"));
        Assertions.assertTrue(guard.isAllowed("_mapping", "GET"));
        Assertions.assertTrue(guard.isAllowed("_cat/indices", "GET"));
    }

    @Test
    void shouldRejectWriteEndpointsAndMethods() {
        ElasticReadOnlyGuard guard = new ElasticReadOnlyGuard();

        Assertions.assertFalse(guard.isAllowed("_delete_by_query", "POST"));
        Assertions.assertFalse(guard.isAllowed("_mapping", "POST"));
        Assertions.assertFalse(guard.isAllowed("_cat/indices", "POST"));
    }
}
