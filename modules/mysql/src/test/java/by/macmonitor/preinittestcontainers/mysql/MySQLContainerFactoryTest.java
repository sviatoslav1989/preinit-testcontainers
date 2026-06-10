package by.macmonitor.preinittestcontainers.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import by.macmonitor.preinittestcontainers.support.TimedContainerStart;

import com.github.dockerjava.api.DockerClient;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

class MySQLContainerFactoryTest {

    private final List<String> cmdParameters = MySQLPreinitFileLockSupport.cmdParameters();

    @Test
    void mySQLContainerFactory_defaultBuilderIsStable() {
        assertThat(MySQLContainerFactory.builder().build())
                .usingRecursiveComparison()
                .isEqualTo(MySQLContainerFactory.builder().build());
    }

    @Test
    void testCreationOfNotPreinitializedContainer_SUCCESS() throws Exception {
        try (MySQLContainer container =
                MySQLContainerFactory.createMySQLContainer(CreateMySQLContainerCommand.builder()
                        .withBaseImageName("mysql:8.0.45")
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
        CreateMySQLContainerCommand command = CreateMySQLContainerCommand.builder()
                .withBaseImageName("mysql:8.0.45")
                .withInitScripts(
                        List.of("mysql/init.tables.expected.sql", "mysql/init.data.expected.sql"))
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
        try (MySQLContainer container = MySQLContainerFactory.createMySQLContainer(command)) {
            TimedContainerStart.start(container);
            imageName = container.getDockerImageName();
            MySQLPreinitFileLockSupport.assertTablesAndDataCreated(container);
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
                List.of("mysql/init.tables.expected100.sql", "mysql/init.data.expected100.sql");
        List<String> imageNames = new ArrayList<>();
        try {
            try (MySQLContainer vanillaContainer =
                    createVanillaMySQLContainer(initScripts, false)) {
                TimedContainerStart.start(
                        vanillaContainer, "vanilla MySQLContainer (init scripts at startup)");
                assertTablesAndData100Created(vanillaContainer);
            }
            try (MySQLContainer vanillaTmpfsContainer =
                    createVanillaMySQLContainer(initScripts, true)) {
                TimedContainerStart.start(
                        vanillaTmpfsContainer,
                        "vanilla MySQLContainer with tmpfs /var/lib/mysql (init scripts at startup)");
                assertTablesAndData100Created(vanillaTmpfsContainer);
            }
            try (MySQLContainer factoryContainer = MySQLContainerFactory.createMySQLContainer(
                    base100TablesCommand().build())) {
                TimedContainerStart.start(
                        factoryContainer,
                        "preinitialized MySQL with tmpfs (init scripts at startup)");
                imageNames.add(factoryContainer.getDockerImageName());
                assertTablesAndData100Created(factoryContainer);
            }
            try (MySQLContainer vanillaEmptyContainer =
                    createVanillaMySQLContainer(List.of(), false)) {
                TimedContainerStart.start(
                        vanillaEmptyContainer, "vanilla MySQLContainer (no init scripts)");
                assertDatabaseEmpty(vanillaEmptyContainer);
            }
            try (MySQLContainer vanillaEmptyTmpfsContainer =
                    createVanillaMySQLContainer(List.of(), true)) {
                TimedContainerStart.start(
                        vanillaEmptyTmpfsContainer,
                        "vanilla MySQLContainer with tmpfs /var/lib/mysql (no init scripts)");
                assertDatabaseEmpty(vanillaEmptyTmpfsContainer);
            }
            try (MySQLContainer factoryEmptyContainer = MySQLContainerFactory.createMySQLContainer(
                    baseEmptyCommand().build())) {
                TimedContainerStart.start(
                        factoryEmptyContainer, "preinitialized MySQL with tmpfs (no init scripts)");
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
        CreateMySQLContainerCommand command = CreateMySQLContainerCommand.builder()
                .withBaseImageName("mysql:8.0.45")
                .withTmpFsFilesystems(Collections.emptyList())
                .withInitScripts(
                        List.of("mysql/init.tables.expected.sql", "mysql/init.data.expected.sql"))
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .withCmdParameters(cmdParameters)
                .build();
        try (MySQLContainer container = MySQLContainerFactory.createMySQLContainer(command)) {
            TimedContainerStart.start(container);
            imageName = container.getDockerImageName();
            MySQLPreinitFileLockSupport.assertTablesAndDataCreated(container);
        } finally {
            if (imageName != null) {
                DockerClient dockerClient = DockerClientFactory.lazyClient();
                dockerClient.removeImageCmd(imageName).exec();
            }
        }
    }

    @Test
    void testUrlParametersAppliedToJdbcUrl_SUCCESS() throws Exception {
        try (MySQLContainer container =
                MySQLContainerFactory.createMySQLContainer(CreateMySQLContainerCommand.builder()
                        .withBaseImageName("mysql:8.0.45")
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

    private CreateMySQLContainerCommand.CreateMySQLContainerCommandBuilder<?, ?>
            base100TablesCommand() {
        return basePreinitializedCommand()
                .withInitScripts(List.of(
                        "mysql/init.tables.expected100.sql", "mysql/init.data.expected100.sql"));
    }

    private CreateMySQLContainerCommand.CreateMySQLContainerCommandBuilder<?, ?>
            baseEmptyCommand() {
        return basePreinitializedCommand();
    }

    private CreateMySQLContainerCommand.CreateMySQLContainerCommandBuilder<?, ?>
            basePreinitializedCommand() {
        return MySQLPreinitFileLockSupport.basePreinitializedCommand();
    }

    private MySQLContainer<?> createVanillaMySQLContainer(
            List<String> initScripts, boolean withTmpfs) {
        MySQLContainer<?> container = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.45"));
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

    private static void assertDatabaseEmpty(MySQLContainer container) throws Exception {
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

    private static void assertTablesAndData100Created(MySQLContainer container) throws Exception {
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
}
