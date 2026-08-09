package com.codelabsk.litepipe.player.engine;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;

@OptIn(markerClass = UnstableApi.class)
class PlayerLoadControl {
	private PlayerLoadControl() {
	}

	static DefaultLoadControl create() {
		return new DefaultLoadControl.Builder()
						.setBufferDurationsMs(
										30_000,
										60_000,
										2_500,
										5_000)
						.setPrioritizeTimeOverSizeThresholds(true)
						.build();
	}
}
