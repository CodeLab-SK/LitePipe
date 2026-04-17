/**
 * @description basic script to YouTube page
 * @author halcyon,modified by Sahil Kumar
 * @version 1.1.2
 * @license MIT
 */
try {
    if (!window.injected) {
        const lite = typeof android !== 'undefined' ? android : {};
        const st = setTimeout.bind(window), si = setInterval.bind(window);
        const ct = clearTimeout.bind(window), ci = clearInterval.bind(window);
        const THRESHOLD = 800, map = new Map();
        let fp = null, token = 0;
        const nextFrame = () => fp || (fp = new Promise(r =>
            requestAnimationFrame(() => { fp = null; r(++token); })
        ));
        const wrap = (setFn) => (fn, delay, ...args) => {
            if (typeof fn !== "function") return setFn(fn, delay, ...args);
            const s = { on: 1, last: 0, frame: 0 };
            const run = async () => {
                if (!s.on) return;
                if (s.last && Date.now() - s.last < THRESHOLD) {
                    const t = await nextFrame();
                    if (!s.on || s.frame === t) return;
                    s.frame = t;
                }
                s.last = Date.now();
                fn(...args);
            };
            const id = setFn(run, delay);
            map.set(id, s);
            return id;
        };
        const clear = (clearFn) => (id) => {
            const s = map.get(id);
            if (s) s.on = 0, map.delete(id);
            clearFn(id);
        };
        window.setTimeout = wrap(st);
        window.setInterval = wrap(si);
        window.clearTimeout = clear(ct);
        window.clearInterval = clear(ci);
        const backoff = (stopOnTruthy = false) => {
            const delays = [16, 32, 64, 128, 256, 512, 1024, 2048];
            let tmr = null;
            let ver = 0;
            return (fn) => {
                clearTimeout(tmr);
                const v = ++ver;
                let k = 0;
                const run = () => {
                    if (v !== ver) return;
                    const done = fn() === true;
                    if (stopOnTruthy && done) return;
                    tmr = setTimeout(run, delays[k] ?? 2048);
                    k += 1;
                };
                run();
            };
        };
        const bindListener = (obj, type, fn, options) => {
            if (!obj?.addEventListener || !obj?.removeEventListener || typeof fn !== 'function') return;
            const capture = typeof options === 'boolean' ? options : !!options?.capture;
            obj.removeEventListener(type, fn, capture);
            obj.addEventListener(type, fn, options);
        };
        const getLocalizedText = (key) => {
            const languages = {
                'zh': { 'download': '下载', 'downloads': '下载', 'extension': 'LitePipe 设置', 'chat': '聊天室', 'about': '关于', 'pip': '画中画' },
                'zt': { 'download': '下載', 'downloads': '下載', 'extension': 'LitePipe 設置', 'chat': '聊天室', 'about': '關於', 'pip': '畫中畫' },
                'en': { 'download': 'Download', 'downloads': 'Downloads', 'extension': 'LitePipe Settings', 'chat': 'Chat', 'about': 'About', 'pip': 'PiP' },
                'ja': { 'download': 'ダウンロード', 'downloads': 'ダウンロード', 'extension': 'LitePipe 設定', 'chat': 'チャット', 'about': '詳細', 'pip': 'PiP' },
                'ko': { 'download': '다운로드', 'downloads': '다운로드', 'extension': 'LitePipe 플러그인', 'chat': '채팅', 'about': '정보', 'pip': 'PiP' },
                'fr': { 'download': 'Télécharger', 'downloads': 'Téléchargements', 'extension': 'Paramètres LitePipe', 'chat': 'Chat', 'about': 'À propos', 'pip': 'PiP' },
                'ru': { 'download': 'Скачать', 'downloads': 'Скачанные', 'extension': 'Настройки LitePipe', 'chat': 'Чат', 'about': 'О программе', 'pip': 'PiP' },
                'tr': { 'download': 'İndir', 'downloads': 'İndirmeler', 'extension': 'LitePipe Ayarları', 'chat': 'Sohbet', 'about': 'Hakkında', 'pip': 'PiP' },
            };
            const lang = (document.documentElement.lang || 'en').toLowerCase();
            let keyLang = lang.substring(0, 2);
            if (lang.includes('tw') || lang.includes('hk') || lang.includes('mo') || lang.includes('hant')) keyLang = 'zt';
            const entry = languages[keyLang] || languages['en'];
            return entry[key] || languages['en'][key] || key;
        };
        const getPageClass = (url) => {
            try {
                const u = new URL(url.toLowerCase());
                if (!u.hostname.includes('youtube.com')) return 'unknown';
                const segments = u.pathname.split('/').filter(Boolean);
                if (segments.length === 0) return 'home';
                const s0 = segments[0];
                if (s0 === 'shorts') return 'shorts';
                if (s0 === 'watch') return 'watch';
                if (s0 === 'channel') return 'channel';
                if (s0 === 'select_site') return 'select_site';
                if (s0.startsWith('@')) return '@';
                if (s0 === 'feed' && segments.length > 1) return segments[1];
                return segments.join('/');
            } catch (e) { return 'unknown'; }
        };
        const getVideoId = (url) => {
            try {
                const u = new URL(url, location.href);
                const queryVideoId = u.searchParams.get('v');
                if (queryVideoId) return queryVideoId;
                const segments = u.pathname.split('/').filter(Boolean);
                if (u.hostname.includes('youtu.be') && segments.length > 0) return segments[0];
                const shortsIndex = segments.indexOf('shorts');
                if (shortsIndex >= 0 && segments.length > shortsIndex + 1) return segments[shortsIndex + 1];
                return null;
            } catch (error) { return null; }
        };
        if (!window.originalFetch) {
            window.originalFetch = fetch;
            window.fetch = async (...args) => {
                const request = args[0] instanceof Request ? args[0] : new Request(...args);
                if (request.url.includes('youtubei/v1/player') && request.method === 'POST') {
                    try {
                        const cloned = request.clone();
                        const text = await cloned.text();
                        if (text) {
                            const json = JSON.parse(text);
                            const poToken = json?.serviceIntegrityDimensions?.poToken;
                            const visitorData = json?.context?.client?.visitorData;
                            if (poToken) lite.setPoToken(poToken, visitorData);
                        }
                    } catch (e) {}
                }
                return window.originalFetch(...args);
            };
        }
        const stripWatchList = (url) => {
            if (!url || !lite.isQueueEnabled?.()) return url;
            try {
                const u = new URL(url, location.href);
                if (getPageClass(u.toString()) === 'watch' && u.searchParams.has('list')) {
                    u.searchParams.delete('list');
                    return u.toString();
                }
            } catch (e) {}
            return url;
        };
        const handlePlayerVisibility = () => {
            if (getPageClass(location.href) === 'watch') lite.play(location.href);
            else lite.hidePlayer();
        };
        bindListener(window, 'onRefresh', () => location.reload());
        bindListener(window, 'onProgressChangeFinish', () => {
            lite.finishRefresh();
            backoff()(run);
        });
        bindListener(window, 'doUpdateVisitedHistory', () => backoff()(run));
        bindListener(window, 'popstate', () => { handlePlayerVisibility(); backoff()(run); });
        const originalPushState = history.pushState;
        history.pushState = function(data, title, url) {
            originalPushState.call(this, data, title, typeof url === 'string' ? stripWatchList(url) : url);
            handlePlayerVisibility();
            backoff()(run);
        };
        const originalReplaceState = history.replaceState;
        history.replaceState = function(data, title, url) {
            originalReplaceState.call(this, data, title, typeof url === 'string' ? stripWatchList(url) : url);
            handlePlayerVisibility();
            backoff()(run);
        };
        const ro = typeof ResizeObserver === 'function' ? new ResizeObserver(() => {
            const p = document.querySelector('#movie_player');
            if (p && getPageClass(location.href) === 'watch') lite.setPlayerHeight(p.clientHeight);
        }) : null;
        const parseTimestampSeconds = (rawValue) => {
            if (rawValue == null) return null;
            const normalized = `${rawValue}`.trim().toLowerCase();
            let totalSeconds = 0, matched = false;
            for (const part of normalized.matchAll(/(\d+)(h|m|s)/g)) {
                const amount = Number(part[1]); matched = true;
                if (part[2] === 'h') totalSeconds += amount * 3600;
                else if (part[2] === 'm') totalSeconds += amount * 60;
                else if (part[2] === 's') totalSeconds += amount;
            }
            return matched ? totalSeconds : ( /^\d+$/.test(normalized) ? Number(normalized) : null);
        };
        bindListener(document, 'click', (e) => {
            const link = e.target.closest('a');
            if (link && getPageClass(location.href) === 'watch') {
                const href = link.getAttribute('href') || link.href;
                if (href.includes('t=')) {
                    try {
                        const targetUrl = new URL(href, location.href);
                        if (getVideoId(location.href) === getVideoId(targetUrl.toString())) {
                            const sec = parseTimestampSeconds(targetUrl.searchParams.get('t') || targetUrl.searchParams.get('start'));
                            if (sec !== null && lite.seekLoadedVideo?.(targetUrl.toString(), sec * 1000)) {
                                e.preventDefault(); e.stopImmediatePropagation();
                            }
                        }
                    } catch (err) {}
                }
            }
        }, true);
        const stopNativePlayer = (target) => {
            if (!target) return;
            try {
                target.mute?.();
                target.setVolume?.(0);
                target.pauseVideo?.();
                target.stopVideo?.();
                const video = target.querySelector?.('video') || document.querySelector('#movie_player video') || document.querySelector('video');
                if (video) { 
                    video.muted = true; 
                    video.pause();
                    if (video.duration && video.duration > 0) {
                        video.currentTime = video.duration / 2;
                    } else {
                        const seekOnce = () => {
                            if (video.duration && video.duration > 0) video.currentTime = video.duration / 2;
                        };
                        video.addEventListener('loadedmetadata', seekOnce, {once: true});
                        video.addEventListener('durationchange', seekOnce, {once: true});
                    }
                }
            } catch(e) {}
        };
        bindListener(document, 'animationstart', (e) => {
            if (e.animationName !== 'nodeInserted') return;
            const target = e.target;
            const pc = getPageClass(location.href);
            if (target.id === 'movie_player') {
                if (pc === 'watch') {
                    stopNativePlayer(target);
                    bindListener(target, 'onStateChange', s => { if (s === 1) stopNativePlayer(target); });
                    setInterval(() => stopNativePlayer(target), 1000);
                }
                if (ro) { ro.disconnect(); ro.observe(target); }
            } else if (pc === 'watch') {
                if (target.id === 'player' || target.id === 'player-container-id') target.style.setProperty('display', 'none', 'important');
                else if (target.classList.contains('watch-below-the-player')) {
                    ['touchmove', 'touchend'].forEach(ev => bindListener(target, ev, evt => evt.stopPropagation(), { passive: false, capture: true }));
                }
            }
        }, true);
        bindListener(window, 'onSkipByOffset', (e) => {
            const offset = e.detail?.offset;
            if (offset === 1) {
                const nextBtn = document.querySelector('button.ytm-autonav-bar-button-renderer-next, .ytm-autonav-bar-renderer-button');
                if (nextBtn) {
                    nextBtn.click();
                } else {
                    const firstRec = document.querySelector('ytm-video-with-context-renderer a, ytm-compact-video-renderer a, ytm-rich-item-renderer a');
                    if (firstRec) firstRec.click();
                }
            } else if (offset === -1) {
                window.history.back();
            }
        });
        function setupCustomButton(base, id, textKey, iconD, onClick) {
            if (!base) return null;
            let btn = document.getElementById(id);
            if (!btn) {
                btn = base.cloneNode(true);
                btn.id = id;
                btn.removeAttribute('aria-label');
            }
            btn.onclick = (e) => { e.preventDefault(); e.stopPropagation(); onClick(); };
            btn.style.setProperty('display', 'flex', 'important');
            btn.style.setProperty('visibility', 'visible', 'important');
            btn.style.setProperty('opacity', '1', 'important');
            const text = btn.querySelector('.yt-spec-button-shape-next__button-text-content, [class*="text"]');
            if (text) {
                text.innerText = getLocalizedText(textKey);
                text.style.setProperty('display', 'block', 'important');
            }
            const iconContainer = btn.querySelector('yt-icon, .yt-spec-button-shape-next__icon, .icon, [class*="icon"]');
            if (iconContainer) {
                iconContainer.style.setProperty('display', 'flex', 'important');
                iconContainer.style.setProperty('align-items', 'center', 'important');
                iconContainer.style.setProperty('justify-content', 'center', 'important');
                iconContainer.innerHTML = `<svg viewBox="0 0 24 24" style="width:24px;height:24px;fill:currentColor;display:block;"><path d="${iconD}"></path></svg>`;
            }
            return btn;
        }
        function run() {
            const prefs = JSON.parse(lite.getPreferences());
            const pc = getPageClass(location.href);
            lite.setRefreshLayoutEnabled(['home', 'subscriptions', 'library', '@'].includes(pc));
            if (prefs.hide_shorts) {
                document.querySelectorAll('ytm-shorts-lockup-view-model, ytm-reel-shelf-renderer, ytm-pivot-bar-item-renderer[aria-label*="Shorts"], ytm-rich-section-renderer:has(ytm-reel-shelf-renderer), ytm-item-section-renderer:has(ytm-reel-shelf-renderer), ytm-rich-section-renderer:has(ytm-shorts-lockup-view-model), ytm-rich-item-renderer:has(ytm-shorts-lockup-view-model), ytm-shorts, ytm-reel-item-renderer').forEach(el => {
                    el.style.setProperty('display', 'none', 'important');
                });
                document.querySelectorAll('ytm-video-with-context-renderer, ytm-compact-video-renderer, ytm-rich-item-renderer, ytm-shelf-renderer').forEach(el => {
                    if (el.querySelector('a[href^="/shorts/"]')) {
                        el.style.setProperty('display', 'none', 'important');
                    }
                });
            }
            if (pc === 'watch') {
                const commentSelectors = [
                    'ytm-item-section-renderer[section-identifier="comments-entry-point"]',
                    'ytm-comments-entry-point-header-renderer',
                    '.comment-section',
                    '#comments',
                    '.watch-below-the-player > ytm-item-section-renderer:last-child'
                ];
                commentSelectors.forEach(sel => {
                    document.querySelectorAll(sel).forEach(el => {
                        if (prefs.hide_comments) {
                            el.style.setProperty('display', 'none', 'important');
                        } else {
                            el.style.removeProperty('display');
                        }
                    });
                });
                const np = document.querySelector('#movie_player');
                if (np) stopNativePlayer(np);
                document.querySelectorAll('video').forEach(v => { v.muted = true; v.pause(); });
                const ad = document.querySelector('.ad-showing video');
                if (ad) ad.currentTime = ad.duration;
                ['player', 'player-container-id'].forEach(id => {
                    const el = document.getElementById(id);
                    if (el) el.style.setProperty('display', 'none', 'important');
                });
                const header = document.querySelector('ytm-header-bar-renderer');
                if (header) header.style.setProperty('display', 'none', 'important');
                document.body.style.setProperty('padding-top', '0', 'important');
                const actionBar = document.querySelector('ytm-slim-video-action-bar-renderer');
                if (actionBar) {
                    const container = actionBar.querySelector('.slim-video-action-bar-actions, [class*="actions"], [class*="action-bar"], div[style*="display: flex"], div[style*="flex"]') || actionBar.querySelector('div') || actionBar.firstElementChild || actionBar;
                    const segmented = actionBar.querySelector('ytm-segmented-like-dislike-button-renderer');
                    const nativeButtons = Array.from(actionBar.querySelectorAll('.ytSpecButtonViewModelHost, ytm-toggle-button-renderer, ytm-button-renderer, .slim_video_action_bar_renderer_button, button, ytm-icon-button-renderer, .yt-spec-button-shape-next'))
                        .filter(b => !b.id.endsWith('Button') && (!segmented || !segmented.contains(b)));
                    let templateBtn = nativeButtons[0] || (segmented ? segmented.querySelector('button') : null);
                    if (templateBtn && container) {
                        const custom = {
                            download: setupCustomButton(templateBtn, 'downloadButton', 'download', 'M19,9h-4V3H9v6H5l7,7L19,9z M5,18v2h14v-2H5z', () => lite.download(location.href)),
                            pip: setupCustomButton(templateBtn, 'pipButton', 'pip', 'M19,11h-8v6h8V11z M21,3H3C1.9,3,1,3.9,1,5v14c0,1.1,0.9,2,2,2h18c1.1,0,2-0.9,2-2V5C23,3.9,22.1,3,21,3z M21,19.01H3V4.99h18V19.01z', () => lite.pip())
                        };
                        const orderKeys = (prefs.action_bar_order || 'like_dislike,download,pip,share,remix,thanks,clip,save,report').split(',');
                        const seen = new Set();
                        orderKeys.forEach(key => {
                            let target = null;
                            let show = false;
                            if (key === 'like_dislike' || key === 'like' || key === 'dislike') {
                                target = segmented;
                                show = prefs.action_bar_show_like_dislike !== false;
                            } else if (key === 'download') {
                                target = custom.download;
                                show = prefs.action_bar_show_download !== false;
                            } else if (key === 'pip') {
                                target = custom.pip;
                                show = prefs.enable_pip !== false;
                            } else {
                                target = nativeButtons.find(btn => {
                                    const l = (btn.getAttribute('aria-label') || btn.innerText || '').toLowerCase();
                                    return l.includes(key);
                                });
                                show = prefs['action_bar_show_' + key] !== false;
                            }
                            if (target && !seen.has(target)) {
                                seen.add(target);
                                if (show) {
                                    target.style.setProperty('display', 'flex', 'important');
                                    target.style.setProperty('visibility', 'visible', 'important');
                                    if (target === segmented) {
                                        const lBtn = target.querySelector('ytm-like-button-renderer, button[aria-label*="like" i], .yt-spec-button-shape-next--segmented-start');
                                        const dBtn = target.querySelector('ytm-dislike-button-renderer, button[aria-label*="dislike" i], .yt-spec-button-shape-next--segmented-end');
                                        if (lBtn) lBtn.style.setProperty('display', prefs.action_bar_show_like_dislike ? 'inline-flex' : 'none', 'important');
                                        if (dBtn) dBtn.style.setProperty('display', prefs.action_bar_show_like_dislike ? 'inline-flex' : 'none', 'important');
                                    }
                                    container.appendChild(target);
                                } else {
                                    target.style.setProperty('display', 'none', 'important');
                                }
                            }
                        });
                        Array.from(container.children).forEach(c => {
                            if (!seen.has(c)) c.style.setProperty('display', 'none', 'important');
                        });
                    }
                }
            } else {
                const header = document.querySelector('ytm-header-bar-renderer');
                if (header) header.style.setProperty('display', 'flex', 'important');
                document.body.style.removeProperty('padding-top');
            }
            if (pc === 'select_site') {
                const s = document.querySelector('ytm-settings, ytm-settings-renderer');
                if (s && !document.getElementById('extensionButton')) {
                    const base = Array.from(s.children).find(c => !c.id || !c.id.endsWith('Button')) || s.firstElementChild;
                    if (base) {
                        const create = (id, key, d, fn, atEnd = false) => {
                            if (document.getElementById(id)) return;
                            const b = base.cloneNode(true); b.id = id; b.removeAttribute('href');
                            b.style.setProperty('display', 'flex', 'important');
                            b.style.setProperty('align-items', 'center', 'important');
                            b.style.setProperty('padding', '12px 16px', 'important');
                            b.style.setProperty('min-height', '48px', 'important');
                            b.style.setProperty('background', 'transparent', 'important');
                            const iconDiv = document.createElement('div');
                            iconDiv.style.cssText = 'display:flex !important;align-items:center !important;justify-content:center !important;margin-right:16px !important;width:24px !important;height:24px !important;flex-shrink:0 !important;';
                            iconDiv.innerHTML = `<svg viewBox="0 0 24 24" style="width:24px;height:24px;fill:currentColor;display:block;"><path d="${d}"></path></svg>`;
                            const textDiv = document.createElement('div');
                            textDiv.style.cssText = 'flex:1 !important;font-size:16px !important;line-height:1.2 !important;color:currentColor !important;display:flex !important;align-items:center !important;';
                            textDiv.innerText = getLocalizedText(key);
                            b.innerHTML = '';
                            b.appendChild(iconDiv);
                            b.appendChild(textDiv);
                            b.onclick = (e) => { e.preventDefault(); e.stopPropagation(); fn(); };
                            if (atEnd) s.appendChild(b);
                            else s.insertBefore(b, s.firstChild);
                        };
                        create('downloadButton', 'downloads', 'M19,9h-4V3H9v6H5l7,7L19,9z M5,18v2h14v-2H5z', () => lite.download());
                        create('extensionButton', 'extension', 'M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.07-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61 l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41 h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.74,8.87 C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.81,11.69,4.81,12c0,0.31,0.02,0.64,0.07,0.94l-2.03,1.58 c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54 c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.44-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96 c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.47-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6 s1.62-3.6,3.6-3.6S13.98,15.6,12,15.6z', () => lite.extension());
                        create('aboutButton', 'about', 'M12,2C6.48,2,2,6.48,2,12s4.48,10,10,10s10-4.48,10-10S17.52,2,12,2z M13,17h-2v-6h2V17z M13,9h-2V7h2V9z', () => lite.about(), true);
                    }
                }
            }
        }
        setInterval(run, 500);
        let longPressTimer, lastUrl;
        const findLink = (el) => { let c = el; while (c && c !== document) { if (c.tagName === 'A' && c.href) return c.href; c = c.parentElement; } return null; };
        const handleLongPress = (url, target) => {
            if (!url || url === lastUrl) return; lastUrl = url;
            lite.showVideoOptions(url, '');
            setTimeout(() => { lastUrl = null; }, 1000);
        };
        bindListener(document, 'touchstart', e => {
            const url = findLink(e.target);
            if (url && (url.includes('/watch') || url.includes('/shorts/'))) {
                clearTimeout(longPressTimer); longPressTimer = setTimeout(() => handleLongPress(url, e.target), 600);
            }
        }, { passive: true });
        bindListener(document, 'touchend', () => clearTimeout(longPressTimer), { passive: true });
        bindListener(document, 'contextmenu', e => {
            const url = findLink(e.target);
            if (url && (url.includes('/watch') || url.includes('/shorts/'))) { e.preventDefault(); handleLongPress(url, e.target); }
        }, true);
        window.injected = true;
    }
} catch (e) { console.error(e); }