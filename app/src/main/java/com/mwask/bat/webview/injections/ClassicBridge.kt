package com.project.lol.webview.injections

object ClassicBridge {
    const val CONTENT = """
        (function(){
            if(window.__spotilolUseProxy) {
                window.mngFetch = function(url, opts) {
                    return (window.fetch || oriFetch).apply(window, arguments);
                };
            } else {
                window.mngFetch = async function(url, opts){
                    var o = opts || {};
                    var h = o.headers || {};
                    var headers = {};
                    if(typeof h.forEach === 'function') {
                        h.forEach(function(v,k){ headers[k]=v; });
                    } else if(typeof h.get === 'function') {
                        try {
                            headers['Authorization'] = h.get('Authorization');
                            headers['Client-Token'] = h.get('Client-Token');
                            headers['Content-Type'] = h.get('Content-Type');
                        } catch(e){}
                    } else {
                        headers = h;
                    }
                    var body = null;
                    if(o.body) {
                        body = (typeof o.body === 'string') ? o.body : JSON.stringify(o.body);
                    }
                    var raw = await AndBridge.nFetch(url, JSON.stringify({
                        method: o.method || 'GET',
                        headers: headers,
                        body: body
                    }));
                    var data;
                    try { data = JSON.parse(raw); }
                    catch(e) { throw new Error('Bad native response: ' + raw); }
                    if(!data || data.status === 0) {
                        throw new Error('network error: ' + (data ? data.body : 'unknown'));
                    }
                    return {
                        status: data.status,
                        ok: data.status >= 200 && data.status < 300,
                        headers: new Headers(data.headers || {}),
                        url: url,
                        json: function(){ return Promise.resolve(JSON.parse(data.body || '{}')); },
                        text: function(){ return Promise.resolve(data.body || ''); },
                        arrayBuffer: function(){ return Promise.resolve(new TextEncoder().encode(data.body || '').buffer); },
                        clone: function(){ return this; }
                    };
                };
            }
        })();
    
    """
}
