package com.sviattech.examples.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import com.sviattech.preinittestcontainers.PreInitStartCallback;
import com.sviattech.preinittestcontainers.mariadb.CreateMariaDBContainerCommand;
import com.sviattech.preinittestcontainers.mariadb.MariaDBContainerFactory;
import com.sviattech.preinittestcontainers.support.TimedContainerStart;

import com.github.dockerjava.api.DockerClient;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mariadb.MariaDBContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

class MavenCentralMariaDBPreinitCallbackTest {

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

    @Test
    void preinitializedContainerAppliesCallbackSeed() throws Exception {
        String imageName = null;
        CreateMariaDBContainerCommand command = CreateMariaDBContainerCommand.builder()
                .withBaseImageName("mariadb:11.4.5")
                .withDbName("testdb")
                .withUsername("user")
                .withPassword("password")
                .withCmdParameters(CMD_PARAMETERS)
                .withAfterPreInitStartCallback(PreInitStartCallback.of(
                        "mariadb-callback-seed-v1",
                        container -> {
                            MariaDBContainer mariadb = (MariaDBContainer) container;
                            try (Connection connection = DriverManager.getConnection(
                                            mariadb.getJdbcUrl(), "user", "password");
                                    Statement statement = connection.createStatement()) {
                                statement.execute(
                                        "CREATE TABLE callback_seed (id INT PRIMARY KEY)");
                                statement.execute("INSERT INTO callback_seed (id) VALUES (42)");
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        }))
                .build();
        try (MariaDBContainer container = MariaDBContainerFactory.createMariaDBContainer(command)) {
            TimedContainerStart.start(container);
            imageName = container.getDockerImageName();
            try (Connection connection = DriverManager.getConnection(
                            container.getJdbcUrl(), "user", "password");
                    Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery("SELECT id FROM callback_seed")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(42);
            }
        } finally {
            if (imageName != null) {
                DockerClient dockerClient = DockerClientFactory.lazyClient();
                dockerClient.removeImageCmd(imageName).exec();
            }
        }
    }
}
