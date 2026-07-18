package com.kita.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.operations.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T014 [US1]: schema-per-service isolation — operations-service migrations and Flyway history land in
 * the {@code operations} schema, not the shared {@code public} schema (008-docker-cache-database).
 */
class SchemaIsolationIT extends AbstractIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void coreTablesLiveInOperationsSchemaNotPublic() {
    List<String> schemas =
        jdbcTemplate.queryForList(
            "select table_schema from information_schema.tables where table_name = 'item'",
            String.class);
    assertThat(schemas).containsExactly("operations");
  }

  @Test
  void flywayHistoryLivesInOperationsSchema() {
    Integer inOperations =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables"
                + " where table_schema = 'operations' and table_name = 'flyway_schema_history'",
            Integer.class);
    assertThat(inOperations).isEqualTo(1);
  }
}
