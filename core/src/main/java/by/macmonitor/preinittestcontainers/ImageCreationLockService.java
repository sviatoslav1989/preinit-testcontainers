package by.macmonitor.preinittestcontainers;

import java.util.function.Consumer;

public interface ImageCreationLockService {
    void withLock(String imageName, ImageCreationLockOption lockOption, Consumer<String> action);
}
