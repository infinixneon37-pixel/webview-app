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

    // User-Agent Infinix Anda (Tanda "; wv" dan "Version/4.0" dihapus untuk lolos blokir Google)
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
            
            // Optimasi tampilan Mobile
            useWideViewPort = true
            loadWithOverviewMode = true
            
            // Mengizinkan WebView menangani popup (penting untuk alur login Google)
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
                
                // Izinkan domain utama untuk dimuat di dalam aplikasi
                if (url.startsWith("https://accounts.google.com") || 
                    url.startsWith("https://myaccount.google.com") || 
                    url.contains("tiktok.com")) {
                    return false
                }

                // Blokir pengalihan ke PlayStore, Intent, atau aplikasi TikTok Native
                if (url.startsWith("intent://") || url.startsWith("market://") ||
                    url.startsWith("tiktok://") || url.startsWith("snssdk") ||
                    url.contains("play.google.com/store")) {
                    return true 
                }
                
                return false
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                // Jika terjadi error saat memuat, biarkan default agar tidak macet di layar putih
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                webProgress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                webProgress.progress = newProgress
            }
            
            // Menangkap jendela baru (popup login) dan memuatnya di WebView yang sama
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = webView
                resultMsg.sendToTarget()
                return true
            }
        }

        // Header custom sesuai permintaan
        val headers = mutableMapOf<String, String>()
        headers["Accept"] = "application/json"
        
        // Memuat URL TikTok Live
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
