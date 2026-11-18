package br.com.fiap.reloop.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import br.com.fiap.reloop.support.TestDocuments;
import br.com.fiap.reloop.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.test.web.servlet.MockMvc;

/** Shared CPF HTTP checks for both persistence profiles. Values are generated test data. */
public final class CpfRegistrationScenarios {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final MockMvc mvc;
    private final UserRepository users;
    public CpfRegistrationScenarios(MockMvc mvc, UserRepository users) { this.mvc = mvc; this.users = users; }

    public static Map<String, Object> request(Object document) {
        var request = new HashMap<String, Object>();
        request.put("name", "CPF Teste"); request.put("email", UUID.randomUUID() + "@example.test");
        request.put("password", "CpfTest123!"); request.put("document", document);
        return request;
    }

    public void invalidDocument(String scenario) throws Exception {
        String valid = TestDocuments.next();
        Object invalid = switch (scenario) {
            case "missing", "null" -> null;
            case "empty" -> "";
            case "short" -> valid.substring(1);
            case "long" -> valid + "0";
            case "letters" -> "a" + valid.substring(1);
            case "repeated" -> "11111111111";
            case "checksum" -> valid.substring(0, 10) + ((valid.charAt(10) - '0' + 1) % 10);
            case "punctuation" -> TestDocuments.formatted(valid).replace('-', '.');
            case "spaces" -> " " + valid + " ";
            case "number" -> Long.parseLong(valid);
            case "boolean" -> false;
            default -> throw new IllegalArgumentException(scenario);
        };
        var payload = request(invalid);
        if (scenario.equals("missing")) payload.remove("document");
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(JSON.writeValueAsBytes(payload)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Bad Request"));
        assertThat(users.findByEmail(payload.get("email").toString())).isEmpty();
        if (scenario.equals("repeated")) {
            for (int digit = 0; digit <= 9; digit++) {
                payload.put("document", Integer.toString(digit).repeat(11));
                mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(JSON.writeValueAsBytes(payload)))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
            }
        }
    }

    public void normalizedPrivateAndDistinct(CapturedOutput output) throws Exception {
        for (boolean formatted : List.of(false, true)) {
            String document = formatted ? TestDocuments.next() : TestDocuments.fromBase("000123450");
            var payload = request(formatted ? TestDocuments.formatted(document) : document);
            var response = mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(JSON.writeValueAsBytes(payload)))
                    .andExpect(status().isCreated()).andReturn().getResponse();
            JsonNode profile = JSON.readTree(response.getContentAsByteArray());
            assertThat(profile.size()).isEqualTo(3);
            assertThat(response.getContentAsString()).doesNotContain(document, "document", "password");
            long id = profile.path("id").asLong();
            assertThat(users.findById(id).orElseThrow().document()).isEqualTo(document);
            var login = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                    .content(JSON.writeValueAsBytes(Map.of("email", payload.get("email"), "password", payload.get("password")))))
                    .andExpect(status().isOk()).andReturn().getResponse();
            String token = JSON.readTree(login.getContentAsByteArray()).path("accessToken").asText();
            JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
            assertThat(claims.path("sub").asText()).isEqualTo(Long.toString(id));
            assertThat(claims.toString()).doesNotContain(document, "document");
            for (String route : List.of("/api/v1/user", "/api/v1/users/me")) {
                var found = mvc.perform(get(route).header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()).andReturn().getResponse();
                assertThat(JSON.readTree(found.getContentAsByteArray())).isEqualTo(profile);
            }
            mvc.perform(put("/api/v1/user").header("Authorization", "Bearer " + token).contentType("application/json")
                    .content(JSON.writeValueAsBytes(Map.of("name", "CPF Atualizado", "email", payload.get("email")))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.document").doesNotExist());
            assertThat(users.findById(id).orElseThrow().document()).isEqualTo(document);
            mvc.perform(get("/api/v1/users/me/wallet").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.availablePoints").value(0));
            assertThat(output.getAll()).doesNotContain(document, TestDocuments.formatted(document));
        }
    }

    public void duplicateIsSafeAndNormalized(CapturedOutput output) throws Exception {
        String document = TestDocuments.next();
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(JSON.writeValueAsBytes(request(TestDocuments.formatted(document)))))
                .andExpect(status().isCreated());
        var duplicate = request(document);
        var response = mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(JSON.writeValueAsBytes(duplicate)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DOCUMENT_ALREADY_IN_USE"))
                .andExpect(jsonPath("$.message").value("CPF já cadastrado.")).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain(document, TestDocuments.formatted(document));
        assertThat(users.findByEmail(duplicate.get("email").toString())).isEmpty();
        assertThat(output.getAll()).doesNotContain(document, TestDocuments.formatted(document));
    }
}
