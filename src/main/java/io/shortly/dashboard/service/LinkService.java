package io.shortly.dashboard.service;

import io.shortly.dashboard.dto.LinkListRequest;
import io.shortly.dashboard.dto.LinkPageResponse;
import io.shortly.dashboard.dto.LinkResponse;
import io.shortly.dashboard.exception.LinkNotFoundException;
import io.shortly.dashboard.model.Link;
import io.shortly.dashboard.model.LinkStatus;
import io.shortly.dashboard.repository.LinkRepository;
import io.shortly.dashboard.util.CodeUtils;
import io.shortly.dashboard.util.UrlUtils;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LinkService {

    private final LinkRepository linkRepository;
    private final String shortBaseUrl;

    public LinkService(
            LinkRepository linkRepository,
            @Value("${shortly.short-base-url}") String shortBaseUrl) {
        this.linkRepository = linkRepository;
        this.shortBaseUrl = shortBaseUrl;
    }

    public LinkResponse create(UUID owner, String rawUrl) {
        String targetUrl = UrlUtils.normaliseTarget(rawUrl, shortBaseUrl);
        return LinkResponse.from(
                linkRepository.insert(owner, CodeUtils.randomCode(), targetUrl), shortBaseUrl);
    }

    public LinkResponse changeStatus(UUID owner, String code, LinkStatus to) {
        Link link =
                linkRepository
                        .findOwned(owner, code)
                        .filter(found -> found.status() != LinkStatus.DELETED)
                        .orElseThrow(() -> new LinkNotFoundException(code));
        if (link.status() == to) {
            return LinkResponse.from(link, shortBaseUrl);
        }
        Link changed =
                linkRepository
                        .transition(owner, code, to)
                        .orElseThrow(() -> new LinkNotFoundException(code));
        return LinkResponse.from(changed, shortBaseUrl);
    }

    public void delete(UUID owner, String code) {
        linkRepository
                .transition(owner, code, LinkStatus.DELETED)
                .orElseThrow(() -> new LinkNotFoundException(code));
    }

    /** The owner's link in any status, deleted included. */
    public LinkResponse get(UUID owner, String code) {
        Link link =
                linkRepository
                        .findOwned(owner, code)
                        .orElseThrow(() -> new LinkNotFoundException(code));
        return LinkResponse.from(link, shortBaseUrl);
    }

    public LinkPageResponse list(UUID owner, LinkListRequest request) {
        List<LinkResponse> items =
                linkRepository
                        .listOwned(owner, request.status(), request.page(), request.size())
                        .stream()
                        .map(link -> LinkResponse.from(link, shortBaseUrl))
                        .toList();
        long totalItems = linkRepository.countOwned(owner, request.status());
        return new LinkPageResponse(
                items,
                request.page(),
                request.size(),
                totalItems,
                Math.ceilDiv(totalItems, request.size()));
    }
}
