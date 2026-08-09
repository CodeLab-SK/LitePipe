package com.codelabsk.litepipe.extension;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExtensionTreeTest {
	@Test
	public void defaultExtensionTree_containsInAppMiniPlayerToggle() {
		assertTrue(Extension.defaultExtensionTree()
						.stream()
						.filter(group -> group.children() != null)
						.flatMap(group -> group.children().stream())
						.anyMatch(item -> Constant.ENABLE_IN_APP_MINI_PLAYER.equals(item.key())));
	}

	@Test
	public void defaultPreferences_enableInAppMiniPlayerByDefault() {
		Object value = Constant.DEFAULT_PREFERENCES.get(Constant.ENABLE_IN_APP_MINI_PLAYER);
		assertTrue(value instanceof Boolean && (Boolean) value);
	}
}
