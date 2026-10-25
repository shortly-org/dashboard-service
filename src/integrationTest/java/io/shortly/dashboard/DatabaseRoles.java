package io.shortly.dashboard;

import static io.shortly.dashboard.jooq.generated.Tables.CLICK_DAILY;
import static io.shortly.dashboard.jooq.generated.Tables.CLICK_DAILY_COUNTRY;
import static io.shortly.dashboard.jooq.generated.Tables.CLICK_DAILY_REFERRER;
import static io.shortly.dashboard.jooq.generated.Tables.LINKS;

import io.shortly.dashboard.model.LinkStatus;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Test access to the database as the schema owner and as {@code link_reader}. */
public class DatabaseRoles {

    public static final String OWNER = "dashboard_owner";
    public static final String APP = "dashboard_app";

    private final DSLContext owner;
    private final DataSource admin;

    DatabaseRoles(PostgreSQLContainer postgres) {
        owner = DSL.using(dataSource(postgres, OWNER, OWNER), SQLDialect.POSTGRES);
        admin = dataSource(postgres, postgres.getUsername(), postgres.getPassword());
    }

    public DSLContext owner() {
        return owner;
    }

    public void resetLinkTables() {
        owner.truncate(LINKS).execute();
    }

    public void insertLink(String code, UUID owner, LinkStatus status, int version) {
        this.owner
                .insertInto(LINKS)
                .set(LINKS.CODE, code)
                .set(LINKS.OWNER_ID, owner)
                .set(LINKS.TARGET_URL, "https://example.com/" + code)
                .set(LINKS.STATUS, status)
                .set(LINKS.VERSION, version)
                .execute();
    }

    public void resetStatsTables() {
        owner.truncate(CLICK_DAILY, CLICK_DAILY_COUNTRY, CLICK_DAILY_REFERRER).execute();
    }

    /** Runs {@code work} with the privileges of {@code link_reader}. */
    public void asLinkReader(Consumer<DSLContext> work) {
        DSL.using(admin, SQLDialect.POSTGRES)
                .transaction(
                        cfg -> {
                            cfg.dsl().execute("SET LOCAL ROLE link_reader");
                            work.accept(cfg.dsl());
                        });
    }

    private static DataSource dataSource(PostgreSQLContainer postgres, String user, String pass) {
        return new DriverManagerDataSource(postgres.getJdbcUrl(), user, pass);
    }
}
