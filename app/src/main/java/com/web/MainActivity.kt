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

    // User-Agent HP Anda (Infinix X6528B). 
    // Catatan: Tanda "; wv" dan "Version/4.0" DIHAPUS agar lolos dari blokir login Google.
    private val globalUserAgent = "Mozilla/5.0 (Linux; Android 13; Infinix X6528B Build/TP1A.220624.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.7727.55 Mobile Safari/537.36"
    private val FACEBOOK_URL = "https://m.facebook.com/"

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
            // Mengoptimalkan tampilan untuk mobile
            useWideViewPort = true
            loadWithOverviewMode = true
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
                
                // Pastikan domain Google (untuk login) dan Facebook diizinkan dimuat di dalam aplikasi
                if (url.startsWith("https://m.facebook.com/login/") || url.contains("facebook.com")) {
                    return false
                }

                // Blokir skema URL eksternal agar tidak dialihkan ke PlayStore atau Aplikasi Native Facebook
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

        // Memuat URL FACEBOOK dengan custom header
        val headers = mutableMapOf<String, String>()
        headers["Accept"] = "application/json"
        webView.loadUrl(FACEBOOK_URL, headers)
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
