package com.sviattech.preinittestcontainers;

import lombok.RequiredArgsConstructor;

import org.testcontainers.containers.GenericContainer;

import java.util.function.Consumer;

/**
 * {@link PreInitStartCallback} backed by a stable key and a {@link Consumer} invoked once during
 * pre-initialized image build.
 *
 * <p>Prefer {@link PreInitStartCallback#of(String, Consumer)} over constructing this type directly.
 */
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
