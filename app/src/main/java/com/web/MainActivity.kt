package com.web

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar

class MainActivity : Activity() {

    private lateinit var webViewContainer: FrameLayout
    private lateinit var webView: WebView
    private var popupWebView: WebView? = null // Menampung WebView sementara untuk popup login
    private lateinit var webProgress: ProgressBar

    // User-Agent Infinix Anda (Tanda "; wv" dan "Version/4.0" dihapus)
    private val globalUserAgent = "Mozilla/5.0 (Linux; Android 13; Infinix X6528B Build/TP1A.220624.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.7727.55 Mobile Safari/537.36"
    private val TIKTOK_LIVE_URL = "https://www.tiktok.com/live"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webViewContainer = findViewById(R.id.webview_container)
        webProgress = findViewById(R.id.web_progress)

        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        webViewContainer.addView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = globalUserAgent
            
            useWideViewPort = true
            loadWithOverviewMode = true
            
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(webView, true)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                
                if (url.startsWith("https://accounts.google.com") || 
                    url.startsWith("https://myaccount.google.com") || 
                    url.contains("tiktok.com")) {
                    return false
                }

                if (url.startsWith("intent://") || url.startsWith("market://") ||
                    url.startsWith("tiktok://") || url.startsWith("snssdk") ||
                    url.contains("play.google.com/store")) {
                    return true 
                }
                
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                webProgress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                webProgress.progress = newProgress
            }
            
            // ✅ PERBAIKAN: Membuat WebView baru khusus untuk Popup Login
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                val newWebView = WebView(this@MainActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }

                newWebView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = globalUserAgent
                    setSupportMultipleWindows(true)
                    javaScriptCanOpenWindowsAutomatically = true
                }

                newWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView) {
                        // Tutup dan hancurkan WebView popup saat login selesai
                        webViewContainer.removeView(window)
                        window.destroy()
                        popupWebView = null
                    }
                }

                newWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        return false
                    }
                }

                // Tumpuk WebView baru di atas WebView utama
                webViewContainer.addView(newWebView)
                popupWebView = newWebView

                // Masukkan WebView baru ke dalam sistem transportasi
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = newWebView
                resultMsg.sendToTarget()
                
                return true
            }
        }

        val headers = mutableMapOf<String, String>()
        headers["Accept"] = "application/json"
        webView.loadUrl(TIKTOK_LIVE_URL, headers)
    }

    override fun onBackPressed() {
        // Tangani tombol "kembali" jika sedang berada di layar popup login
        if (popupWebView != null) {
            if (popupWebView!!.canGoBack()) {
                popupWebView!!.goBack()
            } else {
                webViewContainer.removeView(popupWebView)
                popupWebView!!.destroy()
                popupWebView = null
            }
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        popupWebView?.destroy()
        webView.destroy()
    }
}
