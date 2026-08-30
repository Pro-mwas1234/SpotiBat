package com.project.lol.webview.injections

/*
 * CREDIT: Spotilol - Lyrics Sync Float
 * GitHub: https://github.com/AldySan
 */

object LyricsSyncFix {
    const val CONTENT = """
        (function(){
            if (window.__splLyricsFixInit) return;
            window.__splLyricsFixInit = true;

            var GAP = 10;

            var SWEEP_MS = 800;
            var OBS_DEBOUNCE_MS = 120;

            var tracked = [];
            var lastSweep = 0;
            var obsPending = false;

            function track(el){ tracked.push(el); }
            function untrack(el){
                var i = tracked.indexOf(el);
                if (i >= 0) tracked.splice(i, 1);
            }

            function isSyncBtn(b){
                if (!b || b.tagName !== 'BUTTON') return false;
                if (b.getAttribute('data-encore-id') !== 'buttonPrimary') return false;
                var t = (b.textContent || '').trim();
                return t.length > 0 && t.length <= 24 &&
                    /^(sync|sincron|synchron)/i.test(t);
            }

            function visibleEl(b){
                if (!b || !b.isConnected) return false;
                var r = b.getBoundingClientRect();
                if (r.height < 2 || r.width < 2) return false;
                var cs = getComputedStyle(b);
                if (cs.display === 'none' || cs.visibility === 'hidden') return false;
                if (parseFloat(cs.opacity) < 0.02) return false;
                var p = b.parentElement, hops = 0;
                while (p && hops++ < 6){
                    var pcs = getComputedStyle(p);
                    if (pcs.display === 'none') return false;
                    if (parseFloat(pcs.opacity) < 0.02) return false;
                    p = p.parentElement;
                }
                return true;
            }

            function pickCandidate(){
                var all = document.querySelectorAll('button[data-encore-id="buttonPrimary"]');
                var visible = [];
                for (var i = 0; i < all.length; i++){
                    if (!isSyncBtn(all[i])) continue;
                    if (visibleEl(all[i])) visible.push(all[i]);
                }
                if (!visible.length) return null;
                var pt = (typeof window.splPlayerTop === 'function')
                    ? window.splPlayerTop()
                    : (document.documentElement.clientHeight || window.innerHeight || 600);
                var best = null, bestDist = Infinity;
                for (var i = 0; i < visible.length; i++){
                    var r = visible[i].getBoundingClientRect();
                    var d = Math.abs(r.top - pt);
                    if (d < bestDist){ bestDist = d; best = visible[i]; }
                }
                return best;
            }

            function place(btn){
                var vh = (typeof window.splViewH === 'function')
                    ? window.splViewH()
                    : (document.documentElement.clientHeight || window.innerHeight || 600);
                var pt = (typeof window.splPlayerTop === 'function')
                    ? window.splPlayerTop()
                    : vh;
                var above = Math.max(10, vh - pt) + GAP;
                var roof = vh - 56;
                if (above > roof) above = roof;
                btn.style.setProperty('bottom', Math.round(above) + 'px', 'important');
            }

            function liberate(el){
                var p = el.parentElement, hops = 0;
                while (p && p !== document.body && hops++ < 15){
                    var pcs = getComputedStyle(p);
                    if (pcs.zIndex !== 'auto')
                        p.style.setProperty('z-index', '2147483647', 'important');
                    if (pcs.transform !== 'none')
                        p.style.setProperty('transform', 'none', 'important');
                    p = p.parentElement;
                }
            }

            function dress(btn){
                if (!btn || btn.__splFloated) return;
                btn.__splFloated = true;
                btn.classList.add('spl-sync-btn');
                liberate(btn);
                btn.style.setProperty('position', 'fixed', 'important');
                btn.style.setProperty('left', '50%', 'important');
                btn.style.setProperty('top', 'auto', 'important');
                btn.style.setProperty('right', 'auto', 'important');
                btn.style.setProperty('z-index', '2147483647', 'important');
                btn.style.setProperty('margin', '0', 'important');
                btn.style.setProperty('transform', 'translateX(-50%)', 'important');
                btn.style.setProperty('visibility', 'visible', 'important');
                place(btn);
                track(btn);
            }

            function release(btn){
                if (!btn) return;
                if (btn.__splFloated){
                    btn.__splFloated = false;
                    btn.classList.remove('spl-sync-btn');
                    ['position','left','top','right','z-index','margin','transform','bottom','visibility']
                        .forEach(function(prop){ btn.style.removeProperty(prop); });
                }
                untrack(btn);
            }

            function rebalance(){
                for (var i = tracked.length - 1; i >= 0; i--){
                    if (!tracked[i].isConnected) untrack(tracked[i]);
                }
                var best = pickCandidate();
                for (var i = tracked.length - 1; i >= 0; i--){
                    if (tracked[i] !== best) release(tracked[i]);
                }
                if (!best){
                    for (var i = tracked.length - 1; i >= 0; i--) release(tracked[i]);
                    return;
                }
                if (!best.__splFloated) dress(best);
            }

            function reposition(){
                for (var i = 0; i < tracked.length; i++){
                    if (tracked[i].isConnected) place(tracked[i]);
                }
            }

            function tick(){
                if (window.__splBg) return;
                if (tracked.length) reposition();
                var now = Date.now();
                if (now - lastSweep < SWEEP_MS) return;
                lastSweep = now;
                rebalance();
            }
            if (window.__splFloaters) window.__splFloaters.push(tick);
            else setInterval(tick, 300);

            rebalance();

            var obs = new MutationObserver(function(muts){
                if (window.__splBg) return;
                var dirty = false;
                for (var i = 0; i < muts.length && !dirty; i++){
                    var a = muts[i].addedNodes, r = muts[i].removedNodes;
                    for (var j = 0; j < a.length; j++){
                        if (a[j].nodeType === 1){ dirty = true; break; }
                    }
                    if (!dirty) for (var k = 0; k < r.length; k++){
                        if (r[k].nodeType === 1){ dirty = true; break; }
                    }
                }
                if (dirty && !obsPending){
                    obsPending = true;
                    setTimeout(function(){
                        obsPending = false;
                        rebalance();
                    }, OBS_DEBOUNCE_MS);
                }
            });
            obs.observe(document.body, { childList: true, subtree: true });

            window.addEventListener('beforeunload', function(){
                try { obs.disconnect(); } catch(e){}
            });
        })();
    """
}