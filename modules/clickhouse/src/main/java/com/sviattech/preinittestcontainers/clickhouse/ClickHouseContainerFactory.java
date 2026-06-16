package com.sviattech.preinittestcontainers.clickhouse;

import com.sviattech.preinittestcontainers.JdbcContainerFactory;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.function.Function;

/**
 * {@link com.sviattech.preinittestcontainers.ContainerFactory} for ClickHouse via
 * {@link org.testcontainers.clickhouse.ClickHouseContainer}.
 */
@SuperBuilder(toBuilder = true, setterPrefix = "with")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ClickHouseContainerFactory
        extends JdbcContainerFactory<CreateClickHouseContainerCommand, ClickHouseContainer> {

    private static final ClickHouseContainerFactory INSTANCE = new ClickHouseContainerFactory();

    @Builder.Default
    private final Function<DockerImageName, ClickHouseContainer> containerSupplier =
            ClickHouseContainer::new;

    @Override
    protected Function<DockerImageName, ClickHouseContainer> resolveContainerSupplier() {
        return containerSupplier;
    }

    public static ClickHouseContainer createClickHouseContainer(
            CreateClickHouseContainerCommand command) {
        return INSTANCE.create(command);
    }
}
