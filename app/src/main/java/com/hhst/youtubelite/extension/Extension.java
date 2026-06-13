package com.hhst.youtubelite.extension;

import com.hhst.youtubelite.R;
import java.util.List;

import static com.hhst.youtubelite.extension.Constant.*;

public record Extension(String key, int description, int helpText, List<Extension> children) {

    public Extension(String key, int description, List<Extension> children) {
        this(key, description, 0, children);
    }

    public static List<Extension> defaultExtensionTree() {
        return List.of(
            new Extension(null, R.string.general, List.of(
                new Extension(NAV_BAR_ORDER, R.string.custom_navigation, List.of(
                    new Extension(NAV_BAR_SHOW_HOME, R.string.nav_home, null),
                    new Extension(NAV_BAR_SHOW_MUSIC, R.string.nav_music, null),
                    new Extension(NAV_BAR_SHOW_SHORTS, R.string.nav_shorts, null),
                    new Extension(NAV_BAR_SHOW_SUBSCRIPTIONS, R.string.nav_subscriptions, null),
                    new Extension(NAV_BAR_SHOW_LIBRARY, R.string.nav_library, null),
                    new Extension(NAV_BAR_SHOW_DOWNLOADS, R.string.nav_downloads, null),
                    new Extension(NAV_BAR_SHOW_SETTINGS, R.string.nav_settings, null)
                )),

                new Extension(HIDE_NAV_BAR_LABELS, R.string.hide_nav_bar_labels, null),
                new Extension(ENABLE_DISPLAY_DISLIKES, R.string.return_dislike, null),
                new Extension(ENABLE_LONG_PRESS_MENU, R.string.long_press_context_menu, null),
                new Extension(SHOW_SEARCH_SUGGESTIONS, R.string.show_search_suggestions, null),
                new Extension(HIDE_COMMENTS, R.string.hide_comments, null)
            )),

            new Extension(null, R.string.shorts, List.of(
                    new Extension(SHORTS_BUTTONS, R.string.shorts_buttons, R.string.shorts_buttons_desc, List.of(
                            new Extension(SHORTS_SHOW_LIKE, R.string.shorts_like, null),
                            new Extension(SHORTS_SHOW_DISLIKE, R.string.shorts_dislike, null),
                            new Extension(SHORTS_SHOW_COMMENTS, R.string.shorts_comments, null),
                            new Extension(SHORTS_SHOW_SHARE, R.string.shorts_share, null),
                            new Extension(SHORTS_SHOW_SEARCH_SUGGESTION, R.string.shorts_search_suggestion, null),
                            new Extension(SHORTS_SHOW_PRODUCT_BANNER, R.string.shorts_product_banner, null)
                    )),
                    new Extension(SHORTS_GESTURE_2X, R.string.shorts_gesture_2x, null),
                    new Extension(SHOW_NAV_BAR_IN_SHORTS, R.string.show_nav_bar_in_shorts, null),
                    new Extension(HIDE_SHORTS, R.string.hide_shorts, null)
            )),

            new Extension(null, R.string.player, List.of(
                new Extension(ACTION_BAR_ORDER, R.string.custom_action_bar, R.string.custom_action_bar_desc, List.of(
                    new Extension(ACTION_BAR_SHOW_LIKE_DISLIKE, R.string.action_like_dislike, null),
                    new Extension(ACTION_BAR_SHOW_DOWNLOAD, R.string.action_download, null),
                    new Extension(ACTION_BAR_SHOW_QUEUE, R.string.add_to_queue, null),
                    new Extension(ENABLE_PIP, R.string.pip, null),
                    new Extension(ACTION_BAR_SHOW_CHAT, R.string.action_chat, null),
                    new Extension(ACTION_BAR_SHOW_SHARE, R.string.action_share, null),
                    new Extension(ACTION_BAR_SHOW_REMIX, R.string.action_remix, null),
                    new Extension(ACTION_BAR_SHOW_THANKS, R.string.action_thanks, null),
                    new Extension(ACTION_BAR_SHOW_CLIP, R.string.action_clip, null),
                    new Extension(ACTION_BAR_SHOW_SAVE, R.string.action_save, null),
                    new Extension(ACTION_BAR_SHOW_REPORT, R.string.action_report, null),
                    new Extension(ACTION_BAR_SHOW_ASK_AI, R.string.action_ask_ai, null)
                )),
                new Extension(CUSTOMIZE_PLAYER_BUTTONS, R.string.player_buttons, R.string.player_buttons_desc, List.of(
                    new Extension(PLAYER_SHOW_SPEED, R.string.speed, null),
                    new Extension(PLAYER_SHOW_QUALITY, R.string.quality, null),
                    new Extension(PLAYER_SHOW_SUBTITLES, R.string.subtitles, null),
                    new Extension(PLAYER_SHOW_SEGMENTS, R.string.segments, null),
                    new Extension(PLAYER_SHOW_LOOP, R.string.loop, null),
                    new Extension(PLAYER_SHOW_RELOAD, R.string.restart, null),
                    new Extension(PLAYER_SHOW_LOCK, R.string.lock_screen, null),
                    new Extension(PLAYER_SHOW_NEXT, R.string.action_next, null),
                    new Extension(PLAYER_SHOW_PREVIOUS, R.string.action_previous, null),
                    new Extension(PLAYER_SHOW_REMAINING_DURATION, R.string.show_remaining_duration, null)
                )),
                new Extension(PLAYER_GESTURES, R.string.player_gestures, List.of(
                    new Extension(PLAYER_GESTURE_BRIGHTNESS, R.string.brightness, null),
                    new Extension(PLAYER_GESTURE_VOLUME, R.string.volume, null),
                    new Extension(PLAYER_GESTURE_SEEK, R.string.Seek, null),
                    new Extension(PLAYER_GESTURE_2X, R.string.shorts_gesture_2x, null),
                    new Extension(PLAYER_GESTURE_FULLSCREEN_SWIPE, R.string.gesture_fullscreen_swipe, null),
                    new Extension(PLAYER_GESTURE_MINIPLAYER_SWIPE, R.string.gesture_miniplayer_swipe, null)
                )),
                new Extension(PLAYER_VOLUME_BOOSTER, R.string.volume_booster, null),
                new Extension(REMEMBER_LAST_POSITION, R.string.remember_last_position, null),
                new Extension(DEFAULT_QUALITY, R.string.default_quality, null),
                new Extension(DEFAULT_PLAYBACK_SPEED, R.string.default_playback_speed, null),
                new Extension(DOUBLE_TAP_SEEK_AMOUNT, R.string.double_tap_seek_amount, null),
                new Extension(REMEMBER_RESIZE_MODE, R.string.remember_resize_mode, null),
                new Extension(ENABLE_IN_APP_MINI_PLAYER, R.string.in_app_mini_player, null),
                new Extension(ENABLE_PIP, R.string.pip ,null),
                new Extension(ENABLE_BACKGROUND_PLAY, R.string.background_play, null)
            )),

            new Extension(null, R.string.sponsorblock, List.of(
                new Extension(SKIP_SPONSORS, R.string.skip_sponsored_segments, null),
                new Extension(SKIP_SELF_PROMO, R.string.skip_sponsors_selfpromo, null),
                new Extension(SKIP_POI_HIGHLIGHT, R.string.skip_sponsors_highlight, null)
            )),

            new Extension(null, R.string.download, List.of(
                new Extension(DOWNLOAD_LOCATION, R.string.download_location, null),
                new Extension(DOWNLOAD_MAX_CONCURRENT, R.string.max_concurrent_downloads, null),
                new Extension(DOWNLOAD_CLIPBOARD_DETECTION, R.string.download_clipboard_detection, null)
            ))
        );
    }
}
