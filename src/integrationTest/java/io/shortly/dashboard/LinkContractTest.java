package io.shortly.dashboard;

import static io.shortly.dashboard.jooq.generated.Tables.LINKS;
import static io.shortly.dashboard.jooq.generated.Tables.LINK_SNAPSHOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.shortly.dashboard.model.LinkStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LinkContractTest extends AbstractIntegrationTest {

    @Autowired DatabaseRoles roles;

    @BeforeEach
    void emptyTables() {
        roles.resetLinkTables();
    }

    @Test
    void migratedSchemaMatchesContractColumns() throws Exception {
        String contract = Files.readString(Path.of("contract/link-contract.sql"));
        roles.owner().execute("DROP SCHEMA IF EXISTS contract_check CASCADE");
        roles.owner().execute("CREATE SCHEMA contract_check");
        roles.owner()
                .connection(
                        connection -> {
                            try (var statement = connection.createStatement()) {
                                statement.execute("SET search_path TO contract_check");
                                statement.execute(contract);
                                statement.execute("SET search_path TO public");
                            }
                        });
        assertThat(columns("public", "link_snapshot"))
                .isEqualTo(columns("contract_check", "link_snapshot"));
        roles.owner().execute("DROP SCHEMA contract_check CASCADE");
    }

    @Test
    void linkReaderSeesTheViewAndNothingElse() {
        roles.insertLink("aaaaaaaaa", UUID.randomUUID(), LinkStatus.ACTIVE, 1);

        roles.asLinkReader(
                reader -> {
                    assertThat(reader.fetchCount(LINK_SNAPSHOT)).isEqualTo(1);
                    assertThatThrownBy(() -> reader.fetchCount(LINKS))
                            .hasMessageContaining("permission denied");
                });
    }

    private List<String> columns(String schema, String relation) {
        return roles.owner()
                .fetch(
                        "SELECT column_name || ':' || data_type || ':' || coalesce(character_maximum_length::text, '') AS col "
                                + "FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position",
                        schema,
                        relation)
                .map((Record r) -> r.get("col", String.class));
    }
}
