package dev.tako.papersdelight.api.util;

public interface TaskHandle {
    void cancel();

    boolean isCancelled();
}
