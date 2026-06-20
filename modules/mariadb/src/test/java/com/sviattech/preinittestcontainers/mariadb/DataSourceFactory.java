package com.sviattech.preinittestcontainers.mariadb;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import org.mariadb.jdbc.MariaDbDataSource;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.sql.SQLException;

import javax.sql.DataSource;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DataSourceFactory {

    public static DataSource createDataSource(@NonNull JdbcDatabaseContainer<?> container)
            throws SQLException {
        if (!container.isRunning()) {
            throw new IllegalStateException("Testcontainer is not running!");
        }

        MariaDbDataSource source = new MariaDbDataSource();
        source.setUrl(container.getJdbcUrl());
        source.setUser(container.getUsername());
        source.setPassword(container.getPassword());
        return source;
    }
}
