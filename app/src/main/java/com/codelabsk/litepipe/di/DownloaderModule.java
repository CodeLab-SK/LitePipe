package com.codelabsk.litepipe.di;

import com.codelabsk.litepipe.downloader.core.DownloadPrefs;
import com.codelabsk.litepipe.downloader.core.LiteDownloader;
import com.codelabsk.litepipe.downloader.core.StreamDownloader;
import com.codelabsk.litepipe.downloader.core.impl.LiteDownloaderImpl;
import com.codelabsk.litepipe.downloader.core.impl.StreamDownloaderImpl;
import com.tencent.mmkv.MMKV;

import javax.inject.Singleton;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public abstract class DownloaderModule {

	@Binds
	@Singleton
	public abstract LiteDownloader bindLiteDownloader(LiteDownloaderImpl impl);

	@Binds
	@Singleton
	public abstract StreamDownloader bindStreamDownloader(StreamDownloaderImpl impl);

	@Provides
	@Singleton
	public static DownloadPrefs provideDownloadPrefs(MMKV mmkv) {
		return new DownloadPrefs(mmkv);
	}

}
