package com.project.lol.webview.injections

/*
 * CREDIT: Spotilol - Modal Fix.
 * GitHub: https://github.com/AldySan
 */

object ModalFix {
    const val CONTENT = """
        (function(){
            var st = document.createElement('style');
            st.id = 'spl-modal-fix';
            st.textContent = [
                '.ReactModalPortal > div{',
                '  z-index:2147483647!important',
                '}',
                '.ReactModalPortal > div:first-child{',
                '  position:fixed!important;',
                '  inset:0!important;',
                '  display:flex!important;',
                '  align-items:center!important;',
                '  justify-content:center!important',
                '}',
                'div[role="dialog"][aria-modal="true"]{',
                '  max-width:94vw!important;',
                '  max-height:90vh!important;',
                '  margin:auto!important;',
                '  box-sizing:border-box!important;',
                '  overflow-y:auto!important;',
                '  -webkit-overflow-scrolling:touch',
                '}',
                'div[role="dialog"] [data-testid="playlist-edit-details-modal"]{',
                '  width:100%!important;',
                '  max-width:100%!important;',
                '  box-sizing:border-box!important',
                '}',
                'div[role="dialog"] [data-testid="playlist-edit-details-modal"] > div:first-child{',
                '  display:flex!important;',
                '  align-items:center!important;',
                '  justify-content:space-between!important;',
                '  gap:12px!important',
                '}',
                'div[role="dialog"] button[aria-label="Close"]{',
                '  min-width:44px!important;',
                '  min-height:44px!important',
                '}',
                'div[role="dialog"] input[type="text"],',
                'div[role="dialog"] input[type="search"],',
                'div[role="dialog"] input:not([type="checkbox"]):not([type="radio"]),',
                'div[role="dialog"] textarea{',
                '  width:100%!important;',
                '  max-width:100%!important;',
                '  font-size:16px!important;',
                '  box-sizing:border-box!important;',
                '  min-height:44px!important',
                '}',
                'div[role="dialog"] textarea{',
                '  min-height:88px!important;',
                '  resize:vertical!important',
                '}',
                'div[role="dialog"] button{',
                '  min-height:44px!important;',
                '  padding:10px 16px!important;',
                '  font-size:14px!important',
                '}',
                'div[role="dialog"] [class*="footer" i],',
                'div[role="dialog"] [class*="actions" i]{',
                '  display:flex!important;',
                '  flex-wrap:wrap!important;',
                '  gap:8px!important;',
                '  justify-content:flex-end!important',
                '}',
                '@media (max-width:560px){',
                '  div[role="dialog"] [data-testid="playlist-edit-details-modal"] > div,',
                '  div[role="dialog"] [data-testid="playlist-edit-details-modal"] form > div{',
                '    flex-direction:column!important;',
                '    align-items:stretch!important',
                '  }',
                '  div[role="dialog"] button[aria-label*="image" i],',
                '  div[role="dialog"] [data-testid*="image" i]{',
                '    width:140px!important;',
                '    height:140px!important;',
                '    margin:0 auto 16px!important;',
                '    align-self:center!important',
                '  }',
                '  div[role="dialog"] img{',
                '    object-fit:cover!important',
                '  }',
                '}',
                'div[data-testid="modal-container"],',
                'div.GenericModal{',
                '  max-width:94vw!important;',
                '  max-height:90vh!important;',
                '  margin:auto!important;',
                '  box-sizing:border-box!important',
                '}'
            ].join('\n');
            function append(){
                var t = document.head || document.documentElement;
                if (t && !document.getElementById('spl-modal-fix')) t.appendChild(st);
            }
            append();
        })();
    """
}