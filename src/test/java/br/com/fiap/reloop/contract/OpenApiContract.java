package br.com.fiap.reloop.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.AntPathMatcher;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Reads the versioned contract rather than duplicating its examples in HTTP assertions. */
public final class OpenApiContract {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final JsonNode DOCUMENT = load();
    private static final AntPathMatcher PATHS = new AntPathMatcher();

    private OpenApiContract() { }

    private static JsonNode load() {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (var input = OpenApiContract.class.getResourceAsStream("/static/docs/openapi.yaml")) {
            assertThat(input).as("versioned OpenAPI resource").isNotNull();
            return JSON.valueToTree(new Yaml(new SafeConstructor(options)).load(input));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read OpenAPI", failure);
        }
    }

    public static JsonNode resolve(JsonNode node) {
        var visited = new HashSet<String>();
        while (node.has("$ref")) {
            String ref = node.path("$ref").asText();
            assertThat(ref).startsWith("#/");
            assertThat(visited.add(ref)).as("non-cyclic reference %s", ref).isTrue();
            node = DOCUMENT.at(ref.substring(1));
            assertThat(node.isMissingNode()).as("reference %s resolves", ref).isFalse();
        }
        return node;
    }

    public static void assertErrorShape(JsonNode error, int status) {
        assertThat(error.isObject()).isTrue();
        var fields = new HashSet<String>();
        error.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("status", "code", "message", "timestamp");
        assertThat(error.path("status").isIntegralNumber()).isTrue();
        assertThat(error.path("status").asInt()).isEqualTo(status);
        for (String name : List.of("code", "message", "timestamp")) {
            assertThat(error.path(name).isTextual()).as(name).isTrue();
            assertThat(error.path(name).asText()).as(name).isNotBlank();
        }
        Instant.parse(error.path("timestamp").asText());
    }

    public static void assertDocumentedError(MvcResult result) throws Exception {
        int status = result.getResponse().getStatus();
        if (status < 400 || !result.getRequest().getRequestURI().startsWith("/api/v1/")) return;
        String method = result.getRequest().getMethod().toLowerCase(java.util.Locale.ROOT);
        String uri = result.getRequest().getRequestURI();
        JsonNode operation = null;
        var paths = DOCUMENT.path("paths").fields();
        while (paths.hasNext()) {
            var path = paths.next();
            if (PATHS.match(path.getKey(), uri) && path.getValue().has(method)) {
                operation = path.getValue().get(method);
                break;
            }
        }
        assertThat(operation).as("documented operation %s %s", method, uri).isNotNull();
        JsonNode response = resolve(operation.path("responses").path(String.valueOf(status)));
        assertThat(response.isMissingNode()).as("documented HTTP %s on %s %s", status, method, uri).isFalse();
        assertThat(result.getResponse().getContentType()).isNotNull();
        assertThat(MediaType.parseMediaType(result.getResponse().getContentType()).isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        JsonNode actual = JSON.readTree(result.getResponse().getContentAsByteArray());
        assertErrorShape(actual, status);
        var examples = response.path("content").path("application/json").path("examples");
        assertThat(examples.isObject() && !examples.isEmpty()).isTrue();
        boolean matches = false;
        for (JsonNode example : examples) {
            JsonNode expected = resolve(example).path("value");
            assertErrorShape(expected, status);
            matches |= expected.path("code").equals(actual.path("code"))
                    && expected.path("message").equals(actual.path("message"));
        }
        assertThat(matches).as("%s %s HTTP %s: code/message %s must match an example", method, uri, status, actual).isTrue();
    }
}
