package com.project.lol.webview.injections

object FetchOverride {
    const val CONTENT = """
            (function(){
                if(window.oriFetch) return;
                window.splOpHashes = window.splOpHashes || {};
                var orig = window.fetch.bind(window);
                window.oriFetch = orig;
                window.fetch = function(input, init) {
                    if(init && init.headers) {
                        var h = init.headers;
                        var auth, cliTok;
                        if(typeof h.get === 'function') {
                            auth = h.get('Authorization') || h.get('authorization');
                            cliTok = h.get('Client-Token') || h.get('client-token');
                        } else {
                            auth = h.Authorization || h.authorization;
                            cliTok = h['Client-Token'] || h['client-token'];
                        }
                        if(auth && typeof auth === 'string') {
                            window.spotAuthToken = auth.indexOf('Bearer ')===-1 ? 'Bearer '+auth.replace(/^Bearer\s+/i,'') : auth;
                        }
                        if(cliTok) {
                            window.spotCliToken = cliTok;
                        }
                    }
                    var url = typeof input==='string' ? input : (input ? input.url : '');
                    if(url && url.indexOf) {
                        var m = url.match(/\/from\/([A-Za-z0-9_-]+)\/to\//);
                        if(m && m[1]) window.spotDevId = m[1];
                        var m2 = url.match(/connect-state\/v1\/player\/(?:command|transfer)\/from\/([A-Za-z0-9_-]+)\/to\/([A-Za-z0-9_-]+)/);
                        if(m2 && m2[2]) window.spotDevId = m2[2];
                        var m3 = url.match(/\/track-playback\/v1\/devices/);
                        if(m3 && init && init.body) {
                            try {
                                var pb = typeof init.body==='string' ? JSON.parse(init.body) : init.body;
                                if(pb && pb.device && pb.device.device_id && pb.device.device_id!==window.spotDevId) {
                                    window.spotDevId = pb.device.device_id;
                                }
                            } catch(e){}
                        }
                        var m4 = url.match(/connect-state\/v1\/player\/command/);
                        if(m4 && init && init.headers) {
                            var body = init.body;
                            if(body && typeof body==='string') {
                                try {
                                    var j = JSON.parse(body);
                                    if(j && j.command && j.command.context && j.command.context.uri) {
                                        var m5 = j.command.context.uri.match(/spotify:track:([A-Za-z0-9]+)/);
                                        if(m5) {
                                            window.lastPlayedTrack = m5[1];
                                        }
                                    }
                                } catch(e){}
                            }
                        }
                    }
                    var method = (init && init.method) ? String(init.method).toUpperCase() : 'GET';
                    if(!window.__splOwnCall && url && url.indexOf && url.indexOf('api-partner.spotify.com/pathfinder/v2/query') !== -1 && init && init.body) {
                        try {
                            var qb = typeof init.body==='string' ? JSON.parse(init.body) : init.body;
                            if(qb && qb.operationName && qb.extensions && qb.extensions.persistedQuery && qb.extensions.persistedQuery.sha256Hash) {
                                window.splOpHashes[qb.operationName] = qb.extensions.persistedQuery.sha256Hash;
                            }
                        } catch(e){}
                    }
                    if(!window.__spotilolUseProxy && url && url.indexOf && (url.indexOf('connect-state') !== -1 || url.indexOf('melody/v1/msg') !== -1 || url.indexOf('/track-playback/') !== -1) && window.mngFetch) {
                        return window.mngFetch(input, init);
                    }
                    if(window.ffDone && url && url.indexOf && url.indexOf('/track-playback/') !== -1 && method === 'PUT' && init && init.body) {
                        try {
                            var pb = typeof init.body === 'string' ? JSON.parse(init.body) : init.body;
                            if(pb && pb.state_ref && pb.state_ref.paused === true && window.playing) {
                                window.actPlayPause(false);
                            } else if(pb && pb.state_ref && pb.state_ref.paused === false && !window.playing) {
                                window.actPlayPause(true);
                            }
                        } catch(e){}
                    }
                    return orig.call(window, input, init);
                };
            })();
        
    """
}
