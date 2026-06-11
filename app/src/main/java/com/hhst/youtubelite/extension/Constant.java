package com.hhst.youtubelite.extension;

import java.util.Map;

public final class Constant {
    // Nav Bar
    public static final String NAV_BAR_SHOW_HOME = "nav_bar_show_home";
    public static final String NAV_BAR_SHOW_SHORTS = "nav_bar_show_shorts";
    public static final String NAV_BAR_SHOW_SUBSCRIPTIONS = "nav_bar_show_subscriptions";
    public static final String NAV_BAR_SHOW_LIBRARY = "nav_bar_show_library";
    public static final String NAV_BAR_SHOW_DOWNLOADS = "nav_bar_show_downloads";
    public static final String NAV_BAR_SHOW_SETTINGS = "nav_bar_show_settings";
    public static final String NAV_BAR_ORDER = "nav_bar_order";
    public static final String DEFAULT_NAV_BAR_ORDER = "home,shorts,subscriptions,library,downloads,settings";
    public static final String HIDE_NAV_BAR_LABELS = "hide_nav_bar_labels";

    // Action Bar
    public static final String ACTION_BAR_SHOW_LIKE_DISLIKE = "action_bar_show_like_dislike";
    public static final String ACTION_BAR_SHOW_DOWNLOAD = "action_bar_show_download";
    public static final String ACTION_BAR_SHOW_QUEUE = "action_bar_show_queue";
    public static final String ACTION_BAR_SHOW_CHAT = "action_bar_show_chat";
    public static final String ACTION_BAR_SHOW_SHARE = "action_bar_show_share";
    public static final String ACTION_BAR_SHOW_REMIX = "action_bar_show_remix";
    public static final String ACTION_BAR_SHOW_THANKS = "action_bar_show_thanks";
    public static final String ACTION_BAR_SHOW_CLIP = "action_bar_show_clip";
    public static final String ACTION_BAR_SHOW_SAVE = "action_bar_show_save";
    public static final String ACTION_BAR_SHOW_REPORT = "action_bar_show_report";
    public static final String ACTION_BAR_SHOW_ASK_AI = "action_bar_show_ask_ai";
    public static final String ACTION_BAR_ORDER = "action_bar_order";
    public static final String DEFAULT_ACTION_BAR_ORDER = "download,queue,pip,share,save";

    // General
    public static final String ENABLE_DISPLAY_DISLIKES = "enable_display_dislikes";
    public static final String ENABLE_LONG_PRESS_MENU = "enable_long_press_menu";
    public static final String SHOW_SEARCH_SUGGESTIONS = "show_search_suggestions";
    public static final String HIDE_COMMENTS = "hide_comments";

    // Shorts
    public static final String SHORTS_GESTURE_2X = "shorts_gesture_2x";
    public static final String SHOW_NAV_BAR_IN_SHORTS = "show_nav_bar_in_shorts";
    public static final String HIDE_SHORTS = "hide_shorts";
    public static final String SHORTS_BUTTONS = "shorts_buttons";
    public static final String SHORTS_SHOW_LIKE = "shorts_show_like";
    public static final String SHORTS_SHOW_DISLIKE = "shorts_show_dislike";
    public static final String SHORTS_SHOW_COMMENTS = "shorts_show_comments";
    public static final String SHORTS_SHOW_SHARE = "shorts_show_share";
    public static final String SHORTS_SHOW_SEARCH_SUGGESTION = "shorts_show_search_suggestion";
    public static final String SHORTS_SHOW_PRODUCT_BANNER = "shorts_show_product_banner";

    // Player Buttons
    public static final String CUSTOMIZE_PLAYER_BUTTONS = "customize_player_buttons";
    public static final String PLAYER_SHOW_SPEED = "player_show_speed";
    public static final String PLAYER_SHOW_QUALITY = "player_show_quality";
    public static final String PLAYER_SHOW_SUBTITLES = "player_show_subtitles";
    public static final String PLAYER_SHOW_SEGMENTS = "player_show_segments";
    public static final String PLAYER_SHOW_LOOP = "player_show_loop";
    public static final String PLAYER_SHOW_RELOAD = "player_show_reload";
    public static final String PLAYER_SHOW_LOCK = "player_show_lock";
    public static final String PLAYER_SHOW_NEXT = "player_show_next";
    public static final String PLAYER_SHOW_PREVIOUS = "player_show_previous";
    public static final String PLAYER_SHOW_REMAINING_DURATION = "player_show_remaining_duration";

    // Player Gestures
    public static final String PLAYER_GESTURES = "player_gestures";
    public static final String PLAYER_GESTURE_BRIGHTNESS = "player_gesture_brightness";
    public static final String PLAYER_GESTURE_VOLUME = "player_gesture_volume";
    public static final String PLAYER_GESTURE_SEEK = "player_gesture_seek";
    public static final String PLAYER_GESTURE_2X = "player_gesture_2x";
    public static final String PLAYER_GESTURE_FULLSCREEN_SWIPE = "player_gesture_fullscreen_swipe";
    public static final String PLAYER_GESTURE_MINIPLAYER_SWIPE = "player_gesture_miniplayer_swipe";
    public static final String PLAYER_VOLUME_BOOSTER = "player_volume_booster";

    // Player Misc
    public static final String REMEMBER_LAST_POSITION = "remember_last_position";
    public static final String DEFAULT_QUALITY = "default_quality";
    public static final String DEFAULT_PLAYBACK_SPEED = "default_playback_speed";
    public static final String DOUBLE_TAP_SEEK_AMOUNT = "double_tap_seek_amount";
    public static final String REMEMBER_RESIZE_MODE = "remember_resize_mode";
    public static final String ENABLE_IN_APP_MINI_PLAYER = "enable_in_app_mini_player";
    public static final String ENABLE_PIP = "enable_pip";
    public static final String ENABLE_BACKGROUND_PLAY = "enable_background_play";
    public static final String ENABLE_ORIENTATION_FULLSCREEN = "enable_orientation_fullscreen";

    // SponsorBlock
    public static final String SKIP_SPONSORS = "skip_sponsors";
    public static final String SKIP_SELF_PROMO = "skip_self_promo";
    public static final String SKIP_POI_HIGHLIGHT = "skip_poi_highlight";

    // Download
    public static final String DOWNLOAD_LOCATION = "download_location";
    public static final String DOWNLOAD_MAX_CONCURRENT = "download_max_concurrent";
    public static final String DOWNLOAD_CLIPBOARD_DETECTION = "download_clipboard_detection_enabled";

    public static final Map<String, Object> DEFAULT_PREFERENCES = Map.ofEntries(
            Map.entry(NAV_BAR_SHOW_HOME, true),
            Map.entry(NAV_BAR_SHOW_SHORTS, true),
            Map.entry(NAV_BAR_SHOW_SUBSCRIPTIONS, true),
            Map.entry(NAV_BAR_SHOW_LIBRARY, true),
            Map.entry(NAV_BAR_SHOW_DOWNLOADS, true),
            Map.entry(NAV_BAR_SHOW_SETTINGS, true),
            Map.entry(HIDE_NAV_BAR_LABELS, false),
            Map.entry(ACTION_BAR_SHOW_LIKE_DISLIKE, true),
            Map.entry(ACTION_BAR_SHOW_DOWNLOAD, true),
            Map.entry(ACTION_BAR_SHOW_QUEUE, true),
            Map.entry(ACTION_BAR_SHOW_CHAT, true),
            Map.entry(ACTION_BAR_SHOW_SHARE, true),
            Map.entry(ACTION_BAR_SHOW_REMIX, true),
            Map.entry(ACTION_BAR_SHOW_THANKS, true),
            Map.entry(ACTION_BAR_SHOW_CLIP, true),
            Map.entry(ACTION_BAR_SHOW_SAVE, true),
            Map.entry(ACTION_BAR_SHOW_REPORT, true),
            Map.entry(ACTION_BAR_SHOW_ASK_AI, true),
            Map.entry(ENABLE_DISPLAY_DISLIKES, false),
            Map.entry(ENABLE_LONG_PRESS_MENU, true),
            Map.entry(SHOW_SEARCH_SUGGESTIONS, true),
            Map.entry(HIDE_COMMENTS, false),
            Map.entry(SHORTS_GESTURE_2X, true),
            Map.entry(SHOW_NAV_BAR_IN_SHORTS, true),
            Map.entry(HIDE_SHORTS, false),
            Map.entry(SHORTS_SHOW_LIKE, true),
            Map.entry(SHORTS_SHOW_DISLIKE, true),
            Map.entry(SHORTS_SHOW_COMMENTS, true),
            Map.entry(SHORTS_SHOW_SHARE, true),
            Map.entry(SHORTS_SHOW_SEARCH_SUGGESTION, true),
            Map.entry(SHORTS_SHOW_PRODUCT_BANNER, true),
            Map.entry(PLAYER_SHOW_SPEED, true),
            Map.entry(PLAYER_SHOW_QUALITY, true),
            Map.entry(PLAYER_SHOW_SUBTITLES, true),
            Map.entry(PLAYER_SHOW_SEGMENTS, true),
            Map.entry(PLAYER_SHOW_LOOP, true),
            Map.entry(PLAYER_SHOW_RELOAD, true),
            Map.entry(PLAYER_SHOW_LOCK, true),
            Map.entry(PLAYER_SHOW_NEXT, true),
            Map.entry(PLAYER_SHOW_PREVIOUS, true),
            Map.entry(PLAYER_SHOW_REMAINING_DURATION, true),
            Map.entry(PLAYER_GESTURES, true),
            Map.entry(PLAYER_GESTURE_BRIGHTNESS, true),
            Map.entry(PLAYER_GESTURE_VOLUME, true),
            Map.entry(PLAYER_GESTURE_SEEK, true),
            Map.entry(PLAYER_GESTURE_2X, true),
            Map.entry(PLAYER_GESTURE_FULLSCREEN_SWIPE, true),
            Map.entry(PLAYER_GESTURE_MINIPLAYER_SWIPE, true),
            Map.entry(PLAYER_VOLUME_BOOSTER, false),
            Map.entry(REMEMBER_LAST_POSITION, true),
            Map.entry(DEFAULT_QUALITY, "Auto"),
            Map.entry(DEFAULT_PLAYBACK_SPEED, "1.00x"),
            Map.entry(DOUBLE_TAP_SEEK_AMOUNT, "10s"),
            Map.entry(REMEMBER_RESIZE_MODE, false),
            Map.entry(ENABLE_IN_APP_MINI_PLAYER, true),
            Map.entry(ENABLE_PIP, true),
            Map.entry(ENABLE_BACKGROUND_PLAY, true),
            Map.entry(SKIP_SPONSORS, true),
            Map.entry(SKIP_SELF_PROMO, true),
            Map.entry(SKIP_POI_HIGHLIGHT, true),
            Map.entry(DOWNLOAD_MAX_CONCURRENT, 3),
            Map.entry(DOWNLOAD_CLIPBOARD_DETECTION, true)
    );
}
