package com.project.lol.webview.injections

object AutoFeatures {
    const val CONTENT = """
            window.splAutoPlay = function(){
                if(window.autoPlayMode==='disabled') return;
                if(window.__splApActive) return;
                if(window.__splApDone) return;
                window.__splApRan = true;
                window.__splApActive = true;

                var attempts = 0;
                var noBtnTicks = 0;
                var MAX_ATTEMPTS = 20;
                var TICK_MS = 3000;

                function done(unlocked){
                    window.__splApActive = false;
                    if(unlocked){
                        window.__splApDone = true;
                        window.__splUnlocked = true;
                    }
                    clearInterval(iv);
                }

                var tick = function(){
                    var pb = window.pBtn;
                    if(!pb){
                        if(++noBtnTicks > 40) done(false);
                        return;
                    }
                    if(reqPause){ done(false); return; }
                    if(pb.getAttribute('aria-label')!=='Play'){ done(true); return; }
                    if(!document.querySelector('div[data-testid=playback-progressbar] input[type=range]')){
                        if(++noBtnTicks > 40) done(false);
                        return;
                    }
                    if(attempts >= MAX_ATTEMPTS){ done(false); return; }
                    attempts++;
                    pb.click();
                };

                var iv = setInterval(tick, TICK_MS);
                tick();
            };

            window.addAutoFeatures = function(){
                if(afint) clearInterval(afint);
                afint = setInterval(function(){
                    if(window.closeNpPref) closeNowPlay();
                    var ft = document.querySelector('aside div.encore-bright-accent-set button');
                    if(ft) {
                        ft.click();
                        setTimeout(function(){
                            var cb = document.querySelector('aside ul[role=list] li[role=listitem] div[role=button]');
                            if(cb) cb.click();
                        },500);
                    }
                    if(window.autoPlayMode==='permanent' && 'pBtn' in window && !reqPause && !ulFlag && !window.__splApActive && pBtn.getAttribute('aria-label')==='Play') {
                        pBtn.click();
                    }
                },5000);
            };
    """
}