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

    @Test void mobileProfileAliasesKeepTheOriginalContractsAndJwtSecurity() {
        JsonNode mobile = DOCUMENT.at("/paths/~1api~1v1~1user");
        JsonNode legacy = DOCUMENT.at("/paths/~1api~1v1~1users~1me");
        for (String method : List.of("get", "put")) {
            JsonNode alias = mobile.path(method);
            JsonNode original = legacy.path(method);
            assertThat(alias.isMissingNode()).isFalse();
            assertThat(alias.path("responses")).isEqualTo(original.path("responses"));
            assertThat(alias.path("requestBody")).isEqualTo(original.path("requestBody"));
            assertThat(alias.path("security")).isEqualTo(DOCUMENT.path("security"));
            assertThat(alias.has("parameters")).isFalse();
            assertThat(alias.path("operationId")).isNotEqualTo(original.path("operationId"));
        }
    }

    @Test void collectionDistanceIsRequiredNumericAndExplicitlySimulated() {
        JsonNode point = DOCUMENT.at("/components/schemas/CollectionPointResponse");
        assertThat(point.path("required")).anySatisfy(field -> assertThat(field.asText()).isEqualTo("distanceFromUserLocation"));
        JsonNode distance = resolve(point.at("/properties/distanceFromUserLocation"));
        assertThat(distance.path("type").asText()).isEqualTo("number");
        assertThat(distance.path("minimum").asDouble()).isZero();
        assertThat(distance.path("description").asText()).contains("simulada", "quilômetros", "Não usa GPS", "não representa", "trajeto por ruas");
        assertThat(DOCUMENT.at("/components/schemas/CollectionPointDetailResponse/allOf/0/$ref").asText())
                .isEqualTo("#/components/schemas/CollectionPointResponse");
        JsonNode listExample = DOCUMENT.at("/components/examples/CollectionPoints/value/0/distanceFromUserLocation");
        JsonNode detailExample = DOCUMENT.at("/components/examples/CollectionPoint/value/distanceFromUserLocation");
        assertThat(listExample.isNumber()).isTrue();
        assertThat(detailExample).isEqualTo(listExample);
    }

    @Test void registrationRequiresPrivateCpfAndDocumentsItsOwnErrors() {
        JsonNode request = DOCUMENT.at("/components/schemas/RegisterRequest");
        assertThat(request.path("required")).anySatisfy(field -> assertThat(field.asText()).isEqualTo("document"));
        JsonNode document = resolve(request.at("/properties/document"));
        assertThat(document.path("type").asText()).isEqualTo("string");
        assertThat(document.path("writeOnly").asBoolean()).isTrue();
        assertThat(document.path("description").asText()).contains("CPF", "verificadores", "11 dígitos");
        assertThat(DOCUMENT.at("/components/schemas/ProfileResponse/properties").has("document")).isFalse();
        var errors = resolve(DOCUMENT.at("/paths/~1api~1v1~1auth~1register/post/responses/409"))
                .at("/content/application~1json/examples");
        assertThat(resolve(errors.path("DocumentAlreadyInUse")).at("/value/code").asText()).isEqualTo("DOCUMENT_ALREADY_IN_USE");
        assertThat(errors.has("EmailAlreadyInUse")).isTrue();
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
