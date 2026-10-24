package com.mwask.bat.webview.injections

/*
 * CREDIT: SpotiBat - Lyrics Offset.
 * Adjusts lyric timing by shifting the playback position when lyrics are shown.
 * The user selects an offset (±10s) in Settings → Lyrics Offset.
 * When a track starts, the player seeks by that offset so the lyrics sync.
 */
object LyricsOffset {
    const val CONTENT = """
        (function(){
            if(window.__splLyricsOffsetInit) return;
            window.__splLyricsOffsetInit = true;

            var _offset = parseFloat(window.__splLyricsOffsetPref) || 0;
            window.splLyricsOffset = _offset;
            var _applied = false;

            // Called from native when pref changes.
            window.__splSetLyricsOffsetPref = function(val){
                val = parseFloat(val) || 0;
                window.__splLyricsOffsetPref = val;
                window.splLyricsOffset = val;
                _offset = val;
                // Re-apply on current track if lyrics panel is open.
                if(_offset !== 0 && window.__splLyricsOffsetIsOpen) _applyOffsetNow();
            };

            // Actually seek the player by the offset amount.
            function _applyOffsetNow(){
                var rg = document.querySelector('[data-testid="playback-progressbar"] input[type=range]');
                if(!rg) return;
                var dur = parseFloat(rg.getAttribute('max')) || 0;
                var cur = parseFloat(rg.value) || 0;
                if(dur <= 0) return;
                var newPos = cur + Math.round(_offset * 1000);
                newPos = Math.max(0, Math.min(dur, newPos));
                var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;
                setter.call(rg, String(newPos));
                rg.dispatchEvent(new Event('input',{bubbles:true}));
                rg.dispatchEvent(new Event('change',{bubbles:true}));
            }

            // Listen for lyrics panel open/close to apply offset at right time.
            var lyrBtn = null;
            var _lyrObs = null;
            function watchLyricsBtn(){
                lyrBtn = document.querySelector('button[data-testid=lyrics-button]');
                if(lyrBtn && !_lyrObs){
                    _lyrObs = new MutationObserver(function(){
                        window.__splLyricsOffsetIsOpen = !lyrBtn.disabled;
                        if(window.__splLyricsOffsetIsOpen && _offset !== 0 && !_applied){
                            _applyOffsetNow();
                            _applied = true;
                        } else if(!window.__splLyricsOffsetIsOpen){
                            _applied = false;
                        }
                    });
                    try { _lyrObs.observe(lyrBtn, { attributes: true, attributeFilter: ['disabled'] }); } catch(e){}
                }
            }
            watchLyricsBtn();
            setInterval(watchLyricsBtn, 2000);

            // On track change, reset _applied flag.
            if(typeof splOnTrackChange === 'function'){
                splOnTrackChange(function(uri, id){
                    _applied = false;
                });
            }
        })();
    """
}
