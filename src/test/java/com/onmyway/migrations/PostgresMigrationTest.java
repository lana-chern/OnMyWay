package com.onmyway.migrations;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MIGRATION_TEST_JDBC_URL", matches = ".+")
class PostgresMigrationTest {
    private static final String LEGACY_EMAIL = "legacy-places@onmyway.invalid";

    @Test
    void migratesFreshDatabase() throws SQLException, IOException {
        withIsolatedSchema("fresh", schema -> {
            Flyway flyway = flyway(schema);
            var result = flyway.migrate();

            assertEquals(3, result.migrationsExecuted);
            assertColumnExists(schema, "places", "owner_id");
            assertEquals(3L, queryLong(schema,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version IN ('1', '2', '3')"));
            assertEquals(0L, queryLong(schema, "SELECT COUNT(*) FROM users WHERE email = '" + LEGACY_EMAIL + "'"));
            assertTrue(hasConstraint(schema, "places", "fk_places_owner"));
            assertTrue(hasIndex(schema, "idx_places_owner_id"));
            assertColumnIsNotNull(schema, "places", "owner_id");
        });
    }

    @Test
    void baselinesExistingSchemaAndAssignsLegacyPlacesToBlockedPlaceholder() throws SQLException, IOException {
        withIsolatedSchema("existing", schema -> {
            createLegacySchema(schema);
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.execute("SET search_path TO \"" + schema + "\"");
                statement.execute("""
                        INSERT INTO cities (name, country_code, latitude, longitude, timezone)
                        VALUES ('Legacy city', 'NL', 52.370000, 4.895000, 'Europe/Amsterdam')
                        """);
                statement.execute("""
                        INSERT INTO places (city_id, name, description, latitude, longitude, status, created_at, updated_at)
                        SELECT id, 'Legacy place', 'Existing record', 52.370000, 4.895000, 'PUBLISHED',
                               CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        FROM cities WHERE name = 'Legacy city'
                        """);
            }

            var result = flyway(schema).migrate();

            assertEquals(2, result.migrationsExecuted, "V2 and V3 should run after baseline V1");
            assertColumnExists(schema, "places", "owner_id");
            assertColumnIsNotNull(schema, "places", "owner_id");
            assertTrue(hasConstraint(schema, "places", "fk_places_owner"));
            assertTrue(hasIndex(schema, "places", "idx_places_owner_id"));
            assertEquals(1L, queryLong(schema,
                    "SELECT COUNT(*) FROM users WHERE email = '" + LEGACY_EMAIL + "' AND status = 'BLOCKED'"));
            assertEquals(1L, queryLong(schema, """
                    SELECT COUNT(*) FROM places p
                    JOIN users u ON u.id = p.owner_id
                    WHERE p.name = 'Legacy place' AND u.email = 'legacy-places@onmyway.invalid'
                    """));
            assertEquals(1L, queryLong(schema,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version = '1' AND type = 'BASELINE'"));
            assertEquals(1L, queryLong(schema,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version = '2'"));
            assertEquals(1L, queryLong(schema,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version = '3'"));
        });
    }


    @Test
    void failsWhenReservedPlaceholderEmailIsAlreadyInUse() throws SQLException, IOException {
        withIsolatedSchema("collision", schema -> {
            createLegacySchema(schema);
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.execute("SET search_path TO \"" + schema + "\"");
                statement.execute("""
                        INSERT INTO users (email, password_hash, display_name, status, created_at, updated_at)
                        VALUES ('legacy-places@onmyway.invalid', 'existing-password-hash',
                                'Existing account', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """);
                statement.execute("""
                        INSERT INTO cities (name, country_code, latitude, longitude, timezone)
                        VALUES ('Collision city', 'NL', 52.370000, 4.895000, 'Europe/Amsterdam')
                        """);
                statement.execute("""
                        INSERT INTO places (city_id, name, description, latitude, longitude, status, created_at, updated_at)
                        SELECT id, 'Collision place', 'Must not be reassigned', 52.370000, 4.895000, 'PUBLISHED',
                               CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        FROM cities WHERE name = 'Collision city'
                        """);
            }

            assertThrows(FlywayException.class, () -> flyway(schema).migrate());
            assertEquals(0L, queryLong(schema, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = '%s' AND table_name = 'places' AND column_name = 'owner_id'
                    """.formatted(schema)), "Failed migration must roll back the ownership column");
            assertEquals(1L, queryLong(schema, """
                    SELECT COUNT(*) FROM users
                    WHERE email = 'legacy-places@onmyway.invalid' AND status = 'ACTIVE'
                    """));
        });
    }

    private Flyway flyway(String schema) {
        return Flyway.configure()
                .dataSource(requiredEnv("MIGRATION_TEST_JDBC_URL"),
                        requiredEnv("MIGRATION_TEST_DB_USERNAME"), requiredEnv("MIGRATION_TEST_DB_PASSWORD"))
                .defaultSchema(schema)
                .schemas(schema)
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .locations("classpath:db/migration")
                .load();
    }

    private void createLegacySchema(String schema) throws SQLException, IOException {
        String baseline;
        try (var input = getClass().getClassLoader().getResourceAsStream("db/migration/V1__baseline_schema.sql")) {
            assertNotNull(input, "V1 baseline migration must be available on the test classpath");
            baseline = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO \"" + schema + "\"");
            for (String sql : baseline.split(";")) {
                String command = sql.strip();
                if (!command.isEmpty()) {
                    statement.execute(command);
                }
            }
        }
    }

    private void withIsolatedSchema(String prefix, SqlRunnable runnable) throws SQLException, IOException {
        String schema = "migration_" + prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA \"" + schema + "\"");
        }
        try {
            runnable.run(schema);
        } finally {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
            }
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                requiredEnv("MIGRATION_TEST_JDBC_URL"),
                requiredEnv("MIGRATION_TEST_DB_USERNAME"),
                requiredEnv("MIGRATION_TEST_DB_PASSWORD"));
    }

    private long queryLong(String schema, String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO \"" + schema + "\"");
            try (ResultSet result = statement.executeQuery(sql)) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private void assertColumnExists(String schema, String table, String column) throws SQLException {
        assertEquals(1L, queryLong(schema, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = '%s' AND table_name = '%s' AND column_name = '%s'
                """.formatted(schema, table, column)));
    }

    private void assertColumnIsNotNull(String schema, String table, String column) throws SQLException {
        assertEquals(1L, queryLong(schema, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = '%s' AND table_name = '%s' AND column_name = '%s' AND is_nullable = 'NO'
                """.formatted(schema, table, column)));
    }

    private boolean hasConstraint(String schema, String table, String constraint) throws SQLException {
        return queryLong(schema, """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = '%s' AND table_name = '%s' AND constraint_name = '%s'
                """.formatted(schema, table, constraint)) == 1L;
    }

    private boolean hasIndex(String schema, String index) throws SQLException {
        return queryLong(schema, """
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = '%s' AND indexname = '%s'
                """.formatted(schema, index)) == 1L;
    }

    private String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable is missing: " + name);
        }
        return value;
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run(String schema) throws SQLException, IOException;
    }
}
