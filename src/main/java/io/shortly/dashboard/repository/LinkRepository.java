package io.shortly.dashboard.repository;

import static io.shortly.dashboard.jooq.generated.Tables.LINKS;
import static org.jooq.impl.DSL.currentInstant;

import io.shortly.dashboard.jooq.generated.Sequences;
import io.shortly.dashboard.jooq.generated.tables.records.LinksRecord;
import io.shortly.dashboard.model.Link;
import io.shortly.dashboard.model.LinkStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class LinkRepository {

    private final DSLContext dsl;

    public LinkRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Link insert(UUID owner, String code, String targetUrl) {
        return toLink(
                dsl.insertInto(LINKS)
                        .set(LINKS.CODE, code)
                        .set(LINKS.OWNER_ID, owner)
                        .set(LINKS.TARGET_URL, targetUrl)
                        .set(LINKS.STATUS, LinkStatus.ACTIVE)
                        .set(LINKS.VERSION, 1)
                        .returning()
                        .fetchSingle());
    }

    /**
     * Sets the status of the owner's link unless it is deleted; empty when there is no such link.
     */
    public Optional<Link> transition(UUID owner, String code, LinkStatus to) {
        return dsl.update(LINKS)
                .set(LINKS.STATUS, to)
                .set(LINKS.VERSION, LINKS.VERSION.plus(1))
                .set(LINKS.UPDATED_AT, currentInstant())
                .set(LINKS.CHANGE_SEQ, Sequences.LINK_CHANGE_SEQ.nextval())
                .where(
                        LINKS.CODE.eq(code),
                        LINKS.OWNER_ID.eq(owner),
                        LINKS.STATUS.ne(LinkStatus.DELETED))
                .returning()
                .fetchOptional()
                .map(LinkRepository::toLink);
    }

    /** Whether the link exists and is the owner's, in any status. */
    public boolean isOwned(UUID owner, String code) {
        return dsl.fetchExists(LINKS, LINKS.CODE.eq(code), LINKS.OWNER_ID.eq(owner));
    }

    public Optional<Link> findOwned(UUID owner, String code) {
        return dsl.selectFrom(LINKS)
                .where(LINKS.CODE.eq(code), LINKS.OWNER_ID.eq(owner))
                .fetchOptional()
                .map(LinkRepository::toLink);
    }

    /** One page of the owner's links, newest first. */
    public List<Link> listOwned(UUID owner, @Nullable LinkStatus status, int page, int size) {
        return dsl.selectFrom(LINKS)
                .where(listed(owner, status))
                .orderBy(LINKS.CREATED_AT.desc(), LINKS.CODE.desc())
                .limit(size)
                .offset((long) page * size)
                .fetch(LinkRepository::toLink);
    }

    public long countOwned(UUID owner, @Nullable LinkStatus status) {
        return dsl.fetchCount(LINKS, listed(owner, status));
    }

    /** The owner's links in that status, or all but the deleted ones when no status is given. */
    private static Condition listed(UUID owner, @Nullable LinkStatus status) {
        if (status == null) {
            return LINKS.OWNER_ID.eq(owner).and(LINKS.STATUS.ne(LinkStatus.DELETED));
        }
        return LINKS.OWNER_ID.eq(owner).and(LINKS.STATUS.eq(status));
    }

    private static Link toLink(LinksRecord r) {
        return new Link(
                r.getCode(),
                r.getOwnerId(),
                r.getTargetUrl(),
                r.getStatus(),
                r.getVersion(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
