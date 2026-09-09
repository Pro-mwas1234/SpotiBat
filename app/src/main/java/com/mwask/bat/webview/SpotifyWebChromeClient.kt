package com.project.lol.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.project.lol.webview.injections.BrowserSpoof
import com.project.lol.webview.injections.FbGdprBypass
import com.project.lol.webview.injections.GoogleSpoof
import androidx.core.net.toUri

class SpotifyWebChromeClient(
    private val onProgressChanged: ((Int) -> Unit)? = null,
    private val onShowCustomView: ((View?, CustomViewCallback?) -> Unit)? = null,
    private val onHideCustomView: (() -> Unit)? = null,
    private val onFileChooser: ((ValueCallback<Array<Uri>>, Array<String>) -> Boolean)? = null
) : WebChromeClient() {

    private var childWebView: WebView? = null

    companion object {
        private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        onShowCustomView?.invoke(view, callback)
    }

    override fun onHideCustomView() {
        onHideCustomView?.invoke()
    }

    private fun isSpotifyUrl(url: String): Boolean {
        return url.startsWith("https://open.spotify.com/") ||
                url.startsWith("https://accounts.spotify.com/")
    }

    private fun isOAuthUrl(url: String): Boolean {
        val host = runCatching { url.toUri().host?.lowercase() }.getOrNull() ?: return false
        return host == "google.com" ||
                host.endsWith(".google.com") ||
                host.indexOf(".google.") != -1 ||
                host == "facebook.com" ||
                host.endsWith(".facebook.com") ||
                host == "appleid.apple.com" ||
                host.endsWith(".apple.com")
    }

    private fun isGoogleUrl(url: String): Boolean {
        val host = runCatching { url.toUri().host?.lowercase() }.getOrNull() ?: return false
        return host == "google.com" || host.endsWith(".google.com") || host.indexOf(".google.") != -1
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message?
    ): Boolean {
        childWebView?.let {
            try { it.destroy() } catch (_: Exception) {}
        }
        val context = view?.context ?: return false
        val newWebView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = DESKTOP_UA
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (url != null) {
                        if (isGoogleUrl(url)) {
                            view?.evaluateJavascript(GoogleSpoof.CONTENT, null)
                        } else if (!isSpotifyUrl(url)) {
                            view?.evaluateJavascript(BrowserSpoof.CONTENT, null)
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url != null && url.startsWith("https://www.facebook.com/privacy/consent/gdp/")) {
                        view?.evaluateJavascript(FbGdprBypass.CONTENT, null)
                    }
                }

                @Deprecated("Deprecated in Java")
                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val targetUrl = url ?: return false
                    val allowed = isSpotifyUrl(targetUrl) || isOAuthUrl(targetUrl)
                    if (!allowed) {
                        try { view?.destroy() } catch (_: Exception) {}
                        return true
                    }
                    view?.loadUrl(targetUrl)
                    return true
                }
            }
        }
        childWebView = newWebView
        val transport = resultMsg?.obj as? WebView.WebViewTransport
        transport?.webView = newWebView
        resultMsg?.sendToTarget()
        return true
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        if (filePathCallback == null) return false
        val acceptTypes = fileChooserParams?.acceptTypes ?: emptyArray()
        return onFileChooser?.invoke(filePathCallback, acceptTypes) ?: false
    }

    @Suppress("DEPRECATION")
    override fun onPermissionRequest(permissionRequest: PermissionRequest?) {
        permissionRequest ?: return
        Handler(Looper.getMainLooper()).post {
            val resources = permissionRequest.resources
            if (resources.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
                permissionRequest.grant(resources)
            } else {
                permissionRequest.deny()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onConsoleMessage(message: String?, lineNumber: Int, sourceId: String?) {
        android.util.Log.d("SpotifyJS", "$message [$sourceId:$lineNumber]")
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged?.invoke(newProgress)
    }

    fun cleanup() {
        childWebView?.let {
            try { it.destroy() } catch (_: Exception) {}
        }
        childWebView = null
    }
}