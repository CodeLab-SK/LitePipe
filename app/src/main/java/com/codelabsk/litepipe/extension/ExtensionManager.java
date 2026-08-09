package com.codelabsk.litepipe.extension;

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
		boolean current = isEnabled(key);
		if (current != enable) {
			mmkv.encode(pref, enable);
			bumpVersion();
		}
	}

	public boolean isEnabled(String key) {
		Object defaultValue = Constant.DEFAULT_PREFERENCES.getOrDefault(key, false);
		return mmkv.decodeBool(prefKey(key), defaultValue instanceof Boolean ? (Boolean) defaultValue : false);
	}

	public int getInt(String key) {
		Object defaultValue = Constant.DEFAULT_PREFERENCES.getOrDefault(key, 0);
		return mmkv.decodeInt(prefKey(key), defaultValue instanceof Integer ? (Integer) defaultValue : 0);
	}

	public void setInt(String key, int value) {
		String pref = prefKey(key);
		if (getInt(key) != value) {
			mmkv.encode(pref, value);
			bumpVersion();
		}
	}

	public String getString(String key) {
		Object defaultValue = Constant.DEFAULT_PREFERENCES.getOrDefault(key, "");
		return mmkv.decodeString(prefKey(key), defaultValue instanceof String ? (String) defaultValue : "");
	}

	public void setString(String key, String value) {
		String pref = prefKey(key);
		if (!getString(key).equals(value)) {
			mmkv.encode(pref, value);
			bumpVersion();
		}
	}

	public void resetToDefault() {
		boolean changed = false;
		for (Map.Entry<String, Object> entry : Constant.DEFAULT_PREFERENCES.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			if (!value.equals(decodeInternal(prefKey(key), value))) {
				changed = true;
				encodeInternal(prefKey(key), value);
			}
		}
		if (changed) {
			bumpVersion();
		}
	}

	public Map<String, Object> getAllPreferences() {
		Map<String, Object> allPreferences = new HashMap<>();
		for (String key : Constant.DEFAULT_PREFERENCES.keySet()) {
			allPreferences.put(key, decodeInternal(prefKey(key), Constant.DEFAULT_PREFERENCES.get(key)));
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
