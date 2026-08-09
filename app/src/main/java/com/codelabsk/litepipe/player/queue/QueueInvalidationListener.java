package com.codelabsk.litepipe.player.queue;

@FunctionalInterface
public interface QueueInvalidationListener {
	void onQueueInvalidated();
}
