package by.macmonitor.preinittestcontainers;

import lombok.RequiredArgsConstructor;

import org.testcontainers.containers.GenericContainer;

import java.util.function.Consumer;

@RequiredArgsConstructor
public class SimplePreInitStartCallback implements PreInitStartCallback {

    private final String uniqueKey;

    private final Consumer<GenericContainer<?>> consumer;

    @Override
    public void run(GenericContainer<?> container) {
        consumer.accept(container);
    }

    @Override
    public String uniqueKey() {
        return uniqueKey;
    }
}
