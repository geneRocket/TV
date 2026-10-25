package com.fongmi.android.tv.api.config;

import java.util.function.Consumer;

/** A load has one commit boundary, after all parsing/merging has completed successfully. */
final class ConfigLoadTransaction {
    interface Build<T> { T build() throws Throwable; }
    private ConfigLoadTransaction() {}

    static <T> T execute(Build<T> build, Consumer<T> publish) throws Throwable {
        ConfigLoadTask.check();
        T candidate = build.build();
        ConfigLoadTask.commit(() -> publish.accept(candidate));
        return candidate;
    }
}
