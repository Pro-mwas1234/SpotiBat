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

object ClassicLoginButton {

    const val CONTENT = """
        (function(){
            var gl = document.querySelector('section>div>div>div>div>a:first-child:not(.fuckd)');
            if(gl) {
                var cl = document.createElement('a');
                cl.className = 'fuckd';
                cl.textContent = 'Email + Password Classic Login';
                cl.style.cssText = `
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    box-sizing: border-box;
                    width: 100%;
                    padding: 12px 18px;
                    margin: 12px 0;
                    color: #1DB954;
                    background: transparent;
                    border: 1.5px solid #1DB954;
                    border-radius: 12px;
                    font-size: 14px;
                    font-weight: 600;
                    letter-spacing: .2px;
                    text-align: center;
                    text-decoration: none;
                    cursor: pointer;
                    transform: translateY(0) scale(1);
                    box-shadow: 0 0 0 rgba(29,185,84,0);
                    transition:
                        background .25s ease,
                        color .25s ease,
                        border-color .25s ease,
                        box-shadow .25s ease,
                        transform .25s cubic-bezier(.2,.8,.2,1);
                `;

                cl.addEventListener('mouseenter', () => {
                    cl.style.background = 'rgba(29,185,84,.12)';
                    cl.style.borderColor = '#1ED760';
                    cl.style.color = '#1ED760';
                    cl.style.transform = 'translateY(-2px) scale(1.01)';
                    cl.style.boxShadow = '0 5px 16px rgba(29,185,84,.18)';
                });

                cl.addEventListener('mouseleave', () => {
                    cl.style.background = 'transparent';
                    cl.style.borderColor = '#1DB954';
                    cl.style.color = '#1DB954';
                    cl.style.transform = 'translateY(0) scale(1)';
                    cl.style.boxShadow = '0 0 0 rgba(29,185,84,0)';
                });
                cl.href = '?allow_password=1';
                gl.parentNode.insertBefore(cl, gl);
            }
        })();
    """
}
