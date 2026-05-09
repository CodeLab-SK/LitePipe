try {
    if (!window.injected) {
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

        const getLocalizedText = (key) => {
            const languages = {
                'zh': { 'download': '下载', 'downloads': '下载', 'extension': 'LitePipe 设置', 'chat': '聊天室', 'about': '关于', 'pip': '画中画', 'incognito': '无痕模式', 'incognito_off': '关闭无痕模式' },
                'zt': { 'download': '下載', 'downloads': '下載', 'extension': 'LitePipe 設置', 'chat': '聊天室', 'about': '關於', 'pip': '畫中畫', 'incognito': '無痕模式', 'incognito_off': '關閉無痕模式' },
                'en': { 'download': 'Download', 'downloads': 'Downloads', 'extension': 'LitePipe Settings', 'chat': 'Chat', 'about': 'About', 'pip': 'PiP', 'incognito': 'Turn on Incognito', 'incognito_off': 'Turn off Incognito' },
                'ja': { 'download': 'ダウンロード', 'downloads': 'ダウンロード', 'extension': 'LitePipe 設定', 'chat': 'チャット', 'about': '詳細', 'pip': 'PiP', 'incognito': 'シークレットをオン', 'incognito_off': 'シークレットをオフ' },
                'ko': { 'download': '다운로드', 'downloads': '다운로드', 'extension': 'LitePipe 플러그인', 'chat': '채팅', 'about': '정보', 'pip': 'PiP', 'incognito': '시크릿 모드 켜기', 'incognito_off': '시크릿 모드 끄기' },
                'fr': { 'download': 'Télécharger', 'downloads': 'Téléchargements', 'extension': 'Paramètres LitePipe', 'chat': 'Chat', 'about': 'À propos', 'pip': 'PiP', 'incognito': 'Activer navigation privée', 'incognito_off': 'Désactiver navigation privée' },
                'ru': { 'download': 'Скачать', 'downloads': 'Загрузки', 'extension': 'Настройки LitePipe', 'chat': 'Чат', 'about': 'О программе', 'pip': 'PiP', 'incognito': 'Вкл. инコгнит', 'incognito_off': 'Выкл. инコгнит' },
                'tr': { 'download': 'İndir', 'downloads': 'İndirilenler', 'extension': 'LitePipe Ayarları', 'chat': 'Sohbet', 'about': 'Hakkında', 'pip': 'PiP', 'incognito': 'Gizli modu aç', 'incognito_off': 'Gizli modu kapat' },
            };
            const lang = (document.documentElement.lang || 'en').toLowerCase();
            let keyLang = lang.substring(0, 2);
            if (lang.includes('tw') || lang.includes('hk') || lang.includes('mo') || lang.includes('hant')) {
                keyLang = 'zt';
            }
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

        let shortsSpeedPressState = null;
        const SHORTS_SPEED_LONG_PRESS_MS = 450;
        const SHORTS_SPEED_DRAG_THRESHOLD = 10;
        const getPoint = (event) => event?.touches?.[0] || event?.changedTouches?.[0] || event;

        const showShortsSpeedToast = () => {
            if (window.android?.showHint) {
                android.showHint('2x', -1);
            }
            let toast = document.getElementById('_lp_speed_toast');
            if (!toast) {
                toast = document.createElement('div');
                toast.id = '_lp_speed_toast';
                toast.style.cssText = 'position:fixed;top:4%;left:50%;transform:translateX(-50%);z-index:99999;background:rgba(0,0,0,0.4);color:#fff;font-size:15px;font-weight:700;padding:5px 12px;border-radius:18px;pointer-events:none;letter-spacing:0.02em;transition:opacity 0.15s;';
                toast.textContent = '2×';
                document.body.appendChild(toast);
            }
            toast.style.opacity = '1';
            toast.style.display = 'block';
        };

        const hideShortsSpeedToast = () => {
            if (window.android?.hideHint) android.hideHint();
            const toast = document.getElementById('_lp_speed_toast');
            if (toast) toast.style.display = 'none';
        };

        const clearShortsSpeedPress = () => {
            if (!shortsSpeedPressState) return;
            if (shortsSpeedPressState.timerId) ct(shortsSpeedPressState.timerId);
            const player = shortsSpeedPressState.player;
            const previousStyles = shortsSpeedPressState.previousStyles;
            const activated = !!shortsSpeedPressState.activated;
            if (player instanceof Element && previousStyles) {
                player.style.userSelect = previousStyles.userSelect ?? '';
                player.style.webkitUserSelect = previousStyles.webkitUserSelect ?? '';
                player.style.webkitTouchCallout = previousStyles.webkitTouchCallout ?? '';
            }
            shortsSpeedPressState = null;
            if (activated) {
                player?.setPlaybackRate?.(1);
                hideShortsSpeedToast();
            }
        };

        const startShortsSpeedPress = (player, event) => {
            if (!(player instanceof Element) || shortsSpeedPressState) return;
            const point = getPoint(event);
            shortsSpeedPressState = {
                player,
                previousStyles: {
                    userSelect: player.style.userSelect,
                    webkitUserSelect: player.style.webkitUserSelect,
                    webkitTouchCallout: player.style.webkitTouchCallout
                },
                startX: point.clientX,
                startY: point.clientY,
                activated: false,
                timerId: st(() => {
                    if (!shortsSpeedPressState) return;
                    shortsSpeedPressState.activated = true;
                    player?.setPlaybackRate?.(2);
                    showShortsSpeedToast();
                }, SHORTS_SPEED_LONG_PRESS_MS)
            };
            player.style.userSelect = 'none';
            player.style.webkitUserSelect = 'none';
            player.style.webkitTouchCallout = 'none';
        };

        const moveShortsSpeedPress = (event) => {
            if (!shortsSpeedPressState) return;
            const point = getPoint(event);
            const dx = Math.abs(point.clientX - shortsSpeedPressState.startX);
            const dy = Math.abs(point.clientY - shortsSpeedPressState.startY);
            if (dx > SHORTS_SPEED_DRAG_THRESHOLD || dy > SHORTS_SPEED_DRAG_THRESHOLD) {
                clearShortsSpeedPress();
            }
        };

        const findShortsSpeedSurfaceFromEvent = (event) => {
            const target = event?.target;
            if (target instanceof Element) {
                return target.closest?.('#player-shorts-container, shorts-video') ?? null;
            }
            return null;
        };

        let shortsGestureBound = false;
        const bindShortsGesture = () => {
            if (shortsGestureBound) return;
            shortsGestureBound = true;
            ['pointerdown', 'touchstart'].forEach(type => bindListener(document, type, (e) => {
                if (getPageClass(location.href) !== 'shorts') return;
                const surface = findShortsSpeedSurfaceFromEvent(e);
                const player = document.querySelector('#movie_player');
                if (surface && player) startShortsSpeedPress(player, e);
            }, { passive: false, capture: true }));
            ['pointermove', 'touchmove'].forEach(type => bindListener(document, type, (e) => {
                moveShortsSpeedPress(e);
            }, { passive: false, capture: true }));
            ['pointerup', 'touchend', 'pointercancel', 'touchcancel'].forEach(type => bindListener(document, type, () => {
                clearShortsSpeedPress();
            }, { passive: false, capture: true }));
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
            let totalSeconds = 0;
            let matched = false;
            for (const part of normalized.matchAll(/(\d+)(h|m|s)/g)) {
                const amount = Number(part[1]);
                matched = true;
                if (part[2] === 'h') totalSeconds += amount * 3600;
                if (part[2] === 'm') totalSeconds += amount * 60;
                if (part[2] === 's') totalSeconds += amount;
            }
            return matched ? totalSeconds : null;
        };

        const handleWatchTimestampClick = (event) => {
            if (getPageClass(location.href) !== 'watch') return;
            const link = event.target.closest('a');
            if (!link) return;
            const href = link.getAttribute('href') || link.href;
            if (!href || !href.includes('t=')) return;
            let targetUrl;
            try { targetUrl = new URL(link.href, location.href); } catch (e) { return; }
            const videoId = getVideoId(location.href);
            const targetVideoId = getVideoId(targetUrl.toString());
            if (!videoId || videoId !== targetVideoId) return;
            const timestampSeconds = parseTimestampSeconds(targetUrl.searchParams.get('t') ?? targetUrl.searchParams.get('start'));
            if (timestampSeconds == null) return;
            if (android.seekLoadedVideo?.(targetUrl.toString(), timestampSeconds * 1000)) {
                event.preventDefault();
                event.stopImmediatePropagation();
            }
        };

        const addTapEvent = (el, handler) => {
            let startX, startY;
            bindListener(el, 'pointerdown', e => { startX = e.clientX; startY = e.clientY; }, { passive: false });
            bindListener(el, 'pointerup', e => {
                const dx = Math.abs(e.clientX - startX);
                const dy = Math.abs(e.clientY - startY);
                if (dx < 10 && dy < 10) handler(e);
            }, { passive: false });
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
                    if (innerText) innerText.innerText = label;
                    else textEl.innerText = label;
                }

                const ns = 'http://www.w3.org/2000/svg';
                let newIcon = null;
                if (typeof iconD === 'string' && iconD.trim().startsWith('<svg')) {
                    const tmp = document.createElement('div');
                    tmp.innerHTML = iconD.trim();
                    const svgEl = tmp.querySelector('svg');
                    if (svgEl) {
                        svgEl.style.marginRight = '16px';
                        svgEl.style.fill = 'currentColor';
                        newIcon = svgEl;
                    }
                } else {
                    const svg = document.createElementNS(ns, 'svg');
                    svg.setAttribute('viewBox', '0 -960 960 960');
                    svg.setAttribute('width', '24');
                    svg.setAttribute('height', '24');
                    svg.style.marginRight = '16px';
                    svg.style.fill = 'currentColor';
                    const path = document.createElementNS(ns, 'path');
                    try { path.setAttribute('d', iconD); } catch (e) { /* ignore bad path */ }
                    svg.appendChild(path);
                    newIcon = svg;
                }

                const oldIcon = btn.querySelector('yt-icon, .ytm-settings-item-icon, img, svg');
                if (oldIcon && oldIcon.parentNode && newIcon) {
                    try { oldIcon.parentNode.replaceChild(newIcon, oldIcon); } catch (e) { /* ignore replace errors */ }
                }

                btn.addEventListener('click', (e) => { e.preventDefault(); e.stopPropagation(); clickFn(); }, true);
                return btn;
            } catch (err) {
                console.error('createCustomSettingBtn error', err);
                return null;
            }
        };

        const makeBtnSvg = (iconD) => {
            const ns = 'http://www.w3.org/2000/svg';
            const svg = document.createElementNS(ns, 'svg');
            svg.setAttribute('viewBox', '0 -960 960 960');
            svg.setAttribute('width', '24');
            svg.setAttribute('height', '24');
            svg.setAttribute('fill', 'currentColor');
            svg.style.cssText = 'width:24px!important;height:24px!important;display:block!important;fill:currentColor!important;color:currentColor!important;visibility:visible!important;opacity:1!important;';
            const path = document.createElementNS(ns, 'path');
            path.setAttribute('d', iconD);
            svg.appendChild(path);
            return svg;
        };

        const createYtActionButton = (id, textKey, iconD, clickFn) => {
            if (document.getElementById(id)) return null;
            const label = getLocalizedText(textKey);

            const wrapper = document.createElement('div');
            wrapper.id = id;
            wrapper.className = 'ytSpecButtonViewModelHost slim_video_action_bar_renderer_button';
            wrapper.style.cssText = 'display:inline-flex!important;align-items:center!important;justify-content:center!important;width:auto!important;height:36px!important;flex:0 0 auto!important;min-width:52px!important;padding:0 12px!important;margin-right:8px!important;background:rgba(255,255,255,0.1)!important;border-radius:20px!important;visibility:visible!important;opacity:1!important;';

            const btn = document.createElement('button');
            btn.className = 'yt-spec-button-shape-next yt-spec-button-shape-next--tonal yt-spec-button-shape-next--mono yt-spec-button-shape-next--size-m yt-spec-button-shape-next--icon-leading';
            btn.setAttribute('aria-label', label);
            btn.style.cssText = 'display:inline-flex!important;align-items:center!important;justify-content:center!important;gap:6px!important;height:100%!important;width:auto!important;background:transparent!important;color:inherit!important;border:none!important;font-size:14px!important;font-weight:500!important;cursor:pointer!important;visibility:visible!important;opacity:1!important;padding:0!important;';

            btn.appendChild(makeBtnSvg(iconD));

            const textSpan = document.createElement('span');
            textSpan.className = 'yt-spec-button-shape-next__button-text-content';
            textSpan.textContent = label;
            textSpan.style.cssText = 'display:inline-block!important;color:inherit!important;font-size:14px!important;white-space:nowrap!important;';
            btn.appendChild(textSpan);

            btn.addEventListener('click', (e) => { e.preventDefault(); e.stopPropagation(); clickFn(); }, true);
            wrapper.appendChild(btn);
            return wrapper;
        };

        const INCOGNITO_ICON = 'M12 2C8.13 2 5 5.13 5 9H19C19 5.13 15.87 2 12 2ZM2 11H22V13H2V11ZM7.5 14.5C5.57 14.5 4 16.07 4 18C4 19.93 5.57 21.5 7.5 21.5C9.43 21.5 11 19.93 11 18C11 16.07 9.43 14.5 7.5 14.5ZM16.5 14.5C14.57 14.5 13 16.07 13 18C13 19.93 14.57 21.5 16.5 21.5C18.43 21.5 20 19.93 20 18C20 16.07 18.43 14.5 16.5 14.5Z';

        const makePageHeaderChipBtn = (labelText, iconSvgPath, isActive, onClickFn) => {
            const ns = 'http://www.w3.org/2000/svg';

            const actionDiv = document.createElement('div');
            actionDiv.className = 'ytFlexibleActionsViewModelAction ytFlexibleActionsViewModelActionRowAction';
            actionDiv.style.cssText = 'flex:0 0 auto;';

            const btnViewModel = document.createElement('button-view-model');
            btnViewModel.className = 'ytSpecButtonViewModelHost';

            const btn = document.createElement('button');
            btn.className = 'ytSpecButtonShapeNextHost ytSpecButtonShapeNextTonal ytSpecButtonShapeNextMono ytSpecButtonShapeNextSizeS ytSpecButtonShapeNextIconLeading';
            btn.setAttribute('aria-label', labelText);
            btn.setAttribute('aria-disabled', 'false');
            btn.title = '';

            if (isActive) {
                btn.style.cssText = 'outline:2px solid currentColor;outline-offset:-2px;';
            }

            const iconDiv = document.createElement('div');
            iconDiv.setAttribute('aria-hidden', 'true');
            iconDiv.className = 'ytSpecButtonShapeNextIcon';

            const c3 = document.createElement('c3-icon');
            c3.setAttribute('fill-icon', 'false');
            c3.style.cssText = 'width:16px;height:16px;';

            const iconSpan = document.createElement('span');
            iconSpan.className = 'yt-icon-shape ytSpecIconShapeHost';

            const iconInner = document.createElement('div');
            iconInner.style.cssText = 'width:100%;height:100%;display:block;fill:currentcolor;';

            const svg = document.createElementNS(ns, 'svg');
            svg.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
            svg.setAttribute('height', '24');
            svg.setAttribute('viewBox', '0 0 24 24');
            svg.setAttribute('width', '24');
            svg.setAttribute('focusable', 'false');
            svg.setAttribute('aria-hidden', 'true');
            svg.style.cssText = 'pointer-events:none;display:inherit;width:100%;height:100%;';

            const path = document.createElementNS(ns, 'path');
            path.setAttribute('d', iconSvgPath);
            svg.appendChild(path);
            iconInner.appendChild(svg);
            iconSpan.appendChild(iconInner);
            c3.appendChild(iconSpan);
            iconDiv.appendChild(c3);

            const textDiv = document.createElement('div');
            textDiv.className = 'ytSpecButtonShapeNextButtonTextContent';
            textDiv.textContent = labelText;

            const feedback = document.createElement('yt-touch-feedback-shape');
            feedback.setAttribute('aria-hidden', 'true');
            feedback.className = 'ytSpecTouchFeedbackShapeHost ytSpecTouchFeedbackShapeTouchResponse';
            feedback.innerHTML = '<div class="ytSpecTouchFeedbackShapeStroke"></div><div class="ytSpecTouchFeedbackShapeFill"></div>';

            btn.appendChild(iconDiv);
            btn.appendChild(textDiv);
            btn.appendChild(feedback);
            btnViewModel.appendChild(btn);
            actionDiv.appendChild(btnViewModel);

            btn.addEventListener('click', (e) => { e.preventDefault(); e.stopPropagation(); onClickFn(e); }, true);

            return actionDiv;
        };

        const updateIncognitoChip = (chip, isOn) => {
            const btn = chip.querySelector('button');
            const textEl = chip.querySelector('.ytSpecButtonShapeNextButtonTextContent');
            if (textEl) textEl.textContent = isOn ? getLocalizedText('incognito_off') : getLocalizedText('incognito');
            if (btn) {
                btn.style.cssText = isOn ? 'outline:2px solid currentColor;outline-offset:-2px;' : '';
            }
        };

        const injectIncognitoFallbackBanner = () => {
            if (document.getElementById('_lp_incognito_banner')) {
                return;
            }
            const banner = document.createElement('div');
            banner.id = '_lp_incognito_banner';
            banner.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:99998;display:flex;align-items:center;justify-content:space-between;padding:0 16px;height:48px;background:#212121;color:#fff;font-size:14px;font-family:inherit;box-sizing:border-box;';

            const ns = 'http://www.w3.org/2000/svg';
            const iconWrap = document.createElement('span');
            iconWrap.style.cssText = 'display:inline-flex;align-items:center;gap:8px;font-weight:500;';
            const svg = document.createElementNS(ns, 'svg');
            svg.setAttribute('viewBox', '0 0 24 24');
            svg.setAttribute('width', '20');
            svg.setAttribute('height', '20');
            svg.style.cssText = 'fill:currentColor;display:block;flex-shrink:0;';
            const p = document.createElementNS(ns, 'path');
            p.setAttribute('d', INCOGNITO_ICON);
            svg.appendChild(p);
            const label = document.createElement('span');
            label.textContent = getLocalizedText('incognito_off');
            iconWrap.appendChild(svg);
            iconWrap.appendChild(label);

            const offBtn = document.createElement('button');
            offBtn.textContent = getLocalizedText('incognito_off');
            offBtn.style.cssText = 'background:rgba(255,255,255,0.15);border:none;color:#fff;font-size:13px;font-weight:500;padding:6px 14px;border-radius:18px;cursor:pointer;font-family:inherit;white-space:nowrap;-webkit-tap-highlight-color:transparent;';
            offBtn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                if (window.android?.toggleIncognito) android.toggleIncognito();
            }, true);

            banner.appendChild(iconWrap);
            banner.appendChild(offBtn);
            document.body.appendChild(banner);

            document.body.style.paddingTop = '48px';
        };

        const removeIncognitoFallbackBanner = () => {
            const b = document.getElementById('_lp_incognito_banner');
            if (b) b.remove();
        };

        const injectIncognitoToHeader = () => {
            const pc = getPageClass(location.href);
            if (pc !== 'you' && pc !== 'library') return;

            if (!document.getElementById('_lp_header_styles')) {
                const style = document.createElement('style');
                style.id = '_lp_header_styles';
                style.textContent = `
                    yt-flexible-actions-view-model::-webkit-scrollbar { display: none !important; }
                    yt-flexible-actions-view-model { -ms-overflow-style: none !important; scrollbar-width: none !important; }
                    .ytFlexibleActionsViewModelActionRow { -webkit-tap-highlight-color: transparent !important; user-select: none !important; outline: none !important; }
                    .ytFlexibleActionsViewModelActionRow * { -webkit-tap-highlight-color: transparent !important; outline: none !important; -webkit-user-drag: none !important; }
                    .ytFlexibleActionsViewModelAction:empty { display: none !important; }
                `;
                document.head.appendChild(style);
            }

            const isOn = window.android?.isIncognito?.() === true;
            const actionRow = document.querySelector('yt-flexible-actions-view-model .ytFlexibleActionsViewModelActionRow');

            if (!actionRow) {
                if (isOn) injectIncognitoFallbackBanner();
                else removeIncognitoFallbackBanner();

                window._lpIncognitoRetry = (window._lpIncognitoRetry || 0) + 1;
                if (window._lpIncognitoRetry < 6) st(() => injectIncognitoToHeader(), 350);
                return;
            }
            window._lpIncognitoRetry = 0;

            removeIncognitoFallbackBanner();

            const flexParent = actionRow.closest('yt-flexible-actions-view-model');
            if (flexParent) {
                flexParent.style.cssText = 'overflow-x:auto!important;-webkit-overflow-scrolling:touch!important;scrollbar-width:none!important;display:block!important;margin-left:-16px!important;margin-right:-16px!important;';
                flexParent.style.setProperty('scrollbar-width', 'none', 'important');
                flexParent.style.setProperty('max-width', '100%', 'important');
            }
            actionRow.style.cssText = 'display:flex!important;flex-wrap:nowrap!important;gap:8px!important;padding:0 16px 4px 16px!important;justify-content:flex-start!important;width:max-content!important;';

            const existing = document.getElementById('_lp_incognito_chip');
            if (existing) {
                updateIncognitoChip(existing, isOn);
                if (actionRow.firstChild !== existing) actionRow.prepend(existing);
                return;
            }

            const chip = makePageHeaderChipBtn(
                isOn ? getLocalizedText('incognito_off') : getLocalizedText('incognito'),
                INCOGNITO_ICON,
                isOn,
                () => {
                    if (window.android?.toggleIncognito) {
                        android.toggleIncognito();
                        st(() => injectIncognitoToHeader(), 250);
                    }
                }
            );
            chip.id = '_lp_incognito_chip';
            actionRow.prepend(chip);
        };

let incognitoObserver = null;
        const startIncognitoObserver = () => {
            if (incognitoObserver) return;
            incognitoObserver = new MutationObserver(() => {
                const pc = getPageClass(location.href);
                if (pc !== 'you' && pc !== 'library') return;
                const actionRow = document.querySelector('yt-flexible-actions-view-model .ytFlexibleActionsViewModelActionRow');
                if (actionRow && !document.getElementById('_lp_incognito_chip')) {
                    injectIncognitoToHeader();
                }
            });
            incognitoObserver.observe(document.body, { childList: true, subtree: true });
        };
        startIncognitoObserver();


        const openLiveChat = (vid) => {
            let container = document.getElementById('live_chat_container');
            if (container) {
                const isHidden = container.style.display === 'none';
                container.style.display = isHidden ? 'flex' : 'none';
                document.body.style.overflow = isHidden ? 'hidden' : '';
                return;
            }
            const panel = document.querySelector('#panel-container') || document.querySelector('.watch-below-the-player');
            if (!panel || !vid) return;

            container = document.createElement('div');
            container.id = 'live_chat_container';
            container.style.cssText = 'position:fixed;top:calc(56.25vw + 48px);bottom:0;left:0;right:0;z-index:9999;display:flex;flex-direction:column;background:var(--yt-spec-brand-background-solid,#fff);overflow:hidden;box-shadow:0 -2px 12px rgba(0,0,0,0.18);';

            const header = document.createElement('div');
            header.style.cssText = 'display:flex;align-items:center;justify-content:space-between;padding:0 8px 0 16px;height:48px;min-height:48px;border-bottom:1px solid var(--yt-spec-10-percent-layer,rgba(0,0,0,0.1));flex-shrink:0;box-sizing:border-box;background:var(--yt-spec-brand-background-solid,#fff);';

            const title = document.createElement('span');
            title.textContent = getLocalizedText('chat');
            title.style.cssText = 'font-size:16px;font-weight:600;color:var(--yt-spec-text-primary,#0f0f0f);flex:1;';

            const closeBtn = document.createElement('button');
            closeBtn.innerHTML = '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" style="display:block;pointer-events:none;"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>';
            closeBtn.style.cssText = 'background:none;border:none;cursor:pointer;color:var(--yt-spec-text-primary,#0f0f0f);padding:0;width:40px;height:40px;display:flex;align-items:center;justify-content:center;border-radius:50%;flex-shrink:0;-webkit-tap-highlight-color:transparent;';
            closeBtn.addEventListener('click', () => {
                container.style.display = 'none';
                document.body.style.overflow = '';
            });

            header.appendChild(title);
            header.appendChild(closeBtn);
            container.appendChild(header);

            const loading = document.createElement('div');
            loading.id = 'live_chat_loading';
            loading.style.cssText = 'position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);font-size:14px;color:var(--yt-spec-text-secondary);z-index:1;';
            loading.textContent = 'Loading...';
            container.appendChild(loading);

            const iframe = document.createElement('iframe');
            iframe.src = `https://www.youtube.com/live_chat?v=${vid}&embed_domain=${location.hostname}`;
            iframe.style.cssText = 'flex:1;border:none;width:100%;display:block;background:transparent;';
            iframe.setAttribute('allow', 'autoplay');
            iframe.onload = () => {
                const l = document.getElementById('live_chat_loading');
                if (l) l.style.display = 'none';
            };

            container.appendChild(iframe);
            panel.insertBefore(container, panel.firstChild);
            document.body.style.overflow = 'hidden';
        };

        const updatePageClass = (url) => {
            const pc = getPageClass(url || location.href);
            if (pc && window.pageClass !== pc) {
                window.pageClass = pc;
                document.documentElement.setAttribute('page-class', pc);
                window.dispatchEvent(new Event('onPageClassChange'));
            }
        };

        window.addEventListener('onProgressChangeFinish', () => {
            updatePageClass();
            android.finishRefresh();
        });

        window.addEventListener('onRefresh', () => location.reload());

        window.addEventListener('doUpdateVisitedHistory', () => {
            const pc = getPageClass(location.href);
            android.setRefreshLayoutEnabled(['home', 'subscriptions', '@'].includes(pc));
            android.finishRefresh();
        });

        const handlePlayerVisibility = () => {
            const url = location.href;
            updatePageClass(url);
            if (getPageClass(url) === 'watch') android.play(url);
        };

        window.addEventListener('popstate', handlePlayerVisibility);
        ['pushState', 'replaceState'].forEach(name => {
            const original = history[name];
            history[name] = function() {
                original.apply(this, arguments);
                handlePlayerVisibility();
            };
        });

        const resizeObserver = new ResizeObserver(() => {
            if (getPageClass(location.href) !== 'watch') return;
            const p = document.querySelector('#movie_player');
            if (p) android.setPlayerHeight(p.clientHeight);
        });

        document.addEventListener('animationstart', (e) => {
            if (e.animationName !== 'nodeInserted') return;
            const node = e.target;
            const pc = getPageClass(location.href);

            if (node.id === 'movie_player') {
                if (pc === 'watch') {
                    node.mute();
                    node.seekTo(node.getDuration() / 2);
                    if (window.android?.getResumePosition) {
                        const resumePos = android.getResumePosition(getVideoId(location.href));
                        if (resumePos > 0) node.seekTo(resumePos);
                    }
                    node.addEventListener('onStateChange', s => { if (s === 1) node.pauseVideo(); });
                }
                resizeObserver.disconnect();
                resizeObserver.observe(node);
            } else if (pc === 'watch') {
                if (node.id === 'player' || node.id === 'player-container-id') {
                    node.style.setProperty('display', 'none', 'important');
                } else if (node.classList.contains('watch-below-the-player')) {
                    ['touchmove', 'touchend'].forEach(ev => {
                        node.addEventListener(ev, evt => evt.stopPropagation(), { passive: false, capture: true });
                    });
                }
            }
        }, false);

        bindShortsGesture();

        let injected = false;
        const observer = new MutationObserver((mutations) => {
            const actionBar = document.querySelector('ytm-slim-video-action-bar-renderer');
            if (!actionBar) {
                injected = false;
                return;
            }

            const pc = getPageClass(location.href);
            if (pc !== 'watch') return;

            const moviePlayer = document.querySelector('#movie_player');
            const isLive = moviePlayer?.getPlayerResponse()?.playabilityStatus?.liveStreamability && location.href.includes('/watch');
            const prefs = JSON.parse(android.getPreferences());

            actionBar.style.overflow = 'visible';

            const container = actionBar.querySelector('#menu')
                || actionBar.querySelector('.slim-video-action-bar-actions')
                || actionBar;

            let anchor = container.querySelector('ytm-segmented-like-dislike-button-renderer')
                         || container.querySelector('ytm-toggle-button-renderer')
                         || container.querySelector('segmented-like-dislike-button-view-model')
                         || container.firstElementChild;

            if (!anchor) return;

            const targetOrder = [];
            if (!isLive) targetOrder.push({ id: 'downloadButton', key: 'download', icon: 'M480-328.46 309.23-499.23l42.16-43.38L450-444v-336h60v336l98.61-98.61 42.16 43.38L480-328.46ZM252.31-180Q222-180 201-201q-21-21-21-51.31v-108.46h60v108.46q0 4.62 3.85 8.46 3.84 3.85 8.46 3.85h455.38q4.62 0 8.46-3.85 3.85-3.84 3.85-8.46v-108.46h60v108.46Q780-222 759-201q-21 21-51.31 21H252.31Z', fn: () => android.download(location.href) });
            if (isLive) targetOrder.push({ id: 'chatButton', key: 'chat', icon: 'M240-384h336v-72H240v72Zm0-132h480v-72H240v72Zm0-132h480v-72H240v72ZM96-96v-696q0-29.7 21.15-50.85Q138.3-864 168-864h624q29.7 0 50.85 21.15Q864-821.7 864-792v480q0 29.7-21.15 50.85Q821.7-240 792-240H240L96-96Zm114-216h582v-480H168v-522l42-42Zm-42 0v-480 480Z', fn: () => openLiveChat(getVideoId(location.href)) });
            if (prefs.enable_pip) targetOrder.push({ id: 'pipButton', key: 'pip', icon: 'M720-312v-336H432v336h288ZM240-168q-29.7 0-50.85-21.15Q168-210.3 168-240v-480q0-29.7 21.15-50.85Q210.3-792 240-792h480q29.7 0 50.85 21.15Q792-749.7 792-720v480q0 29.7-21.15 50.85Q749.7-168 720-168H240Zm0-72h480v-480H240v480Zm0 0v-480 480Z', fn: () => android.pip() });

            targetOrder.forEach(item => {
                let btn = document.getElementById(item.id);
                if (!btn) {
                    btn = createYtActionButton(item.id, item.key, item.icon, item.fn);
                }
                if (btn) {
                    if (anchor.nextElementSibling !== btn) {
                        anchor.after(btn);
                    }
                    anchor = btn;
                }
            });

            actionBar.querySelectorAll('.ytSpecButtonViewModelHost, ytm-toggle-button-renderer, ytm-button-renderer, .slim_video_action_bar_renderer_button, ytm-segmented-like-dislike-button-renderer, segmented-like-dislike-button-view-model').forEach((btn) => {
                const label = (btn.getAttribute('aria-label') || btn.textContent || '').toLowerCase().trim();
                const id = btn.id || '';
                const tag = btn.tagName.toLowerCase();
                const isLikeDislike = tag === 'ytm-segmented-like-dislike-button-renderer' || tag === 'segmented-like-dislike-button-view-model' || label.includes('like') || btn.classList.contains('ytSegmentedLikeDislikeButtonViewModelHost');
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

            injected = true;
        });

        observer.observe(document.body, { childList: true, subtree: true });

        window.addEventListener('popstate', () => { injected = false; });
        const originalPushState = history.pushState;
        history.pushState = function() {
            originalPushState.apply(this, arguments);
            injected = false;
        };
        const originalReplaceState = history.replaceState;
        history.replaceState = function() {
            originalReplaceState.apply(this, arguments);
            injected = false;
        };

        setInterval(() => {
            const pc = getPageClass(location.href);

            if (pc === 'you' || pc === 'library') {
                injectIncognitoToHeader();
            } else {
                removeIncognitoFallbackBanner();
            }

            if (pc === 'watch') {
                const playerEl = document.getElementById('player'), container = document.getElementById('player-container-id'), header = document.querySelector('ytm-header-bar-renderer');
                if (playerEl) playerEl.style.setProperty('display', 'none', 'important');
                if (container) container.style.setProperty('display', 'none', 'important');
                if (header) header.style.setProperty('display', 'none', 'important');
                document.body.style.setProperty('padding-top', '0', 'important');
                document.querySelector('ytm-feed-filter-chip-bar-renderer')?.style.setProperty('position', 'fixed', 'important');
                const ad = document.querySelector('.ad-showing video');
                if (ad) ad.currentTime = ad.duration;
                const mp = document.querySelector('#movie_player');
                if (mp) { mp.mute?.(); mp.pauseVideo?.(); }
            } else if (pc === 'shorts') {
                const header = document.querySelector('ytm-header-bar-renderer, .ytm-header-bar-renderer');
                if (header) {
                    header.style.setProperty('display', 'none', 'important');
                }
                document.querySelectorAll('#home-icon, .logo-in-player, [aria-label*="Search"], .topbar-menu-button-avatar-button, .header-bar-search-button, .header-search-button, .search-button').forEach(el => {
                    el.style.setProperty('display', 'none', 'important');
                });
            } else {
                const header = document.querySelector('ytm-header-bar-renderer');
                if (header) {
                    header.style.setProperty('display', 'flex', 'important');
                    header.querySelector('ytm-home-logo')?.style.removeProperty('display');
                    header.querySelectorAll('.header-bar-search-button, .header-search-button').forEach(el => el.style.removeProperty('display'));
                }
                document.body.style.removeProperty('padding-top');
            }

            const prefs = JSON.parse(android.getPreferences() || '{}');
            const suggestions = document.querySelector('.yt-searchbox-suggestions-container');
            if (suggestions) {
                if (prefs.show_search_suggestions === false) suggestions.style.setProperty('display', 'none', 'important');
                else suggestions.style.removeProperty('display');
            }

            if (prefs.hide_comments === true) {
                document.documentElement.classList.add('lp-hide-comments');
            } else {
                document.documentElement.classList.remove('lp-hide-comments');
            }

            if (prefs.action_bar_show_like_dislike === false) {
                document.documentElement.classList.add('lp-hide-likes');
            } else {
                document.documentElement.classList.remove('lp-hide-likes');
            }

            if (prefs.shorts_show_like === false) document.documentElement.classList.add('lp-hide-shorts-like');
            else document.documentElement.classList.remove('lp-hide-shorts-like');

            if (prefs.shorts_show_dislike === false) document.documentElement.classList.add('lp-hide-shorts-dislike');
            else document.documentElement.classList.remove('lp-hide-shorts-dislike');

            if (prefs.shorts_show_comments === false) document.documentElement.classList.add('lp-hide-shorts-comments');
            else document.documentElement.classList.remove('lp-hide-shorts-comments');

            if (prefs.shorts_show_share === false) document.documentElement.classList.add('lp-hide-shorts-share');
            else document.documentElement.classList.remove('lp-hide-shorts-share');

            const settingsBackArrow = document.querySelector('[data-mode="settings"] > .mobile-topbar-back-arrow');
            if (settingsBackArrow instanceof Element && settingsBackArrow.dataset.liteGoBackBound !== 'true') {
                bindListener(settingsBackArrow, 'click', event => {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                    if (window.android?.goBack) android.goBack();
                }, true);
                settingsBackArrow.dataset.liteGoBackBound = 'true';
            }

            if (pc === 'select_site') {
                const settings = document.querySelector('ytm-settings');
                if (settings && !settings.dataset.buttonsInjected) {
                    const base = settings.firstElementChild;
                    if (base) {
                        const aboutPath = 'M444-288h72v-240h-72v240Zm35.79-312q15.21 0 25.71-10.29t10.5-25.5q0-15.21-10.29-25.71t-25.5-10.5q-15.21 0-25.71 10.29t-10.5 25.5q0 15.21 10.29 25.71t25.5 10.5Zm.49 504Q401-96 331-126t-122.5-82.5Q156-261 126-330.96t-30-149.5Q96-560 126-629.5q30-69.5 82.5-122T330.96-834q69.96-30 149.5-30t149.04 30q69.5 30 122 82.5T834-629.28q30 69.73 30 149Q864-401 834-331t-82.5 122.5Q699-156 629.28-126q-69.73 30-149 30Zm-.28-72q130 0 221-91t91-221q0-130-91-221t-221-91q-130 0-221 91t-91 221q0 130 91 221t221 91Zm0-312Z';
                        const dlPath = 'M480-336 288-528l51-51 105 105v-342h72v342l105-105 51 51-192 192ZM263.72-192Q234-192 213-213.15T192-264v-72h72v72h432v-72h72v72q0 29.7-21.16 50.85Q725.68-192 695.96-192H263.72Z';
                        const extPath = 'M497-120l-33-124q-15-7-30-16t-28-20l-116 50-70-121 98-88q-2-10-3-20t-1-20q0-10 1-20t3-20l-98-88 70-121 116 50q13-11 28-20t30-16l33-124h140l33 124q15 7 30 16t28 20l116-50 70 121-98 88q2 10 3 20t1 20q0 10-1 20t-3 20l98 88-70 121-116-50q-13 11-28 20t-30 16l-33 124H497Zm70-227q55 0 94-39t39-94q0-55-39-94t-94-39q-55 0-94 39t-39 94q0 55 39 94t94 39Z';

                        const aboutBtn = createCustomSettingBtn(base, 'aboutButton', 'about', aboutPath, () => android.about());

                        const dlBtn = createCustomSettingBtn(base, 'downloadButton', 'downloads', dlPath, () => android.download());

                        const extBtn = createCustomSettingBtn(base, 'extensionButton', 'extension', extPath, () => android.extension());

                        if (aboutBtn) settings.appendChild(aboutBtn);
                        if (dlBtn) settings.insertBefore(dlBtn, settings.firstElementChild);
                        if (extBtn) settings.insertBefore(extBtn, settings.firstElementChild);

                        settings.dataset.buttonsInjected = 'true';
                    }
                }
            }
        }, 800);

        document.addEventListener('click', e => {
            handleWatchTimestampClick(e);
            const a = e.target.closest('a'), logo = e.target.closest('ytm-home-logo'), nav = e.target.closest('ytm-pivot-bar-item-renderer');
            let href;
            if (nav?.data?.navigationEndpoint) href = nav.data.navigationEndpoint.commandMetadata?.webCommandMetadata?.url;
            else if (a?.href) href = a.getAttribute('href');
            else if (logo) href = '/';
            if (!href) return;
            const url = href.startsWith('http') ? href : 'https://m.youtube.com' + href;
            const targetClass = getPageClass(url);
            if (targetClass !== getPageClass(location.href)) {
                e.preventDefault(); e.stopImmediatePropagation();
                requestOpenTab(url, targetClass);
            }
        }, true);

        addTapEvent(document, e => {
            const renderer = e.target.closest('ytm-post-multi-image-renderer');
            if (renderer && window.android?.onPosterLongPress) android.onPosterLongPress(JSON.stringify([...renderer.querySelectorAll('ytm-backstage-image-renderer')].map(el => el?.data?.image?.thumbnails?.at(-1)?.url)));
        });

        let longPressTimer;
        const handleLongPress = (e) => {
            const prefs = JSON.parse(android.getPreferences() || '{}');
            if (prefs.enable_long_press_menu === false) return;

            const el = e.target;
            const a = el.closest('a');
            let url = a?.href;
            if (!url) {
                const renderer = el.closest('ytm-compact-video-renderer, ytm-video-with-context-renderer, ytm-playlist-renderer');
                const endpoint = renderer?.data?.navigationEndpoint || a?.data?.navigationEndpoint;
                url = endpoint?.commandMetadata?.webCommandMetadata?.url;
                if (url && !url.startsWith('http')) url = 'https://m.youtube.com' + url;
            }

            if (url && (url.includes('/watch') || url.includes('list='))) {
                if (e.type === 'touchstart') {
                    clearTimeout(longPressTimer);
                    longPressTimer = setTimeout(() => {
                        if (window.android?.showVideoOptions) android.showVideoOptions(url, 'Options');
                    }, 600);
                } else if (e.type === 'contextmenu') {
                    e.preventDefault();
                    if (window.android?.showVideoOptions) android.showVideoOptions(url, 'Options');
                }
            }
        };

        document.addEventListener('touchstart', handleLongPress, { passive: true, capture: true });
        ['touchend', 'touchmove'].forEach(ev => document.addEventListener(ev, () => clearTimeout(longPressTimer), { passive: true, capture: true }));
        document.addEventListener('contextmenu', handleLongPress, true);

        updatePageClass();
        window.injected = true;
    }
} catch (e) { console.error(e); }