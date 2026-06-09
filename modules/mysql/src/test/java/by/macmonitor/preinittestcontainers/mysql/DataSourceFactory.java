package by.macmonitor.preinittestcontainers.mysql;

import com.mysql.cj.jdbc.MysqlDataSource;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import org.testcontainers.containers.JdbcDatabaseContainer;

import javax.sql.DataSource;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DataSourceFactory {

    public static DataSource createDataSource(@NonNull JdbcDatabaseContainer<?> container) {
        if (!container.isRunning()) {
            throw new IllegalStateException("Testcontainer is not running!");
        }

        MysqlDataSource source = new MysqlDataSource();
        source.setUrl(container.getJdbcUrl());
        source.setUser(container.getUsername());
        source.setPassword(container.getPassword());
        return source;
    }
}
