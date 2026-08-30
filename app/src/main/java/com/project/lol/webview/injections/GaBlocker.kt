package com.project.lol.webview.injections

/*
 * CREDIT: uBlock Origin (Raymond Hill) - Google Analytics Neutralizer
 * Source: https://github.com/gorhill/uBlock
 *
 * Replaces window.ga with a noop that still fires hitCallbacks
 * so pages depending on GA callbacks don't break. Empties the
 * existing ga.q queue and patches dataLayer.push the same way.
 */

object GaBlocker {
    const val CONTENT = """
        (function(){
            'use strict';

            var noopfn = function(){};

            var Tracker = function(){};
            var p = Tracker.prototype;
            p.get = noopfn;
            p.set = noopfn;
            p.send = noopfn;

            var w = window;
            var gaName = w.GoogleAnalyticsObject || 'ga';
            var gaQueue = w[gaName];

            var ga = function(){
                var len = arguments.length;
                if (len === 0) return;
                var args = Array.from(arguments);
                var fn;
                var a = args[len-1];
                if (a instanceof Object && a.hitCallback instanceof Function) {
                    fn = a.hitCallback;
                } else if (a instanceof Function) {
                    fn = function(){ a(ga.create()); };
                } else {
                    var pos = args.indexOf('hitCallback');
                    if (pos !== -1 && args[pos+1] instanceof Function) {
                        fn = args[pos+1];
                    }
                }
                if (fn instanceof Function === false) return;
                try { fn(); } catch(ex){}
            };
            ga.create = function(){ return new Tracker(); };
            ga.getByName = function(){ return new Tracker(); };
            ga.getAll = function(){ return [new Tracker()]; };
            ga.remove = noopfn;
            ga.loaded = true;
            w[gaName] = ga;

            var dl = w.dataLayer;
            if (dl instanceof Object) {
                if (dl.hide instanceof Object && typeof dl.hide.end === 'function') {
                    dl.hide.end();
                    dl.hide.end = function(){};
                }
                if (typeof dl.push === 'function') {
                    var doCallback = function(item){
                        if (item instanceof Object === false) return;
                        if (typeof item.eventCallback !== 'function') return;
                        setTimeout(item.eventCallback, 1);
                        item.eventCallback = function(){};
                    };
                    dl.push = new Proxy(dl.push, {
                        apply: function(target, thisArg, args){
                            doCallback(args[0]);
                            return Reflect.apply(target, thisArg, args);
                        }
                    });
                    if (Array.isArray(dl)) {
                        var q = dl.slice();
                        for (var i = 0; i < q.length; i++) {
                            doCallback(q[i]);
                        }
                    }
                }
            }

            if (gaQueue instanceof Function && Array.isArray(gaQueue.q)) {
                var q2 = gaQueue.q.slice();
                gaQueue.q.length = 0;
                for (var j = 0; j < q2.length; j++) {
                    ga.apply(null, q2[j]);
                }
            }

            try { AndBridge.dbg('s', 'GA tracker neutralized'); } catch(e){}
        })();
    """
}