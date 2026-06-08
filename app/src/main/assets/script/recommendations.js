(function() {
    window.extractRecommendations = function() {
        try {
            const videos = [];
            const items = document.querySelectorAll('ytm-video-with-context-renderer, ytm-compact-video-renderer, ytm-compact-show-renderer, ytm-rich-item-renderer, ytm-item-section-renderer ytm-video-with-context-renderer');

            items.forEach(item => {
                try {
                    // Extract Video ID
                    const anchor = item.querySelector('a[href*="/watch?v="], a[href*="/shorts/"], a.media-item-thumbnail-container, a.compact-media-item-image');
                    if (!anchor) return;

                    const url = anchor.getAttribute('href') || anchor.href;
                    if (!url) return;

                    const videoIdMatch = url.match(/[?&]v=([^&]+)/) || url.match(/shorts\/([^?]+)/);
                    const videoId = videoIdMatch ? videoIdMatch[1] : null;
                    if (!videoId) return;

                    // Title extraction
                    const titleEl = item.querySelector('.media-item-headline, .compact-media-item-headline, .video-with-context-renderer-headline, h3, h4');
                    const title = titleEl ? titleEl.textContent.trim() : '';
                    if (!title) return;

                    // Channel name extraction
                    let channelName = '';
                    const bylineRenderer = item.querySelector('ytm-badge-and-byline-renderer');
                    if (bylineRenderer) {
                        const bylines = bylineRenderer.querySelectorAll('.YtmBadgeAndBylineRendererItemByline, .ytm-badge-and-byline-item-byline');
                        if (bylines.length > 0) channelName = bylines[0].textContent.trim();
                    }

                    if (!channelName) {
                        const channelEl = item.querySelector(
                            '.compact-media-item-byline, ' +
                            '.video-with-context-renderer-channel-name, ' +
                            '.compact-media-item-subtitle, ' +
                            '.ytm-badge-and-byline-item-byline, ' +
                            '.media-item-subtitle'
                        );
                        if (channelEl) {
                            const fullText = channelEl.textContent.trim();
                            channelName = fullText.split('•')[0].split('·')[0].trim();
                        }
                    }

                    // Thumbnail extraction
                    const img = item.querySelector('img.ytCoreImageHost, img.video-thumbnail-img, img');
                    let thumbUrl = '';
                    if (img) {
                        thumbUrl = img.getAttribute('data-src') || img.src || img.getAttribute('src');
                        if (!thumbUrl || thumbUrl.startsWith('data:image') || thumbUrl.includes('clear.png')) {
                            thumbUrl = 'https://i.ytimg.com/vi/' + videoId + '/hqdefault.jpg';
                        }
                        if (thumbUrl.startsWith('//')) thumbUrl = 'https:' + thumbUrl;
                    } else {
                        thumbUrl = 'https://i.ytimg.com/vi/' + videoId + '/hqdefault.jpg';
                    }

                    videos.push({
                        videoId: videoId,
                        title: title,
                        channelName: channelName,
                        thumbnailUrl: thumbUrl
                    });
                } catch (e) {}
            });

            if (videos.length > 0) {
                // Deduplicate by videoId
                const unique = [];
                const seen = new Set();
                for (const v of videos) {
                    if (!seen.has(v.videoId)) {
                        seen.add(v.videoId);
                        unique.push(v);
                    }
                }
                if (window.lite && typeof window.lite.onRecommendationsExtracted === 'function') {
                    lite.onRecommendationsExtracted(JSON.stringify(unique));
                }
            }
        } catch (e) {
            console.error('extractRecommendations error:', e);
        }
    };
})();