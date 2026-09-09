package com.project.lol.webview.helpers

object DevLogPrelude {

    fun js(): String = """
        (function(){
            function send(lvl,m){
                try{ AndBridge.dbg(lvl,String(m)); }catch(e){}
            }
            window.dbg =function(m){send('l',m)};
            window.dbgw=function(m){send('w',m)};
            window.dbge=function(m){send('e',m)};
            window.DevLog={
                log:function(){var a=[].slice.call(arguments).join(' ');send('l',a)},
                warn:function(){var a=[].slice.call(arguments).join(' ');send('w',a)},
                error:function(){var a=[].slice.call(arguments).join(' ');send('e',a)},
                sys:function(){var a=[].slice.call(arguments).join(' ');send('s',a)},
                clear:function(){/* handled by kotlin */},
                dump:function(){return '(see Settings > Devlog)'}
            };
        })();
    """.trimIndent()
}