package com.project.lol.webview.injections

object PlaybackControls {
    const val CONTENT = """
            window.playFromUri = function(uri, contextUri) {
                var playContext = contextUri || uri;
                var isLikedSongs = (playContext === 'your_library' || playContext.indexOf('collection') !== -1 || playContext === 'playlists' || playContext === 'spotify:collection:tracks');

                var playOptions = {
                    license: 'tft',
                    skip_to: {},
                    player_options_override: {}
                };

                var commandContext = {
                    uri: playContext,
                    url: 'context://' + playContext,
                    metadata: {}
                };

                var featIdent = playContext.match(/^spotify:([^:]+)/);
                featIdent = featIdent ? featIdent[1] : null;
                if (featIdent == 'user' || isLikedSongs) featIdent = 'your_library';

                if (isLikedSongs) {
                    var tracks = window.likedSongsCache || [];
                    var targetUri = (uri && uri.indexOf(':track:') !== -1) ? uri : ((tracks[0] && tracks[0].id) || uri);
                    var trackList = (tracks || []).map(function(t) { return t.id || t.uri || t; }).filter(Boolean);

                    if (targetUri && targetUri.indexOf(':track:') !== -1 && trackList.indexOf(targetUri) === -1) {
                        trackList.unshift(targetUri);
                    }

                    var collectionUri = window.spotUserId ? 'spotify:user:' + window.spotUserId + ':collection' : 'spotify:collection:tracks';

                    commandContext = {
                        uri: collectionUri,
                        url: 'context://' + collectionUri,
                        metadata: { context_description: 'Liked Songs' },
                        pages: [{ page_url: 'context://' + collectionUri, tracks: trackList.map(function(u) { return { uri: u }; }) }]
                    };

                    featIdent = 'collection-tracks';

                    playOptions.skip_to = { track_uri: targetUri };
                } else if (contextUri && contextUri !== uri) {
                    playOptions.skip_to = { track_uri: uri };
                }

                (window.mngFetch || oriFetch)('https://gew4-spclient.spotify.com/connect-state/v1/player/command/from/' + window.spotDevId + '/to/' + window.spotDevId, {
                    method: 'POST',
                    headers: { 'Authorization': window.spotAuthToken, 'Client-Token': window.spotCliToken, 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        command: {
                            context: commandContext,
                            play_origin: {
                                feature_identifier: featIdent || 'your_library',
                                feature_version: featVer,
                                referrer_identifier: 'your_library'
                            },
                            options: playOptions,
                            endpoint: 'play'
                        }
                    })
                });
            };
            window.actPlayPause = function(play) {
                var pb = window.pBtn;
                if (!pb) return;
                if (play === null || typeof play === 'undefined') {
                    pb.click();
                } else if (play === true) {
                    if (!window.playing) pb.click();
                } else if (play === false) {
                    if (window.playing) pb.click();
                }
            };
            window.actSkipBack = function() {
                var bb = document.querySelector('button[data-testid=control-button-skip-back]');
                if(bb) { AndBridge.wakeUp(); bb.click(); }
            };
            window.actSkipForward = function() {
                var fb = document.querySelector('button[data-testid=control-button-skip-forward]');
                if(fb) { AndBridge.wakeUp(); fb.click(); }
            };
            window.actToggleShuffle = function() {
                var sb = document.querySelector('button[data-testid="control-button-shuffle"]');
                if(!sb) {
                    var allb=document.querySelectorAll('button');
                    for(var i=0;i<allb.length;i++){
                        var sbal=allb[i].getAttribute('aria-label')||'';
                        if(/shuffle/i.test(sbal)&&!/spl-btn/.test(allb[i].className||'')){ sb=allb[i]; break; }
                    }
                }
                if(sb && sb.getAttribute('aria-disabled')!=='true') {
                    AndBridge.wakeUp();
                    sb.click();
                }
            };
            window.actRepeat = function() {
                var rb = document.querySelector('button[data-testid=control-button-repeat]');
                if(rb) {
                    if(repmode=='false') repmode='true';
                    else if(repmode=='true') repmode='mixed';
                    else repmode='false';
                    updMedia();
                    rb.click();
                }
            };
            window.actAddToFav = function() {
                var fb = document.querySelector('div[data-testid=now-playing-widget]>div:last-child>button');
                if(fb) {
                    if(fb.getAttribute('aria-checked')==='false') {
                        fb.click();
                        isfav=true;
                        updMedia();
                    } else {
                        AndBridge.wakeUp();
                        fb.click();
                        var rfint = setInterval(function(){
                            var fr = document.querySelector('#context-menu button[role=menuitemcheckbox][aria-checked=true]');
                            if(fr) {
                                clearInterval(rfint);
                                fr.click();
                                setTimeout(function(){
                                    var sb = document.querySelector('#context-menu button[type=submit]');
                                    if(sb) { sb.click(); isfav=false; updMedia(); }
                                    AndBridge.wakeOff();
                                },500);
                            }
                        },1000);
                    }
                }
            };
            window.actSeek = function(pos) {
                var rg = document.querySelector('div[data-testid=playback-progressbar] input[type=range]');
                if(rg) { rg.value=pos+1; rg.dispatchEvent(new Event('change',{bubbles:true})); }
            };
        
    """
}
