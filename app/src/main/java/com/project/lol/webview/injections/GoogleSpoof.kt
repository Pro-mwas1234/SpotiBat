package com.project.lol.webview.injections

object GoogleSpoof {
    const val CONTENT = """
            (function(){
                var DESKTOP_UA='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36';
                var DESKTOP_APP='5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36';
                try {
                    window.screen.__defineGetter__('width', function(){ return 1920; });
                    window.screen.__defineGetter__('height', function(){ return 1080; });
                    window.screen.__defineGetter__('availWidth', function(){ return 1920; });
                    window.screen.__defineGetter__('availHeight', function(){ return 1040; });
                    window.__defineGetter__('innerWidth', function(){ return 1920; });
                    window.__defineGetter__('innerHeight', function(){ return 978; });
                } catch(e){}
                function safeDefine(obj, name, getter){
                    try { Object.defineProperty(obj, name, { get: getter, configurable: true }); } catch(e){}
                }
                safeDefine(navigator, 'webdriver', function(){ return false; });
                safeDefine(navigator, 'vendor', function(){ return 'Google Inc.'; });
                safeDefine(navigator, 'productSub', function(){ return '20030107'; });
                safeDefine(navigator, 'userAgent', function(){ return DESKTOP_UA; });
                safeDefine(navigator, 'appVersion', function(){ return DESKTOP_APP; });
                safeDefine(navigator, 'appName', function(){ return 'Netscape'; });
                safeDefine(navigator, 'appCodeName', function(){ return 'Mozilla'; });
                safeDefine(navigator, 'product', function(){ return 'Gecko'; });
                safeDefine(navigator, 'platform', function(){ return 'Win32'; });
                safeDefine(navigator, 'oscpu', function(){ return 'Windows NT 10.0; Win64; x64'; });
                safeDefine(navigator, 'languages', function(){ return ['en-US','en']; });
                safeDefine(navigator, 'language', function(){ return 'en-US'; });
                safeDefine(navigator, 'plugins', function(){
                    var p = [
                        { name:'Chrome PDF Plugin', filename:'internal-pdf-viewer', description:'Portable Document Format' },
                        { name:'Chrome PDF Viewer', filename:'mhjfbmdgcfjbbpaeojofohoefgiehjai', description:'' },
                        { name:'Native Client', filename:'internal-nacl-plugin', description:'' }
                    ];
                    p.length = 3;
                    return p;
                });
                safeDefine(navigator, 'mimeTypes', function(){
                    var m = [
                        { type:'application/pdf', suffixes:'pdf', description:'Portable Document Format' },
                        { type:'application/x-google-chrome-pdf', suffixes:'pdf', description:'Portable Document Format' }
                    ];
                    m.length = 2;
                    return m;
                });
                try {
                    var uaData = {
                        brands: [
                            { brand: 'Not.A/Brand', version: '8' },
                            { brand: 'Chromium', version: '150' },
                            { brand: 'Google Chrome', version: '150' }
                        ],
                        mobile: false,
                        platform: 'Windows',
                        architecture: 'x86',
                        bitness: '64',
                        wow64: false,
                        model: '',
                        platformVersion: '10.0.0',
                        uaFullVersion: '150.0.0.0',
                        fullVersionList: [
                            { brand: 'Not.A/Brand', version: '8' },
                            { brand: 'Chromium', version: '150.0.0.0' },
                            { brand: 'Google Chrome', version: '150.0.0.0' }
                        ]
                    };
                    uaData.getHighEntropyValues = async function(hints){
                        var out = {};
                        hints.forEach(function(h){ if(h in uaData) out[h] = uaData[h]; });
                        return out;
                    };
                    safeDefine(navigator, 'userAgentData', function(){ return uaData; });
                    safeDefine(navigator, 'maxTouchPoints', function(){ return 0; });
                } catch(e){}
                try {
                    if(!window.chrome){
                        var chromeStub={
                            runtime:{id:undefined,connect:function(){},sendMessage:function(){}},
                            app:{isInstalled:false},
                            loadTimes:function(){return {}},
                            csi:function(){return {}},
                            webstore:undefined
                        };
                        Object.defineProperty(window,'chrome',{value:chromeStub,configurable:true,writable:true});
                    }
                } catch(e){}
                try {
                    var getParameter = WebGLRenderingContext.prototype.getParameter;
                    WebGLRenderingContext.prototype.getParameter = function(param) {
                        if (param === 37445) return 'Google Inc. (NVIDIA)';
                        if (param === 37446) return 'ANGLE (NVIDIA, NVIDIA GeForce RTX 3050 (0x00002584) Direct3D11 vs_5_0 ps_5_0, D3D11)';
                        return getParameter.call(this, param);
                    };
                    if (window.WebGL2RenderingContext) {
                        var getParameter2 = WebGL2RenderingContext.prototype.getParameter;
                        WebGL2RenderingContext.prototype.getParameter = function(param) {
                            if (param === 37445) return 'Google Inc. (NVIDIA)';
                            if (param === 37446) return 'ANGLE (NVIDIA, NVIDIA GeForce RTX 3050 (0x00002584) Direct3D11 vs_5_0 ps_5_0, D3D11)';
                            return getParameter2.call(this, param);
                        };
                    }
                } catch(e){}
                safeDefine(navigator, 'hardwareConcurrency', function(){ return 16; });
                safeDefine(navigator, 'deviceMemory', function(){ return 16; });
            })();
        
    """
}