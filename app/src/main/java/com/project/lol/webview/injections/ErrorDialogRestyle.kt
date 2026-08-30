package com.project.lol.webview.injections

/*
 * CREDIT: Spotilol - Error Dialog Restyle
 */

object ErrorDialogRestyle {
    const val CONTENT = """
        (function(){
            if (window.__splErrDlgInit) return;
            window.__splErrDlgInit = true;

            var KNOWN_TITLES = [
                'something went wrong',
                'unexpected error',
                'oops, something went wrong',
                'ein fehler ist aufgetreten',
                'se ha producido un error',
                'une erreur est survenue',
                'si è verificato un errore',
                'ocorreu um erro',
                'произошла ошибка',
                '問題が発生しました'
            ];

            var st = document.createElement('style');
            st.id = 'spl-errdlg-style';
            st.textContent = [
                '.spl-errdlg{position:fixed!important;inset:0!important;width:auto!important;max-width:none!important;margin:0!important;padding:24px!important;box-sizing:border-box!important;display:flex!important;align-items:center!important;justify-content:center!important;background:rgba(0,0,0,.62)!important;-webkit-backdrop-filter:blur(6px);backdrop-filter:blur(6px);z-index:2147483647!important;overflow:auto!important}',
                '.spl-errdlg-card{width:min(400px,100%)!important;position:relative;background:rgba(18,18,18,.96)!important;-webkit-backdrop-filter:blur(24px);backdrop-filter:blur(24px);border:1px solid rgba(255,255,255,.08)!important;border-radius:24px!important;padding:36px 26px 26px!important;display:flex!important;flex-direction:column!important;align-items:center!important;text-align:center!important;box-shadow:0 24px 64px rgba(0,0,0,.65)!important;animation:splErrIn .45s cubic-bezier(.2,.8,.2,1) both;overflow:hidden;margin:0!important}',
                '.spl-errdlg-card::before{content:"";position:absolute;top:0;left:0;right:0;height:3px;background:linear-gradient(90deg,transparent,#1DB954,transparent);opacity:.7}',
                '.spl-errdlg-icon{width:84px;height:84px;border-radius:50%;background:rgba(29,185,84,.10);display:flex;align-items:center;justify-content:center;margin-bottom:20px;animation:splErrPulse 1.8s ease-in-out infinite;flex-shrink:0}',
                '.spl-errdlg-icon svg{width:40px;height:40px;color:#1DB954}',
                '.spl-errdlg h1{font-size:22px!important;font-weight:700!important;color:#fff!important;letter-spacing:-.3px;margin:0 0 8px!important;padding:0!important;text-transform:none!important;max-width:100%!important}',
                '.spl-errdlg p{font-size:14px!important;color:rgba(255,255,255,.55)!important;margin:0 0 26px!important;line-height:1.5!important;padding:0!important;max-width:100%!important}',
                '.spl-errdlg-card > div:last-child{width:100%!important;display:flex!important;justify-content:center!important;margin:0!important;padding:0!important}',
                '.spl-errdlg button{-webkit-tap-highlight-color:transparent;width:100%!important;min-height:50px!important;background:#1DB954!important;color:#fff!important;border:none!important;border-radius:25px!important;font-size:15px!important;font-weight:700!important;letter-spacing:.3px!important;cursor:pointer;padding:0 24px!important;font-family:inherit!important;transition:transform .15s,background .2s,box-shadow .2s;box-shadow:0 8px 24px rgba(29,185,84,.35)}',
                '.spl-errdlg button:hover{background:#1ed760!important;transform:translateY(-1px);box-shadow:0 10px 28px rgba(29,185,84,.45)}',
                '.spl-errdlg button:active{transform:scale(.97)!important}',
                '@keyframes splErrIn{0%{opacity:0;transform:translateY(16px) scale(.94)}100%{opacity:1;transform:translateY(0) scale(1)}}',
                '@keyframes splErrPulse{0%,100%{box-shadow:0 0 0 0 rgba(29,185,84,.28)}50%{box-shadow:0 0 0 16px rgba(29,185,84,0)}}',
                '@media(max-width:420px){.spl-errdlg{padding:16px!important}.spl-errdlg-card{padding:30px 22px 22px!important;border-radius:20px!important}.spl-errdlg-icon{width:72px;height:72px;margin-bottom:16px}.spl-errdlg-icon svg{width:34px;height:34px}.spl-errdlg h1{font-size:19px!important}.spl-errdlg p{font-size:13px!important;margin-bottom:22px!important}}'
            ].join('\n');

            function appendStyle(){
                var t = document.head || document.documentElement;
                if (t && !document.getElementById('spl-errdlg-style')) t.appendChild(st);
            }
            appendStyle();

            var ICON = '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M19.35 10.04C18.67 6.59 15.64 4 12 4c-1.48 0-2.85.43-4.01 1.17l1.46 1.46C10.21 6.23 11.08 6 12 6c3.04 0 5.5 2.46 5.5 5.5v.5H19c1.66 0 3 1.34 3 3 0 1.13-.64 2.11-1.56 2.62l1.45 1.45C23.16 18.16 24 16.68 24 15c0-2.64-2.05-4.78-4.65-4.96zM3 5.27l2.75 2.74C2.56 8.15 0 10.77 0 14c0 3.31 2.69 6 6 6h11.73l2 2L21 20.73 4.27 4 3 5.27z"/></svg>';

            function looksLikeErrorDialog(dlg){
                var h = dlg.querySelector('h1');
                if (!h) return false;
                var t = (h.textContent || '').trim().toLowerCase();
                for (var i = 0; i < KNOWN_TITLES.length; i++){
                    if (t === KNOWN_TITLES[i]) return true;
                }
                var btn = dlg.querySelector('button');
                if (btn && /reload|refresh|recarrega|ricarica|neuladen/i.test(btn.textContent || '')) return true;
                return false;
            }

            function dress(dlg){
                if (dlg.__splErrDressed) return;
                dlg.__splErrDressed = true;
                dlg.classList.add('spl-errdlg');
                var inner = dlg.firstElementChild;
                if (inner) inner.classList.add('spl-errdlg-card');
                var h1 = dlg.querySelector('h1');
                if (h1 && !dlg.querySelector('.spl-errdlg-icon')){
                    var ic = document.createElement('div');
                    ic.className = 'spl-errdlg-icon';
                    ic.innerHTML = ICON;
                    h1.parentNode.insertBefore(ic, h1);
                }
            }

            function scan(node){
                if (!node || node.nodeType !== 1) return;
                var hits = [];
                if (node.matches && node.matches('div[role=dialog][aria-modal=true]')) hits.push(node);
                if (node.querySelectorAll){
                    var q = node.querySelectorAll('div[role=dialog][aria-modal=true]');
                    for (var i = 0; i < q.length; i++) hits.push(q[i]);
                }
               for (var j = 0; j < hits.length; j++){
                    var hit = hits[j];
                    if (hit.__splErrDressed) continue;
                    if (looksLikeErrorDialog(hit)) dress(hit);
                }
            }

            var obs = new MutationObserver(function(muts){
                for (var i = 0; i < muts.length; i++){
                    var a = muts[i].addedNodes;
                    for (var j = 0; j < a.length; j++) {
                        if (a[j].nodeType === 1) scan(a[j]);
                    }
                }
            });
            obs.observe(document.body, { childList: true, subtree: true });
            scan(document.body);
        })();
    """
}