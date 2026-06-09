package by.macmonitor.preinittestcontainers;

import com.clickhouse.client.config.ClickHouseDefaults;
import com.clickhouse.jdbc.ClickHouseDataSource;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;

import org.testcontainers.clickhouse.ClickHouseContainer;

import java.util.Properties;

import javax.sql.DataSource;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClickHouseDataSourceUtils {

    @SneakyThrows
    public static DataSource createDataSource(@NonNull ClickHouseContainer container) {
        Properties properties = new Properties();
        properties.put(ClickHouseDefaults.USER.getKey(), container.getUsername());
        properties.put(ClickHouseDefaults.PASSWORD.getKey(), container.getPassword());
        return new ClickHouseDataSource(container.getJdbcUrl(), properties);
    }
}
