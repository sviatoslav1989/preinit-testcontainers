package by.macmonitor.preinittestcontainers.mysql;

import by.macmonitor.preinittestcontainers.JdbcContainerFactory;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.function.Function;

@SuperBuilder(toBuilder = true, setterPrefix = "with")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class MySQLContainerFactory
        extends JdbcContainerFactory<CreateMySQLContainerCommand, MySQLContainer<?>> {

    private static final MySQLContainerFactory INSTANCE = new MySQLContainerFactory();

    @Builder.Default
    private final Function<DockerImageName, MySQLContainer<?>> containerSupplier =
            MySQLContainer::new;

    @Override
    protected Function<DockerImageName, MySQLContainer<?>> resolveContainerSupplier() {
        return containerSupplier;
    }

    public static MySQLContainer<?> createMySQLContainer(CreateMySQLContainerCommand command) {
        return INSTANCE.create(command);
    }
}
