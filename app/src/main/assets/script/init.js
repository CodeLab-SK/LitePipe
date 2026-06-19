try {
    if (!window.injected) {
        const st = setTimeout.bind(window), si = setInterval.bind(window);
        const ct = clearTimeout.bind(window), ci = clearInterval.bind(window);

        if (!document.getElementById('_lp_style')) {
            const styleEl = document.createElement('style');
            styleEl.id = '_lp_style';
            styleEl.textContent = `
                html.lp-library-loading .YtmBrowseHost { opacity: 0 !important; }
                html.lp-library-loaded .YtmBrowseHost { opacity: 1; transition: opacity 0.7s ease; }
            `;
            (document.head || document.documentElement).appendChild(styleEl);
        }

        const getLocalizedText = (key) => {
            const languages = {
                'zh': { 'download': '下载', 'add_to_queue': '加入队列', 'downloads': '下载', 'extension': 'LitePipe 设置', 'chat': '聊天室', 'about': '关于', 'pip': '画中画', 'incognito': '无痕模式', 'incognito_off': '关闭无痕模式' },
                'zt': { 'download': '下載', 'add_to_queue': '加入佇列', 'downloads': '下載', 'extension': 'LitePipe 設置', 'chat': '聊天室', 'about': '關於', 'pip': '畫中畫', 'incognito': '無痕模式', 'incognito_off': '關閉無痕模式' },
                'en': { 'download': 'Download', 'add_to_queue': 'Add to queue', 'downloads': 'Downloads', 'extension': 'LitePipe Settings', 'chat': 'Chat', 'about': 'About', 'pip': 'PiP', 'incognito': 'Turn on Incognito', 'incognito_off': 'Turn off Incognito' },
                'ja': { 'download': 'ダウンロード', 'add_to_queue': 'キューに追加', 'downloads': 'ダウンロード', 'extension': 'LitePipe 設定', 'chat': 'チャット', 'about': '詳細', 'pip': 'PiP', 'incognito': 'シークレットをオン', 'incognito_off': 'シークレットをオフ' },
                'ko': { 'download': '다운로드', 'add_to_queue': '대기열에 추가', 'downloads': '다운로드', 'extension': 'LitePipe 플러그인', 'chat': '채팅', 'about': '정보', 'pip': 'PiP', 'incognito': '시릿 모드 켜기', 'incognito_off': '시릿 모드 끄기' },
                'fr': { 'download': 'Télécharger', 'add_to_queue': 'Ajouter à la file', 'downloads': 'Téléchargements', 'extension': 'Paramètres LitePipe', 'chat': 'Chat', 'about': 'À propos', 'pip': 'PiP', 'incognito': 'Activer navigation privée', 'incognito_off': 'Désactiver navigation privée' },
                'ru': { 'download': 'Скачать', 'add_to_queue': 'Добавить в очередь', 'downloads': 'Загрузки', 'extension': 'Настройки LitePipe', 'chat': 'Чат', 'about': 'О программе', 'pip': 'PiP', 'incognito': 'Вкл. инкогнито', 'incognito_off': 'Выкл. инкогнито' },
                'tr': { 'download': 'İndir', 'add_to_queue': 'Kuyruğa ekle', 'downloads': 'İndirilenler', 'extension': 'LitePipe Ayarları', 'chat': 'Sohbet', 'about': 'Hakkında', 'pip': 'PiP', 'incognito': 'Gizli modu aç', 'incognito_off': 'Gizli modu kapat' },
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
                const isMusic = u.hostname === 'music.youtube.com';
                if (!u.hostname.includes('youtube.com')) return 'unknown';
                const segments = u.pathname.split('/').filter(Boolean);
                if (segments.length === 0) return isMusic ? 'music' : 'home';
                const s0 = segments[0];
                if (s0 === 'shorts') return 'shorts';
                if (s0 === 'watch') return isMusic ? 'music_watch' : 'watch';
                if (s0 === 'channel') return 'channel';
                if (s0 === 'gaming') return 'gaming';
                if (s0 === 'feed' && segments.length > 1) return segments[1];
                if (s0 === 'select_site') return 'select_site';
                if (s0 === 'you' || s0 === 'library') return 'library';
                if (s0.startsWith('@')) return '@';
                return segments.join('/');
            } catch (e) { return 'unknown'; }
        };

        let cachedPageClass = null, lastPageUrl = null;
        const getCachedPageClass = (url) => {
            if (lastPageUrl === url && cachedPageClass) return cachedPageClass;
            lastPageUrl = url; cachedPageClass = getPageClass(url);
            return cachedPageClass;
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
                            if (poToken && window.android?.setPoToken) android.setPoToken(poToken, visitorData);
                        }
                    } catch (e) {}
                }
                return window.originalFetch(...args);
            };
        }

        const getVideoId = (url) => {
            const match = url.match(/^.*((youtu.be\/)|(v\/)|(\/u\/\w\/)|(embed\/)|(watch\?))\??v?=?([^#&?]*).*/);
            return (match && match[7].length == 11) ? match[7] : null;
        };

        const bindListener = (obj, type, fn, options) => {
            if (!obj?.addEventListener || !obj?.removeEventListener || typeof fn !== 'function') return;
            const capture = typeof options === 'boolean' ? options : !!options?.capture;
            obj.removeEventListener(type, fn, capture);
            obj.addEventListener(type, fn, options);
        };

        const INCOGNITO_ICON = 'M17.06 13C15.2 13 13.64 14.33 13.24 16.1C12.29 15.69 11.42 15.8 10.76 16.09C10.35 14.31 8.79 13 6.94 13C4.77 13 3 14.79 3 17C3 19.21 4.77 21 6.94 21C9 21 10.68 19.38 10.84 17.32C11.18 17.08 12.07 16.63 13.16 17.34C13.34 19.39 15 21 17.06 21C19.23 21 21 19.21 21 17C21 14.79 19.23 13 17.06 13M6.94 19.86C5.38 19.86 4.13 18.58 4.13 17S5.39 14.14 6.94 14.14C8.5 14.14 9.75 15.42 9.75 17S8.5 19.86 6.94 19.86M17.06 19.86C15.5 19.86 14.25 18.58 14.25 17S15.5 14.14 17.06 14.14C18.62 14.14 19.88 15.42 19.88 17S18.61 19.86 17.06 19.86M22 10.5H2V12H22V10.5M15.53 2.63C15.31 2.14 14.75 1.88 14.22 2.05L12 2.79L9.77 2.05L9.72 2.04C9.19 1.89 8.63 2.17 8.43 2.68L6 9H18L15.56 2.68L15.53 2.63Z';

        let shortsSpeedPressState = null;
        const SHORTS_SPEED_LONG_PRESS_MS = 450, SHORTS_SPEED_DRAG_THRESHOLD = 10;
        const getPoint = (event) => event?.touches?.[0] || event?.changedTouches?.[0] || event;

        const showShortsSpeedToast = () => {
            if (window.android?.showHint) android.showHint('2x', -1);
            let toast = document.getElementById('_lp_speed_toast');
            if (!toast) {
                toast = document.createElement('div');
                toast.id = '_lp_speed_toast';
                toast.style.cssText = 'position:fixed;top:4%;left:50%;transform:translateX(-50%);z-index:99999;background:rgba(0,0,0,0.4);color:#fff;font-size:15px;font-weight:700;padding:5px 12px;border-radius:18px;pointer-events:none;letter-spacing:0.02em;transition:opacity 0.15s;';
                toast.textContent = '2×';
                document.body.appendChild(toast);
            }
            toast.style.opacity = '1'; toast.style.display = 'block';
        };

        const hideShortsSpeedToast = () => {
            if (window.android?.hideHint) android.hideHint();
            const toast = document.getElementById('_lp_speed_toast');
            if (toast) toast.style.display = 'none';
        };

        const clearShortsSpeedPress = () => {
            if (!shortsSpeedPressState) return;
            if (shortsSpeedPressState.timerId) ct(shortsSpeedPressState.timerId);
            const { player, previousStyles, activated } = shortsSpeedPressState;
            if (player instanceof Element && previousStyles) {
                player.style.userSelect = previousStyles.userSelect ?? '';
                player.style.webkitUserSelect = previousStyles.webkitUserSelect ?? '';
                player.style.webkitTouchCallout = previousStyles.webkitTouchCallout ?? '';
            }
            shortsSpeedPressState = null;
            if (activated) { player?.setPlaybackRate?.(1); hideShortsSpeedToast(); }
        };

        const startShortsSpeedPress = (player, event) => {
            if (!(player instanceof Element) || shortsSpeedPressState) return;
            const point = getPoint(event);
            shortsSpeedPressState = {
                player,
                previousStyles: { userSelect: player.style.userSelect, webkitUserSelect: player.style.webkitUserSelect, webkitTouchCallout: player.style.webkitTouchCallout },
                startX: point.clientX, startY: point.clientY, activated: false,
                timerId: st(() => {
                    if (!shortsSpeedPressState) return;
                    shortsSpeedPressState.activated = true;
                    player?.setPlaybackRate?.(2); showShortsSpeedToast();
                }, SHORTS_SPEED_LONG_PRESS_MS)
            };
            player.style.userSelect = 'none'; player.style.webkitUserSelect = 'none'; player.style.webkitTouchCallout = 'none';
        };

        const moveShortsSpeedPress = (event) => {
            if (!shortsSpeedPressState) return;
            const point = getPoint(event);
            if (Math.abs(point.clientX - shortsSpeedPressState.startX) > SHORTS_SPEED_DRAG_THRESHOLD ||
                Math.abs(point.clientY - shortsSpeedPressState.startY) > SHORTS_SPEED_DRAG_THRESHOLD) clearShortsSpeedPress();
        };

        const findShortsSpeedSurfaceFromEvent = (event) => {
            const target = event?.target;
            return (target instanceof Element) ? (target.closest?.('#player-shorts-container, shorts-video') ?? null) : null;
        };

        let shortsGestureBound = false;
        const bindShortsGesture = () => {
            if (shortsGestureBound) return; shortsGestureBound = true;
            ['pointerdown', 'touchstart'].forEach(type => bindListener(document, type, (e) => {
                if (getCachedPageClass(location.href) !== 'shorts') return;
                const surface = findShortsSpeedSurfaceFromEvent(e);
                const player = document.querySelector('#movie_player');
                if (surface && player) startShortsSpeedPress(player, e);
            }, { passive: false, capture: true }));
            ['pointermove', 'touchmove'].forEach(type => bindListener(document, type, moveShortsSpeedPress, { passive: false, capture: true }));
            ['pointerup', 'touchend', 'pointercancel', 'touchcancel'].forEach(type => bindListener(document, type, clearShortsSpeedPress, { passive: false, capture: true }));
        };

        const requestOpenTab = (nextUrl, nextPageClass) => {
            if (window.android?.openTab) android.openTab(nextUrl, nextPageClass);
            else location.href = nextUrl;
        };

        const parseTimestampSeconds = (rawValue) => {
            if (rawValue == null) return null;
            const normalized = `${rawValue}`.trim().toLowerCase();
            if (!normalized) return null;
            if (/^\d+$/.test(normalized)) return Number(normalized);
            let totalSeconds = 0, matched = false;
            for (const part of normalized.matchAll(/(\d+)(h|m|s)/g)) {
                const amount = Number(part[1]); matched = true;
                if (part[2] === 'h') totalSeconds += amount * 3600;
                if (part[2] === 'm') totalSeconds += amount * 60;
                if (part[2] === 's') totalSeconds += amount;
            }
            return matched ? totalSeconds : null;
        };

        const handleWatchTimestampClick = (event) => {
            if (getCachedPageClass(location.href) !== 'watch') return;
            const link = event.target.closest('a');
            if (!link) return;
            const href = link.getAttribute('href') || link.href;
            if (!href || !href.includes('t=')) return;
            let targetUrl;
            try { targetUrl = new URL(link.href, location.href); } catch (e) { return; }
            const videoId = getVideoId(location.href), targetVideoId = getVideoId(targetUrl.toString());
            if (!videoId || videoId !== targetVideoId) return;
            const timestampSeconds = parseTimestampSeconds(targetUrl.searchParams.get('t') ?? targetUrl.searchParams.get('start'));
            if (timestampSeconds == null) return;
            if (android.seekLoadedVideo?.(targetUrl.toString(), timestampSeconds * 1000)) {
                event.preventDefault(); event.stopImmediatePropagation();
            }
        };

        const addTapEvent = (el, handler) => {
            let startX, startY;
            bindListener(el, 'pointerdown', e => { startX = e.clientX; startY = e.clientY; }, { passive: false });
            bindListener(el, 'pointerup', e => {
                if (Math.abs(e.clientX - startX) < 10 && Math.abs(e.clientY - startY) < 10) handler(e);
            }, { passive: false });
        };

        const replaceTextInNode = (root, label) => {
            if (!root) return;
            const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
            const textNodes = [];
            while (walker.nextNode()) {
                const n = walker.currentNode;
                if (n.parentElement && n.parentElement.closest('svg')) continue;
                if (n.textContent.trim()) textNodes.push(n);
            }
            if (textNodes.length > 0) {
                textNodes[0].textContent = label;
                for (let i = 1; i < textNodes.length; i++) textNodes[i].textContent = '';
            }
        };

        const createLibraryIncognitoButton = (label, iconPath, isOn, onClick) => {
            const row = document.querySelector('.ytFlexibleActionsViewModelActionRow');
            if (!row) return null;

            let source = row.querySelector('.ytFlexibleActionsViewModelActionRowAction button');
            if (!source) {
                source = document.querySelector('button-view-model.ytSpecButtonViewModelHost button') ||
                         document.querySelector('ytm-topbar-menu-button-renderer button');
            }
            if (!source) return null;

            const wrapper = document.createElement('div');
            wrapper.className = 'ytFlexibleActionsViewModelAction ytFlexibleActionsViewModelActionRowAction';

            const clone = source.cloneNode(true);
            clone.removeAttribute('id');
            const btn = clone.tagName === 'BUTTON' ? clone : clone.querySelector('button');
            if (!btn) return null;

            btn.removeAttribute('href');
            btn.removeAttribute('target');
            btn.setAttribute('aria-label', label);

            if (isOn) {
                btn.style.outline = '2px solid currentColor';
                btn.style.outlineOffset = '-2px';
            } else {
                btn.style.outline = '';
                btn.style.outlineOffset = '';
            }

            const iconContainer = btn.querySelector('.yt-spec-button-shape-next__icon, .yt-icon-shape, yt-icon');
            if (iconContainer) {
                let svg = iconContainer.querySelector('svg');
                const ns = 'http://www.w3.org/2000/svg';
                if (!svg) {
                    svg = document.createElementNS(ns, 'svg');
                    svg.setAttribute('viewBox', '0 0 24 24');
                    iconContainer.innerHTML = '';
                    iconContainer.appendChild(svg);
                }
                svg.setAttribute('width', '24');
                svg.setAttribute('height', '24');
                svg.style.fill = 'currentColor';
                const path = svg.querySelector('path');
                if (path) path.setAttribute('d', iconPath);
                else svg.innerHTML = `<path d="${iconPath}"></path>`;
            }

            replaceTextInNode(btn, label);

            const hasAnyText = (function() {
                const w = document.createTreeWalker(btn, NodeFilter.SHOW_TEXT);
                while (w.nextNode()) { if (w.currentNode.textContent.trim()) return true; }
                return false;
            })();
            if (!hasAnyText) {
                const textSpan = document.createElement('span');
                textSpan.className = 'yt-core-attributed-string yt-core-attributed-string--white-space-no-wrap yt-spec-button-shape-next__button-text-content';
                textSpan.textContent = label;
                btn.appendChild(textSpan);
            }

            wrapper.appendChild(clone);
            bindListener(btn, 'click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                onClick();
            }, true);
            return wrapper;
        };

        const createWatchButton = (label, iconPath, id, onClick) => {
            const actionBar = document.querySelector('ytm-slim-video-action-bar-renderer');

            let liveSource = null;
            if (actionBar) {
                const actionsContainer = actionBar.querySelector('.slim-video-action-bar-actions');
                if (actionsContainer) {
                    liveSource = actionsContainer.querySelector(':scope > ytm-button-renderer:not([data-lp-custom])') ||
                                actionsContainer.querySelector(':scope > button-view-model:not([data-lp-custom])') ||
                                actionsContainer.querySelector(':scope > .slim_video_action_bar_renderer_button:not([data-lp-custom])') ||
                                actionsContainer.querySelector(':scope > :not(ytm-segmented-like-dislike-button-renderer):not(segmented-like-dislike-button-view-model):not([data-lp-custom])');
                }
                if (!liveSource) {
                    liveSource = actionBar.querySelector('ytm-button-renderer:not([data-lp-custom])') ||
                                 actionBar.querySelector('button-view-model:not([data-lp-custom])') ||
                                 actionBar.querySelector('.slim_video_action_bar_renderer_button:not([data-lp-custom])');
                }
            }
            if (!liveSource) {
                liveSource = document.querySelector('ytm-button-renderer:not([data-lp-custom])') ||
                             document.querySelector('button-view-model:not([data-lp-custom])') ||
                             document.querySelector('.slim_video_action_bar_renderer_button:not([data-lp-custom])');
            }

            if (!liveSource) return null;
            const clone = liveSource.cloneNode(true);

            clone.removeAttribute('id');
            clone.id = id;
            clone.dataset.lpCustom = 'true';
            clone.removeAttribute('hidden');
            clone.style.removeProperty('display');
            clone.querySelectorAll('[style]').forEach(el => {
                if (el.style.display) el.style.removeProperty('display');
            });
            clone.querySelectorAll('[hidden]').forEach(el => el.removeAttribute('hidden'));

            const btn = clone.querySelector('button') || clone;
            btn.removeAttribute('href');
            btn.removeAttribute('target');
            btn.setAttribute('aria-label', label);
            btn.dataset.lpCustom = 'true';

            const iconContainer = clone.querySelector('.yt-spec-button-shape-next__icon, .yt-icon-shape, yt-icon');
            if (iconContainer) {
                iconContainer.style.cssText = 'display:inline-flex;align-items:center;justify-content:center;width:24px;height:24px;flex-shrink:0;';
                let svg = iconContainer.querySelector('svg');
                const ns = 'http://www.w3.org/2000/svg';
                if (!svg) {
                    svg = document.createElementNS(ns, 'svg');
                    svg.setAttribute('viewBox', '0 0 24 24');
                    iconContainer.innerHTML = '';
                    iconContainer.appendChild(svg);
                }
                svg.setAttribute('width', '24');
                svg.setAttribute('height', '24');
                svg.style.cssText = 'width:24px;height:24px;fill:currentColor;flex-shrink:0;';
                const path = svg.querySelector('path');
                if (path) path.setAttribute('d', iconPath);
                else svg.innerHTML = `<path d="${iconPath}"></path>`;
            } else {
                const ns = 'http://www.w3.org/2000/svg';
                const svg = document.createElementNS(ns, 'svg');
                svg.setAttribute('viewBox', '0 0 24 24');
                svg.setAttribute('width', '24');
                svg.setAttribute('height', '24');
                svg.style.cssText = 'width:24px;height:24px;fill:currentColor;flex-shrink:0;';
                svg.innerHTML = `<path d="${iconPath}"></path>`;
                const flexContainer = document.createElement('div');
                flexContainer.className = 'yt-spec-button-shape-next__icon';
                flexContainer.style.cssText = 'display:inline-flex;align-items:center;justify-content:center;width:24px;height:24px;flex-shrink:0;';
                flexContainer.appendChild(svg);
                if (btn.firstChild) btn.insertBefore(flexContainer, btn.firstChild);
                else btn.appendChild(flexContainer);
            }

            replaceTextInNode(clone, label);

            bindListener(btn, 'click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
                onClick();
            }, true);
            return clone;
        };

        const createCustomSettingBtn = (baseItem, id, textKey, iconD, clickFn) => {
            try {
                if (document.getElementById(id)) return null;
                const btn = baseItem.cloneNode(true);
                btn.id = id;
                btn.removeAttribute('href');
                const label = getLocalizedText(textKey);
                const textEl = btn.querySelector('.ytAttributedStringHost, .ytm-settings-item-content, .ytm-compact-link-renderer-content');
                if (textEl) {
                    const innerText = textEl.querySelector('.ytAttributedStringHost');
                    if (innerText) innerText.innerText = label; else textEl.innerText = label;
                }
                const ns = 'http://www.w3.org/2000/svg';
                const svg = document.createElementNS(ns, 'svg');
                svg.setAttribute('viewBox', '0 -960 960 960');
                svg.setAttribute('width', '24'); svg.setAttribute('height', '24');
                svg.setAttribute('focusable', 'false'); svg.setAttribute('aria-hidden', 'true');
                svg.setAttribute('fill', 'currentColor');
                const path = document.createElementNS(ns, 'path');
                path.setAttribute('d', iconD);
                svg.appendChild(path);
                const iconContainer = btn.querySelector('yt-icon, c3-icon, .ytm-settings-item-icon, .icon') || btn.firstElementChild;
                if (iconContainer) {
                    while (iconContainer.firstChild) iconContainer.removeChild(iconContainer.firstChild);
                    iconContainer.appendChild(svg);
                    iconContainer.style.cssText = 'display:flex;align-items:center;justify-content:center;width:24px;height:24px;min-width:24px;';
                } else {
                    btn.insertBefore(svg, btn.firstChild);
                }
                btn.addEventListener('click', (e) => { e.preventDefault(); e.stopPropagation(); clickFn(); }, true);
                btn.style.cssText = 'display:flex;align-items:center;width:100%;box-sizing:border-box;';
                const content = btn.querySelector('.ytm-settings-item-content, .ytm-compact-link-renderer-content');
                if (content) { content.style.flex = '1'; content.style.display = 'flex'; content.style.alignItems = 'center'; }
                return btn;
            } catch (err) { return null; }
        };

        const injectIncognitoFallbackBanner = () => {
            if (document.getElementById('_lp_incognito_banner')) return;

            const isDark = document.documentElement.getAttribute('dark') === 'true' || window.matchMedia('(prefers-color-scheme: dark)').matches;
            const bg = isDark ? '#0f0f0f' : '#f9f9f9';
            const fg = isDark ? '#e8eaed' : '#202124';
            const subFg = isDark ? '#9aa0a6' : '#5f6368';
            const cardBg = isDark ? '#1c1c1e' : '#ffffff';

            const headerHeight = (() => {
                const h = document.querySelector('ytm-header-bar-renderer');
                return h ? h.offsetHeight : 56;
            })();

            const banner = document.createElement('div');
            banner.id = '_lp_incognito_banner';
            banner.style.cssText = `position:fixed;top:${headerHeight - 8}px;left:0;right:0;bottom:-60px;z-index:99990;display:flex;flex-direction:column;align-items:center;justify-content:center;background:${bg};color:${fg};font-family:"YouTube Sans","Roboto",sans-serif;padding:24px 28px 60px;box-sizing:border-box;overflow:hidden;`;
            const card = document.createElement('div');
            card.style.cssText = `display:flex;flex-direction:column;align-items:center;justify-content:center;background:${cardBg};border-radius:20px;padding:36px 28px 32px;box-shadow:0 4px 24px rgba(0,0,0,${isDark ? '0.5' : '0.10'});max-width:340px;width:100%;box-sizing:border-box;`;

            const ns = 'http://www.w3.org/2000/svg';
            const svg = document.createElementNS(ns, 'svg');
            svg.setAttribute('viewBox', '0 0 40 40');
            svg.setAttribute('width', '120');
            svg.setAttribute('height', '120');
            svg.style.cssText = `display:block;margin-bottom:20px;`;

            const defs = document.createElementNS(ns, 'defs');
            const grad = document.createElementNS(ns, 'radialGradient');
            grad.setAttribute('id', '_lp_ig_grad');
            grad.setAttribute('cx', '50%'); grad.setAttribute('cy', '40%');
            grad.setAttribute('r', '55%');
            const s1 = document.createElementNS(ns, 'stop');
            s1.setAttribute('offset', '0%'); s1.setAttribute('stop-color', isDark ? '#9aa0a6' : '#bdc1c6');
            const s2 = document.createElementNS(ns, 'stop');
            s2.setAttribute('offset', '100%'); s2.setAttribute('stop-color', isDark ? '#5f6368' : '#80868b');
            grad.appendChild(s1); grad.appendChild(s2); defs.appendChild(grad);
            svg.appendChild(defs);

            const hatPath = document.createElementNS(ns, 'path');
            hatPath.setAttribute('d', 'M20 3C13.37 3 8 8.37 8 15H32C32 8.37 26.63 3 20 3Z');
            hatPath.setAttribute('fill', 'url(#_lp_ig_grad)');
            svg.appendChild(hatPath);

            const brimRect = document.createElementNS(ns, 'rect');
            brimRect.setAttribute('x', '2'); brimRect.setAttribute('y', '15');
            brimRect.setAttribute('width', '36'); brimRect.setAttribute('height', '4');
            brimRect.setAttribute('rx', '2');
            brimRect.setAttribute('fill', isDark ? '#9aa0a6' : '#bdc1c6');
            svg.appendChild(brimRect);

            [
                { cx: '12', cy: '27', r: '5.5' },
                { cx: '28', cy: '27', r: '5.5' },
            ].forEach(attrs => {
                const circle = document.createElementNS(ns, 'circle');
                Object.entries(attrs).forEach(([k, v]) => circle.setAttribute(k, v));
                circle.setAttribute('fill', isDark ? '#9aa0a6' : '#bdc1c6');
                svg.appendChild(circle);
                const lens = document.createElementNS(ns, 'circle');
                lens.setAttribute('cx', attrs.cx); lens.setAttribute('cy', attrs.cy);
                lens.setAttribute('r', '3.5');
                lens.setAttribute('fill', isDark ? '#1c1c1e' : '#f1f3f4');
                svg.appendChild(lens);
            });

            card.appendChild(svg);

            const title = document.createElement('div');
            title.textContent = "You're Incognito";
            title.style.cssText = `font-size:22px;font-weight:700;text-align:center;color:${fg};letter-spacing:-0.3px;margin-bottom:10px;`;
            card.appendChild(title);

            const subtitle = document.createElement('div');
            subtitle.textContent = "Your activity in this session won't be saved to your YouTube history.";
            subtitle.style.cssText = `font-size:13px;text-align:center;color:${subFg};line-height:1.5;margin-bottom:28px;`;
            card.appendChild(subtitle);

            const offBtn = document.createElement('button');
            offBtn.textContent = getLocalizedText('incognito_off');
            offBtn.style.cssText = `background:#1a73e8;border:none;color:#ffffff;font-size:15px;font-weight:600;padding:13px 0;border-radius:24px;cursor:pointer;font-family:inherit;white-space:nowrap;-webkit-tap-highlight-color:transparent;width:100%;letter-spacing:0.01em;box-shadow:0 2px 12px rgba(26,115,232,0.45),0 0 0 0 rgba(26,115,232,0);transition:box-shadow 0.15s,transform 0.1s;`;
            offBtn.addEventListener('pointerdown', () => {
                offBtn.style.transform = 'scale(0.97)';
                offBtn.style.boxShadow = '0 1px 6px rgba(26,115,232,0.55),0 0 18px rgba(26,115,232,0.3)';
            }, { passive: true });
            offBtn.addEventListener('pointerup', () => {
                offBtn.style.transform = '';
                offBtn.style.boxShadow = '0 2px 12px rgba(26,115,232,0.45)';
            }, { passive: true });
            offBtn.addEventListener('click', (e) => {
                e.preventDefault(); e.stopPropagation();
                if (window.android?.toggleIncognito) android.toggleIncognito();
            }, true);
            card.appendChild(offBtn);

            banner.appendChild(card);
            document.body.appendChild(banner);
            if (window.android?.setRefreshLayoutEnabled) android.setRefreshLayoutEnabled(true);
        };

        const removeIncognitoFallbackBanner = () => {
            const b = document.getElementById('_lp_incognito_banner');
            if (b) b.remove();
            if (window.android?.setRefreshLayoutEnabled) android.setRefreshLayoutEnabled(true);
        };

        const ensureLibraryButton = () => {
            const pc = getCachedPageClass(location.href);
            if (pc !== 'you' && pc !== 'library') {
                removeIncognitoFallbackBanner();
                document.documentElement.classList.remove('lp-library-loading', 'lp-library-loaded');
                return;
            }
            const row = document.querySelector('yt-flexible-actions-view-model .ytFlexibleActionsViewModelActionRow') || document.querySelector('.ytFlexibleActionsViewModelActionRow');
            const isIncognito = window.android?.isIncognito?.() === true;

            if (!row) {
                if (isIncognito) {
                    document.documentElement.classList.add('lp-library-loading');
                    document.documentElement.classList.remove('lp-library-loaded');
                    injectIncognitoFallbackBanner();
                } else {
                    document.documentElement.classList.remove('lp-library-loading');
                    document.documentElement.classList.add('lp-library-loaded');
                }
                return;
            }

            document.documentElement.classList.add('lp-library-loaded');
            document.documentElement.classList.remove('lp-library-loading');
            removeIncognitoFallbackBanner();

            let chip = document.getElementById('_lp_incognito_chip');
            if (!chip) {
                chip = createLibraryIncognitoButton(
                    isIncognito ? getLocalizedText('incognito_off') : getLocalizedText('incognito'),
                    INCOGNITO_ICON,
                    isIncognito,
                    () => {
                        if (window.android?.toggleIncognito) android.toggleIncognito();
                        st(ensureLibraryButton, 100);
                    }
                );
                if (chip) {
                    chip.id = '_lp_incognito_chip';
                    row.prepend(chip);
                }
            } else {
                const btn = chip.querySelector('button');
                const currentLabel = isIncognito ? getLocalizedText('incognito_off') : getLocalizedText('incognito');
                replaceTextInNode(chip, currentLabel);

                if (btn) {
                    if (isIncognito) {
                        btn.style.outline = '2px solid currentColor';
                        btn.style.outlineOffset = '-2px';
                    } else {
                        btn.style.outline = '';
                        btn.style.outlineOffset = '';
                    }
                }
            }
        };

        let libraryObserver = null;
        const startLibraryObserver = () => {
            if (libraryObserver) libraryObserver.disconnect();
            const container = document.querySelector('yt-flexible-actions-view-model');
            if (container) {
                libraryObserver = new MutationObserver(() => {
                    ensureLibraryButton();
                });
                libraryObserver.observe(container, { childList: true, subtree: true });
                ensureLibraryButton();
            } else {
                libraryObserver = new MutationObserver((mutations, obs) => {
                    if (document.querySelector('yt-flexible-actions-view-model')) {
                        obs.disconnect();
                        startLibraryObserver();
                    }
                });
                libraryObserver.observe(document.body, { childList: true, subtree: true });
            }
        };

        let cachedPrefs = null, lastPrefCheckTime = 0;
        const PREF_CACHE_DURATION = 5000;
        const getPrefs = () => {
            const now = Date.now();
            if (cachedPrefs && now - lastPrefCheckTime < PREF_CACHE_DURATION) return cachedPrefs;
            lastPrefCheckTime = now;
            try { cachedPrefs = JSON.parse(window.android?.getPreferences() || '{}'); } catch (e) { cachedPrefs = {}; }
            return cachedPrefs;
        };

        const applyActionBarVisibility = (actionBar, prefs) => {
            actionBar.querySelectorAll([
                '.ytSpecButtonViewModelHost',
                'ytm-toggle-button-renderer',
                'ytm-button-renderer',
                '.slim_video_action_bar_renderer_button',
                'ytm-segmented-like-dislike-button-renderer',
                'segmented-like-dislike-button-view-model',
                'button-view-model',
            ].join(',')).forEach((btn) => {
                const label = (btn.getAttribute('aria-label') || btn.textContent || '').toLowerCase().trim();
                const id = btn.id || '';
                const tag = btn.tagName.toLowerCase();
                const isLikeDislike = (
                    tag === 'ytm-segmented-like-dislike-button-renderer' ||
                    tag === 'segmented-like-dislike-button-view-model' ||
                    btn.classList.contains('ytSegmentedLikeDislikeButtonViewModelHost') ||
                    btn.closest?.('ytm-segmented-like-dislike-button-renderer, segmented-like-dislike-button-view-model') !== null ||
                    (label.includes('like') && !label.includes('dislike') && tag !== 'div') ||
                    label === 'like' || label === 'dislike' ||
                    label.includes('like this video') || label.includes('dislike this video')
                );
                let hide = false;
                if (prefs.action_bar_show_like_dislike === false && isLikeDislike) hide = true;
                if (prefs.action_bar_show_download === false && (label.includes('download') || id === 'downloadButton')) hide = true;
                if (prefs.action_bar_show_queue === false && (label.includes('add to queue') || id === 'queueButton')) hide = true;
                if (prefs.enable_pip === false && id === 'pipButton') hide = true;
                if (prefs.action_bar_show_chat === false && (label.includes('chat') || id === 'chatButton')) hide = true;
                if (prefs.action_bar_show_share === false && label.includes('share')) hide = true;
                if (prefs.action_bar_show_remix === false && label.includes('remix')) hide = true;
                if (prefs.action_bar_show_thanks === false && label.includes('thanks')) hide = true;
                if (prefs.action_bar_show_clip === false && label.includes('clip')) hide = true;
                if (prefs.action_bar_show_save === false && (label.includes('save') || label.includes('playlist'))) hide = true;
                if (prefs.action_bar_show_report === false && label.includes('report')) hide = true;
                if (prefs.action_bar_show_ask_ai === false && (label.includes('ask') || label.includes('ai'))) hide = true;
                if (hide) btn.style.setProperty('display', 'none', 'important');
                else btn.style.removeProperty('display');
            });

            if (prefs.action_bar_show_like_dislike === false) {
                actionBar.querySelectorAll('ytm-segmented-like-dislike-button-renderer, segmented-like-dislike-button-view-model').forEach(el => {
                    el.style.setProperty('display', 'none', 'important');
                });
            } else {
                actionBar.querySelectorAll('ytm-segmented-like-dislike-button-renderer, segmented-like-dislike-button-view-model').forEach(el => {
                    el.style.removeProperty('display');
                });
            }
        };

        let watchObserver = null, watchDebounce = null;
        const startWatchObserver = () => {
            if (watchObserver) watchObserver.disconnect();
            const container = document.querySelector('ytm-slim-video-action-bar-renderer');
            if (container) {
                watchObserver = new MutationObserver(() => {
                    ct(watchDebounce);
                    watchDebounce = st(ensureWatchButtons, 50);
                });
                watchObserver.observe(container, { childList: true, subtree: true });
                ensureWatchButtons();
            }
        };

        const ensureWatchButtons = () => {
            if (getCachedPageClass(location.href) !== 'watch') return;
            const actionBar = document.querySelector('ytm-slim-video-action-bar-renderer');
            if (!actionBar) return;
            const prefs = getPrefs();
            const container = actionBar.querySelector('.slim-video-action-bar-actions') || actionBar;
            let anchor = container.querySelector('ytm-segmented-like-dislike-button-renderer') ||
                         container.querySelector('ytm-toggle-button-renderer') ||
                         container.querySelector('segmented-like-dislike-button-view-model') ||
                         container.firstElementChild;
            if (!anchor) return;
            const moviePlayer = document.querySelector('#movie_player');
            const isLive = moviePlayer?.getPlayerResponse?.()?.playabilityStatus?.liveStreamability && location.href.includes('/watch');
            const targetOrder = [];

            if (prefs.action_bar_show_download !== false && !isLive) {
                targetOrder.push({
                    id: 'downloadButton', key: 'download',
                    icon: 'M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z',
                    fn: () => window.android?.download(location.href),
                });
            } else {
                const el = document.getElementById('downloadButton');
                if (el) el.remove();
            }

            if (prefs.action_bar_show_queue !== false) {
                targetOrder.push({
                    id: 'queueButton', key: 'add_to_queue',
                    icon: 'M4 10h12v2H4zm0-4h12v2H4zm0 8h8v2H4zm10 0v6l5-3z',
                    fn: () => {
                        const videoId = getVideoId(location.href);
                        if (!videoId) return;
                        const metadata = {
                            url: location.href,
                            videoId: videoId,
                            title: document.querySelector('.slim-video-information-title, .watch-title, ytm-slim-video-metadata-renderer .title')?.textContent?.trim() || videoId,
                            author: document.querySelector('.slim-owner-name, .channel-name, ytm-slim-owner-renderer .ytm-slim-owner-renderer-text')?.textContent?.trim() || '',
                            thumbnailUrl: `https://img.youtube.com/vi/${videoId}/hqdefault.jpg`
                        };
                        if (window.android?.addToQueue) android.addToQueue(JSON.stringify(metadata));
                    }
                });
            } else {
                const el = document.getElementById('queueButton');
                if (el) el.remove();
            }

            if (prefs.action_bar_show_chat !== false && isLive) {
                targetOrder.push({
                    id: 'chatButton', key: 'chat',
                    icon: 'M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z',
                    fn: () => {
                        let chatContainer = document.getElementById('live_chat_container');
                        if (chatContainer) {
                            if (chatContainer.style.display === 'none') {
                                chatContainer.style.display = 'flex';
                                document.body.style.overflow = 'hidden';
                                document.documentElement.style.overflow = 'hidden';
                                history.pushState({ chatOpen: true }, '', location.href + '#chat');
                            } else {
                                chatContainer.style.display = 'none';
                                document.body.style.overflow = '';
                                document.documentElement.style.overflow = '';
                                if (location.hash === '#chat') history.back();
                            }
                            return;
                        }
                        const panelContainer = document.querySelector('#panel-container') || document.querySelector('.watch-below-the-player');
                        if (!panelContainer) return;
                        chatContainer = document.createElement('div');
                        chatContainer.id = 'live_chat_container';
                        chatContainer.style.cssText = 'position:fixed;top:calc(56.25vw + 48px);bottom:0;left:0;right:0;z-index:4;display:flex;flex-direction:column;box-shadow:0 -2px 10px rgba(0,0,0,0.1);border-top-left-radius:12px;border-top-right-radius:12px;overflow:hidden;';
                        document.body.style.overflow = 'hidden'; document.documentElement.style.overflow = 'hidden';
                        history.pushState({ chatOpen: true }, '', location.href + '#chat');
                        const header = document.createElement('div');
                        header.style.cssText = 'display:flex;justify-content:space-between;align-items:center;padding:12px 16px;border-bottom:1px solid var(--yt-spec-10-percent-layer);background-color:inherit;border-top-left-radius:12px;border-top-right-radius:12px;';
                        const title = document.createElement('h2');
                        title.className = 'engagement-panel-section-list-header-title';
                        title.innerText = getLocalizedText('chat');
                        title.style.cssText = 'font-family:"YouTube Sans","Roboto",sans-serif;font-size:1.8rem;font-weight:600;color:var(--yt-spec-text-primary);margin:0;';
                        const closeBtn = document.createElement('div');
                        const closeSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
                        closeSvg.setAttribute('viewBox', '0 0 24 24'); closeSvg.setAttribute('width', '24'); closeSvg.setAttribute('height', '24');
                        closeSvg.setAttribute('fill', 'currentColor'); closeSvg.style.display = 'block';
                        const closePath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                        closePath.setAttribute('d', 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z');
                        closeSvg.appendChild(closePath); closeBtn.appendChild(closeSvg);
                        closeBtn.style.cssText = 'cursor:pointer;color:var(--yt-spec-text-primary);padding:4px;';
                        closeBtn.onclick = (e) => { e.stopPropagation(); chatContainer.style.display = 'none'; document.body.style.overflow = ''; document.documentElement.style.overflow = ''; if (location.hash === '#chat') history.back(); };
                        header.appendChild(title); header.appendChild(closeBtn); chatContainer.appendChild(header);
                        const videoId = getVideoId(location.href);
                        if (videoId) {
                            const iframe = document.createElement('iframe');
                            const isDark = document.documentElement.getAttribute('dark') === 'true' || window.matchMedia('(prefers-color-scheme: dark)').matches;
                            chatContainer.style.backgroundColor = isDark ? '#0f0f0f' : '#ffffff';
                            iframe.src = `https://www.youtube.com/live_chat?v=${videoId}&embed_domain=${location.hostname}${isDark ? '&dark_theme=1' : ''}`;
                            iframe.style.cssText = 'width:100%;height:100%;border:none;flex:1;background-color:transparent;';
                            chatContainer.appendChild(iframe);
                            panelContainer.insertBefore(chatContainer, panelContainer.firstChild);
                            bindListener(window, 'popstate', () => {
                                if (chatContainer && chatContainer.style.display !== 'none' && !location.hash.includes('chat')) {
                                    chatContainer.style.display = 'none'; document.body.style.overflow = ''; document.documentElement.style.overflow = '';
                                }
                            });
                        }
                    },
                });
            } else {
                const el = document.getElementById('chatButton');
                if (el) el.remove();
                const chatContainer = document.getElementById('live_chat_container');
                if (chatContainer) {
                    chatContainer.remove();
                    document.body.style.overflow = '';
                    document.documentElement.style.overflow = '';
                }
            }

            if (prefs.enable_pip) {
                targetOrder.push({
                    id: 'pipButton', key: 'pip',
                    icon: 'M19 11h-8v6h8v-6zm4 8V4.98C23 3.88 22.1 3 21 3H3c-1.1 0-2 .88-2 1.98V19c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2zm-2 .02H3V4.97h18v14.05z',
                    fn: () => window.android?.pip(),
                });
            } else {
                const el = document.getElementById('pipButton');
                if (el) el.remove();
            }

            targetOrder.forEach(item => {
                let btn = document.getElementById(item.id);
                if (!btn) btn = createWatchButton(getLocalizedText(item.key), item.icon, item.id, item.fn);
                if (btn) {
                    if (anchor.nextElementSibling !== btn) anchor.after(btn);
                    anchor = btn;
                }
            });

            applyActionBarVisibility(actionBar, prefs);
            window.watchInjected = true;
        };

        const closeBottomSheet = () => {
            const scrimBtn = document.querySelector('.ytWebScrimHiddenButton');
            if (scrimBtn) { scrimBtn.click(); return; }
            const scrim = document.querySelector('ytm-scrim, .ytm-scrim');
            if (scrim) scrim.click();
        };

        const makeSheetItem = (key, iconPath, onClick, type) => {
            const label = getLocalizedText(key);
            const ns = 'http://www.w3.org/2000/svg';

            const createIcon = () => {
                const c3 = document.createElement('c3-icon');
                c3.setAttribute('fill-icon', 'false');
                c3.style.cssText = 'width:24px;height:24px;display:flex;align-items:center;justify-content:center;flex-shrink:0;';
                const span = document.createElement('span');
                span.className = 'yt-icon-shape ytSpecIconShapeHost';
                const innerDiv = document.createElement('div');
                innerDiv.style.cssText = 'width:100%;height:100%;display:block;fill:currentcolor;';
                const svg = document.createElementNS(ns, 'svg');
                svg.setAttribute('xmlns', ns);
                svg.setAttribute('viewBox', '0 0 24 24');
                svg.setAttribute('focusable', 'false');
                svg.setAttribute('aria-hidden', 'true');
                svg.style.cssText = 'pointer-events:none;display:block;width:100%;height:100%;';
                const path = document.createElementNS(ns, 'path');
                path.setAttribute('d', iconPath);
                svg.appendChild(path);
                innerDiv.appendChild(svg);
                span.appendChild(innerDiv);
                c3.appendChild(span);
                return c3;
            };

            const bindClick = (el) => {
                bindListener(el, 'click', (e) => {
                    e.preventDefault(); e.stopPropagation();
                    onClick();
                    closeBottomSheet();
                }, true);
            };

            if (type === 'music') {
                const item = document.createElement('ytmusic-menu-service-item-renderer');
                item.setAttribute('role', 'menuitem');
                item.setAttribute('tabindex', '-1');
                item.setAttribute('aria-disabled', 'false');
                item.dataset.lpCustom = 'true';
                item.innerHTML = `
                    <yt-icon class="icon style-scope ytmusic-menu-service-item-renderer" style="width: 18px; height: 18px;">
                        <span class="yt-icon-shape style-scope yt-icon ytSpecIconShapeHost">
                            <div style="width: 100%; height: 100%; display: block; fill: currentcolor;">
                                <svg xmlns="http://www.w3.org/2000/svg" height="18" viewBox="0 0 24 24" width="18" focusable="false" aria-hidden="true" style="pointer-events: none; display: inherit; width: 100%; height: 100%;"><path d="${iconPath}"></path></svg>
                            </div>
                        </span>
                    </yt-icon>
                    <yt-formatted-string class="text style-scope ytmusic-menu-service-item-renderer">${label}</yt-formatted-string>
                `;
                bindClick(item, () => { onClick(); closeBottomSheet(); });
                return item;
            }

            if (type === 'list') {
                const wrapper = document.createElement('yt-list-item-view-model');
                wrapper.className = 'ytListItemViewModelHost';
                wrapper.setAttribute('role', 'menuitem');
                wrapper.dataset.lpCustom = 'true';

                const layoutWrapper = document.createElement('div');
                layoutWrapper.className = 'ytListItemViewModelLayoutWrapper ytListItemViewModelContainer ytListItemViewModelTappable ytListItemViewModelInPopup ytListItemViewModelNoTrailingText';

                const mainContainer = document.createElement('div');
                mainContainer.className = 'ytListItemViewModelMainContainer';

                const imgContainer = document.createElement('div');
                imgContainer.className = 'ytListItemViewModelImageContainer ytListItemViewModelLeading';
                imgContainer.setAttribute('aria-hidden', 'true');

                const c3 = createIcon();
                c3.className = 'ytListItemViewModelAccessory ytListItemViewModelImage';
                c3.setAttribute('role', 'img');
                c3.style.cssText = 'width:24px;height:24px;display:flex;align-items:center;justify-content:center;';
                imgContainer.appendChild(c3);
                mainContainer.appendChild(imgContainer);

                const btn = document.createElement('button');
                btn.className = 'ytButtonOrAnchorHost ytButtonOrAnchorButton ytListItemViewModelButtonOrAnchor';
                btn.setAttribute('aria-label', label);

                const textWrapper = document.createElement('div');
                textWrapper.className = 'ytListItemViewModelTextWrapper';
                const titleWrapper = document.createElement('div');
                titleWrapper.className = 'ytListItemViewModelTitleWrapper';
                const textSpan = document.createElement('span');
                textSpan.className = 'ytAttributedStringHost ytListItemViewModelTitle ytAttributedStringWhiteSpacePreWrap';
                textSpan.setAttribute('role', 'text');
                textSpan.textContent = label;

                titleWrapper.appendChild(textSpan);
                textWrapper.appendChild(titleWrapper);
                btn.appendChild(textWrapper);
                mainContainer.appendChild(btn);
                layoutWrapper.appendChild(mainContainer);
                wrapper.appendChild(layoutWrapper);

                bindClick(wrapper);
                return wrapper;
            } else {
                const menuItem = document.createElement('ytm-menu-item');
                menuItem.dataset.lpCustom = 'true';
                const btn = document.createElement('button');
                btn.className = 'menu-item-button';
                btn.setAttribute('aria-label', label);

                const c3 = createIcon();
                btn.appendChild(c3);

                const textSpan = document.createElement('span');
                textSpan.className = 'ytAttributedStringHost';
                textSpan.setAttribute('role', 'text');
                textSpan.textContent = label;

                btn.appendChild(textSpan);
                menuItem.appendChild(btn);
                bindClick(btn);
                return menuItem;
            }
        };

        let sheetObserver = null;
        const Sheet = {
            _metadata: null,
            _skip: false,
            _playlistOnly: false,
            _root: null,

            resolveMetadata(root) {
                if (!root) return null;
                const link = root.querySelector('a[href*="/watch"], a[href*="/shorts/"]');
                const href = link?.getAttribute('href') || link?.href;
                if (!href) return null;

                const url = href.startsWith('http') ? href : 'https://m.youtube.com' + href;
                const videoId = getVideoId(url);
                if (!videoId) return null;

                const titleEl = root.querySelector('.media-item-headline, .ytLockupViewModelTitle, h3, .title, .compact-media-item-metadata-content');
                const authorEl = root.querySelector('.media-item-byline, .ytLockupViewModelMetadata, .secondary-text, .compact-media-item-metadata-subtitle');

                return {
                    url,
                    videoId,
                    title: titleEl?.textContent?.trim() || videoId,
                    author: authorEl?.textContent?.trim() || '',
                    thumbnailUrl: `https://img.youtube.com/vi/${videoId}/hqdefault.jpg`
                };
            },

            init() {
                document.addEventListener('click', Sheet.onAnyClick, true);
                if (sheetObserver) return;
                sheetObserver = new MutationObserver(() => {
                    const sheet = document.querySelector('.ytSpecBottomSheetLayoutHost, bottom-sheet-layout, .ytm-bottom-sheet-renderer, ytmusic-menu-popup-renderer');
                    if (!sheet || sheet.querySelector('[data-lp-custom]')) return;

                    const hasItems = sheet.querySelector('.bottom-sheet-media-menu-item, yt-list-item-view-model, ytm-menu-service-item-renderer, ytm-menu-navigation-item-renderer, ytmusic-menu-service-item-renderer, ytmusic-menu-navigation-item-renderer, toggleable-list-item-view-model, ytm-menu-item');
                    if (hasItems) Sheet.inject(sheet);
                });
                sheetObserver.observe(document.body, { childList: true, subtree: true });
            },

            onAnyClick(event) {
                Sheet._metadata = null;
                Sheet._skip = false;
                Sheet._playlistOnly = false;
                Sheet._root = null;

                const pc = getCachedPageClass(location.href);
                if (pc === 'shorts') { Sheet._skip = true; return; }
                if (event.target.closest('ytm-backstage-post-renderer, ytm-post-renderer, ytm-backstage-post-thread-renderer')) {
                    Sheet._skip = true;
                    return;
                }

                const root = event.target.closest('ytm-media-item, yt-lockup-view-model, ytm-rich-item-renderer, .ytLockupViewModelHost, ytm-compact-video-renderer, ytm-video-with-context-renderer, ytm-playlist-renderer, ytm-compact-playlist-renderer, ytm-history-item-renderer, ytmusic-responsive-list-item-renderer, ytmusic-two-row-item-renderer');
                if (!root) return;

                const tag = root.tagName.toLowerCase();
                const hasShorts = !!root.querySelector('a[href*="/shorts/"]');
                const hasWatch = !!root.querySelector('a[href*="/watch"]');
                if (hasShorts && !hasWatch) { Sheet._skip = true; return; }

                Sheet._playlistOnly = tag === 'ytm-playlist-renderer' || tag === 'ytm-compact-playlist-renderer';
                Sheet._root = root;

                const link = root.querySelector('a[href*="/watch"], a[href*="/shorts/"]');
                const href = link?.getAttribute('href') || link?.href;
                if (href) {
                    const url = href.startsWith('http') ? href : 'https://m.youtube.com' + href;
                    const videoId = getVideoId(url);
                    if (videoId) {
                        const title = root.querySelector('.media-item-headline, .ytLockupViewModelTitle, h3, .title')?.textContent?.trim() || videoId;
                        const author = root.querySelector('.media-item-byline, .ytLockupViewModelMetadata, .secondary-text')?.textContent?.trim() || '';
                        Sheet._metadata = { url, videoId, title, author, thumbnailUrl: `https://img.youtube.com/vi/${videoId}/hqdefault.jpg` };
                    }
                }
            },

            inject(sheet) {
                if (Sheet._skip) return;

                let metadata = Sheet.resolveMetadata(Sheet._root) || Sheet._metadata;

                if (!metadata && (getCachedPageClass(location.href) === 'watch' || getCachedPageClass(location.href) === 'music_watch')) {
                    const videoId = getVideoId(location.href);
                    if (videoId) {
                        metadata = {
                            url: location.href,
                            videoId,
                            title: document.querySelector('.slim-video-information-title, .watch-title, ytm-slim-video-metadata-renderer .title, yt-formatted-string.title')?.textContent?.trim() || videoId,
                            author: document.querySelector('.slim-owner-name, .channel-name, ytm-slim-owner-renderer .ytm-slim-owner-renderer-text, yt-formatted-string.byline')?.textContent?.trim() || '',
                            thumbnailUrl: `https://img.youtube.com/vi/${videoId}/hqdefault.jpg`
                        };
                    }
                }

                if (!metadata?.url) return;

                const doInject = () => {
                    if (sheet.querySelector('[data-lp-custom]')) return true;

                    const firstItem = sheet.querySelector('.bottom-sheet-media-menu-item, yt-list-item-view-model, ytm-menu-service-item-renderer, ytm-menu-navigation-item-renderer, ytmusic-menu-service-item-renderer, ytmusic-menu-navigation-item-renderer, toggleable-list-item-view-model');
                    if (!firstItem) return false;

                    const container = firstItem.parentElement;
                    if (!container) return true;

                    let itemType = 'menu';
                    let needsWrapper = null;

                    if (firstItem.tagName.toLowerCase() === 'yt-list-item-view-model') {
                        itemType = 'list';
                    } else if (firstItem.tagName.toLowerCase() === 'ytmusic-menu-service-item-renderer' || firstItem.tagName.toLowerCase() === 'ytmusic-menu-navigation-item-renderer') {
                        itemType = 'music';
                    } else if (firstItem.tagName.toLowerCase() === 'ytm-menu-service-item-renderer') {
                        itemType = 'menu';
                        needsWrapper = 'ytm-menu-service-item-renderer';
                    } else if (firstItem.tagName.toLowerCase() === 'ytm-menu-navigation-item-renderer') {
                        itemType = 'menu';
                        needsWrapper = 'ytm-menu-navigation-item-renderer';
                    }

                    const createItem = (key, icon, fn) => {
                        const el = makeSheetItem(key, icon, fn, itemType);
                        if (needsWrapper) {
                            const wrapper = document.createElement(needsWrapper);
                            wrapper.dataset.lpCustom = 'true';
                            wrapper.appendChild(el);
                            return wrapper;
                        }
                        return el;
                    };

                    if (itemType !== 'music') {
                        const dlItem = createItem('download', 'M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z', () => {
                            if (window.android?.download) android.download(metadata.url);
                        });
                        container.insertBefore(dlItem, container.firstElementChild);
                    }

                    if (!Sheet._playlistOnly) {
                        const qItem = createItem('add_to_queue', 'M4 10h12v2H4zm0-4h12v2H4zm0 8h8v2H4zm10 0v6l5-3z', () => {
                            if (window.android?.addToQueue) android.addToQueue(JSON.stringify(metadata));
                        });
                        container.insertBefore(qItem, container.firstElementChild);
                    }

                    const cw = sheet.querySelector('.ytSpecBottomSheetLayoutContentWrapper');
                    if (cw) cw.style.maxHeight = '80vh';
                    return true;
                };

                if (!doInject()) {
                    let retries = 0;
                    const retry = () => { if (!doInject() && retries++ < 10) st(retry, 150); };
                    st(retry, 100);
                }
            }
        };

        const ensureMusicPlayerDownloadButton = () => {
            if (getCachedPageClass(location.href) !== 'music_watch') return;
            const controls = document.querySelector('ytmusic-player-controls') || document.querySelector('ytmusic-player');
            if (!controls) return;
            const bylineWrapper = controls.querySelector('.byline-wrapper') || document.querySelector('.byline-wrapper');
            if (!bylineWrapper || document.getElementById('_lp_music_dl_btn')) return;

            const btn = document.createElement('button');
            btn.id = '_lp_music_dl_btn';
            btn.setAttribute('aria-label', getLocalizedText('download'));
            btn.style.cssText = 'background: transparent; border: none; color: var(--ytmusic-text-primary, #fff); cursor: pointer; padding: 4px; margin-left: 8px; display: inline-flex; align-items: center; border-radius: 50%; -webkit-tap-highlight-color: transparent;';
            btn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" height="20" viewBox="0 0 24 24" width="20" fill="currentColor" style="pointer-events: none;"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"></path></svg>';

            bylineWrapper.appendChild(btn);
            bindListener(btn, 'click', (e) => {
                e.preventDefault(); e.stopPropagation();
                if (window.android?.download) android.download(location.href);
            }, true);
        };

        Sheet.init();
        startLibraryObserver();
        startWatchObserver();

        document.addEventListener('visibilitychange', () => {
            if (!document.hidden) { const pc = getCachedPageClass(location.href); if (pc === 'you' || pc === 'library') ensureLibraryButton(); }
        }, { passive: true });

        const updatePageClass = (url) => {
            cachedPageClass = null;
            const pc = getCachedPageClass(url || location.href);
            if (pc && window.pageClass !== pc) {
                window.pageClass = pc;
                document.documentElement.setAttribute('page-class', pc);
                window.dispatchEvent(new Event('onPageClassChange'));
            }
        };

        window.addEventListener('onProgressChangeFinish', () => { updatePageClass(); if (window.android?.finishRefresh) android.finishRefresh(); });
        window.addEventListener('onRefresh', () => location.reload());
        window.addEventListener('doUpdateVisitedHistory', () => {
            const pc = getCachedPageClass(location.href);
            if (window.android?.setRefreshLayoutEnabled) android.setRefreshLayoutEnabled(['home', 'subscriptions', '@', 'library', 'music'].includes(pc));
            if (window.android?.finishRefresh) android.finishRefresh();
        });

        const handlePlayerVisibility = () => {
            const url = location.href;
            cachedPageClass = null;
            updatePageClass(url);
            const pc = getCachedPageClass(url);
            if (pc === 'watch' && window.android?.play) android.play(url);
            if (pc === 'shorts') st(() => { if (!document.querySelector('ytm-shorts, shorts-video, #shorts-container, ytm-reel-watch-sequence')) location.href = url; }, 1200);
            if (pc === 'you' || pc === 'library') {
                document.documentElement.classList.add('lp-library-loading');
                document.documentElement.classList.remove('lp-library-loaded');
                window.watchInjected = false;
                ensureLibraryButton();
            }
            if (pc === 'watch') {
                window.watchInjected = false;
                startWatchObserver();
            }
        };

        window.addEventListener('popstate', handlePlayerVisibility);
        ['pushState', 'replaceState'].forEach(name => {
            const original = history[name];
            history[name] = function() {
                original.apply(this, arguments);
                window.watchInjected = false;
                handlePlayerVisibility();
            };
        });

        const resizeObserver = new ResizeObserver(() => {
            if (getCachedPageClass(location.href) !== 'watch') return;
            const p = document.querySelector('#movie_player');
            if (p && p.clientHeight > 10 && window.android?.setPlayerHeight) android.setPlayerHeight(p.clientHeight);
        });

        window.__liteSyncInFlight = false;

        const getMusicPlayer = () => document.querySelector('#movie_player') || document.querySelector('ytmusic-player')?.player_;
        const getMusicVideo = () => document.querySelector('ytmusic-player video') || document.querySelector('#movie_player video');

        window.__syncNativeLoading = function() {
            if (getCachedPageClass(location.href) !== 'music_watch') return;
            const p = getMusicPlayer();
            const v = getMusicVideo();
            if (p && typeof p.pauseVideo === 'function') p.pauseVideo();
            else if (v) v.pause();
        };

        window.__syncNativeReady = function() {
            if (getCachedPageClass(location.href) !== 'music_watch') return;
            const p = getMusicPlayer();
            const v = getMusicVideo();
            if (p && typeof p.playVideo === 'function') {
                if (!p.isMuted?.()) p.mute?.();
                p.playVideo();
            } else if (v) {
                v.muted = true;
                v.play()?.catch(() => {});
            }
        };

        window.__syncNativePlay = function() {
            if (getCachedPageClass(location.href) !== 'music_watch') return;
            const p = getMusicPlayer();
            const v = getMusicVideo();
            if (p && typeof p.playVideo === 'function') {
                window.__liteSyncInFlight = true;
                p.playVideo();
                setTimeout(() => { window.__liteSyncInFlight = false; }, 500);
            } else if (v && v.paused) {
                window.__liteSyncInFlight = true;
                try { v.muted = true; v.play()?.catch(() => {}); } catch(e) {}
                setTimeout(() => { window.__liteSyncInFlight = false; }, 500);
            }
        };

        window.__syncNativePause = function() {
            if (getCachedPageClass(location.href) !== 'music_watch') return;
            const p = getMusicPlayer();
            const v = getMusicVideo();
            if (p && typeof p.pauseVideo === 'function') {
                window.__liteSyncInFlight = true;
                p.pauseVideo();
                setTimeout(() => { window.__liteSyncInFlight = false; }, 500);
            } else if (v && !v.paused) {
                window.__liteSyncInFlight = true;
                try { v.pause(); } catch(e) {}
                setTimeout(() => { window.__liteSyncInFlight = false; }, 500);
            }
        };

        window.__syncNativeSeek = function(seconds) {
            if (getCachedPageClass(location.href) !== 'music_watch') return;
            const p = getMusicPlayer();
            const v = getMusicVideo();
            if (!p && !v) return;
            window.__liteSyncInFlight = true;
            try {
                if (p && typeof p.seekTo === 'function') p.seekTo(seconds);
                else if (v) v.currentTime = seconds;
            } catch(e) {}
            setTimeout(() => { window.__liteSyncInFlight = false; }, 500);
        };

        window.__syncNativeProgress = function(seconds) {
            if (getCachedPageClass(location.href) !== 'music_watch') return;
            const v = getMusicVideo();
            if (!v) return;
            window.__liteSyncInFlight = true;
            try {
                if (Math.abs(v.currentTime - seconds) > 2) v.currentTime = seconds;
            } catch(e) {}
            setTimeout(() => { window.__liteSyncInFlight = false; }, 500);
        };

        const setupMusicVideoListeners = () => {
            const v = getMusicVideo();
            if (!v || v.dataset.lpSyncBound === 'true') return;
            v.dataset.lpSyncBound = 'true';

            v.addEventListener('play', () => {
                if (!window.__liteSyncInFlight) window.android?.musicPlay?.();
            });
            v.addEventListener('pause', () => {
                if (!window.__liteSyncInFlight) window.android?.musicPause?.();
            });
            v.addEventListener('seeked', () => {
                if (!window.__liteSyncInFlight) window.android?.musicSeek?.(Math.floor(v.currentTime * 1000));
            });
        };

        document.addEventListener('animationstart', (e) => {
            if (e.animationName !== 'nodeInserted') return;
            const node = e.target, pc = getCachedPageClass(location.href);
            if (pc === 'music_watch' && (node.id === 'movie_player' || node.tagName.toLowerCase() === 'ytmusic-player')) {
                setTimeout(setupMusicVideoListeners, 500);
            }
            if (node.id === 'movie_player') {
                if (pc === 'watch') {
                    node.mute?.();
                    const resumePos = (window.android?.getResumePosition ? android.getResumePosition(getVideoId(location.href)) : 0) / 1000;
                    node.seekTo?.(resumePos || (node.getDuration() / 2));
                    node.addEventListener('onStateChange', s => { if (s === 1 && !window.__liteSyncInFlight) node.pauseVideo?.(); });
                    document.body.style.setProperty('overflow', 'auto', 'important');
                    document.documentElement.style.setProperty('overflow', 'auto', 'important');
                    document.body.style.setProperty('position', 'relative', 'important');
                }
                resizeObserver.disconnect(); resizeObserver.observe(node);
            } else if (pc === 'watch' && node.classList.contains('watch-below-the-player')) {
                node.style.setProperty('overflow', 'visible', 'important');
                node.style.setProperty('height', 'auto', 'important');
                node.style.setProperty('touch-action', 'auto', 'important');
            }
        }, false);

        bindShortsGesture();

        let lastPC = null, cachedHeaderElement = null, cachedSuggestionsElement = null;

        setInterval(() => {
            if (document.hidden) return;
            const pc = getCachedPageClass(location.href);
            const prefs = getPrefs();
            if (pc === 'you' || pc === 'library') {
                ensureLibraryButton();
            } else {
                removeIncognitoFallbackBanner();
            }

            if (pc !== lastPC) { cachedHeaderElement = null; lastPC = pc; }

            if (pc === 'watch') {
                ensureWatchButtons();
                if (!cachedHeaderElement) cachedHeaderElement = document.querySelector('ytm-header-bar-renderer');
                if (cachedHeaderElement) cachedHeaderElement.style.setProperty('display', 'none', 'important');
                document.body.style.setProperty('padding-top', '0', 'important');
                const ad = document.querySelector('.ad-showing video');
                if (ad) ad.currentTime = ad.duration;
                const mp = document.querySelector('#movie_player');
                if (mp && !window.__liteSyncInFlight) { mp.mute?.(); mp.pauseVideo?.(); }
            } else if (pc === 'music_watch') {
                setupMusicVideoListeners();
                ensureMusicPlayerDownloadButton();
                const v = getMusicVideo();
                if (v) v.muted = true;
            } else if (pc === 'shorts') {
                if (!cachedHeaderElement) cachedHeaderElement = document.querySelector('ytm-header-bar-renderer, .ytm-header-bar-renderer');
                if (cachedHeaderElement) cachedHeaderElement.style.setProperty('display', 'none', 'important');
                document.querySelectorAll('#home-icon, .logo-in-player, [aria-label*="Search"], .topbar-menu-button-avatar-button, .header-bar-search-button, .header-search-button, .search-button').forEach(el => el.style.setProperty('display', 'none', 'important'));

                document.querySelectorAll('yt-shorts-suggested-action-view-model:not([data-lp-tagged])').forEach(el => {
                    const text = el.textContent.toLowerCase();
                    if (text.includes('view products')) el.classList.add('lp-shorts-product-banner');
                    else if (text.includes('search "')) el.classList.add('lp-shorts-search-suggestion');
                    el.dataset.lpTagged = 'true';
                });
            } else {
                if (!cachedHeaderElement) cachedHeaderElement = document.querySelector('ytm-header-bar-renderer');
                if (cachedHeaderElement) {
                    cachedHeaderElement.style.setProperty('display', 'flex', 'important');
                    cachedHeaderElement.querySelector('ytm-home-logo')?.style.removeProperty('display');
                    cachedHeaderElement.querySelectorAll('.header-bar-search-button, .header-search-button').forEach(el => el.style.removeProperty('display'));
                }
                document.body.style.removeProperty('padding-top');
            }

            if (!cachedSuggestionsElement) cachedSuggestionsElement = document.querySelector('.yt-searchbox-suggestions-container');
            if (cachedSuggestionsElement) cachedSuggestionsElement.style.display = prefs.show_search_suggestions === false ? 'none' : '';

            document.documentElement.classList.toggle('lp-hide-comments', prefs.hide_comments === true);
            const hideLikes = prefs.action_bar_show_like_dislike === false;
            document.documentElement.classList.toggle('lp-hide-likes', hideLikes);
            document.documentElement.classList.toggle('lp-hide-shorts-like', prefs.shorts_show_like === false);
            document.documentElement.classList.toggle('lp-hide-shorts-dislike', prefs.shorts_show_dislike === false);
            document.documentElement.classList.toggle('lp-hide-shorts-comments', prefs.shorts_show_comments === false);
            document.documentElement.classList.toggle('lp-hide-shorts-share', prefs.shorts_show_share === false);
            document.documentElement.classList.toggle('lp-hide-shorts-search-suggestion', prefs.shorts_show_search_suggestion === false);
            document.documentElement.classList.toggle('lp-hide-shorts-product-banner', prefs.shorts_show_product_banner === false);

            document.querySelectorAll('.mobile-topbar-back-arrow').forEach(el => {
                const mode = el.closest('[data-mode]')?.dataset.mode;
                if (['settings', 'history', 'playlist'].includes(mode) && el.dataset.liteGoBackBound !== 'true') {
                    bindListener(el, 'click', e => {
                        e.preventDefault(); e.stopImmediatePropagation();
                        if (window.android?.goBack) android.goBack();
                    }, true);
                    el.dataset.liteGoBackBound = 'true';
                }
            });

            if (pc === 'select_site') {
                const settings = document.querySelector('ytm-settings');
                if (settings && !settings.dataset.buttonsInjected && settings.firstElementChild) {
                    const base = settings.firstElementChild;
                    const btns = [
                        { id: 'extensionButton', key: 'extension', icon: 'M497-120l-33-124q-15-7-30-16t-28-20l-116 50-70-121 98-88q-2-10-3-20t-1-20q0-10 1-20t3-20l-98-88 70-121 116 50q13-11 28-20t30-16l33-124h140l33 124q15 7 30 16t28 20l116-50 70 121-98 88q2 10 3 20t1 20q0 10-1 20t-3 20l98 88-70 121-116-50q-13 11-28 20t-30 16l-33 124H497Zm70-227q55 0 94-39t39-94q0-55-39-94t-94-39q-55 0-94 39t-39 94q0 55 39 94t94 39Z', fn: () => window.android?.extension() },
                        { id: 'downloadButton', key: 'downloads', icon: 'M480-336 288-528l51-51 105 105v-342h72v342l105-105 51 51-192 192ZM263.72-192Q234-192 213-213.15T192-264v-72h72v72h432v-72h72v72q0 29.7-21.16 50.85Q725.68-192 695.96-192H263.72Z', fn: () => window.android?.download() },
                        { id: 'aboutButton', key: 'about', icon: 'M444-288h72v-240h-72v240Zm35.79-312q15.21 0 25.71-10.29t10.5-25.5q0-15.21-10.29-25.71t-25.5-10.5q-15.21 0-25.71 10.29t-10.5 25.5q0 15.21 10.29 25.71t25.5 10.5Zm.49 504Q401-96 331-126t-122.5-82.5Q156-261 126-330.96t-30-149.5Q96-560 126-629.5q30-69.5 82.5-122T330.96-834q69.96-30 149.5-30t149.04 30q69.5 30 122 82.5T834-629.28q30 69.73 30 149Q864-401 834-331t-82.5 122.5Q699-156 629.28-126q-69.73 30-149 30Zm0-72q130 0 221-91t91-221q0-130-91-221t-221-91q-130 0-221 91t-91 221q0 130 91 221t221 91Zm0-312Z', fn: () => window.android?.about() },
                    ];
                    btns.forEach(b => {
                        const el = createCustomSettingBtn(base, b.id, b.key, b.icon, b.fn);
                        if (el) b.id === 'aboutButton' ? settings.appendChild(el) : settings.insertBefore(el, settings.firstElementChild);
                    });
                    settings.dataset.buttonsInjected = 'true';
                }
            }
        }, 1500);

        document.addEventListener('click', e => {
            handleWatchTimestampClick(e);
            const a = e.target.closest('a'), logo = e.target.closest('ytm-home-logo'), nav = e.target.closest('ytm-pivot-bar-item-renderer');
            let href = nav?.data?.navigationEndpoint?.commandMetadata?.webCommandMetadata?.url || (a?.href ? a.getAttribute('href') : (logo ? '/' : null));
            if (!href) return;
            const url = href.startsWith('http') ? href : 'https://m.youtube.com' + href;
            const targetClass = getCachedPageClass(url);
            if (targetClass !== getCachedPageClass(location.href)) { e.preventDefault(); e.stopImmediatePropagation(); requestOpenTab(url, targetClass); }
        }, true);

        addTapEvent(document, e => {
            const renderer = e.target.closest('ytm-post-multi-image-renderer');
            if (renderer && window.android?.onPosterLongPress) android.onPosterLongPress(JSON.stringify([...renderer.querySelectorAll('ytm-backstage-image-renderer')].map(el => el?.data?.image?.thumbnails?.at(-1)?.url)));
        });

        let longPressTimer;
        const handleLongPress = (e) => {
            if (getPrefs().enable_long_press_menu === false) return;
            const el = e.target, a = el.closest('a');
            let url = a?.href || (el.closest('ytm-compact-video-renderer, ytm-video-with-context-renderer, ytm-playlist-renderer')?.data?.navigationEndpoint || a?.data?.navigationEndpoint)?.commandMetadata?.webCommandMetadata?.url;
            if (url && !url.startsWith('http')) url = 'https://m.youtube.com' + url;
            if (url && (url.includes('/watch') || url.includes('list='))) {
                if (e.type === 'touchstart') { ct(longPressTimer); longPressTimer = st(() => { if (window.android?.showVideoOptions) android.showVideoOptions(url, 'Options'); }, 600); }
                else if (e.type === 'contextmenu') { e.preventDefault(); if (window.android?.showVideoOptions) android.showVideoOptions(url, 'Options'); }
            }
        };
        document.addEventListener('touchstart', handleLongPress, { passive: true, capture: true });
        ['touchend', 'touchmove'].forEach(ev => document.addEventListener(ev, () => ct(longPressTimer), { passive: true, capture: true }));
        document.addEventListener('contextmenu', handleLongPress, true);

        updatePageClass();
        window.injected = true;
    }
} catch (e) { console.error(e); }