package com.sviattech.examples.springbootmysql;

import com.sviattech.preinittestcontainers.mysql.CreateMySQLContainerCommand;
import com.sviattech.preinittestcontainers.mysql.MySQLContainerFactory;
import com.sviattech.preinittestcontainers.support.TimedContainerStart;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.MySQLContainer;

import java.util.List;

import javax.sql.DataSource;

@TestConfiguration
public class PreinitMySQLTestConfiguration {

    private static final List<String> CMD_PARAMETERS = List.of(
            "--character-set-server=utf8",
            "--collation-server=utf8_general_ci",
            "--max_connections=150",
            "--innodb-buffer-pool-size=128M",
            "--innodb-doublewrite=0",
            "--innodb-flush-log-at-trx-commit=0",
            "--log_bin_trust_function_creators=1",
            "--sync-binlog=0");

    private static final class Holder {
        static MySQLContainer container;
        static DataSource dataSource;

        static synchronized MySQLContainer getContainer(
                String username, String password, String databaseName, String baseImage) {
            if (container == null) {
                container = createAndStart(username, password, databaseName, baseImage);
                dataSource = createDataSource(container, username, password);
            }
            return container;
        }

        static synchronized DataSource getDataSource() {
            return dataSource;
        }
    }

    @Bean(destroyMethod = "stop")
    MySQLContainer mysqlContainer(
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${preinit.mysql.database-name}") String databaseName,
            @Value("${preinit.mysql.base-image}") String baseImage) {
        return Holder.getContainer(username, password, databaseName, baseImage);
    }

    @Bean(destroyMethod = "close")
    @Primary
    DataSource dataSource(MySQLContainer mysqlContainer) {
        return Holder.getDataSource();
    }

    private static MySQLContainer createAndStart(
            String username, String password, String databaseName, String baseImage) {
        CreateMySQLContainerCommand command = CreateMySQLContainerCommand.builder()
                .withBaseImageName(baseImage)
                .withInitScripts(List.of("mysql/init.greeting.expected.sql"))
                .withDbName(databaseName)
                .withUsername(username)
                .withPassword(password)
                .withCmdParameters(CMD_PARAMETERS)
                .build();
        MySQLContainer container = MySQLContainerFactory.createMySQLContainer(command);
        TimedContainerStart.start(container);
        return container;
    }

    private static DataSource createDataSource(
            MySQLContainer container, String username, String password) {
        if (!container.isRunning()) {
            throw new IllegalStateException("Testcontainer is not running!");
        }
        HikariDataSource source = new HikariDataSource();
        source.setJdbcUrl(container.getJdbcUrl());
        source.setUsername(username);
        source.setPassword(password);
        return source;
    }
}
