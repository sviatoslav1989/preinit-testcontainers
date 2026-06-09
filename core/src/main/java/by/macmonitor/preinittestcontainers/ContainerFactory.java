package by.macmonitor.preinittestcontainers;

import org.testcontainers.containers.Container;

public interface ContainerFactory<C extends CreateContainerCommand, T extends Container<?>> {
    T create(C command);
}
