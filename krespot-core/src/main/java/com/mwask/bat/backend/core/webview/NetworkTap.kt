/*
 *  _   __                          _   
 * | | / /                         | |  
 * | |/ / _ __ ___  ___ _ __   ___ | |_ 
 * |    \| '__/ _ \/ __| '_ \ / _ \| __|
 * | |\  \ | |  __/\__ \ |_) | (_) | |_ 
 * \_| \_/_|  \___||___/ .__/ \___/ \__|
 *                     | |              
 *                     |_|              
 *
 * Copyright (c) 2026 23fpsz (SpotiBat). All Rights Reserved.
 * Original repository: https://github.com/Pro-mwas1234/SpotiBat
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.mwask.bat.backend.core.webview

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.mwask.bat.backend.core.auth.EncoreAuth
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.Pathfinder
import org.json.JSONObject

private val log = Logs.of(Logs.Module.NET)
private const val SPOTIFY_ORIGIN = "https://open.spotify.com"

object NetworkTap {

    fun install(view: WebView, context: Context) {
        view.addJavascriptInterface(Bridge(context.applicationContext), "AndNet")
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            log.w("tap: DOCUMENT_START_SCRIPT unsupported, webview calls will not be logged")
            return
        }
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(view, JS, setOf(SPOTIFY_ORIGIN))
            log.i("tap installed on $SPOTIFY_ORIGIN (document-start)")
        }.onFailure { log.e("tap install failed", it) }
    }

    class Bridge(private val context: Context) {

        private var seen = 0
        private var credSeen = false
        private var pathfinderWithoutCred = 0

        private fun firstSeen(kind: String) {
            seen++
            if (seen == 1) log.i("tap is alive, first $kind event arrived")
        }

        @JavascriptInterface
        fun call(json: String) {
            val o = parse(json) ?: return
            if (o.optString("url").isBlank()) return
            firstSeen("call")
        }

        @JavascriptInterface
        fun pathfinder(json: String) {
            val o = parse(json) ?: return
            val op = o.optString("op")
            val hash = o.optString("hash")
            val vars = o.optString("vars")
            firstSeen("pathfinder")
            if (op.isNotBlank() && hash.isNotBlank()) {
                Pathfinder.record(context, op, hash, vars)
            }
            if (!credSeen) {
                pathfinderWithoutCred++
                if (pathfinderWithoutCred == 3) {
                    log.w("3 pathfinder calls and still no authorization header — " +
                            "credentials are probably set outside fetch/XHR"
                    )
                }
            }
        }

        @JavascriptInterface
        fun headers(json: String) {
            val o = parse(json) ?: return
            val auth = o.optString("auth")
            val clientToken = o.optString("ct")
            val bearer = auth.removePrefix("Bearer ").removePrefix("bearer ").trim()
            if (bearer.isNotBlank() || clientToken.isNotBlank()) credSeen = true
            if (bearer.isNotBlank()) {
                if (bearer != EncoreAuth.token(context)) {
                    log.i("web credentials: bearer (${bearer.length}B) from ${shorten(o.optString("url"))}"
                    )
                    EncoreAuth.saveToken(context, bearer)
                } else {
                    log.i("web credentials: bearer (${bearer.length}B) unchanged, timestamp refreshed")
                    EncoreAuth.markTokenSeen(context)
                }
            }
            if (clientToken.isNotBlank() && clientToken != EncoreAuth.clientToken(context)) {
                log.i("web credentials: client-token (${clientToken.length}B)")
                EncoreAuth.saveClientToken(context, clientToken)
            }
        }
    }

    private fun parse(json: String): JSONObject? =
        runCatching { JSONObject(json) }.getOrElse {
            log.w("tap payload not-json ${json.take(120)}")
            null
        }

    private fun shorten(url: String): String {
        val clean = url.substringBefore('?')
        return if (clean.length <= 140) clean else clean.take(140) + "…"
    }

    private val JS = """
        (function(){
            if (window.__spotibatNetTap) return;
            window.__spotibatNetTap = 1;

            function send(kind, payload){
                try {
                    if (!window.AndNet) return;
                    var s = JSON.stringify(payload);
                    if (kind === 'pf') window.AndNet.pathfinder(s);
                    else if (kind === 'cred') window.AndNet.headers(s);
                    else window.AndNet.call(s);
                } catch(e) {}
            }

            function normHeaders(h){
                var out = {};
                if (!h) return out;
                try {
                    if (typeof h.forEach === 'function' && typeof h.get === 'function') {
                        h.forEach(function(v, k){ out[String(k).toLowerCase()] = String(v); });
                    } else if (Object.prototype.toString.call(h) === '[object Array]') {
                        for (var i = 0; i < h.length; i++) {
                            var p = h[i];
                            if (p && p.length === 2) out[String(p[0]).toLowerCase()] = String(p[1]);
                        }
                    } else {
                        for (var k in h) {
                            if (Object.prototype.hasOwnProperty.call(h, k)) {
                                out[String(k).toLowerCase()] = String(h[k]);
                            }
                        }
                    }
                } catch(e) {}
                return out;
            }

            var lastAuth = '', lastCt = '';

            function reportCreds(url, h){
                if (!h) return;
                var auth = h['authorization'] || '';
                var ct = h['client-token'] || '';
                if (!auth && !ct) return;
                if (auth === lastAuth && ct === lastCt) return;
                lastAuth = auth;
                lastCt = ct;
                send('cred', { url: url, auth: auth, ct: ct });
            }

            function abs(u){
                try { return new URL(String(u), location.href).href; }
                catch(e) { return String(u); }
            }

            function isPf(u){ return u.indexOf('/pathfinder') >= 0; }

            function fromQuery(url){
                var out = { op: '', hash: '', vars: '' };
                try {
                    var q = new URL(url).searchParams;
                    out.op = q.get('operationName') || '';
                    out.vars = q.get('variables') || '';
                    var ext = q.get('extensions');
                    if (ext) {
                        var j = JSON.parse(ext);
                        out.hash = (j.persistedQuery && j.persistedQuery.sha256Hash) || '';
                    }
                } catch(e) {}
                return out;
            }

            function fromBody(body){
                var out = { op: '', hash: '', vars: '' };
                if (!body) return out;
                try {
                    var b = JSON.parse(body);
                    out.op = b.operationName || '';
                    out.vars = b.variables ? JSON.stringify(b.variables) : '';
                    out.hash = (b.extensions && b.extensions.persistedQuery &&
                        b.extensions.persistedQuery.sha256Hash) || '';
                } catch(e) {}
                return out;
            }

            function report(url, method, body, status, ms){
                if (!url || url.indexOf('blob:') === 0 || url.indexOf('data:') === 0) return;
                var base = { url: url, method: method, status: status, ms: ms };
                if (isPf(url)) {
                    var q = fromQuery(url);
                    var b = fromBody(body);
                    base.op = q.op || b.op;
                    base.hash = q.hash || b.hash;
                    base.vars = (q.vars || b.vars || '').substring(0, 400);
                    send('pf', base);
                } else {
                    send('call', base);
                }
            }

            var of = window.fetch;
            if (of) {
                window.fetch = function(input, init){
                    var url = abs(typeof input === 'string' ? input : (input && input.url) || '');
                    var method = (init && init.method) || (input && input.method) || 'GET';
                    var body = (init && typeof init.body === 'string') ? init.body : '';
                    try {
                        reportCreds(url, normHeaders(init && init.headers));
                        if (input && typeof input === 'object' && input.headers) {
                            reportCreds(url, normHeaders(input.headers));
                        }
                    } catch(e) {}
                    var t0 = Date.now();
                    return of.apply(this, arguments).then(function(res){
                        try { report(url, method, body, res.status, Date.now() - t0); } catch(e) {}
                        return res;
                    })['catch'](function(err){
                        try { report(url, method, body, -1, Date.now() - t0); } catch(e) {}
                        throw err;
                    });
                };
            }

            var XP = XMLHttpRequest.prototype;
            var xo = XP.open, xs = XP.send, xh = XP.setRequestHeader;
            XP.open = function(m, u){
                try { this.__slt = { m: m, u: abs(u), b: '', h: {} }; } catch(e) {}
                return xo.apply(this, arguments);
            };
            XP.setRequestHeader = function(k, v){
                try {
                    if (this.__slt && this.__slt.h) {
                        this.__slt.h[String(k).toLowerCase()] = String(v);
                    }
                } catch(e) {}
                return xh.apply(this, arguments);
            };
            XP.send = function(b){
                var t = this.__slt || { m: 'GET', u: '', b: '', h: {} };
                if (typeof b === 'string') t.b = b;
                try { reportCreds(t.u, t.h); } catch(e) {}
                var t0 = Date.now();
                var self = this;
                this.addEventListener('loadend', function(){
                    try { report(t.u, t.m, t.b, self.status, Date.now() - t0); } catch(e) {}
                });
                return xs.apply(this, arguments);
            };
        })();
    """.trimIndent()
}
