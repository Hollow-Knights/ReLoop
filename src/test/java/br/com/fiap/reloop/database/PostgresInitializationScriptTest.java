package br.com.fiap.reloop.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Executes the real shell script with a fake Docker CLI; it cannot reach a developer's database. */
class PostgresInitializationScriptTest {
    @TempDir Path root;
    private Path calls;
    private Path sql;
    private final Path script = Path.of("scripts/init-postgres.sh").toAbsolutePath();

    @BeforeEach void fakeDocker() throws Exception {
        calls = root.resolve("docker-calls");
        sql = root.resolve("captured-sql");
        var docker = root.resolve("docker");
        Files.writeString(docker, """
                #!/bin/sh
                set -eu
                printf '%s\n' "$*" >> "$TEST_DOCKER_CALLS"
                if [ "$1 $2" = 'compose up' ]; then
                  exit "${TEST_HEALTH_EXIT:-0}"
                fi
                if [ "$1 $2" = 'compose exec' ]; then
                  cat > "$TEST_SQL_CAPTURE"
                  exit "${TEST_SQL_EXIT:-0}"
                fi
                exit 99
                """);
        assertThat(docker.toFile().setExecutable(true)).isTrue();
    }

    private Result execute(String timeout, int healthExit, int sqlExit) throws Exception {
        var builder = new ProcessBuilder("sh", script.toString()).directory(root.toFile()).redirectErrorStream(true);
        var env = builder.environment();
        env.put("PATH", root + java.io.File.pathSeparator + env.getOrDefault("PATH", "/usr/bin:/bin"));
        env.put("TEST_DOCKER_CALLS", calls.toString());
        env.put("TEST_SQL_CAPTURE", sql.toString());
        env.put("TEST_HEALTH_EXIT", Integer.toString(healthExit));
        env.put("TEST_SQL_EXIT", Integer.toString(sqlExit));
        env.remove("POSTGRES_INIT_TIMEOUT_SECONDS");
        if (timeout != null) env.put("POSTGRES_INIT_TIMEOUT_SECONDS", timeout);
        var process = builder.start();
        try {
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            return new Result(process.exitValue(), new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            process.destroyForcibly();
        }
    }
    private record Result(int exit, String output) { }

    @Test void defaultWaitUsesHealthcheckAndFiniteTimeoutBeforePassingSql() throws Exception {
        assertThat(execute(null, 0, 0).exit()).isZero();
        assertThat(Files.readAllLines(calls)).hasSize(2).first()
                .isEqualTo("compose up -d --wait --wait-timeout 60 postgres");
        assertThat(Files.readAllLines(calls).get(1)).startsWith("compose exec -T postgres ");
        assertThat(Files.readString(sql)).contains("pg_advisory_lock", "AS empty_schema", "\\if :empty_schema",
                "\\i /opt/reloop/database.sql", "RAISE EXCEPTION 'Inicialização recusada:");
    }

    @Test void customTimeoutIsPassedToDocker() throws Exception {
        assertThat(execute("12", 0, 0).exit()).isZero();
        assertThat(Files.readAllLines(calls).getFirst()).contains("--wait-timeout 12 postgres");
    }

    @Test void unhealthyDatabaseFailsClearlyWithoutExecutingSql() throws Exception {
        var result = execute("7", 1, 0);
        assertThat(result.exit()).isEqualTo(1);
        assertThat(result.output()).contains("PostgreSQL não ficou saudável", "7s", "SQL não executado");
        assertThat(Files.readAllLines(calls)).containsExactly("compose up -d --wait --wait-timeout 7 postgres");
        assertThat(Files.exists(sql)).isFalse();
    }

    @Test void populatedDatabaseRejectionRemainsANonzeroExit() throws Exception {
        assertThat(execute(null, 0, 3).exit()).isEqualTo(3);
        assertThat(Files.readAllLines(calls)).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "00", "-1", "invalid", "999999999999999999999999999999"})
    void invalidOrUnboundedTimeoutIsRejectedBeforeCallingDocker(String timeout) throws Exception {
        var result = execute(timeout, 0, 0);
        assertThat(result.exit()).isEqualTo(2);
        assertThat(result.output()).contains("POSTGRES_INIT_TIMEOUT_SECONDS", "inteiro positivo");
        assertThat(Files.exists(calls)).isFalse();
        assertThat(Files.exists(sql)).isFalse();
    }
}
