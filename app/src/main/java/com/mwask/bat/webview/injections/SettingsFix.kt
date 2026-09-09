package com.project.lol.webview.injections

/*
 * CREDIT: Spotilol - Settings Page Fix
 */

object SettingsFix {
    const val CONTENT = """
        (function(){
            if(window.__splSettingsFix) return;
            window.__splSettingsFix = true;

            try {
                var origDefine = customElements.define.bind(customElements);
                customElements.define = function(name, constructor, options) {
                    if (name === 'ms-store-badge') {
                        try { AndBridge.dbg('s', 'Blocked ms-store-badge CE'); } catch(e) {}
                        return;
                    }
                    return origDefine(name, constructor, options);
                };
            } catch(e) {}

            function isSpotifyUrl(url) {
                return url.indexOf('https://open.spotify.com/') === 0 ||
                       url.indexOf('https://accounts.spotify.com/') === 0;
            }

            function isOAuthUrl(url) {
                var host = '';
                try {
                    host = new URL(url).hostname.toLowerCase();
                } catch(e) { return false; }
                return host === 'google.com' ||
                       host.indexOf('.google.com') !== -1 ||
                       host.indexOf('.google.') !== -1 ||
                       host === 'facebook.com' ||
                       host.indexOf('.facebook.com') !== -1 ||
                       host === 'appleid.apple.com' ||
                       host.indexOf('.apple.com') !== -1;
            }

            function isAllowed(url) {
                return isSpotifyUrl(url) || isOAuthUrl(url);
            }

            function hardBlock(el) {
                if (el.__splHardBlocked) return;
                el.__splHardBlocked = true;
                el.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    return false;
                }, true);
                el.addEventListener('auxclick', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    return false;
                }, true);
                el.addEventListener('pointerdown', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    return false;
                }, true);
            }

            function interceptAll() {
                var links = document.querySelectorAll('a[target="_blank"]');
                for (var i = 0; i < links.length; i++) {
                    var a = links[i];
                    if (a.__splIntercepted) continue;
                    a.__splIntercepted = true;

                    a.addEventListener('click', function(e) {
                        var href = this.href || '';
                        if (!href || href.charAt(0) === '#') return;
                        if (isAllowed(href)) return;

                        e.preventDefault();
                        e.stopPropagation();
                        try { AndBridge.dbg('s', 'Blocked external nav: ' + href); } catch(err) {}
                        return false;
                    }, true);

                    a.addEventListener('auxclick', function(e) {
                        var href = this.href || '';
                        if (!href || isAllowed(href)) return;
                        e.preventDefault();
                        e.stopPropagation();
                        return false;
                    }, true);
                }

                var badges = document.querySelectorAll('ms-store-badge');
                for (var i = 0; i < badges.length; i++) {
                    hardBlock(badges[i]);
                    badges[i].style.pointerEvents = 'none';
                }

                var imgs = document.querySelectorAll('img[src*="get.microsoft.com"]');
                for (var i = 0; i < imgs.length; i++) {
                    hardBlock(imgs[i]);
                    imgs[i].style.pointerEvents = 'none';
                    imgs[i].style.display = 'none';
                }
            }

            interceptAll();

            var obs = new MutationObserver(function() { interceptAll(); });
            obs.observe(document.documentElement, {
                childList: true, subtree: true
            });
        })();
    """
}