package io.shortly.dashboard;

import static io.shortly.dashboard.TestTokens.userToken;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class LinkApiTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired DatabaseRoles roles;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void clean() {
        roles.resetLinkTables();
    }

    @Test
    void aLinkIsCreatedReadDeactivatedAndDeleted() throws Exception {
        ResultActions created =
                create(alice, "https://Example.com/x").andExpect(status().isCreated());
        String code = code(created);
        created.andExpect(header().string("Location", "http://localhost/api/v1/links/" + code))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/" + code))
                .andExpect(jsonPath("$.targetUrl").value("https://example.com/x"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(get("/api/v1/links/" + code).with(userToken(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
        mvc.perform(
                        put("/api/v1/links/" + code + "/status")
                                .with(userToken(alice))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.version").value(2));
        mvc.perform(delete("/api/v1/links/" + code).with(userToken(alice)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/links").with(userToken(alice)))
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/links/" + code).with(userToken(alice)))
                .andExpect(jsonPath("$.status").value("DELETED"));
    }

    @Test
    void theListPagesNewestFirst() throws Exception {
        String first = code(create(alice, "https://example.com/1"));
        String second = code(create(alice, "https://example.com/2"));
        String third = code(create(alice, "https://example.com/3"));

        mvc.perform(get("/api/v1/links").param("size", "2").with(userToken(alice)))
                .andExpect(jsonPath("$.items[0].code").value(third))
                .andExpect(jsonPath("$.items[1].code").value(second))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(
                        get("/api/v1/links")
                                .param("size", "2")
                                .param("page", "1")
                                .with(userToken(alice)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value(first));
    }

    @Test
    void someoneElsesLinkIsNotFound() throws Exception {
        String code = code(create(alice, "https://example.com/x"));

        mvc.perform(get("/api/v1/links/" + code).with(userToken(bob)))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath(
                                "$.type",
                                startsWith("https://shortly.example/problems/link-not-found")));
        mvc.perform(delete("/api/v1/links/" + code).with(userToken(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    void badRequestsAreRefused() throws Exception {
        create(alice, "http://127.0.0.1/admin")
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath(
                                "$.type",
                                startsWith("https://shortly.example/problems/invalid-target-url")));
        String code = code(create(alice, "https://example.com/x"));
        mvc.perform(
                        put("/api/v1/links/" + code + "/status")
                                .with(userToken(alice))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"DELETED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/links")).andExpect(status().isUnauthorized());
    }

    private ResultActions create(UUID owner, String targetUrl) throws Exception {
        return mvc.perform(
                post("/api/v1/links")
                        .with(userToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUrl\":\"" + targetUrl + "\"}"));
    }

    private static String code(ResultActions created) throws Exception {
        return JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.code");
    }
}
