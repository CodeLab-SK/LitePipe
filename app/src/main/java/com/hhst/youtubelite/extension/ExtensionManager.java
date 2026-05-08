package com.hhst.youtubelite.extension;

import com.tencent.mmkv.MMKV;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ExtensionManager {
	private static final String KEY_VERSION = "preferences:version";

	private final MMKV mmkv;

	@Inject
	public ExtensionManager(MMKV mmkv) {
		this.mmkv = mmkv;
		initializeDefaultPreferences();
	}

	private void initializeDefaultPreferences() {
		for (Map.Entry<String, Object> entry : Constant.DEFAULT_PREFERENCES.entrySet()) {
			String key = prefKey(entry.getKey());
			if (!mmkv.contains(key)) {
				encodeInternal(key, entry.getValue());
			}
		}
	}

	private String prefKey(String key) {
		return "preferences:" + key;
	}

	public void setEnabled(String key, boolean enable) {
		String pref = prefKey(key);
		boolean changed = !mmkv.contains(pref) || mmkv.decodeBool(pref, isDefaultTrue(key)) != enable;
		mmkv.encode(pref, enable);
		if (changed) {
			bumpVersion();
		}
	}

	public boolean isEnabled(String key) {
		return mmkv.decodeBool(prefKey(key), isDefaultTrue(key));
	}

	private boolean isDefaultTrue(String key) {
		Object def = Constant.DEFAULT_PREFERENCES.get(key);
		return def instanceof Boolean && (Boolean) def;
	}

	public void resetToDefault() {
		boolean changed = false;
		for (Map.Entry<String, Object> entry : Constant.DEFAULT_PREFERENCES.entrySet()) {
			String key = prefKey(entry.getKey());
			Object value = entry.getValue();
			if (!mmkv.contains(key) || !value.equals(decodeInternal(key, value))) {
				changed = true;
			}
			encodeInternal(key, value);
		}
		if (changed) {
			bumpVersion();
		}
	}

	public Map<String, Object> getAllPreferences() {
		Map<String, Object> allPreferences = new HashMap<>();
		for (String key : Constant.DEFAULT_PREFERENCES.keySet()) {
			Object value = decodeInternal(prefKey(key), Constant.DEFAULT_PREFERENCES.get(key));
			allPreferences.put(key, value);
		}
		return allPreferences;
	}

	private void encodeInternal(String key, Object value) {
		if (value instanceof Boolean) mmkv.encode(key, (Boolean) value);
		else if (value instanceof Integer) mmkv.encode(key, (Integer) value);
		else if (value instanceof String) mmkv.encode(key, (String) value);
	}

	private Object decodeInternal(String key, Object defaultValue) {
		if (defaultValue instanceof Boolean) return mmkv.decodeBool(key, (Boolean) defaultValue);
		if (defaultValue instanceof Integer) return mmkv.decodeInt(key, (Integer) defaultValue);
		if (defaultValue instanceof String) return mmkv.decodeString(key, (String) defaultValue);
		return null;
	}

	public long version() {
		return mmkv.decodeLong(KEY_VERSION, 0L);
	}

	private void bumpVersion() {
		mmkv.encode(KEY_VERSION, version() + 1L);
	}

}
