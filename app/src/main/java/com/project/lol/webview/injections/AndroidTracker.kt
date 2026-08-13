package com.project.lol.webview.injections

object AndroidTracker {
    const val CONTENT = """
            window.addAndAuto = function(){
                if(aaint) {
                    if(typeof aaint.disconnect==='function') { try { aaint.disconnect(); } catch(e){} }
                    else { clearInterval(aaint); }
                }
                function readTrackState(){
                    var ta = document.querySelector('a[data-testid=context-item-link]');
                    if(ta) track=ta.text; else track=null;
                    var aa = document.querySelector('a[data-testid=context-item-info-artist]');
                    if(!aa) aa = document.querySelector('a[data-testid=context-item-info-show]');
                    if(aa) artist=aa.text; else artist='';
                    var rr = document.querySelector('button[data-testid=control-button-repeat]');
                    if(rr) repmode=rr.getAttribute('aria-checked'); else repmode='false';
                    var sbb = document.querySelector('button[data-testid="control-button-shuffle"]');
                    if(!sbb) {
                        var allb=document.querySelectorAll('button');
                        for(var i=0;i<allb.length;i++){
                            var sbal=allb[i].getAttribute('aria-label')||'';
                            if(/shuffle/i.test(sbal)&&!/spl-btn/.test(allb[i].className||'')){ sbb=allb[i]; break; }
                        }
                    }
                    if(sbb){
                        if(sbb.getAttribute('aria-disabled')==='true') shuffle='disabled';
                        else if(sbb.getAttribute('aria-checked')==='true') shuffle='shuffle';
                        else {
                            var sbl=sbb.getAttribute('aria-label')||'';
                            if(/smart shuffle/i.test(sbl)) shuffle=/^disable/i.test(sbl)?'smart':'shuffle';
                            else shuffle=/^disable/i.test(sbl)?'shuffle':'off';
                        }
                    } else shuffle='off';
                    var fb = document.querySelector('div[data-testid=now-playing-widget]>div:last-child>button');
                    if(fb && fb.getAttribute('aria-checked')==='true') isfav=true; else isfav=false;
                    var pb = document.querySelector('button[data-testid=control-button-playpause]');
                    if(pb) playing=pb.getAttribute('aria-label')!=='Play';
                    var rg = document.querySelector('div[data-testid=playback-progressbar] input[type=range]');
                    if(rg) { duration=parseInt(rg.getAttribute('max')); position=parseInt(rg.getAttribute('value')); }
                    else { duration=null; position=null; }
                    var im = document.querySelector('img[data-testid=cover-art-image]');
                    if(im) {
                        var s=im.src;
                        if(s.indexOf('i.scdn.co')!==-1) {
                            s=s.replace(/ab67616d0000[0-9a-f]{4}/,'ab67616d000082c1');
                            s=s.replace(/ab6761670000[0-9a-f]{4}/,'ab676167000082e8');
                        }
                        cover=s;
                    } else cover=null;
                    updMedia();
                }
                try {
                    var npTarget = document.querySelector('aside[data-testid="now-playing-bar"]') || document.body;
                    var readTimeout;
                    var obs = new MutationObserver(function(){
                        clearTimeout(readTimeout);
                        readTimeout = setTimeout(readTrackState, 200);
                    });
                    obs.observe(npTarget, { childList: true, subtree: true, attributes: true, characterData: true });
                    aaint = obs;
                    readTrackState();
                } catch(e) {
                    aaint = setInterval(readTrackState, 1000);
                }
            };
        
    """
}
