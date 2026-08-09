package com.codelabsk.litepipe.util;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for String operations.
 */
public final class StringUtils {

	private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");

	/**
	 * Parses the first occurrence of a number in a string.
	 * Useful for extracting height from resolution strings like "1080p".
	 */
	public static int parseHeight(@Nullable final String res) {
		if (res == null) return 0;
		final Matcher matcher = DIGIT_PATTERN.matcher(res);
		return matcher.find() ? Integer.parseInt(matcher.group()) : 0;
	}

	/**
	 * Capitalizes the first letter of a string.
	 */
	public static String capitalize(@Nullable String s) {
		if (s == null || s.isEmpty()) return s;
		return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
	}
}
