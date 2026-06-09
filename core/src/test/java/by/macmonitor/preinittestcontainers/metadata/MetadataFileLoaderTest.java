package by.macmonitor.preinittestcontainers.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import by.macmonitor.preinittestcontainers.ContainerMetadata;

import org.junit.jupiter.api.Test;

import java.util.Optional;

class MetadataFileLoaderTest {

    private static final String RANGES_PATH =
            "preinit-testcontainers/metadata/test-ranges.metadata";

    @Test
    void exactMatchAtRangeBoundaries() {
        MetadataFile file =
                MetadataFileLoader.load(RANGES_PATH, MetadataFileLoaderTest.class.getClassLoader());

        assertMetadata(
                file.resolve(ImageVersion.parse("1.0")).orElseThrow(),
                "/old/entrypoint.sh",
                "old-cmd");
        assertMetadata(
                file.resolve(ImageVersion.parse("1.9")).orElseThrow(),
                "/old/entrypoint.sh",
                "old-cmd");
        assertMetadata(
                file.resolve(ImageVersion.parse("2.0")).orElseThrow(),
                "/new/entrypoint.sh",
                "new-cmd");
        assertMetadata(
                file.resolve(ImageVersion.parse("2.9")).orElseThrow(),
                "/new/entrypoint.sh",
                "new-cmd");
    }

    @Test
    void latestOrEmptyReturnsMaxRecord() {
        MetadataFile file =
                MetadataFileLoader.load(RANGES_PATH, MetadataFileLoaderTest.class.getClassLoader());

        ContainerMetadata latest = file.resolve(ImageVersion.parse("latest")).orElseThrow();
        ContainerMetadata empty = file.resolve(ImageVersion.parse("")).orElseThrow();
        assertThat(latest.getEntrypointPath()).isEqualTo("/new/entrypoint.sh");
        assertThat(empty.getEntrypointPath()).isEqualTo("/new/entrypoint.sh");
    }

    @Test
    void overlappingRangesFailAtLoadTime() {
        assertThatThrownBy(() -> {
                    MetadataFileLoader.load(
                            "preinit-testcontainers/metadata/overlapping.metadata",
                            MetadataFileLoaderTest.class.getClassLoader());
                })
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Overlapping version ranges");
    }

    @Test
    void parseArgv_escapesColonInsideToken() {
        String[] argv = MetadataFileLoader.parseArgv("postgres:\\-c:date '+%Y-%m-%dT%H\\:%M\\:%S'");
        assertThat(argv).containsExactly("postgres", "-c", "date '+%Y-%m-%dT%H:%M:%S'");
    }

    @Test
    void parseArgv_preservesCommaInsideToken() {
        String[] argv = MetadataFileLoader.parseArgv("foo:bar,baz");
        assertThat(argv).containsExactly("foo", "bar,baz");
    }

    @Test
    void parseArgvUsesColonSeparatedTokens() {
        String[] argv = MetadataFileLoader.parseArgv("postgres:-c:fsync=off");
        assertThat(argv).containsExactly("postgres", "-c", "fsync=off");
    }

    @Test
    void parseVolumes_multiplePaths() {
        String[] volumes = MetadataFileLoader.parseVolumes("/var/lib/mysql:/var/log/mysql");
        assertThat(volumes).containsExactly("/var/lib/mysql", "/var/log/mysql");
    }

    @Test
    void parseVolumes_pathWithColon() {
        String[] volumes = MetadataFileLoader.parseVolumes("/data\\:vol:/var/lib/mysql");
        assertThat(volumes).containsExactly("/data:vol", "/var/lib/mysql");
    }

    @Test
    void tagAboveMaxEndVersionReturnsMaxRecord() {
        MetadataFile file =
                MetadataFileLoader.load(RANGES_PATH, MetadataFileLoaderTest.class.getClassLoader());

        ContainerMetadata metadata = file.resolve(ImageVersion.parse("9.0")).orElseThrow();
        assertThat(metadata.getEntrypointPath()).isEqualTo("/new/entrypoint.sh");
        assertThat(metadata.getCmd()).containsExactly("new-cmd");
    }

    @Test
    void tagBelowMinStartVersionReturnsEmpty() {
        MetadataFile file =
                MetadataFileLoader.load(RANGES_PATH, MetadataFileLoaderTest.class.getClassLoader());

        Optional<ContainerMetadata> metadata = file.resolve(ImageVersion.parse("0.9"));
        assertThat(metadata).isEmpty();
    }

    private static void assertMetadata(
            ContainerMetadata metadata, String entrypointPath, String cmd) {
        assertThat(metadata.getEntrypointPath()).isEqualTo(entrypointPath);
        assertThat(metadata.getCmd()).containsExactly(cmd);
    }
}
