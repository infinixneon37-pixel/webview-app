package com.web

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar

class MainActivity : Activity() {

    private lateinit var webViewContainer: FrameLayout
    private lateinit var webView: WebView
    private lateinit var webProgress: ProgressBar

    private val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Infinix X6528B Build/TP1A.220624.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.7727.55 Mobile Safari/537.36"

    private val HOME_URL = "https://m.facebook.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        webViewContainer = findViewById(R.id.webview_container)
        webProgress = findViewById(R.id.web_progress)

        initWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {

        webView = WebView(this)

        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        webViewContainer.addView(webView)

        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        settings.allowFileAccess = true
        settings.allowContentAccess = true

        settings.loadsImagesAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false

        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)

        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(false)
        settings.displayZoomControls = false
        settings.builtInZoomControls = false

        settings.userAgentString = USER_AGENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        CookieManager.getInstance().setAcceptCookie(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView?, progress: Int) {
                webProgress.progress = progress

                if (progress == 100) {
                    webProgress.visibility = View.GONE
                } else {
                    webProgress.visibility = View.VISIBLE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {

                val host = request.url.host ?: ""

                if (
                    host == "m.facebook.com" ||
                    host == "facebook.com" ||
                    host == "www.facebook.com" ||
                    host.endsWith(".facebook.com")
                ) {
                    return false
                }

                return when (request.url.scheme) {
                    "intent", "market", "facebook" -> true
                    else -> false
                }
            }

            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: Bitmap?
            ) {
                webProgress.visibility = View.VISIBLE
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(
                view: WebView,
                url: String
            ) {
                CookieManager.getInstance().flush()

                webProgress.visibility = View.GONE

                super.onPageFinished(view, url)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    view.loadData(
                        """
                        <html>
                        <body style="font-family:sans-serif;text-align:center;padding-top:60px">
                        <h2>Halaman gagal dimuat</h2>
                        <p>Silakan periksa koneksi internet Anda.</p>
                        </body>
                        </html>
                        """.trimIndent(),
                        "text/html",
                        "UTF-8"
                    )
                }

                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
            }
        }

        webView.loadUrl(HOME_URL)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.apply {
            stopLoading()
            clearHistory()
            clearCache(false)
            removeAllViews()
            destroy()
        }

        super.onDestroy()
    }
}
