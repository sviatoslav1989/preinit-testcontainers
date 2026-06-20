package com.sviattech.preinittestcontainers.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import com.sviattech.preinittestcontainers.support.TimedContainerStart;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

class MariaDBContainerFactoryTest {

    private static final String BASE_IMAGE_NAME = "mariadb:12.2";

    private static final List<String> CMD_PARAMETERS = List.of(
            "--character-set-server=utf8",
            "--collation-server=utf8_general_ci",
            "--max_connections=150",
            "--innodb-buffer-pool-size=128M",
            "--innodb-doublewrite=0",
            "--innodb-flush-log-at-trx-commit=0",
            "--innodb-flush-neighbors=0",
            "--innodb-fast-shutdown=1",
            "--innodb-log-file-size=128M",
            "--skip-name-resolve");

    private final List<String> cmdParameters = CMD_PARAMETERS;

    @Test
    void mariaDBContainerFactory_defaultBuilderIsStable() {
        assertThat(MariaDBContainerFactory.builder().build())
                .usingRecursiveComparison()
                .isEqualTo(MariaDBContainerFactory.builder().build());
    }

    @Test
    void testCreationOfNotPreinitializedContainer_SUCCESS() throws Exception {
        try (MariaDBContainer container = MariaDBContainerFactory.createMariaDBContainer(
                CreateMariaDBContainerCommand.builder()
                        .withBaseImageName(BASE_IMAGE_NAME)
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
        CreateMariaDBContainerCommand command = CreateMariaDBContainerCommand.builder()
                .withBaseImageName(BASE_IMAGE_NAME)
                .withInitScripts(List.of(
                        "mariadb/init.tables.expected.sql", "mariadb/init.data.expected.sql"))
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .withCmdParameters(cmdParameters)
                .withCreateContainerCmdModifier(cmd -> {
                    cmd.withHostName("host-a");
                    cmd.withCmd("1", "2", "3");
                    cmd.withEntrypoint("/1.sh");
                })
                .build();
        try (MariaDBContainer container = MariaDBContainerFactory.createMariaDBContainer(command)) {
            TimedContainerStart.start(container);
            imageName = container.getDockerImageName();
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
        List<String> initScripts =
                List.of("mariadb/init.tables.expected100.sql", "mariadb/init.data.expected100.sql");
        List<String> imageNames = new ArrayList<>();
        try {
            try (MariaDBContainer vanillaContainer =
                    createVanillaMariaDBContainer(initScripts, false)) {
                TimedContainerStart.start(
                        vanillaContainer, "vanilla MariaDBContainer (init scripts at startup)");
                assertTablesAndData100Created(vanillaContainer);
            }
            try (MariaDBContainer vanillaTmpfsContainer =
                    createVanillaMariaDBContainer(initScripts, true)) {
                TimedContainerStart.start(
                        vanillaTmpfsContainer,
                        "vanilla MariaDBContainer with tmpfs /var/lib/mysql (init scripts at startup)");
                assertTablesAndData100Created(vanillaTmpfsContainer);
            }
            try (MariaDBContainer factoryContainer = MariaDBContainerFactory.createMariaDBContainer(
                    base100TablesCommand().build())) {
                TimedContainerStart.start(
                        factoryContainer,
                        "preinitialized MariaDB with tmpfs (init scripts at startup)");
                imageNames.add(factoryContainer.getDockerImageName());
                assertTablesAndData100Created(factoryContainer);
            }
            try (MariaDBContainer vanillaEmptyContainer =
                    createVanillaMariaDBContainer(List.of(), false)) {
                TimedContainerStart.start(
                        vanillaEmptyContainer, "vanilla MariaDBContainer (no init scripts)");
                assertDatabaseEmpty(vanillaEmptyContainer);
            }
            try (MariaDBContainer vanillaEmptyTmpfsContainer =
                    createVanillaMariaDBContainer(List.of(), true)) {
                TimedContainerStart.start(
                        vanillaEmptyTmpfsContainer,
                        "vanilla MariaDBContainer with tmpfs /var/lib/mysql (no init scripts)");
                assertDatabaseEmpty(vanillaEmptyTmpfsContainer);
            }
            try (MariaDBContainer factoryEmptyContainer =
                    MariaDBContainerFactory.createMariaDBContainer(
                            baseEmptyCommand().build())) {
                TimedContainerStart.start(
                        factoryEmptyContainer,
                        "preinitialized MariaDB with tmpfs (no init scripts)");
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

    @Disabled
    @Test
    void testCreationOfPreinitializedContainerWithoutTmpfs_SUCCESS() throws Exception {
        String imageName = null;
        CreateMariaDBContainerCommand command = CreateMariaDBContainerCommand.builder()
                .withBaseImageName(BASE_IMAGE_NAME)
                .withTmpFsFilesystems(Collections.emptyList())
                .withInitScripts(List.of(
                        "mariadb/init.tables.expected.sql", "mariadb/init.data.expected.sql"))
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .withCmdParameters(cmdParameters)
                .build();
        try (MariaDBContainer container = MariaDBContainerFactory.createMariaDBContainer(command)) {
            TimedContainerStart.start(container);
            imageName = container.getDockerImageName();
            assertTablesAndDataCreated(container);
        } finally {
            if (imageName != null) {
                DockerClient dockerClient = DockerClientFactory.lazyClient();
                dockerClient.removeImageCmd(imageName).exec();
            }
        }
    }

    @Test
    void testUrlParametersAppliedToJdbcUrl_SUCCESS() throws Exception {
        try (MariaDBContainer container = MariaDBContainerFactory.createMariaDBContainer(
                CreateMariaDBContainerCommand.builder()
                        .withBaseImageName(BASE_IMAGE_NAME)
                        .withPreInitialized(false)
                        .withDbName("testdb")
                        .withUsername("user")
                        .withPassword("password")
                        .withUrlParameters(Map.of("serverTimezone", "UTC"))
                        .withCmdParameters(cmdParameters)
                        .build())) {
            TimedContainerStart.start(container);
            assertThat(container.getJdbcUrl()).contains("serverTimezone=UTC");
        }
    }

    private CreateMariaDBContainerCommand.CreateMariaDBContainerCommandBuilder<?, ?>
            base100TablesCommand() {
        return basePreinitializedCommand()
                .withInitScripts(List.of(
                        "mariadb/init.tables.expected100.sql",
                        "mariadb/init.data.expected100.sql"));
    }

    private CreateMariaDBContainerCommand.CreateMariaDBContainerCommandBuilder<?, ?>
            baseEmptyCommand() {
        return basePreinitializedCommand();
    }

    private CreateMariaDBContainerCommand.CreateMariaDBContainerCommandBuilder<?, ?>
            basePreinitializedCommand() {
        return CreateMariaDBContainerCommand.builder()
                .withBaseImageName(BASE_IMAGE_NAME)
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .withCmdParameters(CMD_PARAMETERS);
    }

    private MariaDBContainer createVanillaMariaDBContainer(
            List<String> initScripts, boolean withTmpfs) {
        MariaDBContainer container = new MariaDBContainer(DockerImageName.parse(BASE_IMAGE_NAME));
        container.withDatabaseName("testdb").withUsername("user").withPassword("password");
        if (!initScripts.isEmpty()) {
            container.withInitScripts(initScripts.toArray(new String[0]));
        }
        if (!cmdParameters.isEmpty()) {
            container.withCreateContainerCmdModifier(
                    c -> c.withCmd(cmdParameters.toArray(new String[0])));
        }
        if (withTmpfs) {
            container.withTmpFs(Map.of("/var/lib/mysql", "rw"));
        }
        return container;
    }

    private static void assertDatabaseEmpty(MariaDBContainer container) throws Exception {
        DataSource dataSource = DataSourceFactory.createDataSource(container);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'testdb' AND table_name = 'test'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("database should not contain init script table test")
                        .isZero();
            }
        }
    }

    private static void assertTablesAndData100Created(MariaDBContainer container) throws Exception {
        DataSource dataSource = DataSourceFactory.createDataSource(container);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'testdb' AND table_name LIKE 'table_%'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("init scripts should create 100 tables")
                        .isEqualTo(100);
            }
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM table_001")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).as("table_001 should contain 20 rows").isEqualTo(20);
            }
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM table_100")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).as("table_100 should contain 20 rows").isEqualTo(20);
            }
            try (ResultSet rs = statement.executeQuery("SELECT MIN(id), MAX(id) FROM table_001")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).as("table_001 min id should be 1").isEqualTo(1);
                assertThat(rs.getInt(2)).as("table_001 max id should be 20").isEqualTo(20);
            }
        }
    }

    private static void assertTablesAndDataCreated(MariaDBContainer container) throws Exception {
        DataSource dataSource = DataSourceFactory.createDataSource(container);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'testdb' AND table_name = 'test'")) {
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
