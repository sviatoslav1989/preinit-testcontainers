package com.sviattech.preinittestcontainers.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import com.sviattech.preinittestcontainers.JdbcContainerFactory;
import com.sviattech.preinittestcontainers.support.TimedContainerStart;

import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Slf4j
class PostgreSQLContainerFactoryTest {

    private final List<String> cmdParameters = List.of();

    @Test
    void applyCommandProperties_startupTimeoutDuration_fallbackWhenSecondsNotSet()
            throws Exception {
        CreatePostgreSQLContainerCommand command = CreatePostgreSQLContainerCommand.builder()
                .withBaseImageName("postgres:17")
                .withStartupTimeout(Duration.ofSeconds(42))
                .build();
        try (PostgreSQLContainer container =
                new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
            ExposingPostgreSQLContainerFactory.INSTANCE.apply(container, command);
            assertStartupTimeoutSeconds(container, 42);
        }
    }

    @Test
    void applyCommandProperties_startupTimeoutSeconds_preferredOverDuration() throws Exception {
        CreatePostgreSQLContainerCommand command = CreatePostgreSQLContainerCommand.builder()
                .withBaseImageName("postgres:17")
                .withStartupTimeoutSeconds(90)
                .withStartupTimeout(Duration.ofSeconds(42))
                .build();
        try (PostgreSQLContainer container =
                new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
            ExposingPostgreSQLContainerFactory.INSTANCE.apply(container, command);
            assertStartupTimeoutSeconds(container, 90);
        }
    }

    @Test
    void applyCommandProperties_startupTimeoutSeconds_setsJdbcField() throws Exception {
        CreatePostgreSQLContainerCommand command = CreatePostgreSQLContainerCommand.builder()
                .withBaseImageName("postgres:17")
                .withStartupTimeoutSeconds(90)
                .build();
        try (PostgreSQLContainer container =
                new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
            ExposingPostgreSQLContainerFactory.INSTANCE.apply(container, command);
            assertStartupTimeoutSeconds(container, 90);
        }
    }

    @Test
    void postgreSQLContainerFactory_defaultBuilderIsStable() {
        assertThat(PostgreSQLContainerFactory.builder().build())
                .usingRecursiveComparison()
                .isEqualTo(PostgreSQLContainerFactory.builder().build());
    }

    @Test
    void testCreationOfNotPreinitializedContainer_SUCCESS() throws Exception {
        try (PostgreSQLContainer container = PostgreSQLContainerFactory.createPostgreSQLContainer(
                CreatePostgreSQLContainerCommand.builder()
                        .withBaseImageName("postgres:17")
                        .withPreInitialized(false)
                        .withDbName("testdb")
                        .withUsername("user")
                        .withPassword("password")
                        .withCmdParameters(cmdParameters)
                        .withLogConsumer(new Slf4jLogConsumer(log))
                        .build())) {
            TimedContainerStart.start(container);
            assertDatabaseEmpty(container);
        }
    }

    @Test
    void testCreationOfPreinitializedContainer_SUCCESS() throws Exception {
        String imageName = null;
        CreatePostgreSQLContainerCommand command = CreatePostgreSQLContainerCommand.builder()
                .withBaseImageName("postgres:17")
                .withInitScripts(List.of(
                        "postgresql/init.tables.expected.sql", "postgresql/init.data.expected.sql"))
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .withCmdParameters(cmdParameters)
                .withLogConsumer(new Slf4jLogConsumer(log))
                .build();
        try (PostgreSQLContainer container =
                PostgreSQLContainerFactory.createPostgreSQLContainer(command)) {
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
        List<String> initScripts = List.of(
                "postgresql/init.tables.expected100.sql", "postgresql/init.data.expected100.sql");
        List<String> imageNames = new ArrayList<>();
        try {
            try (PostgreSQLContainer vanillaContainer =
                    createVanillaPostgreSQLContainer(initScripts, false)) {
                TimedContainerStart.start(
                        vanillaContainer, "vanilla PostgreSQLContainer (init scripts at startup)");
                assertTablesAndData100Created(vanillaContainer);
            }
            try (PostgreSQLContainer vanillaTmpfsContainer =
                    createVanillaPostgreSQLContainer(initScripts, true)) {
                TimedContainerStart.start(
                        vanillaTmpfsContainer,
                        "vanilla PostgreSQLContainer with tmpfs /var/lib/postgresql/data (init scripts at startup)");
                assertTablesAndData100Created(vanillaTmpfsContainer);
            }
            try (PostgreSQLContainer factoryContainer =
                    PostgreSQLContainerFactory.createPostgreSQLContainer(
                            base100TablesCommand().build())) {
                TimedContainerStart.start(
                        factoryContainer,
                        "preinitialized PostgreSQL with tmpfs (init scripts at startup)");
                imageNames.add(factoryContainer.getDockerImageName());
                assertTablesAndData100Created(factoryContainer);
            }
            try (PostgreSQLContainer vanillaEmptyContainer =
                    createVanillaPostgreSQLContainer(List.of(), false)) {
                TimedContainerStart.start(
                        vanillaEmptyContainer, "vanilla PostgreSQLContainer (no init scripts)");
                assertDatabaseEmpty(vanillaEmptyContainer);
            }
            try (PostgreSQLContainer vanillaEmptyTmpfsContainer =
                    createVanillaPostgreSQLContainer(List.of(), true)) {
                TimedContainerStart.start(
                        vanillaEmptyTmpfsContainer,
                        "vanilla PostgreSQLContainer with tmpfs /var/lib/postgresql/data (no init scripts)");
                assertDatabaseEmpty(vanillaEmptyTmpfsContainer);
            }
            try (PostgreSQLContainer factoryEmptyContainer =
                    PostgreSQLContainerFactory.createPostgreSQLContainer(
                            baseEmptyCommand().build())) {
                TimedContainerStart.start(
                        factoryEmptyContainer,
                        "preinitialized PostgreSQL with tmpfs (no init scripts)");
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
        CreatePostgreSQLContainerCommand command = CreatePostgreSQLContainerCommand.builder()
                .withBaseImageName("postgres:17")
                .withTmpFsFilesystems(Collections.emptyList())
                .withInitScripts(List.of(
                        "postgresql/init.tables.expected.sql", "postgresql/init.data.expected.sql"))
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .withCmdParameters(cmdParameters)
                .build();
        try (PostgreSQLContainer container =
                PostgreSQLContainerFactory.createPostgreSQLContainer(command)) {
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

    @ParameterizedTest(name = "postgres:{0} non-preinitialized")
    @ValueSource(ints = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18})
    void waitStrategy_notPreinitializedContainer_success(int majorVersion) throws Exception {
        String imageName = "postgres:" + majorVersion;
        try (PostgreSQLContainer container = PostgreSQLContainerFactory.createPostgreSQLContainer(
                CreatePostgreSQLContainerCommand.builder()
                        .withBaseImageName(imageName)
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

    @ParameterizedTest(name = "postgres:{0} preinitialized")
    @ValueSource(ints = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18})
    void waitStrategy_preinitializedContainer_success(int majorVersion) throws Exception {
        String imageName = "postgres:" + majorVersion;
        String endImageName = null;
        CreatePostgreSQLContainerCommand command = CreatePostgreSQLContainerCommand.builder()
                .withBaseImageName(imageName)
                .withInitScripts(List.of(
                        "postgresql/init.tables.expected.sql", "postgresql/init.data.expected.sql"))
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .withCmdParameters(cmdParameters)
                .build();
        try (PostgreSQLContainer container =
                PostgreSQLContainerFactory.createPostgreSQLContainer(command)) {
            TimedContainerStart.start(container);
            endImageName = container.getDockerImageName();
            assertTablesAndDataCreated(container);
        } finally {
            if (endImageName != null) {
                DockerClient dockerClient = DockerClientFactory.lazyClient();
                dockerClient.removeImageCmd(endImageName).exec();
            }
        }
    }

    private CreatePostgreSQLContainerCommand.CreatePostgreSQLContainerCommandBuilder<?, ?>
            base100TablesCommand() {
        return basePreinitializedCommand()
                .withInitScripts(List.of(
                        "postgresql/init.tables.expected100.sql",
                        "postgresql/init.data.expected100.sql"));
    }

    private CreatePostgreSQLContainerCommand.CreatePostgreSQLContainerCommandBuilder<?, ?>
            baseEmptyCommand() {
        return basePreinitializedCommand();
    }

    private CreatePostgreSQLContainerCommand.CreatePostgreSQLContainerCommandBuilder<?, ?>
            basePreinitializedCommand() {
        return CreatePostgreSQLContainerCommand.builder()
                .withBaseImageName("postgres:17")
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .withCmdParameters(cmdParameters);
    }

    private PostgreSQLContainer<?> createVanillaPostgreSQLContainer(
            List<String> initScripts, boolean withTmpfs) {
        PostgreSQLContainer<?> container =
                new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));
        container.withDatabaseName("testdb").withUsername("user").withPassword("password");
        if (!initScripts.isEmpty()) {
            container.withInitScripts(initScripts.toArray(new String[0]));
        }
        if (!cmdParameters.isEmpty()) {
            container.withCreateContainerCmdModifier(
                    c -> c.withCmd(cmdParameters.toArray(new String[0])));
        }
        if (withTmpfs) {
            container.withTmpFs(Map.of("/var/lib/postgresql/data", "rw"));
        }
        return container;
    }

    private static void assertDatabaseEmpty(PostgreSQLContainer container) throws Exception {
        if (!databaseExists(container, "testdb")) {
            return;
        }
        try (Connection connection = container.createConnection("");
                Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'test'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("database should not contain init script table test")
                        .isZero();
            }
        }
    }

    private static void assertStartupTimeoutSeconds(
            PostgreSQLContainer container, int expectedSeconds) throws Exception {
        Field field = JdbcDatabaseContainer.class.getDeclaredField("startupTimeoutSeconds");
        field.setAccessible(true);
        assertThat(field.get(container)).isEqualTo(expectedSeconds);
    }

    private static void assertTablesAndData100Created(PostgreSQLContainer container)
            throws Exception {
        try (Connection connection = container.createConnection("");
                Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'table_%'")) {
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

    private static void assertTablesAndDataCreated(PostgreSQLContainer container) throws Exception {
        try (Connection connection = container.createConnection("");
                Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'test'")) {
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

    private static boolean databaseExists(PostgreSQLContainer container, String dbName)
            throws Exception {
        String adminJdbcUrl = container.getJdbcUrl().replaceFirst("/testdb", "/postgres");
        try (Connection connection = DriverManager.getConnection(
                        adminJdbcUrl, container.getUsername(), container.getPassword());
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM pg_database WHERE datname = '" + dbName + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    @SuperBuilder(toBuilder = true, setterPrefix = "with")
    private static final class ExposingPostgreSQLContainerFactory
            extends JdbcContainerFactory<CreatePostgreSQLContainerCommand, PostgreSQLContainer<?>> {

        private static final ExposingPostgreSQLContainerFactory INSTANCE =
                ExposingPostgreSQLContainerFactory.builder().build();

        @Builder.Default
        private final Function<DockerImageName, PostgreSQLContainer<?>> containerSupplier =
                PostgreSQLContainer::new;

        @Override
        protected Function<DockerImageName, PostgreSQLContainer<?>> resolveContainerSupplier() {
            return containerSupplier;
        }

        void apply(PostgreSQLContainer<?> container, CreatePostgreSQLContainerCommand command) {
            applyCommandProperties(container, command, false);
        }
    }
}
