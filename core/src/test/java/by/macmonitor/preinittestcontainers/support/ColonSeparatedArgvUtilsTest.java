package by.macmonitor.preinittestcontainers.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

class ColonSeparatedArgvUtilsTest {

    @ParameterizedTest
    @MethodSource("roundTripCases")
    void bashSplit_matchesJavaDecode(List<String> tokens) throws Exception {
        Assumptions.assumeTrue(bashAvailable(), "bash not available");
        String encoded = ColonSeparatedArgvUtils.encode(tokens);
        assertThat(runBashSplit(entrypointScriptPath(), encoded)).isEqualTo(tokens);
    }

    @ParameterizedTest
    @MethodSource("decodeOnlyCases")
    void bashSplit_matchesJavaDecodeOnlyCases(String encoded, List<String> expected)
            throws Exception {
        Assumptions.assumeTrue(bashAvailable(), "bash not available");
        assertThat(runBashSplit(entrypointScriptPath(), encoded)).isEqualTo(expected);
    }

    @Test
    void decode_null_returnsEmptyList() {
        assertThat(ColonSeparatedArgvUtils.decode(null)).isEqualTo(List.of());
    }

    @ParameterizedTest
    @MethodSource("decodeOnlyCases")
    void decode_producesExpectedTokens(String encoded, List<String> expected) {
        assertThat(ColonSeparatedArgvUtils.decode(encoded)).isEqualTo(expected);
    }

    @Test
    void decode_trailingBackslash_throws() {
        assertThatThrownBy(() -> ColonSeparatedArgvUtils.decode("a\\"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Trailing backslash in colon-separated argv");
    }

    @Test
    void encode_emptyVarargs_returnsEmptyString() {
        assertThat(ColonSeparatedArgvUtils.encode()).isEmpty();
    }

    @Test
    void encode_pathWithColon_usesBackslashEscape() {
        assertThat(ColonSeparatedArgvUtils.encode("/data:vol", "/var/lib/mysql"))
                .isEqualTo("/data\\:vol:/var/lib/mysql");
    }

    @ParameterizedTest
    @MethodSource("roundTripCases")
    void encodeDecode_roundTrip(List<String> tokens) {
        String encoded = ColonSeparatedArgvUtils.encode(tokens);
        assertThat(ColonSeparatedArgvUtils.decode(encoded)).isEqualTo(tokens);
    }

    public static Stream<Arguments> decodeOnlyCases() {
        return Stream.of(
                Arguments.of("docker-entrypoint.sh", List.of("docker-entrypoint.sh")),
                Arguments.of(
                        "/var/lib/mysql:/var/log/mysql",
                        List.of("/var/lib/mysql", "/var/log/mysql")),
                Arguments.of("/data\\:vol:/var/lib/mysql", List.of("/data:vol", "/var/lib/mysql")));
    }

    public static Stream<List<String>> roundTripCases() {
        return Stream.of(
                List.of("docker-entrypoint.sh"),
                List.of("/entrypoint.sh"),
                List.of("/bin/sh", "-c", "echo hi"),
                List.of("date '+%Y-%m-%dT%H:%M:%S'"),
                List.of("/data:vol", "/var/lib/mysql"),
                List.of("/var/lib/mysql", "/var/log/mysql"),
                List.of("a", "", "c"),
                List.of("a\\b", "\\\\"));
    }

    private static boolean bashAvailable() {
        try {
            Process process = new ProcessBuilder("bash", "--version")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path entrypointScriptPath() throws Exception {
        URL url = ColonSeparatedArgvUtilsTest.class
                .getClassLoader()
                .getResource("docker/testcontainer-entrypoint.sh");
        if (url == null) {
            throw new IllegalStateException("testcontainer-entrypoint.sh not on classpath");
        }
        return Paths.get(url.toURI());
    }

    private static List<String> runBashSplit(Path scriptPath, String encoded) throws Exception {
        String script = scriptPath.toAbsolutePath().toString().replace('\\', '/');
        ProcessBuilder pb = new ProcessBuilder(
                "bash",
                "-c",
                "source " + shellQuote(script)
                        + "; tce_split_colon_escaped \"$TCE_ENCODED\" OUT; printf '%s\\n' \"${OUT[@]}\"");
        pb.environment().put("TCE_ENCODED", encoded);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("bash split timed out");
        }
        if (process.exitValue() != 0) {
            String output;
            try (var reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
            }
            throw new IllegalStateException("bash split failed: " + output);
        }
        List<String> lines = new ArrayList<>();
        try (var reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
