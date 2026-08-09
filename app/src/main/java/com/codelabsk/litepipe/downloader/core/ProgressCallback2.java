package com.codelabsk.litepipe.downloader.core;

import java.io.File;

public interface ProgressCallback2 {

	void onProgress(int progress, long downloadedBytes, long totalBytes, long speedBytesPerSecond);

	void onComplete(File file);

	void onError(Exception error);

	void onCancel();

	void onMerge();

}