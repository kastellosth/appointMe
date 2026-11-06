package com.kastellosth.appointme;

import com.kastellosth.appointme.support.DatabaseTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MigrationsTest extends DatabaseTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void testsNeverRunAgainstTheDevDatabase() {
        assertEquals(DATABASE_NAME, jdbc.queryForObject("SELECT current_database()", String.class));
    }

    @Test
    void everyMigrationFileWasAppliedSuccessfully() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*.sql");
        List<String> expected = Arrays.stream(files)
                .map(file -> file.getFilename().substring(1, file.getFilename().indexOf("__")))
                .sorted((a, b) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b)))
                .toList();

        List<String> applied = jdbc.queryForList("""
                SELECT version FROM flyway_schema_history
                 WHERE success AND version IS NOT NULL
                 ORDER BY installed_rank
                """, String.class);

        assertEquals(expected, applied);
    }

    @Test
    void theRulesAddedByMigrationsExist() {
        assertEquals(1, count("SELECT count(*) FROM pg_constraint WHERE conname = 'appointments_no_overlap'"));

        List<String> triggers = jdbc.queryForList("SELECT tgname::text FROM pg_trigger WHERE NOT tgisinternal", String.class);
        assertTrue(triggers.containsAll(List.of(
                "trg_invoice_lines_only_while_draft", "trg_invoices_enforce_rules", "trg_payments_check_invoice",
                "trg_payments_append_only", "trg_invoices_payment_rules", "trg_audit_events_append_only")), triggers::toString);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
