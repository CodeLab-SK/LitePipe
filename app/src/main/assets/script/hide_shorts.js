(function () {
    const prefs = JSON.parse(lite.getPreferences());
    if (!prefs.hide_shorts) return;
    if (window.hideShortsInjected) return;

    function hideElement(element) {
        if (!element) return;
        const closestSelectors = [
            'ytm-reel-shelf-renderer',
            'ytm-video-with-context-renderer',
            'ytm-rich-section-renderer',
            'grid-shelf-view-model',
            'ytm-shelf-renderer'
        ];
        
        for (const selector of closestSelectors) {
            const container = element.closest(selector);
            if (container && container.style.display !== 'none') {
                container.style.display = 'none';
                break;
            }
        }
    }

    document.addEventListener('animationstart', (e) => {
        if (e.animationName === 'nodeInserted') {
            const element = e.target;
            
            if (element.matches('ytm-shorts-lockup-view-model')) {
                hideElement(element);
            } else if (element.matches('.pivot-bar-item-tab.pivot-shorts')) {
                hideElement(element);
            } else if (element.matches('a[href^="/shorts/"]')) {
                hideElement(element);
            } else if (element.matches('grid-shelf-view-model')) {
                hideElement(element);
            }
        }
    }, false);

    const shortsElements = [
        'ytm-shorts-lockup-view-model',
        '.pivot-bar-item-tab.pivot-shorts',
        'a[href^="/shorts/"]',
        'grid-shelf-view-model'
    ];
    
    shortsElements.forEach(selector => {
        document.querySelectorAll(selector).forEach(element => {
            hideElement(element);
        });
    });

    window.hideShortsInjected = true;
})();
