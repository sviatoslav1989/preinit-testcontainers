package com.sviattech.preinittestcontainers.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import com.sviattech.preinittestcontainers.ClickHouseDataSourceUtils;
import com.sviattech.preinittestcontainers.support.TimedContainerStart;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

class ClickHouseContainerFactoryTest {

    private final List<String> cmdParameters = List.of(
            "--",
            "--background_merges_mutations_concurrency=1",
            "--logger.level=error",
            "--mark_cache_size=134217728",
            "--max_server_memory_usage=536870912",
            "--merge_tree.fsync_metadata=0",
            "--merge_tree.min_bytes_to_use_direct_io=0");

    @Test
    void clickHouseContainerFactory_defaultBuilderIsStable() {
        assertThat(ClickHouseContainerFactory.builder().build())
                .usingRecursiveComparison()
                .isEqualTo(ClickHouseContainerFactory.builder().build());
    }

    @Test
    void testCreationOfNotPreinitializedContainer_SUCCESS() throws Exception {
        try (ClickHouseContainer container = ClickHouseContainerFactory.createClickHouseContainer(
                CreateClickHouseContainerCommand.builder()
                        .withBaseImageName("clickhouse/clickhouse-server:26.3.4.11")
                        .withPreInitialized(false)
                        .withDbName("testdb")
                        .withUsername("user")
                        .withPassword("password")
                        .withCmdParameters(cmdParameters)
                        .build())) {
            TimedContainerStart.start(container);
            assertDatabaseEmpty(container);
        }
    }

    @Test
    void testCreationOfPreinitializedContainer_SUCCESS() throws Exception {
        String imageName = null;
        CreateClickHouseContainerCommand command = CreateClickHouseContainerCommand.builder()
                .withBaseImageName("clickhouse/clickhouse-server:26.3.4.11")
                .withInitScripts(List.of(
                        "clickhouse/init.tables.expected.sql", "clickhouse/init.data.expected.sql"))
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .withCmdParameters(cmdParameters)
                .build();
        try (ClickHouseContainer container =
                ClickHouseContainerFactory.createClickHouseContainer(command)) {
            imageName = container.getDockerImageName();
            TimedContainerStart.start(container);
            assertTablesAndDataCreated(container);
        } finally {
            if (imageName != null) {
                DockerClient dockerClient = DockerClientFactory.lazyClient();
                dockerClient.removeImageCmd(imageName).exec();
            }
        }
    }

    @Test
    void testCreationOfPreinitializedContainer100tables_Benchmark() throws Exception {
        List<String> initScripts = List.of(
                "clickhouse/init.tables.expected100.sql", "clickhouse/init.data.expected100.sql");
        List<String> imageNames = new ArrayList<>();
        try {
            try (ClickHouseContainer vanillaContainer =
                    createVanillaClickHouseContainer(initScripts, false)) {
                TimedContainerStart.start(
                        vanillaContainer, "vanilla ClickHouseContainer (init scripts at startup)");
                assertTablesAndData100Created(vanillaContainer);
            }
            try (ClickHouseContainer vanillaTmpfsContainer =
                    createVanillaClickHouseContainer(initScripts, true)) {
                TimedContainerStart.start(
                        vanillaTmpfsContainer,
                        "vanilla ClickHouseContainer with tmpfs /var/lib/clickhouse (init scripts at startup)");
                assertTablesAndData100Created(vanillaTmpfsContainer);
            }
            try (ClickHouseContainer factoryContainer =
                    ClickHouseContainerFactory.createClickHouseContainer(
                            base100TablesCommand().build())) {
                TimedContainerStart.start(
                        factoryContainer,
                        "preinitialized ClickHouse with tmpfs (init scripts at startup)");
                imageNames.add(factoryContainer.getDockerImageName());
                assertTablesAndData100Created(factoryContainer);
            }
            try (ClickHouseContainer vanillaEmptyContainer =
                    createVanillaClickHouseContainer(List.of(), false)) {
                TimedContainerStart.start(
                        vanillaEmptyContainer, "vanilla ClickHouseContainer (no init scripts)");
                assertDatabaseEmpty(vanillaEmptyContainer);
            }
            try (ClickHouseContainer vanillaEmptyTmpfsContainer =
                    createVanillaClickHouseContainer(List.of(), true)) {
                TimedContainerStart.start(
                        vanillaEmptyTmpfsContainer,
                        "vanilla ClickHouseContainer with tmpfs /var/lib/clickhouse (no init scripts)");
                assertDatabaseEmpty(vanillaEmptyTmpfsContainer);
            }
            try (ClickHouseContainer factoryEmptyContainer =
                    ClickHouseContainerFactory.createClickHouseContainer(
                            baseEmptyCommand().build())) {
                TimedContainerStart.start(
                        factoryEmptyContainer,
                        "preinitialized ClickHouse with tmpfs (no init scripts)");
                imageNames.add(factoryEmptyContainer.getDockerImageName());
                assertDatabaseEmpty(factoryEmptyContainer);
            }
        } finally {
            DockerClient dockerClient = DockerClientFactory.lazyClient();
            for (String imageName : imageNames) {
                dockerClient.removeImageCmd(imageName).exec();
            }
        }
    }

    private CreateClickHouseContainerCommand.CreateClickHouseContainerCommandBuilder<?, ?>
            base100TablesCommand() {
        return basePreinitializedCommand()
                .withInitScripts(List.of(
                        "clickhouse/init.tables.expected100.sql",
                        "clickhouse/init.data.expected100.sql"));
    }

    private CreateClickHouseContainerCommand.CreateClickHouseContainerCommandBuilder<?, ?>
            baseEmptyCommand() {
        return basePreinitializedCommand();
    }

    private CreateClickHouseContainerCommand.CreateClickHouseContainerCommandBuilder<?, ?>
            basePreinitializedCommand() {
        return CreateClickHouseContainerCommand.builder()
                .withBaseImageName("clickhouse/clickhouse-server:26.3.4.11")
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .withCmdParameters(cmdParameters);
    }

    private ClickHouseContainer createVanillaClickHouseContainer(
            List<String> initScripts, boolean withTmpfs) {
        ClickHouseContainer container = new ClickHouseContainer(
                DockerImageName.parse("clickhouse/clickhouse-server:26.3.4.11"));
        container.withDatabaseName("testdb").withUsername("user").withPassword("password");
        if (!initScripts.isEmpty()) {
            container.withInitScripts(initScripts.toArray(new String[0]));
        }
        if (!cmdParameters.isEmpty()) {
            container.withCreateContainerCmdModifier(
                    c -> c.withCmd(cmdParameters.toArray(new String[0])));
        }
        if (withTmpfs) {
            container.withTmpFs(Map.of("/var/lib/clickhouse", "rw"));
        }
        return container;
    }

    private static void assertDatabaseEmpty(ClickHouseContainer container) throws Exception {
        DataSource dataSource = ClickHouseDataSourceUtils.createDataSource(container);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                    "SELECT count() FROM system.tables WHERE database = 'testdb' AND name = 'test'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("database should not contain init script table test")
                        .isZero();
            }
        }
    }

    private static void assertTablesAndData100Created(ClickHouseContainer container)
            throws Exception {
        DataSource dataSource = ClickHouseDataSourceUtils.createDataSource(container);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                    "SELECT count() FROM system.tables WHERE database = 'testdb' AND name LIKE 'table_%'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("init scripts should create 100 tables")
                        .isEqualTo(100);
            }
            try (ResultSet rs = statement.executeQuery("SELECT count() FROM table_001")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).as("table_001 should contain 20 rows").isEqualTo(20);
            }
            try (ResultSet rs = statement.executeQuery("SELECT count() FROM table_100")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).as("table_100 should contain 20 rows").isEqualTo(20);
            }
            try (ResultSet rs = statement.executeQuery("SELECT min(id), max(id) FROM table_001")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).as("table_001 min id should be 1").isEqualTo(1);
                assertThat(rs.getInt(2)).as("table_001 max id should be 20").isEqualTo(20);
            }
        }
    }

    private static void assertTablesAndDataCreated(ClickHouseContainer container) throws Exception {
        DataSource dataSource = ClickHouseDataSourceUtils.createDataSource(container);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                    "SELECT count() FROM system.tables WHERE database = 'testdb' AND name = 'test'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("init script should create table test")
                        .isEqualTo(1);
            }
            try (ResultSet rs = statement.executeQuery("SELECT id FROM test ORDER BY id")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("init script should insert row id=1")
                        .isEqualTo(1);
                assertThat(rs.next())
                        .as("init scripts should define exactly one row in test")
                        .isFalse();
            }
        }
    }
}
