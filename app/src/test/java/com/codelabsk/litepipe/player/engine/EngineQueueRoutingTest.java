package com.codelabsk.litepipe.player.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.codelabsk.litepipe.player.queue.QueueNav;

import org.junit.Test;

public class EngineQueueRoutingTest {
	@Test
	public void next_entersQueueAtHeadWhenCurrentVideoIsMissing() {
		final QueueNav availability =
						QueueNav.from(true, true, false, false, false);

		assertTrue(availability.usesQueueForNext());
	}

	@Test
	public void shuffle_usesWholeQueueWhenCurrentVideoIsMissing() {
		final QueueNav availability =
						QueueNav.from(true, true, false, false, false);

		assertTrue(availability.usesQueueForShuffle());
	}

	@Test
	public void previous_isBlockedWhenCurrentVideoIsMissingButQueueIsActive() {
		final QueueNav availability =
						QueueNav.from(true, true, false, false, false);

		assertFalse(availability.usesQueueForPrevious());
		assertFalse(availability.hasPreviousFallback());
	}

	@Test
	public void next_wrapsToQueueHeadWhenCurrentVideoIsAtQueueTail() {
		final QueueNav availability =
						QueueNav.from(true, true, true, false, false);

		assertTrue(availability.usesQueueForNext());
	}

	@Test
	public void previous_isBlockedWhenCurrentVideoIsAtQueueHead() {
		final QueueNav availability =
						QueueNav.from(true, true, true, true, false);

		assertFalse(availability.usesQueueForPrevious());
		assertFalse(availability.hasPreviousFallback());
	}

	@Test
	public void inactiveAvailability_isInactive() {
		final QueueNav availability =
						QueueNav.from(false, true, true, false, false);

		assertFalse(availability.usesQueueForNext());
		assertFalse(availability.usesQueueForShuffle());
		assertFalse(availability.usesQueueForPrevious());
	}

	@Test
	public void previous_keepsQueueRoutingBlockedWhenWatchPrevExists() {
		final QueueNav availability = watch();

		assertTrue(availability.isPreviousActionEnabled());
		assertFalse(availability.usesQueueForPrevious());
		assertTrue(availability.hasPreviousFallback());
	}

	private static QueueNav watch() {
		return QueueNav.from(true, true, true, true, false, true);
	}
}
