package com.codelabsk.litepipe.downloader.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.util.concurrent.CompletableFuture;

public interface StreamDownloader {
    CompletableFuture<File> download(@NonNull String key, @NonNull String url, @NonNull File output, @Nullable ProgressCallback callback);
    void setMaxThreadCount(int count);
    void pause(@NonNull String key);
    void resume(@NonNull String key);
    void cancel(@NonNull String key);
}