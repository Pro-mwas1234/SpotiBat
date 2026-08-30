package com.project.lol.webview.injections

object DownloadButton {
    const val CONTENT = """
            window.splDoDownload = function(){
                var id = window.__curTrackId;
                if(!id){
                    AndBridge.deferMessage('Track not ready');
                    return;
                }
                var payload = JSON.stringify({
                    trackId: id,
                    title: window.__curTrackName || window.track || '',
                    artist: window.__curTrackArtist || window.artist || '',
                    album: window.__curTrackAlbum || '',
                    cover: window.__curTrackCover || window.cover || ''
                });
                AndBridge.downloadTrack(payload);
            };
            window.splAddDownloadBtn = function(){
                if(typeof window.dlBtn !== 'undefined') return;
                var lyBtn = document.querySelector('button[data-testid=lyrics-button]:not(.splf)');
                var queueBtn = document.querySelector('button[data-testid=control-button-queue]:not(.splf)');
                var anchorBtn = lyBtn || queueBtn;
                if(!anchorBtn) return;
                if(anchorBtn === lyBtn) lyBtn.classList.add('splf');
                var btn = document.createElement('button');
                btn.className = 'npbtn';
                btn.title = 'Download track';
                btn.innerHTML = '<svg viewBox="0 0 16 16" width="16" height="16"><path fill="currentColor" d="M8 1a1 1 0 0 1 1 1v6.586l2.293-2.293a1 1 0 1 1 1.414 1.414l-4 4a1 1 0 0 1-1.414 0l-4-4a1 1 0 1 1 1.414-1.414L7 8.586V2a1 1 0 0 1 1-1zM2 13a1 1 0 0 1 1-1h10a1 1 0 1 1 0 2H3a1 1 0 0 1-1-1z"/></svg>';
                btn.onclick = function(){ splDoDownload(); };
                anchorBtn.before(btn);
                window.dlBtn = btn;
            };
            setInterval(splAddDownloadBtn, 5000);
        
    """
}