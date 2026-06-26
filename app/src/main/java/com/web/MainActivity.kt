package com.web

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private lateinit var webViewContainer: FrameLayout
    private val tabs = mutableListOf<WebView>()
    private var currentTabIndex = -1
    private lateinit var btnTabs: TextView

    private lateinit var urlInput: EditText
    private lateinit var btnHome: ImageView
    private lateinit var btnRecordToggle: ImageView
    private lateinit var webProgress: ProgressBar

    // ✅ 1. Update User-Agent ke Windows 7 Chrome 109
    private val globalUserAgent = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.5414.74 Safari/537.36"
    private val HOME_URL = "https://duckduckgo.com/"

    class TabRecordState {
        var isRecording = false
        var sessionFolder = "Sesi_Default"
        var lockedStreamId: String? = null
    }

    private val streamStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if ("ACTION_STREAM_STOPPED" == intent.action) {
                val folder = intent.getStringExtra("folder")
                if (folder != null) {
                    for (webView in tabs) {
                        val state = webView.tag as? TabRecordState
                        if (state != null && folder == state.sessionFolder) {
                            state.isRecording = false
                            state.lockedStreamId = null
                            if (webView == getActiveWebView()) {
                                btnRecordToggle.setColorFilter(Color.parseColor("#888888"))
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    inner class WebAppInterface(private val mBoundWebView: WebView) {
        @JavascriptInterface
        fun onStreamEnded(reason: String) {
            runOnUiThread {
                val state = mBoundWebView.tag as? TabRecordState
                if (state != null && state.isRecording) {
                    Toast.makeText(this@MainActivity, "📡 $reason", Toast.LENGTH_LONG).show()
                    state.isRecording = false
                    state.lockedStreamId = null

                    if (mBoundWebView == getActiveWebView()) {
                        btnRecordToggle.setColorFilter(Color.parseColor("#888888"))
                    }

                    val stopIntent = Intent(this@MainActivity, LiveMonitorService::class.java).apply {
                        action = "STOP_SERVICE"
                        putExtra("folder", state.sessionFolder)
                    }
                    startService(stopIntent)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        webViewContainer = findViewById(R.id.webview_container)
        urlInput = findViewById(R.id.url_input)
        btnHome = findViewById(R.id.btn_home)
        btnTabs = findViewById(R.id.btn_tabs)
        btnRecordToggle = findViewById(R.id.btn_record_toggle)
        webProgress = findViewById(R.id.web_progress)

        urlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                loadWebOrSearch(urlInput.text.toString().trim())
                hideKeyboard()
                true
            } else false
        }

        btnHome.setOnClickListener { loadUrlWithHeaders(getActiveWebView(), HOME_URL) }

        btnTabs.setOnClickListener { showTabSwitcherDialog() }

        btnRecordToggle.setOnClickListener {
            val activeWebView = getActiveWebView() ?: return@setOnClickListener
            val state = activeWebView.tag as? TabRecordState ?: return@setOnClickListener

            state.isRecording = !state.isRecording
            if (state.isRecording) {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                state.sessionFolder = "Sesi_Tab${currentTabIndex + 1}_$timeStamp"
                state.lockedStreamId = null
                btnRecordToggle.setColorFilter(Color.parseColor("#34C759"))
                Toast.makeText(this, "🔴 Sesi Rekam Tab ${currentTabIndex + 1} Aktif!", Toast.LENGTH_SHORT).show()
            } else {
                btnRecordToggle.setColorFilter(Color.parseColor("#888888"))
                val stopIntent = Intent(this, LiveMonitorService::class.java).apply {
                    action = "STOP_SERVICE"
                    putExtra("folder", state.sessionFolder)
                }
                startService(stopIntent)
                Toast.makeText(this, "Perekaman Sesi Dihentikan. Memproses...", Toast.LENGTH_LONG).show()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(streamStopReceiver, IntentFilter("ACTION_STREAM_STOPPED"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(streamStopReceiver, IntentFilter("ACTION_STREAM_STOPPED"))
        }

        createNewTab(HOME_URL)
    }

    private fun getActiveWebView(): WebView? {
        return if (currentTabIndex in 0 until tabs.size) tabs[currentTabIndex] else null
    }

    private fun createNewTab(url: String?) {
        val newTab = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            tag = TabRecordState()
        }
        setupBrowserEngine(newTab)
        tabs.add(newTab)
        switchTab(tabs.size - 1)

        if (!url.isNullOrEmpty()) loadUrlWithHeaders(newTab, url)
    }

    private fun switchTab(index: Int) {
        if (index !in 0 until tabs.size) return
        currentTabIndex = index

        webViewContainer.removeAllViews()
        val activeWebView = tabs[currentTabIndex]
        webViewContainer.addView(activeWebView)

        urlInput.setText(activeWebView.url)
        btnTabs.text = tabs.size.toString()

        val state = activeWebView.tag as? TabRecordState
        if (state?.isRecording == true) {
            btnRecordToggle.setColorFilter(Color.parseColor("#34C759"))
        } else {
            btnRecordToggle.setColorFilter(Color.parseColor("#888888"))
        }
    }

    private fun closeTab(index: Int) {
        if (tabs.size <= 1) {
            loadUrlWithHeaders(tabs[0], HOME_URL)
            return
        }

        val webViewToClose = tabs[index]
        val state = webViewToClose.tag as? TabRecordState
        if (state?.isRecording == true) {
            val stopIntent = Intent(this, LiveMonitorService::class.java).apply {
                action = "STOP_SERVICE"
                putExtra("folder", state.sessionFolder)
            }
            startService(stopIntent)
        }

        webViewContainer.removeView(webViewToClose)
        webViewToClose.destroy()
        tabs.removeAt(index)

        if (currentTabIndex >= tabs.size) currentTabIndex = tabs.size - 1
        switchTab(currentTabIndex)
    }

    private fun showTabSwitcherDialog() {
        val tabTitles = tabs.mapIndexed { i, webView ->
            var title = webView.title.takeUnless { it.isNullOrEmpty() } ?: "Tab ${i + 1}"
            val state = webView.tag as? TabRecordState
            if (state?.isRecording == true) title += " 🔴"
            if (i == currentTabIndex) title = "➔ $title (Aktif)"
            title
        }

        AlertDialog.Builder(this).apply {
            setTitle("Kelola Tab Browser")
            setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, tabTitles)) { _, which -> switchTab(which) }
            setPositiveButton("+ TAB BARU") { _, _ -> createNewTab(HOME_URL) }
            setNegativeButton("TUTUP TAB AKTIF") { _, _ -> closeTab(currentTabIndex) }
            show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun setupBrowserEngine(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = globalUserAgent
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) setAcceptThirdPartyCookies(webView, true)
        }

        webView.addJavascriptInterface(WebAppInterface(webView), "Android")
        webView.webViewClient = MyWebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (view == getActiveWebView()) {
                    webProgress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                    webProgress.progress = newProgress
                }
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                createNewTab("")
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = tabs[currentTabIndex]
                resultMsg.sendToTarget()
                return true
            }
        }
    }

    // ✅ 2. Fungsi helper untuk menyuntikkan header Accept: application/json
    private fun loadUrlWithHeaders(webView: WebView?, url: String) {
        val headers = mutableMapOf<String, String>()
        headers["Accept"] = "application/json"
        webView?.loadUrl(url, headers)
    }

    private fun loadWebOrSearch(query: String) {
        val activeWebView = getActiveWebView() ?: return
        if (query.isEmpty()) return
        val url = if (query.contains(".") && !query.contains(" ")) {
            if (query.startsWith("http")) query else "https://$query"
        } else "https://duckduckgo.com/?q=${Uri.encode(query)}"
        
        loadUrlWithHeaders(activeWebView, url)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let { imm?.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    override fun onBackPressed() {
        val activeTab = getActiveWebView()
        if (activeTab?.canGoBack() == true) activeTab.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(streamStopReceiver) } catch (e: Exception) {}
        tabs.forEach { it.destroy() }
    }

    private inner class MyWebViewClient : WebViewClient() {
        
        // ✅ 3. Pemblokiran Redirect TikTok / Intent Eskternal
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            
            // Blokir deep link seperti intent://, tiktok://, snssdk://, dan market://
            if (url.startsWith("intent://") || url.startsWith("market://") ||
                url.startsWith("tiktok://") || url.startsWith("snssdk") ||
                url.contains("play.google.com/store")) {
                
                // Return true berarti "Aplikasi sudah menangani URL ini, WebView jangan lakukan apa-apa"
                return true 
            }
            // Biarkan WebView memuat HTTP/HTTPS secara normal
            return false
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            if (view == getActiveWebView()) urlInput.setText(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            CookieManager.getInstance().flush()

            val jsInjector = """javascript:(function() {
                if (window.hasTangoHook) return; window.hasTangoHook = true;
                const originalFetch = window.fetch;
                window.fetch = async function() {
                   const response = await originalFetch.apply(this, arguments);
                   const clone = response.clone();
                   const reqUrl = arguments[0] instanceof Request ? arguments[0].url : arguments[0];
                   if (reqUrl && (reqUrl.includes('stream-pullevents.tango.me') || reqUrl.includes('gateway.tango.me'))) {
                       clone.text().then(text => {
                           if (text.includes('"status":"ENDED"') || text.includes('"status":"CLOSED"') || text.includes('"status":"OFFLINE"') || text.includes('live_ended') || text.includes('room_closed')) {
                               if (window.Android) window.Android.onStreamEnded('Sinyal 100%: Host Menutup Siaran!');
                           }
                       }).catch(e => {});
                   }
                   return response;
                };
                const originalXhrOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                   this.addEventListener('load', function() {
                       if (url && (url.includes('stream-pullevents.tango.me') || url.includes('gateway.tango.me'))) {
                           try {
                               const text = this.responseText;
                               if (text.includes('"status":"ENDED"') || text.includes('"status":"CLOSED"') || text.includes('"status":"OFFLINE"') || text.includes('live_ended') || text.includes('room_closed')) {
                                   if (window.Android) window.Android.onStreamEnded('Sinyal 100%: Host Menutup Siaran!');
                               }
                           } catch(e) {}
                       }
                   });
                   originalXhrOpen.apply(this, arguments);
                };
                Object.defineProperty(document, 'hidden', {value: false, writable: false});
                Object.defineProperty(document, 'visibilityState', {value: 'visible', writable: false});
                document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
                setInterval(function() { document.querySelectorAll('video').forEach(v => { v.muted = true; v.play().catch(e=>{}); }); }, 1000);
            })()"""

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) view.evaluateJavascript(jsInjector, null)
            else view.loadUrl(jsInjector)
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            val state = view.tag as? TabRecordState

            if (state?.isRecording == true && (url.contains(".ts") || url.contains(".m4s"))) {
                val cleanUrl = if (url.contains("?")) url.substring(0, url.indexOf("?")) else url

                try {
                    val uri = Uri.parse(cleanUrl)
                    uri.path?.split("/")?.let { parts ->
                        if (parts.size >= 4) {
                            val currentStreamId = parts[3]
                            if (state.lockedStreamId == null) state.lockedStreamId = currentStreamId
                            if (state.lockedStreamId != currentStreamId) return super.shouldInterceptRequest(view, request)
                        }
                    }

                    val chunkName = cleanUrl.substring(cleanUrl.lastIndexOf('/') + 1)

                    runOnUiThread {
                        val serviceIntent = Intent(this@MainActivity, LiveMonitorService::class.java).apply {
                            action = "DOWNLOAD_CHUNK"
                            putExtra("url", url)
                            putExtra("filename", chunkName)
                            putExtra("folder", state.sessionFolder)
                            putExtra("cookie", CookieManager.getInstance().getCookie(url))
                            putExtra("userAgent", globalUserAgent)
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
                        else startService(serviceIntent)
                    }
                } catch (e: Exception) {}
            }
            return super.shouldInterceptRequest(view, request)
        }
    }
}
