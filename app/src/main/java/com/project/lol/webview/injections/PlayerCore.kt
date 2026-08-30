package com.project.lol.webview.injections

object PlayerCore {
    const val CONTENT = """
            var reqPause=false,firstPlay=true,ulFlag=false,ffDone=false,npOpen=false;
            window.__splUnlocked=false;
            window.__splApActive=false;
            window.__splApDone=false;
            var featVer='web-player_'+new Date().toISOString().split('T')[0]+'_'+Date.now()+'_'+Math.floor(Math.random()*0xFFFFFFF).toString(16).padStart(7,'0');
            var lastState=null,lastPos=null,playing=false;
            var pfint=null,afint=null,cssint=null,aaint=null;
            window.opHash=function(name,fb){var m=window.splOpHashes||{};return m[name]||fb;};
            window.splViewH=function(){
                try{if(window.visualViewport&&window.visualViewport.height)return window.visualViewport.height;}catch(e){}
                return document.documentElement.clientHeight||window.innerHeight||0;
            };
            window.splPlayerTop=function(){
                var p=document.getElementById('spotilolPlayerControls');
                if(p){var r=p.getBoundingClientRect();if(r.height>2)return r.top;}
                var o=document.querySelector('aside[data-testid="now-playing-bar"]');
                if(o){var s=getComputedStyle(o);if(s.display!=='none'&&s.visibility!=='hidden'){var r2=o.getBoundingClientRect();if(r2.height>2)return r2.top;}}
                return window.splViewH();
            };
            window.__splFloaters=[];
            setInterval(function(){
                if(window.__splBg) return;
                for(var i=0;i<window.__splFloaters.length;i++){
                    try{window.__splFloaters[i]();}catch(e){}
                }
            },250);
        
    """
}