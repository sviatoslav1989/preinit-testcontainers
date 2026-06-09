package by.macmonitor.preinittestcontainers.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;

class FileBasedContainerMetadataRegistryTest {

    @Test
    void metadataResourcePath_usesLastRepositorySegment() {
        assertThat(FileBasedContainerMetadataRegistry.metadataResourcePath(
                        DockerImageName.parse("mysql:8.0.45")))
                .isEqualTo("preinit-testcontainers/metadata/mysql.metadata");
        assertThat(FileBasedContainerMetadataRegistry.metadataResourcePath(
                        DockerImageName.parse("library/mysql:8.0.45")))
                .isEqualTo("preinit-testcontainers/metadata/mysql.metadata");
        assertThat(FileBasedContainerMetadataRegistry.metadataResourcePath(
                        DockerImageName.parse("postgres:17")))
                .isEqualTo("preinit-testcontainers/metadata/postgres.metadata");
        assertThat(FileBasedContainerMetadataRegistry.metadataResourcePath(
                        DockerImageName.parse("clickhouse/clickhouse-server:26.3.4.11")))
                .isEqualTo("preinit-testcontainers/metadata/clickhouse-server.metadata");
    }
}
