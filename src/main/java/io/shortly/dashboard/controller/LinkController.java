package io.shortly.dashboard.controller;

import io.shortly.dashboard.dto.CreateLinkRequest;
import io.shortly.dashboard.dto.LinkListRequest;
import io.shortly.dashboard.dto.LinkPageResponse;
import io.shortly.dashboard.dto.LinkResponse;
import io.shortly.dashboard.dto.StatusRequest;
import io.shortly.dashboard.model.CurrentUser;
import io.shortly.dashboard.service.LinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/links")
@Tag(name = "Links", description = "Create and manage your short links")
class LinkController {

    private final LinkService links;

    LinkController(LinkService links) {
        this.links = links;
    }

    @Operation(summary = "Create a short link for a target URL")
    @ApiResponse(
            responseCode = "201",
            description = "Created; Location points at the new link.",
            content = @Content(schema = @Schema(implementation = LinkResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description =
                    "invalid-target-url: the target is not an allowed http(s) URL; or a malformed"
                            + " request.")
    @PostMapping
    ResponseEntity<LinkResponse> create(
            @AuthenticationPrincipal CurrentUser user, @RequestBody CreateLinkRequest request) {
        LinkResponse created = links.create(user.id(), request.targetUrl());
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{code}")
                        .buildAndExpand(created.code())
                        .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "List your links, newest first")
    @GetMapping
    LinkPageResponse list(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @ParameterObject LinkListRequest request) {
        return links.list(user.id(), request);
    }

    @Operation(summary = "Get one of your links")
    @GetMapping("/{code}")
    LinkResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable String code) {
        return links.get(user.id(), code);
    }

    @Operation(summary = "Activate or deactivate a link")
    @PutMapping("/{code}/status")
    LinkResponse setStatus(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable String code,
            @Valid @RequestBody StatusRequest request) {
        return links.changeStatus(user.id(), code, request.toLinkStatus());
    }

    @Operation(summary = "Delete a link")
    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable String code) {
        links.delete(user.id(), code);
    }
}
