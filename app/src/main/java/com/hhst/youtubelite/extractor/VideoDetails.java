package com.hhst.youtubelite.extractor;

import org.schabi.newpipe.extractor.stream.StreamSegment;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Video metadata extracted from YouTube.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoDetails {
	private String id;
	private String title;
	private String author;
	private String description;
	private Long duration;
	private String thumbnailUrl;
	private long likeCount;
	private long dislikeCount;
	private Date uploadDate;
	private String uploaderUrl;
	private String uploaderAvatarUrl;
	private long viewCount;
	private List<StreamSegment> segments;

	public VideoDetails(String id, String title, String author, String description, Long duration, String thumbnailUrl, long likeCount, long dislikeCount, Date uploadDate, String uploaderUrl, String uploaderAvatarUrl, long viewCount) {
		this.id = id;
		this.title = title;
		this.author = author;
		this.description = description;
		this.duration = duration;
		this.thumbnailUrl = thumbnailUrl;
		this.likeCount = likeCount;
		this.dislikeCount = dislikeCount;
		this.uploadDate = uploadDate;
		this.uploaderUrl = uploaderUrl;
		this.uploaderAvatarUrl = uploaderAvatarUrl;
		this.viewCount = viewCount;
	}

	public String getThumbnail() {
		return thumbnailUrl;
	}

	public void setThumbnail(String thumbnailUrl) {
		this.thumbnailUrl = thumbnailUrl;
	}

	public String getUploaderAvatar() {
		return uploaderAvatarUrl;
	}

	public void setUploaderAvatar(String uploaderAvatarUrl) {
		this.uploaderAvatarUrl = uploaderAvatarUrl;
	}
}
