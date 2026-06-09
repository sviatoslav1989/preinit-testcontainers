package by.macmonitor.preinittestcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import by.macmonitor.preinittestcontainers.metadata.ContainerMetadataRegistry;
import by.macmonitor.preinittestcontainers.metadata.FileBasedContainerMetadataRegistry;

import org.junit.jupiter.api.Test;

import java.util.Optional;

class ContainerMetadataRegistryTest {

    private final ContainerMetadataRegistry registry = new FileBasedContainerMetadataRegistry();

    @Test
    void emptyRegistryReturnsEmpty() {
        ContainerMetadataRegistry empty = imageName -> Optional.empty();
        assertThat(empty.find("mysql:8.0.45")).isEmpty();
    }

    @Test
    void findClickHouseServerResolvesKnownTag() {
        ContainerMetadata metadata =
                registry.find("clickhouse/clickhouse-server:26.3.4.11").orElseThrow();
        assertThat(metadata.getEntrypointPath()).isEqualTo("/entrypoint.sh");
    }

    @Test
    void findMySQLAboveMaxRangeUsesMaxRecord() {
        ContainerMetadata current = registry.find("mysql:8.0.45").orElseThrow();
        ContainerMetadata newer = registry.find("mysql:99.0.0").orElseThrow();
        assertThat(newer).usingRecursiveComparison().isEqualTo(current);
    }

    @Test
    void findMySQLResolvesKnownTag() {
        ContainerMetadata metadata = registry.find("mysql:8.0.45").orElseThrow();
        assertThat(metadata.getEntrypointPath()).isEqualTo("/usr/local/bin/docker-entrypoint.sh");
        assertThat(metadata.getCmd()).containsExactly("mysqld");
    }

    @Test
    void findMySQLWithLibraryPrefixResolvesKnownTag() {
        ContainerMetadata metadata = registry.find("library/mysql:8.0.45").orElseThrow();
        assertThat(metadata.getEntrypointPath()).isEqualTo("/usr/local/bin/docker-entrypoint.sh");
        assertThat(metadata.getCmd()).containsExactly("mysqld");
    }

    @Test
    void findReturnsEmptyForUnknownImage() {
        assertThat(registry.find("unknown/image:1")).isEmpty();
    }
}
