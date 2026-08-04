(function() {
    const Android = window.android || window.lite;
    if (!Android) return;

    if (window._lp_player_active) return;
    window._lp_player_active = true;

    if (window.trustedTypes && window.trustedTypes.createPolicy && !window.trustedTypes.defaultPolicy) {
        try {
            window.trustedTypes.createPolicy('default', {
                createHTML: (s) => s,
                createScriptURL: s => s,
                createScript: s => s
            });
        } catch (e) {}
    }

    var touchstartY = 0;
    var sens = 0.005;
    var vol = Android.getVolume();
    var brt = Android.getBrightness() / 100;
    var sTime = [];
    var currentVideoId = "";
    var sbBound = false;
    var currentZoom = 1;
    var prevDistance = null;
    const zoomSens = 0.005;

    // Speed buttons
    function applyExtraSpeed() {
        const video = document.querySelector('video.video-stream');
        const slider = document.getElementById("slider");

        if (slider && slider.max != 10) {
            slider.max = 10;
            slider.setAttribute('aria-valuemax', '10');
            const updateSpeed = () => { if (video) video.playbackRate = parseFloat(slider.value); };
            slider.removeEventListener("input", updateSpeed);
            slider.addEventListener("input", updateSpeed);
        }

        const container = document.querySelector(".ytwVariableSpeedControllerViewModelButtonContainer");
        if (container && !document.getElementById("lite_extra_speeds_marker")) {
            // Keep the buttons in one line
            container.style.cssText = "display:flex !important; flex-wrap:nowrap !important; overflow-x:auto !important; white-space:nowrap !important; -webkit-overflow-scrolling:touch !important; padding: 12px 6px !important; scrollbar-width: none !important; justify-content: flex-start !important; width: 100% !important; box-sizing: border-box !important; gap: 8px !important;";

            let parent = container.parentElement;
            for(let i = 0; i < 3; i++) {
                if (parent) {
                    parent.style.overflowX = "auto";
                    parent.style.maxWidth = "100%";
                    parent = parent.parentElement;
                }
            }

            const marker = document.createElement("div");
            marker.id = "lite_extra_speeds_marker";
            container.appendChild(marker);

            [3, 4, 5, 10].forEach(s => {
                const elm = document.createElement("ytw-variable-speed-controller-speed-button-view-model");
                elm.className = "ytwVariableSpeedControllerSpeedButtonViewModelHost ytwVariableSpeedControllerViewModelPlaybackSpeedButton";
                elm.style.flexShrink = "0";
                elm.innerHTML = `
                    <button-view-model class="ytSpecButtonViewModelHost">
                        <button class="ytSpecButtonShapeNextHost ytSpecButtonShapeNextTonal ytSpecButtonShapeNextMono ytSpecButtonShapeNextSizeS">
                            <div class="ytSpecButtonShapeNextButtonTextContent">${s}x</div>
                        </button>
                    </button-view-model>`;

                elm.addEventListener("click", (e) => {
                    e.preventDefault(); e.stopPropagation();
                    if (video) video.playbackRate = s;
                    if (slider) {
                        slider.value = s;
                        slider.dispatchEvent(new Event("input", { bubbles: true }));
                    }
                });
                container.appendChild(elm);
            });
        }
    }

    // Sponsorblock
    async function checkSponsors() {
        const urlParams = new URLSearchParams(window.location.search);
        let vId = urlParams.get("v");
        if (!vId && window.location.pathname.includes("/shorts/")) {
            vId = window.location.pathname.split("/shorts/")[1].split(/[?#]/)[0];
        }

        if (!vId || vId.length !== 11 || vId === currentVideoId) return;

        currentVideoId = vId;
        sTime = [];
        if (document.getElementById("sDiv")) document.getElementById("sDiv").remove();

        // Reset zoom when changing videos
        currentZoom = 1;
        prevDistance = null;

        try {
            const response = await fetch(`https://sponsor.ajay.app/api/skipSegments?videoID=${vId}`);
            if (response.ok) {
                const jsonObject = await response.json();
                for (var x in jsonObject) {
                    var time = jsonObject[x].segment;
                    sTime.push(time);
                }
            }
        } catch (e) {}
    }

    // Sponsorblock progress bar markers
    function skipSponsor() {
        if (document.getElementById("sDiv") || sTime.length === 0) return;

        var sDiv = document.createElement("div");
        sDiv.setAttribute("id", "sDiv");
        sDiv.setAttribute("style", `height:3px;pointer-events:none;width:100%;position:absolute;z-index:99;`);

        var player = document.querySelector("video.video-stream");
        if (!player) return;
        var dur = player.duration;
        if (isNaN(dur)) return;

        for (var x in sTime) {
            var s1 = document.createElement("div");
            var s2 = sTime[x];
            s1.setAttribute("style", `height:3px;width:${(100 / dur) * (s2[1] - s2[0])}%;background:#0f8;position:absolute;z-index:9;left:${(100 / dur) * s2[0]}%;`);
            sDiv.appendChild(s1);
        }

        if (document.querySelector('.ytPlayerProgressBarHost')) {
            document.querySelector('.ytPlayerProgressBarHost').appendChild(sDiv);
        } else {
            try { document.querySelector('.ytProgressBarLineProgressBarLine').appendChild(sDiv); } catch (e) {}
        }
    }

    // Sponsorblock skip UI
    function addSkipper(sT) {
        if (document.getElementById("lite_skipper")) return;

        const sSDiv = document.createElement("div");
        sSDiv.id = "lite_skipper";
        sSDiv.setAttribute("style", `
            position: absolute; bottom: 60px; left: 50%; transform: translateX(-50%);
            height: 44px; display: flex; align-items: center; justify-content: space-between;
            padding: 0 16px; background: rgba(28, 28, 28, 0.9); backdrop-filter: blur(12px);
            border-radius: 22px; border: 1px solid rgba(255, 255, 255, 0.1);
            color: white; font-family: "Roboto", sans-serif; font-size: 14px; font-weight: 500;
            z-index: 2147483647; box-shadow: 0 4px 15px rgba(0,0,0,0.4);
            white-space: nowrap; transition: opacity 0.3s ease;
        `);

        sSDiv.innerHTML = `
            <span style="margin-right: 20px; letter-spacing: 0.2px;">Skipped Sponsor</span>
            <div style="display: flex; align-items: center; gap: 16px; height: 100%;">
                <div data-action="rewind" style="display: flex; align-items: center; justify-content: center; width: 32px; height: 32px; border-radius: 50%; background: rgba(255,255,255,0.1); cursor: pointer;">
                    <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16">
                        <path fill-rule="evenodd" d="M8 3a5 5 0 1 1-4.546 2.914.5.5 0 0 0-.908-.417A6 6 0 1 0 8 2v1z"/>
                        <path d="M8 4.466V.534a.25.25 0 0 0-.41-.192L5.23 2.308a.25.25 0 0 0 0 .384l2.36 1.966A.25.25 0 0 0 8 4.466z"/>
                    </svg>
                </div>
                <div data-action="close" style="display: flex; align-items: center; justify-content: center; width: 32px; height: 32px; border-radius: 50%; background: rgba(244, 67, 54, 0.2); cursor: pointer;">
                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="#f44336" viewBox="0 0 16 16">
                        <path d="M4.646 4.646a.5.5 0 0 1 .708 0L8 7.293l2.646-2.647a.5.5 0 0 1 .708.708L8.707 8l2.647 2.646a.5.5 0 0 1-.708.708L8 8.707l-2.646 2.647a.5.5 0 0 1-.708-.708L7.293 8 4.646 5.354a.5.5 0 0 1 0-.708z"/>
                    </svg>
                </div>
            </div>
        `;

        const container = document.getElementById("player-control-container") || document.getElementById("movie_player") || document.body;
        container.appendChild(sSDiv);

        sSDiv.addEventListener("click", (e) => {
            const el = e.target.closest("[data-action]");
            if (!el) return;
            const action = el.dataset.action;

            if (action === "close") {
                sSDiv.style.opacity = "0";
                setTimeout(() => sSDiv.remove(), 300);
            } else if (action === "rewind") {
                sSDiv.remove();
                const v = document.querySelector('.video-stream');
                if (v) v.currentTime = sT + 0.5;
            }
        });

        setTimeout(() => {
            if (sSDiv.parentNode) {
                sSDiv.style.opacity = "0";
                setTimeout(() => sSDiv.remove(), 300);
            }
        }, 5000);
    }

    const volSvg = `<svg xmlns="http://www.w3.org/2000/svg" height="16" viewBox="0 0 24 24" width="16" fill="#fff" style="filter:drop-shadow(0 0 1px black)"><path d="M11.485 2.143 3.913 6.687A6 6 0 001 11.832v.338a6 6 0 002.913 5.144l7.572 4.543A1 1 0 0013 21V3a1.001 1.001 0 00-1.515-.857Zm6.88 2.079a1 1 0 00-.001 1.414 9 9 0 010 12.728 1 1 0 001.414 1.414 11 11 0 000-15.556 1 1 0 00-1.413 0Zm-2.83 2.828a1 1 0 000 1.415 5 5 0 010 7.07 1 1 0 001.415 1.415 6.999 6.999 0 000-9.9 1 1 0 00-1.415 0Z"></path></svg>`;
    const brtSvg = `<svg xmlns="http://www.w3.org/2000/svg" height="16" viewBox="0 0 24 24" width="16" fill="#fff" style="filter:drop-shadow(0 0 1px black)"><path d="M12,7c-2.76,0-5,2.24-5,5s2.24,5,5,5s5-2.24,5-5S14.76,7,12,7L12,7z M2,13l2,0c0.55,0,1-0.45,1-1s-0.45-1-1-1l-2,0 c-0.55,0-1,0.45-1,1S1.45,13,2,13z M20,13l2,0c0.55,0,1-0.45,1-1s-0.45-1-1-1l-2,0c-0.55,0-1,0.45-1,1S19.45,13,20,13z M11,2v2 c0,0.55,0.45,1,1,1s1-0.45,1-1V2c0-0.55-0.45-1-1-1S11,1.45,11,2z M11,20v2c0,0.55,0.45,1,1,1s1-0.45,1-1v-2c0-0.55-0.45-1-1-1 C11.45,19,11,19.45,11,20z M5.99,4.58c-0.39-0.39-1.03-0.39-1.41,0c-0.39,0.39-0.39,1.03,0,1.41l1.06,1.06 c0.39,0.39,1.03,0.39,1.41,0s0.39-1.03,0-1.41L5.99,4.58z M18.36,16.95c-0.39-0.39-1.03-0.39-1.41,0c-0.39,0.39-0.39,1.03,0,1.41 l1.06,1.06c0.39,0.39,1.03,0.39,1.41,0c0.39-0.39,0.39-1.03,0-1.41L18.36,16.95z M19.42,5.99c0.39-0.39,0.39-1.03,0-1.41 c-0.39-0.39-1.03-0.39-1.41,0l-1.06,1.06c-0.39,0.39-0.39,1.03,0,1.41s1.03,0.39,1.41,0L19.42,5.99z M7.05,18.36 c0.39-0.39,0.39-1.03,0-1.41c-0.39-0.39-1.03-0.39-1.41,0l-1.06,1.06c-0.39,0.39-0.39,1.03,0,1.41s1.03,0.39,1.41,0L7.05,18.36z"/></svg>`;

    // Volume and brightness gestures
      function createGestureOverlays(isShorts) {
        const overlayStyle = "height:50%;width:20%;display:flex;flex-direction:column;align-items:center;justify-content:center;position:absolute;top:25%;opacity:0;transition:opacity 0.3s;z-index:2147483647;pointer-events:none;";
        const barHeightPercent = isShorts ? 45 : 80;
        const barWrap = `position:relative;background:rgba(255,255,255,0.2);height:${barHeightPercent}%;width:6px;border-radius:6px;backdrop-filter:blur(4px);display:flex;flex-direction:column-reverse;overflow:hidden;`;
        const barInner = "background:#fff;width:100%;transition:height 0.1s linear;";
        const iconStyle = "margin-bottom:12px;filter:drop-shadow(0 0 4px rgba(0,0,0,0.5));";

        const volS = document.createElement("div");
        volS.id = "volS";
        volS.dataset.shorts = String(isShorts);
        volS.style.cssText = overlayStyle + "right:0%;";
        volS.innerHTML = `<div style="${iconStyle}">${volSvg}</div><div style="${barWrap}"><div id="volIS" style="${barInner}height:${vol * 100}%;"></div></div>`;

        const brtS = document.createElement("div");
        brtS.id = "brtS";
        brtS.dataset.shorts = String(isShorts);
        brtS.style.cssText = overlayStyle + "left:0%;";
        brtS.innerHTML = `<div style="${iconStyle}">${brtSvg}</div><div style="${barWrap}"><div id="brtIS" style="${barInner}height:${brt * 100}%;"></div></div>`;

        return { volS, brtS };
     }

        function bindGestureListeners() {
         if (window._lp_gesture_bound) return;
         window._lp_gesture_bound = true;

         let dragging = false;
         let side = null;
         let startX = 0, startY = 0;
         let startTarget = null;
         const DRAG_THRESHOLD = 10;

         function getSide(x) {
             const w = window.innerWidth;
             if (x > w * 0.8) return "vol";
             if (x < w * 0.2) return "brt";
             return null;
         }

         document.addEventListener("touchstart", (e) => {
             if (e.touches.length !== 1) { side = null; return; }
             const t = e.touches[0];
             const h = window.innerHeight;
             if (t.pageY < h * 0.15 || t.pageY > h * 0.85) { side = null; return; }
             side = getSide(t.pageX);
             if (!side) return;
             dragging = false;
             startX = t.pageX;
             startY = t.pageY;
             touchstartY = t.pageY;
             startTarget = t.target;
         }, { capture: true, passive: true });

         document.addEventListener("touchmove", (e) => {
             if (!side || e.touches.length !== 1) return;
             const t = e.touches[0];
             const diffX = t.pageX - startX;
             const diffY = t.pageY - startY;

             if (!dragging) {
                 if (Math.abs(diffX) > Math.abs(diffY)) { side = null; return; }
                 if (Math.abs(diffY) < DRAG_THRESHOLD) return;
                 dragging = true;

                  if (startTarget) {
                      try {
                          startTarget.dispatchEvent(new TouchEvent("touchcancel", {
                              touches: [],
                              targetTouches: [],
                              changedTouches: e.changedTouches,
                              bubbles: true,
                              cancelable: true
                          }));
                     } catch (err) {}
                 }
             }

            e.preventDefault();
            e.stopPropagation();

            const isBrt = side === "brt";
            const target = document.getElementById(isBrt ? "brtS" : "volS");
            const bar = document.getElementById(isBrt ? "brtIS" : "volIS");
            if (target) target.style.opacity = "1";

            const moveDiff = touchstartY - t.pageY;
            if (isBrt) {
                brt = Math.max(0, Math.min(1, brt + (moveDiff * sens)));
                Android.setBrightness(brt);
                if (bar) bar.style.height = (brt * 100) + "%";
            } else {
                vol = Math.max(0, Math.min(1, vol + (moveDiff * sens)));
                Android.setVolume(vol);
                if (bar) bar.style.height = (vol * 100) + "%";
            }
            touchstartY = t.pageY;
         }, { capture: true, passive: false });

         document.addEventListener("touchend", () => {
             if (dragging) {
                 const target = document.getElementById(side === "brt" ? "brtS" : "volS");
                 if (target) target.style.opacity = "0";
             }
             dragging = false;
             side = null;
             startTarget = null;
         }, { capture: true, passive: true });
     }

     function initGestures() {
         const player = document.getElementById("player-container-id") || document.getElementById("movie_player");
         if (!player) return;

         const isShorts = window.location.pathname.includes("/shorts/");
         const existingVolS = document.getElementById("volS");
         if (existingVolS && existingVolS.dataset.shorts === String(isShorts)) {
             bindGestureListeners();
             return;
         }

        if (existingVolS) existingVolS.remove();
        const existingBrtS = document.getElementById("brtS");
        if (existingBrtS) existingBrtS.remove();

         const { volS, brtS } = createGestureOverlays(isShorts);
         player.appendChild(volS);
         player.appendChild(brtS);

         bindGestureListeners();
     }

    // Video zoom
    function initPinchZoom() {
        if (window._lp_pinch_active) return;
        window._lp_pinch_active = true;

        document.addEventListener('touchstart', e => {
            if (e.touches.length === 2) {
                prevDistance = Math.hypot(e.touches[1].pageX - e.touches[0].pageX, e.touches[1].pageY - e.touches[0].pageY);
            }
        }, { capture: true, passive: true });

        document.addEventListener('touchmove', e => {
            if (e.touches.length === 2 && prevDistance) {
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();

                const video = document.querySelector('video.video-stream');
                if (!video) return;

                const dist = Math.hypot(e.touches[1].pageX - e.touches[0].pageX, e.touches[1].pageY - e.touches[0].pageY);
                const delta = dist - prevDistance;
                currentZoom = Math.max(1, Math.min(10, currentZoom + (delta * zoomSens)));
                prevDistance = dist;
            }
        }, { capture: true, passive: false });

        document.addEventListener('touchend', e => {
            if (e.touches.length < 2) prevDistance = null;
        }, { capture: true, passive: true });
    }

    function applyForceZoom() {
        const vp = document.querySelector('meta[name="viewport"]');
        if (vp && vp.getAttribute("content") !== "") {
            vp.setAttribute('content', 'width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=10.0, user-scalable=yes');
        }
    }

    function pkc() {
        const prefs = JSON.parse(Android.getPreferences() || '{}');
        const isShorts = window.location.pathname.includes("/shorts/");
        const webviewPlayerEnabled = prefs.use_webview_player !== false;

        if (!window.location.href.includes("watch") && !window.location.href.includes("shorts")) return;

        if (isShorts) initGestures();

        if (!webviewPlayerEnabled) return;

        applyForceZoom();
        applyExtraSpeed();
        if (!isShorts) initGestures();
        initPinchZoom();
        checkSponsors();

        const video = document.querySelector('video.video-stream');
        if (video) {
            if (!video._lp_bound) {
                video._lp_bound = true;
                setTimeout(() => {
                    video.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                }, 100);

                const observer = new MutationObserver(() => {
                    const expectedTransform = `scale(${currentZoom})`;
                    if (video.style.getPropertyValue('transform') !== expectedTransform ||
                        video.style.getPropertyPriority('transform') !== 'important' ||
                        video.style.getPropertyValue('transform-origin') !== `center center` ||
                        video.style.getPropertyPriority('transform-origin') !== 'important') {
                        video.style.setProperty('transform', expectedTransform, 'important');
                        video.style.setProperty('transform-origin', 'center center', 'important');
                    }
                });
                observer.observe(video, { attributes: true, attributeFilter: ['style'] });

                video.addEventListener('timeupdate', () => {
                    skipSponsor();
                    var cur = video.currentTime;
                    for (var x in sTime) {
                        var s2 = sTime[x];
                        if (Math.floor(cur) == Math.floor(s2[0])) {
                            video.currentTime = s2[1];
                            addSkipper(s2[0]);
                        }
                    }
                });
            }

            if (video.muted) video.muted = false;
        }
    }

    setInterval(pkc, 500);
    pkc();
})();