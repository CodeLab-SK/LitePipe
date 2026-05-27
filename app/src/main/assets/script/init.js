try {
    if (!window.injected) {
        const st = setTimeout.bind(window), si = setInterval.bind(window);
        const ct = clearTimeout.bind(window), ci = clearInterval.bind(window);

        const getLocalizedText = (key) => {
            const languages = {
                'zh': { 'download': '下载', 'downloads': '下载', 'extension': 'LitePipe 设置', 'chat': '聊天室', 'about': '关于', 'pip': '画中画', 'incognito': '无痕模式', 'incognito_off': '关闭无痕模式' },
                'zt': { 'download': '下載', 'downloads': '下載', 'extension': 'LitePipe 設置', 'chat': '聊天室', 'about': '關於', 'pip': '畫中畫', 'incognito': '無痕模式', 'incognito_off': '關閉無痕模式' },
                'en': { 'download': 'Download', 'downloads': 'Downloads', 'extension': 'LitePipe Settings', 'chat': 'Chat', 'about': 'About', 'pip': 'PiP', 'incognito': 'Turn on Incognito', 'incognito_off': 'Turn off Incognito' },
                'ja': { 'download': 'ダウンロード', 'downloads': 'ダウンロード', 'extension': 'LitePipe 設定', 'chat': 'チャット', 'about': '詳細', 'pip': 'PiP', 'incognito': 'シークレットをオン', 'incognito_off': 'シークレットをオフ' },
                'ko': { 'download': '다운로드', 'downloads': '다운로드', 'extension': 'LitePipe 플러그인', 'chat': '채팅', 'about': '정보', 'pip': 'PiP', 'incognito': '시크릿 모드 켜기', 'incognito_off': '시크릿 모드 끄기' },
                'fr': { 'download': 'Télécharger', 'downloads': 'Téléchargements', 'extension': 'Paramètres LitePipe', 'chat': 'Chat', 'about': 'À propos', 'pip': 'PiP', 'incognito': 'Activer navigation privée', 'incognito_off': 'Désactiver navigation privée' },
                'ru': { 'download': 'Скачать', 'downloads': 'Загрузки', 'extension': 'Настройки LitePipe', 'chat': 'Чат', 'about': 'О программе', 'pip': 'PiP', 'incognito': 'Вкл. инкогнито', 'incognito_off': 'Выкл. инкогнито' },
                'tr': { 'download': 'İndir', 'downloads': 'İndirilenler', 'extension': 'LitePipe Ayarları', 'chat': 'Sohbet', 'about': 'Hakkında', 'pip': 'PiP', 'incognito': 'Gizli modu aç', 'incognito_off': 'Gizli modu kapat' },
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

        const INCOGNITO_ICON = 'M12 2C8.13 2 5 5.13 5 9H19C19 5.13 15.87 2 12 2ZM2 11H22V13H2V11ZM7.5 14.5C5.57 14.5 4 16.07 4 18C4 19.93 5.57 21.5 7.5 21.5C9.43 21.5 11 19.93 11 18C11 16.07 9.43 14.5 7.5 14.5ZM16.5 14.5C14.57 14.5 13 16.07 13 18C13 19.93 14.57 21.5 16.5 21.5C18.43 21.5 20 19.93 20 18C20 16.07 18.43 14.5 16.5 14.5Z';
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

        let shortsSpeedPressState = null;
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

        const createNativeYtButton = (label, iconPath, isOn, clickFn, sourceEl = null) => {
            const source =
                sourceEl ||
                document.querySelector(
                    'button-view-model.ytSpecButtonViewModelHost'
                ) ||
                document.querySelector(
                    '.slim_video_action_bar_renderer_button'
                );

            if (!source) return null;

            const clone = source.cloneNode(true);
            clone.style.overflow = 'visible';
            clone.removeAttribute('id');

            const isActionBarButton = !!(source && (source.classList?.contains('slim_video_action_bar_renderer_button') || source.matches?.('.slim_video_action_bar_renderer_button') || source.closest?.('.slim_video_action_bar_renderer_button')));

            const clickable =
                clone.querySelector('button, a');

            if (!clickable) return null;

            if (clickable.tagName.toLowerCase() === 'a') {
                const btn = document.createElement('button');

                [...clickable.attributes].forEach(attr => {
                    if (attr.name !== 'href') {
                        btn.setAttribute(attr.name, attr.value);
                    }
                });

                btn.className = clickable.className;
                btn.innerHTML = clickable.innerHTML;

                clickable.replaceWith(btn);
            }

            const btn =
                clone.querySelector('button');

            if (!btn) return null;

            btn.removeAttribute('href');
            btn.removeAttribute('target');
            btn.removeAttribute('rel');

            btn.setAttribute('aria-label', label);

            const textEl =
                clone.querySelector(
                    '.ytSpecButtonShapeNextButtonTextContent'
                );

            if (textEl) {
                textEl.textContent = label;
            }

            (function updateIcon() {
                const spanHost = clone.querySelector('span.yt-icon-shape, span.ytSpecIconShapeHost, .yt-icon-shape');
                const useLargeViewBox = /-?\d{3,}/.test(iconPath);
                const viewBox = useLargeViewBox ? '0 -960 960 960' : '0 0 24 24';
                const ns = 'http://www.w3.org/2000/svg';
                const makeSvg = (d) => {
                    const s = document.createElementNS(ns, 'svg');
                    s.setAttribute('viewBox', viewBox);
                    const iconSize = isActionBarButton ? '28' : '24';
                    s.setAttribute('width', iconSize);
                    s.setAttribute('height', iconSize);
                    s.setAttribute('aria-hidden', 'true');
                    s.setAttribute('fill', 'currentColor');
                    s.setAttribute('focusable', 'false');
                    s.style.cssText = 'width:' + iconSize + 'px;height:' + iconSize + 'px;display:block;';
                    s.innerHTML = '<path d="' + d + '"></path>';
                    return s;
                };

                if (spanHost) {
                    try {
                        const existing = spanHost.querySelector('svg');
                        if (existing && existing instanceof SVGElement) {
                            existing.setAttribute('viewBox', viewBox);
                            existing.setAttribute('width', '24'); existing.setAttribute('height', '24');
                            const p = existing.querySelector('path');
                            if (p) p.setAttribute('d', iconPath); else existing.innerHTML = '<path d="' + iconPath + '"></path>';
                        } else {
                            while (spanHost.firstChild) spanHost.removeChild(spanHost.firstChild);
                            const wrapperDiv = document.createElement('div');
                            wrapperDiv.style.cssText = 'width:100%;height:100%;display:block;fill:currentcolor;';
                            const s = makeSvg(iconPath);
                            wrapperDiv.appendChild(s);
                            spanHost.appendChild(wrapperDiv);
                            try { spanHost.style.width = '100%'; spanHost.style.height = '100%'; } catch (e) {}
                        }
                    } catch (e) {}
                    return;
                }

                const containerSelectors = [
                    '.yt-spec-button-shape-next__icon',
                    '.yt-spec-button-shape__icon',
                    '.ytm-button__icon',
                    '.yt-spec-icon-shape',
                    '.ytm-spec-icon-shape',
                    '.icon',
                    '.ytm-settings-item-icon'
                ];

                let container = null;
                for (const sel of containerSelectors) {
                    container = clone.querySelector(sel);
                    if (container) break;
                }

                let svg = container ? container.querySelector('svg') : null;
                if (!svg) svg = clone.querySelector('svg');

                const ensureSvg = (parent) => {
                    let s = parent.querySelector('svg');
                    if (s && s instanceof SVGElement) return s;
                    s = document.createElementNS(ns, 'svg');
                    const iconSize = isActionBarButton ? '28' : '24';

                    s.setAttribute('width', iconSize); s.setAttribute('height', iconSize);
                    parent.appendChild(s);
                    return s;
                };

                const host = container || (svg && svg.parentElement) || clone;
                if (!host) return;

                const targetSvg = ensureSvg(host);
                targetSvg.setAttribute('viewBox', viewBox);
                targetSvg.setAttribute('aria-hidden', 'true');
                targetSvg.setAttribute('fill', 'currentColor');
                targetSvg.setAttribute('focusable', 'false');
                const targetSize = isActionBarButton ? '28' : '24';
                targetSvg.style.cssText = 'width:' + targetSize + 'px;height:' + targetSize + 'px;display:block;flex-shrink:0;';
                const path = targetSvg.querySelector('path');
                if (path) path.setAttribute('d', iconPath); else targetSvg.innerHTML = '<path d="' + iconPath + '"></path>';

                const custom = host.querySelector('yt-icon, c3-icon, ytm-icon');
                if (custom && custom.parentElement && !custom.querySelector('svg')) {
                    try {
                        const s = makeSvg(iconPath);
                        custom.insertAdjacentElement('afterend', s);
                    } catch (e) {}
                }
            })();

            if (isActionBarButton) {
                try {
                    const host = clone.querySelector('.yt-spec-button-shape-next__icon, .yt-spec-button-shape__icon, .ytm-button__icon, .yt-spec-icon-shape, .ytm-spec-icon-shape, .icon, span.yt-icon-shape') || clone;
                    const targetSize = '24';
                    if (host && host.style) {
                        host.style.display = 'inline-flex';
                        host.style.alignItems = 'center';
                        host.style.justifyContent = 'center';
                        host.style.width = host.style.width || targetSize + 'px';
                        host.style.height = host.style.height || targetSize + 'px';
                        host.style.minWidth = host.style.minWidth || targetSize + 'px';
                    }
                    if (textEl && textEl.style) {
                        textEl.style.display = 'inline-flex';
                        textEl.style.alignItems = 'center';
                        textEl.style.height = targetSize + 'px';
                        textEl.style.lineHeight = targetSize + 'px';
                    }
                } catch (e) {}
            }

            if (isOn) {
                btn.style.outline = '2px solid currentColor';
            } else {
                btn.style.outline = '';
            }

            const newBtn = btn.cloneNode(true);
            btn.replaceWith(newBtn);
            newBtn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
                clickFn?.();
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

        const createYtActionButton = (id, textKey, iconD, clickFn) => {
            if (document.getElementById(id)) return null;
            const label = getLocalizedText(textKey);
            const source =
                document.querySelector(
                    '.slim_video_action_bar_renderer_button'
                ) ||
                document.querySelector(
                    'ytm-slim-video-action-bar-renderer button'
                ) ||
                document.querySelector(
                    'ytm-toggle-button-renderer'
                ) ||
                document.querySelector(
                    'button-view-model.ytSpecButtonViewModelHost'
                );

            const wrapper =
                createNativeYtButton(
                    label,
                    iconD,
                    false,
                    clickFn,
                    source
                );
            if (!wrapper) return null;
            wrapper.id = id;
            return wrapper;
        };

        const updateIncognitoChip = (chip, isOn) => {
            const btn = chip.querySelector('button');
            btn.style.overflow = 'visible';
            const textEl = chip.querySelector('.ytSpecButtonShapeNextButtonTextContent');
            if (textEl) textEl.textContent = isOn ? getLocalizedText('incognito_off') : getLocalizedText('incognito');
            if (btn) btn.style.outline = isOn ? '2px solid currentColor' : '';
        };

        const injectIncognitoFallbackBanner = () => {
            if (document.getElementById('_lp_incognito_banner')) return;
            const banner = document.createElement('div');
            banner.id = '_lp_incognito_banner';
            banner.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:99998;display:flex;align-items:center;justify-content:space-between;padding:0 16px;height:48px;background:#212121;color:#fff;font-size:14px;font-family:inherit;box-sizing:border-box;';
            const ns = 'http://www.w3.org/2000/svg';
            const iconWrap = document.createElement('span');
            iconWrap.style.cssText = 'display:inline-flex;align-items:center;gap:8px;font-weight:500;';
            const svg = document.createElementNS(ns, 'svg');
            svg.setAttribute('viewBox', '0 0 24 24'); svg.setAttribute('width', '20'); svg.setAttribute('height', '20');
            svg.style.cssText = 'fill:currentColor;display:block;flex-shrink:0;';
            const p = document.createElementNS(ns, 'path');
            p.setAttribute('d', INCOGNITO_ICON);
            svg.appendChild(p);
            const lbl = document.createElement('span');
            lbl.textContent = getLocalizedText('incognito_off');
            iconWrap.appendChild(svg); iconWrap.appendChild(lbl);
            const offBtn = document.createElement('button');
            offBtn.textContent = getLocalizedText('incognito_off');
            offBtn.style.cssText = 'background:rgba(255,255,255,0.15);border:none;color:#fff;font-size:13px;font-weight:500;padding:6px 14px;border-radius:18px;cursor:pointer;font-family:inherit;white-space:nowrap;-webkit-tap-highlight-color:transparent;';
            offBtn.addEventListener('click', (e) => {
                e.preventDefault(); e.stopPropagation();
                if (window.android?.toggleIncognito) android.toggleIncognito();
            }, true);
            banner.appendChild(iconWrap); banner.appendChild(offBtn);
            document.body.appendChild(banner);
            document.body.style.paddingTop = '48px';
        };

        const removeIncognitoFallbackBanner = () => {
            const b = document.getElementById('_lp_incognito_banner');
            if (b) { b.remove(); document.body.style.paddingTop = ''; }
        };

        const _ensureHeaderStyles = () => {
            if (document.getElementById('_lp_header_styles')) return;
            const style = document.createElement('style');
            style.id = '_lp_header_styles';
            style.textContent = `
            yt-flexible-actions-view-model::-webkit-scrollbar {
                display:none !important;
            }

            .ytFlexibleActionsViewModelActionRow {
                -webkit-tap-highlight-color:transparent !important;
                user-select:none !important;
                outline:none !important;

                display:flex !important;
                flex-wrap:nowrap !important;
                width:max-content !important;
                min-width:100% !important;
                align-items:center !important;
            }

            .ytFlexibleActionsViewModelActionRow * {
                -webkit-tap-highlight-color:transparent !important;
                outline:none !important;
                -webkit-user-drag:none !important;
            }

            .ytFlexibleActionsViewModelAction:empty {
                display:none !important;
            }
            `;

            document.head.appendChild(style);
        };

        let incognitoObserver = null;
        let currentActionRow = null;
        let injectScheduled = false;

        const getActionRow = () => {
            return (
                document.querySelector(
                    'yt-flexible-actions-view-model .ytFlexibleActionsViewModelActionRow'
                ) ||
                document.querySelector(
                    '.ytFlexibleActionsViewModelActionRow'
                )
            );
        };

        const scheduleInject = () => {
            if (injectScheduled) return;

            injectScheduled = true;

            requestAnimationFrame(() => {
                requestAnimationFrame(() => {
                    injectScheduled = false;
                    injectIncognito();
                });
            });
        };

        const injectIncognito = () => {
            const pc = getCachedPageClass(location.href);

            if (pc !== 'you' && pc !== 'library') {
                removeIncognitoFallbackBanner();
                return;
            }

            const row = getActionRow();
            const isOn = window.android?.isIncognito?.() === true;

            if (!row) {
                if (isOn) injectIncognitoFallbackBanner();
                return;
            }

            removeIncognitoFallbackBanner();

            if (
                currentActionRow !== row ||
                !document.contains(currentActionRow)
            ) {
                currentActionRow = row;
                attachRowObserver(row);
            }

            let chip = row.querySelector('#_lp_incognito_chip');

            if (!chip) {
                chip = createNativeYtButton(
                    isOn
                        ? getLocalizedText('incognito_off')
                        : getLocalizedText('incognito'),
                    INCOGNITO_ICON,
                    isOn,
                    () => {
                        if (window.android?.toggleIncognito) {
                            android.toggleIncognito();
                        }

                        requestAnimationFrame(() => {
                            scheduleInject();
                        });
                    }
                );

                if (!chip) return;

                chip.id = '_lp_incognito_chip';
                chip.dataset.lpIncognito = 'true';

                const wrap = document.createElement('div');
                wrap.className =
                    'ytFlexibleActionsViewModelAction ytFlexibleActionsViewModelActionRowAction';
                wrap.appendChild(chip);
                row.prepend(wrap);
                wrap.dataset.lpProtected = 'true';
            }

            updateIncognitoChip(chip, isOn);
            document.documentElement.classList.remove('lp-actionbar-hidden');

            const chipWrap = chip.parentElement;

            if (chipWrap && row.firstElementChild !== chipWrap) {
                row.prepend(chipWrap);
            }
        };

        const attachRowObserver = (row) => {
            if (incognitoObserver) {
                incognitoObserver.disconnect();
            }

            incognitoObserver = new MutationObserver(() => {

                if (!document.contains(row)) {
                    currentActionRow = null;
                    scheduleInject();
                    return;
                }

                if (!row.querySelector('#_lp_incognito_chip')) {
                    scheduleInject();
                }

                if (!row.querySelector('[data-lp-protected="true"]')) {
                    scheduleInject();
                }
            });

            incognitoObserver.observe(row, {
                childList: true
            });
        };

        const startIncognitoSystem = () => {
            _ensureHeaderStyles();
            document.documentElement.classList.add('lp-actionbar-hidden');
            scheduleInject();

            [
                'yt-navigate-finish',
                'yt-page-data-updated',
                'yt-page-type-changed'
            ].forEach(evt => {
                document.addEventListener(evt, () => {
                    currentActionRow = null;

                    requestAnimationFrame(() => {
                        requestAnimationFrame(() => {
                            scheduleInject();
                        });
                    });
                }, true);
            });
        };

        startIncognitoSystem();

        document.addEventListener('visibilitychange', () => {
            if (!document.hidden) { const pc = getCachedPageClass(location.href); if (pc === 'you' || pc === 'library') scheduleInject(); }
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
            if (window.android?.setRefreshLayoutEnabled) android.setRefreshLayoutEnabled(['home', 'subscriptions', '@'].includes(pc));
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
                window.watchInjected = false;
                requestAnimationFrame(() => {
                    requestAnimationFrame(() => {
                        scheduleInject();
                    });
                });
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
        window.__syncNativeProgress = function(seconds) {
            const p = document.querySelector('#movie_player');
            if (!p || window.__liteSyncInFlight) return false;
            window.__liteSyncInFlight = true;
            try {
                p.mute?.(); p.seekTo?.(seconds); p.playVideo?.();
                setTimeout(() => { p.pauseVideo?.(); window.__liteSyncInFlight = false; }, 2000);
                return true;
            } catch (e) { window.__liteSyncInFlight = false; return false; }
        };

               document.addEventListener('animationstart', (e) => {
                   if (e.animationName !== 'nodeInserted') return;
                   const node = e.target, pc = getCachedPageClass(location.href);
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

        let cachedPrefs = null, lastPrefCheckTime = 0;
        const PREF_CACHE_DURATION = 1500;
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

        const watchObserver = new MutationObserver(() => {
            const actionBar = document.querySelector('ytm-slim-video-action-bar-renderer');
            if (!actionBar || getCachedPageClass(location.href) !== 'watch') return;

            const prefs = getPrefs();
            actionBar.style.overflow = 'visible';

            const container = actionBar.querySelector('#menu') || actionBar.querySelector('.slim-video-action-bar-actions') || actionBar;
            let anchor = container.querySelector('ytm-segmented-like-dislike-button-renderer') ||
                         container.querySelector('ytm-toggle-button-renderer') ||
                         container.querySelector('segmented-like-dislike-button-view-model') ||
                         container.firstElementChild;
            if (!anchor) return;

            const moviePlayer = document.querySelector('#movie_player');
            const isLive = moviePlayer?.getPlayerResponse?.()?.playabilityStatus?.liveStreamability && location.href.includes('/watch');

            const targetOrder = [];
            if (!isLive) {
                targetOrder.push({
                    id: 'downloadButton', key: 'download',
                    icon: 'M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z',
                    fn: () => window.android?.download(location.href),
                });
            }
            if (isLive) {
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
            }
            if (prefs.enable_pip) {
                targetOrder.push({
                    id: 'pipButton', key: 'pip',
                    icon: 'M19 11h-8v6h8v-6zm4 8V4.98C23 3.88 22.1 3 21 3H3c-1.1 0-2 .88-2 1.98V19c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2zm-2 .02H3V4.97h18v14.05z',
                    fn: () => window.android?.pip(),
                });
            }

            targetOrder.forEach(item => {
                let btn = document.getElementById(item.id);
                if (!btn) btn = createYtActionButton(item.id, item.key, item.icon, item.fn);
                if (btn) {
                    if (anchor.nextElementSibling !== btn) anchor.after(btn);
                    anchor = btn;
                }
            });

            applyActionBarVisibility(actionBar, prefs);

            window.watchInjected = true;
        });
        watchObserver.observe(document.body, { childList: true, subtree: true });

        let lastPC = null, cachedHeaderElement = null, cachedSuggestionsElement = null;

        setInterval(() => {
            if (document.hidden) return;
            const pc = getCachedPageClass(location.href);
            const prefs = getPrefs();
            if (pc === 'you' || pc === 'library') {
                scheduleInject();
            } else {
                removeIncognitoFallbackBanner();
            }

            if (pc !== lastPC) { cachedHeaderElement = null; lastPC = pc; }

            if (pc === 'watch') {
                if (!cachedHeaderElement) cachedHeaderElement = document.querySelector('ytm-header-bar-renderer');
                if (cachedHeaderElement) cachedHeaderElement.style.setProperty('display', 'none', 'important');
                document.body.style.setProperty('padding-top', '0', 'important');
                const ad = document.querySelector('.ad-showing video');
                if (ad) ad.currentTime = ad.duration;
                const mp = document.querySelector('#movie_player');
                if (mp && !window.__liteSyncInFlight) { mp.mute?.(); mp.pauseVideo?.(); }
            } else if (pc === 'shorts') {
                if (!cachedHeaderElement) cachedHeaderElement = document.querySelector('ytm-header-bar-renderer, .ytm-header-bar-renderer');
                if (cachedHeaderElement) cachedHeaderElement.style.setProperty('display', 'none', 'important');
                document.querySelectorAll('#home-icon, .logo-in-player, [aria-label*="Search"], .topbar-menu-button-avatar-button, .header-bar-search-button, .header-search-button, .search-button').forEach(el => el.style.setProperty('display', 'none', 'important'));
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
        }, 800);


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