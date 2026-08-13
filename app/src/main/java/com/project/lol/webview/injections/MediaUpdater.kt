package com.project.lol.webview.injections

object MediaUpdater {
    const val CONTENT = """
            window.updMedia = function(){
                var currState=track+'|'+artist+'|'+playing+'|'+repmode+'|'+isfav+'|'+shuffle;
                if(currState!==lastState) {
                    lastState=currState;
                    var values={artist:artist,track:track,playing:playing,repeat:repmode,fav:isfav,shuffle:shuffle,duration:duration,position:position,cover:cover};
                    AndBridge.recMediaStatus(JSON.stringify(values));
                } else {
                    AndBridge.recMediaPosition(position);
                    lastPos=position;
                }
            };
        
    """
}
