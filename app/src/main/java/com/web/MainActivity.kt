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
    private lateinit var webProgress: ProgressBar

    // User-Agent Windows 7 Chrome 109 
    private val globalUserAgent = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.5414.74 Safari/537.36"
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
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(webView, true)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            // Blokir Redirect paksa ke Aplikasi TikTok / PlayStore
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
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
        }

        // Load halaman dengan custom header
        val headers = mutableMapOf<String, String>()
        headers["Accept"] = "application/json"
        webView.loadUrl(TIKTOK_LIVE_URL, headers)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }
}
