package com.hhst.youtubelite.player.model;

import androidx.annotation.NonNull;

import lombok.Setter;

@Setter
public class RecommendationVideo {
    private String videoId;
    private String title;
    private String thumbnailUrl;
    private String channelName;

    public RecommendationVideo() {}

    public RecommendationVideo(@NonNull String videoId, @NonNull String title, @NonNull String thumbnailUrl) {
        this.videoId = videoId;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
    }

    @NonNull
    public String getVideoId() {
        return videoId != null ? videoId : "";
    }

    @NonNull
    public String getTitle() {
        return title != null ? title : "";
    }

    @NonNull
    public String getThumbnailUrl() {
        return thumbnailUrl != null ? thumbnailUrl : "";
    }

    public String getChannelName() {
        return channelName != null ? channelName : "";
    }

}
