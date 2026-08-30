package com.project.lol.webview.injections

/*
 * CREDIT: Spotilol - Worker Neutralizer.
 * GitHub: https://github.com/AldySan
 *
 * Unregisters Spotify's service worker and prevents re-registration.
 * The SW intercepts ALL network requests to check its cache map - pure
 * overhead on a mobile WebView that always has internet and has its own
 * HTTP cache + optional MITM proxy.
 *
 * Injected in onPageStarted so it runs before Spotify's scripts try to
 * register the SW via Workbox. The register() override returns a rejected
 * promise - Workbox catches this and continues without SW, falling back
 * to network-first behavior (which is what we want).
 *
 * Also throttles aggressive 250ms setInterval polling to 500ms.
 * The 250ms timers from web-player.js:215359 are non-core UI polling
 * that gets cleared/recreated in bursts. A gentle 2x throttle reduces
 * CPU wakeups by 50% with zero perceptible UX impact. Core intervals
 * (progress at 500ms, Connect at 1000ms) have different delays and
 * are untouched. PowerSave mode applies its own throttle upstream,
 * so this check never fires when PowerSave is active.
 */
object WorkerNeutralize {
    const val CONTENT = """
        (function(){
            if(window.__splWorkerNeutralized) return;
            window.__splWorkerNeutralized = true;

            /* === Service Worker: unregister + prevent re-registration === */
            if(navigator.serviceWorker){
                try {
                    navigator.serviceWorker.register = function(){
                        return Promise.reject(new Error('SW blocked by Spotilol'));
                    };
                } catch(e){}
                try {
                    navigator.serviceWorker.getRegistrations().then(function(regs){
                        regs.forEach(function(reg){
                            reg.unregister().then(function(ok){
                                if(ok){
                                    try{AndBridge.dbg('s','SW unregistered: '+reg.scope)}catch(e){}
                                }
                            }).catch(function(){});
                        });
                    }).catch(function(){});
                } catch(e){}
            }

            /* === Interval throttle: 250ms -> 500ms ===
               Gentle 2x throttle for burst polling timers.
               Core intervals (500ms progress, 1000ms Connect) 
               have different delays and pass through untouched.
               PowerSave modifies delay before this check fires,
               so no compounding occurs. */
            try {
                var origSI = window.setInterval.bind(window);
                window.setInterval = function(fn, delay){
                    if(delay === 250) delay = 500;
                    return origSI(fn, delay);
                };
            } catch(e){}

            /* === Background video park/restore ===
               onStop used to strip canvas video srcs with no restore path.
               Spotify's React state never noticed, so returning to the app
               left a sourceless <video> = blank Now Playing canvas.
               Park: stash src/timecode, then kill. Restore: put it back. */
            try {
                var parked = [];
                var sweepIv = null;

                function isCanvasVid(v){
                    try{
                        if(v.muted) return true;
                        if(v.hasAttribute && v.hasAttribute('loop')) return true;
                        if(v.style && v.style.objectFit === 'cover') return true;
                    }catch(e){}
                    return false;
                }

                function parkLive(){
                    var vs = document.querySelectorAll('video');
                    for(var i=0;i<vs.length;i++){
                        var v = vs[i];
                        if(v.__splParked) continue;
                        if(!isCanvasVid(v)) continue;
                        var src = v.currentSrc || v.getAttribute('src') || '';
                        /* blob: = MediaSource, can't be re-attached after load().
                           Leave those alone rather than strand them. */
                        if(!src || src.indexOf('blob:') === 0) continue;
                        v.__splParked = true;
                        parked.push({el:v, src:src, t:(v.currentTime||0), playing:(!v.paused && !v.ended)});
                        try{ v.pause(); }catch(e){}
                        try{ v.removeAttribute('src'); }catch(e){}
                        try{ v.load(); }catch(e){}
                    }
                    return parked.length;
                }

                window.__splParkVideos = function(){
                    parkLive();
                };

                window.__splRestoreVideos = function(){
                    if(sweepIv){ clearInterval(sweepIv); sweepIv = null; }
                    if(!parked.length) return;
                    for(var i=0;i<parked.length;i++){
                        var b = parked[i];
                        var v = b.el;
                        if(!v || !v.isConnected) continue;
                        /* Spotify already swapped in a fresh src (track changed
                           while backgrounded) - its own source wins. */
                        if(v.getAttribute('src') || v.currentSrc){ v.__splParked = false; continue; }
                        try{
                            v.src = b.src;
                            v.currentTime = b.t || 0;
                            if(b.playing){
                                var p = v.play();
                                if(p && p.catch) p.catch(function(){});
                            }
                        }catch(e){}
                        v.__splParked = false;
                    }
                    parked = [];
                    try{ AndBridge.dbg('s','videos restored from background park'); }catch(e){}
                };

                document.addEventListener('visibilitychange', function(){
                    if(document.visibilityState === 'hidden') window.__splParkVideos();
                    else window.__splRestoreVideos();
                });
            } catch(e){}
        })();
    """
}