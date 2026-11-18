package br.com.fiap.reloop.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The same HTTP scenarios run against bootstrap memory and an isolated PostgreSQL container. */
public final class MobileContractScenarios {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> PROFILE_ROUTES = List.of("/api/v1/user", "/api/v1/users/me");
    private final MockMvc mvc;

    public MobileContractScenarios(MockMvc mvc) { this.mvc = mvc; }

    private record Account(long id, String bearer) { }

    private Account register(String name, String email) throws Exception {
        var registered = body(mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(JSON.writeValueAsBytes(Map.of("name", name, "email", email, "password", "Mobile123!"))))
                .andExpect(status().isCreated()).andReturn());
        var login = body(mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(JSON.writeValueAsBytes(Map.of("email", email, "password", "Mobile123!"))))
                .andExpect(status().isOk()).andReturn());
        return new Account(registered.path("id").asLong(), "Bearer " + login.path("accessToken").asText());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode profile(String path, Account account) throws Exception {
        var result = body(mvc.perform(get(path).header("Authorization", account.bearer()))
                .andExpect(status().isOk()).andReturn());
        assertThat(result.path("id").asLong()).isEqualTo(account.id());
        assertFields(result, "id", "name", "email");
        return result;
    }

    private void assertFields(JsonNode node, String... expected) {
        var fields = new HashSet<String>();
        node.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(expected);
    }

    public void profileAliasesReadAndUpdateTheSameAccount() throws Exception {
        var account = register("Ana Mobile", "ana.mobile@example.test");
        JsonNode initial = profile("/api/v1/user", account);
        assertThat(initial.path("name").asText()).isEqualTo("Ana Mobile");
        assertThat(initial.path("email").asText()).isEqualTo("ana.mobile@example.test");
        assertThat(profile("/api/v1/users/me", account)).isEqualTo(initial);
        // Update through each alias, then read through both aliases using the original JWT.
        int revision = 0;
        for (String path : PROFILE_ROUTES) {
            revision++;
            String name = "Ana Atualizada " + revision;
            String email = "ana.updated" + revision + "@example.test";
            var updated = body(mvc.perform(put(path).header("Authorization", account.bearer()).contentType("application/json")
                    .content(JSON.writeValueAsBytes(Map.of("name", name, "email", email.toUpperCase(java.util.Locale.ROOT)))))
                    .andExpect(status().isOk()).andReturn());
            assertThat(updated.path("id").asLong()).isEqualTo(account.id());
            assertThat(updated.path("name").asText()).isEqualTo(name);
            assertThat(updated.path("email").asText()).isEqualTo(email);
            for (String readPath : PROFILE_ROUTES) assertThat(profile(readPath, account)).isEqualTo(updated);
        }
    }

    public void bothProfileAliasesRequireBearerJwt() throws Exception {
        for (String path : PROFILE_ROUTES) {
            for (boolean invalid : List.of(false, true)) {
                for (var request : List.of(get(path), put(path).contentType("application/json").content("{}"))) {
                    if (invalid) request.header("Authorization", "Bearer invalid-token");
                    mvc.perform(request).andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.status").value(401))
                            .andExpect(jsonPath("$.code").value(invalid ? "INVALID_TOKEN" : "AUTHENTICATION_REQUIRED"))
                            .andExpect(jsonPath("$.message").value(invalid ? "Token de acesso inválido ou expirado." : "Autenticação necessária."))
                            .andExpect(header().string("WWW-Authenticate", invalid ? "Bearer error=\"invalid_token\"" : "Bearer"));
                }
            }
        }
        mvc.perform(get("/api/v1/user").header("X-Device-Key", "not-a-citizen-token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    public void profileIdentityAndValidationStayIsolated() throws Exception {
        var ana = register("Ana Mobile", "ana.mobile@example.test");
        var bruno = register("Bruno Mobile", "bruno.mobile@example.test");
        JsonNode brunoBefore = profile("/api/v1/user", bruno);
        for (String path : PROFILE_ROUTES) {
            mvc.perform(get(path).header("Authorization", ana.bearer()).param("userId", Long.toString(bruno.id())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(ana.id()))
                    .andExpect(jsonPath("$.email").value("ana.mobile@example.test"));
            expectError(put(path).header("Authorization", ana.bearer()).contentType("application/json")
                    .content(JSON.writeValueAsBytes(Map.of("name", "Tentativa", "email", "other@example.test", "id", bruno.id()))),
                    400, "BAD_REQUEST");
            expectError(put(path).header("Authorization", ana.bearer()).contentType("application/json").content("{}"),
                    400, "BAD_REQUEST");
            expectError(put(path).header("Authorization", ana.bearer()).contentType("text/plain").content("{}"),
                    415, "UNSUPPORTED_MEDIA_TYPE");
            expectError(put(path).header("Authorization", ana.bearer()).contentType("application/json")
                    .content(JSON.writeValueAsBytes(Map.of("name", "Ana", "email", "BRUNO.MOBILE@EXAMPLE.TEST"))),
                    409, "EMAIL_ALREADY_IN_USE");
        }
        // A valid update through the new route affects Ana only, never the account named in a query parameter.
        mvc.perform(put("/api/v1/user").header("Authorization", ana.bearer()).param("userId", Long.toString(bruno.id()))
                .contentType("application/json").content(JSON.writeValueAsBytes(Map.of("name", "Ana Nova", "email", "ana.new@example.test"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(ana.id()));
        assertThat(profile("/api/v1/user", bruno)).isEqualTo(brunoBefore);
        assertThat(profile("/api/v1/users/me", bruno)).isEqualTo(brunoBefore);
        assertThat(profile("/api/v1/users/me", ana).path("name").asText()).isEqualTo("Ana Nova");
    }

    private void expectError(MockHttpServletRequestBuilder request, int status, String code) throws Exception {
        mvc.perform(request).andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    public void everyPointHasTheSameStableNumericDistanceInListAndDetail() throws Exception {
        JsonNode first = body(mvc.perform(get("/api/v1/collection-points")).andExpect(status().isOk()).andReturn());
        JsonNode repeated = body(mvc.perform(get("/api/v1/collection-points")).andExpect(status().isOk()).andReturn());
        assertThat(first.isArray() && !first.isEmpty()).isTrue();
        assertThat(repeated).isEqualTo(first);
        for (JsonNode point : first) {
            assertFields(point, "id", "name", "address", "latitude", "longitude", "isActive", "distanceFromUserLocation");
            JsonNode distance = point.path("distanceFromUserLocation");
            assertThat(distance.isNumber()).isTrue();
            assertThat(distance.doubleValue()).isGreaterThanOrEqualTo(0);
            String path = "/api/v1/collection-points/" + point.path("id").asLong();
            JsonNode detail = body(mvc.perform(get(path)).andExpect(status().isOk()).andReturn());
            assertFields(detail, "id", "name", "address", "latitude", "longitude", "isActive", "acceptedCategoryIds", "devices", "distanceFromUserLocation");
            for (var field : point.properties()) assertThat(detail.get(field.getKey())).isEqualTo(field.getValue());
            assertThat(body(mvc.perform(get(path)).andExpect(status().isOk()).andReturn())).isEqualTo(detail);
        }
    }
}
