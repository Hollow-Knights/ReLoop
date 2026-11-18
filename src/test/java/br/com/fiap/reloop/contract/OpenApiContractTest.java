package br.com.fiap.reloop.contract;

import static br.com.fiap.reloop.contract.OpenApiContract.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OpenApiContractTest {
    private static final List<String> METHODS = List.of("get", "post", "put", "delete", "patch", "head", "options", "trace");

    static Stream<Arguments> operations() {
        var operations = Stream.<Arguments>builder();
        DOCUMENT.path("paths").properties().forEach(path -> path.getValue().properties().forEach(method -> {
            if (METHODS.contains(method.getKey())) operations.add(Arguments.of(path.getKey(), method.getKey(), method.getValue()));
        }));
        return operations.build();
    }

    @Test void genericErrorSchemaCannotCarryScenarioExamples() {
        var schema = DOCUMENT.at("/components/schemas/ApiError");
        assertThat(schema.has("example") || schema.has("examples")).isFalse();
        var required = new java.util.HashSet<String>();
        schema.path("required").forEach(field -> required.add(field.asText()));
        assertThat(required).containsExactlyInAnyOrder("status", "code", "message", "timestamp");
        for (String field : List.of("status", "code", "message")) {
            var property = schema.path("properties").path(field);
            assertThat(property.isMissingNode()).isFalse();
            assertThat(property.has("example") || property.has("examples") || property.has("default") || property.has("enum"))
                    .as("ApiError.%s must stay generic", field).isFalse();
        }
    }

    @Test void everyLocalReferenceResolves() { checkReferences(DOCUMENT); }

    private void checkReferences(JsonNode node) {
        if (node.isObject() && node.has("$ref")) resolve(node);
        for (JsonNode child : node) checkReferences(child);
    }

    @ParameterizedTest(name = "{1} {0}: response examples and authentication")
    @MethodSource("operations")
    void everyOperationHasContextualResponseExamples(String path, String method, JsonNode operation) {
        for (var entry : operation.path("responses").properties()) {
            int status = Integer.parseInt(entry.getKey());
            JsonNode response = resolve(entry.getValue());
            assertThat(response.path("description").asText()).isNotBlank();
            if (status == 204) {
                assertThat(response.has("content")).isFalse();
                continue;
            }
            if (status == 200 && path.equals("/api/v1/uploads/{photoId}")) {
                for (String type : List.of("image/png", "image/jpeg")) {
                    assertThat(response.path("content").path(type).path("schema").path("format").asText()).isEqualTo("binary");
                }
                continue;
            }
            var media = response.path("content").path("application/json");
            var examples = media.path("examples");
            assertThat(examples.isObject() && !examples.isEmpty()).as("%s %s %s examples", method, path, status).isTrue();
            for (JsonNode example : examples) {
                var value = resolve(example).path("value");
                assertThat(value.isMissingNode()).isFalse();
                if (status >= 400) {
                    assertThat(media.path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/ApiError");
                    assertErrorShape(value, status);
                }
            }
        }
        boolean publicOperation = path.startsWith("/api/v1/auth/") || path.startsWith("/api/v1/collection-points")
                || path.equals("/api/v1/electronic-categories");
        if (publicOperation) {
            assertThat(operation.path("security").isArray() && operation.path("security").isEmpty()).isTrue();
        } else if (path.equals("/api/v1/iot/events")) {
            assertThat(operation.at("/security/0/deviceKey").isArray()).isTrue();
            assertThat(resolve(operation.at("/responses/401")).path("headers").has("WWW-Authenticate")).isFalse();
        } else {
            assertThat(DOCUMENT.at("/security/0/bearerAuth").isArray()).isTrue();
            assertThat(resolve(operation.at("/responses/401")).path("headers").has("WWW-Authenticate")).isTrue();
        }
        if (path.equals("/api/v1/auth/login")) {
            assertThat(resolve(operation.at("/responses/401")).path("headers").has("WWW-Authenticate")).isFalse();
        }
    }
}
