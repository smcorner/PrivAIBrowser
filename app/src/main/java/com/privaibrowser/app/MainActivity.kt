package com.privaibrowser.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var aiDrawerWebView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnDesktop: Button
    private var isDesktopMode = false
    private var isAiOpen = false

    private val prefs by lazy { getSharedPreferences("privaibrowser_secure_prefs", Context.MODE_PRIVATE) }

    private val mobileUA by lazy { WebSettings.getDefaultUserAgent(this) }
    private val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val blockedTrackerHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "google-analytics.com",
        "googletagmanager.com",
        "connect.facebook.net",
        "pixel.facebook.com",
        "ads.twitter.com",
        "analytics.twitter.com",
        "scorecardresearch.com",
        "quantserve.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "adnxs.com",
        "amazon-adsystem.com"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        aiDrawerWebView = findViewById(R.id.aiDrawerWebView)
        urlBar = findViewById(R.id.urlBar)
        progressBar = findViewById(R.id.progressBar)
        btnDesktop = findViewById(R.id.btnDesktop)

        setupMainWebView()
        setupAiDrawerWebView()
        setupControls()
        syncBackgroundService()

        webView.loadUrl("https://duckduckgo.com")
    }

    private fun isTrackerBlockEnabled(): Boolean {
        return try {
            JSONObject(prefs.getString("ai_settings_json", "{}") ?: "{}").optBoolean("trackerBlock", true)
        } catch (_: Exception) {
            true
        }
    }

    private fun isBackgroundAudioEnabled(): Boolean {
        return try {
            JSONObject(prefs.getString("ai_settings_json", "{}") ?: "{}").optBoolean("bgAudio", true)
        } catch (_: Exception) {
            true
        }
    }

    private fun syncBackgroundService() {
        val intent = Intent(this, MediaPlaybackService::class.java)
        if (isBackgroundAudioEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            stopService(intent)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMainWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                request.setMimeType(mimetype)
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrEmpty()) request.addRequestHeader("cookie", cookies)
                request.addRequestHeader("User-Agent", userAgent)
                request.setDescription("Downloading file...")
                request.setTitle(fileName)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "Downloading: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (isTrackerBlockEnabled()) {
                    val host = request?.url?.host?.lowercase() ?: ""
                    if (blockedTrackerHosts.any { host == it || host.endsWith(".$it") }) {
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { urlBar.setText(it) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    urlBar.setText(it)
                    injectCoreHelpers()
                    runMatchingAddons(it)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupAiDrawerWebView() {
        aiDrawerWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        aiDrawerWebView.addJavascriptInterface(PrivAIBridge(), "PrivAIBridge")
        aiDrawerWebView.loadUrl("file:///android_asset/ai_panel.html")
    }

    private fun setupControls() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            if (isAiOpen) toggleAiDrawer(false) else if (webView.canGoBack()) webView.goBack()
        }
        findViewById<ImageButton>(R.id.btnForward).setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        findViewById<ImageButton>(R.id.btnReload).setOnClickListener {
            webView.reload()
        }
        btnDesktop.setOnClickListener {
            isDesktopMode = !isDesktopMode
            webView.settings.userAgentString = if (isDesktopMode) desktopUA else mobileUA
            btnDesktop.setTextColor(if (isDesktopMode) 0xFF38BDF8.toInt() else 0xFF94A3B8.toInt())
            webView.reload()
        }
        findViewById<Button>(R.id.btnAiPanel).setOnClickListener {
            toggleAiDrawer(!isAiOpen)
        }

        urlBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                navigateTo(urlBar.text.toString())
                true
            } else false
        }
    }

    private fun navigateTo(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        val url = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
        }
        webView.loadUrl(url)
        if (isAiOpen) toggleAiDrawer(false)
    }

    private fun toggleAiDrawer(open: Boolean) {
        isAiOpen = open
        aiDrawerWebView.visibility = if (open) View.VISIBLE else View.GONE
        if (open) {
            aiDrawerWebView.evaluateJavascript("if(window.onPanelOpen) window.onPanelOpen();", null)
        }
    }

    private fun injectCoreHelpers() {
        val coreJs = assets.open("injected_core.js").bufferedReader().use { it.readText() }
        webView.evaluateJavascript(coreJs, null)
    }

    private fun runMatchingAddons(currentUrl: String) {
        val raw = prefs.getString("addons_json", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                if (!item.optBoolean("enabled", true)) continue
                val pattern = item.optString("match", "*://*/*")
                if (urlMatchesPattern(currentUrl, pattern)) {
                    val css = item.optString("css", "")
                    val js = item.optString("js", "")
                    if (css.isNotBlank()) {
                        val escapedCss = JSONObject.quote(css)
                        webView.evaluateJavascript(
                            "(function(){var s=document.createElement('style');s.textContent=$escapedCss;document.head.appendChild(s);})();",
                            null
                        )
                    }
                    if (js.isNotBlank()) {
                        webView.evaluateJavascript("try{
$js
}catch(e){console.error('AddonErr:',e);}", null)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun urlMatchesPattern(url: String, pattern: String): Boolean {
        if (pattern == "*" || pattern == "*://*/*" || pattern == "<all_urls>") return true
        val regexStr = "^" + Regex.escape(pattern).replace("\\*", ".*") + "$"
        return try {
            Regex(regexStr, RegexOption.IGNORE_CASE).containsMatchIn(url)
        } catch (_: Exception) {
            url.contains(pattern, ignoreCase = true)
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isBackgroundAudioEnabled()) {
            webView.onPause()
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isAiOpen) {
            toggleAiDrawer(false)
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    inner class PrivAIBridge {
        @JavascriptInterface
        fun closePanel() {
            runOnUiThread { toggleAiDrawer(false) }
        }

        @JavascriptInterface
        fun getCurrentUrl(): String {
            var res = ""
            val lock = Object()
            runOnUiThread {
                res = webView.url ?: ""
                synchronized(lock) { lock.notify() }
            }
            synchronized(lock) { try { lock.wait(500) } catch (_: Exception) {} }
            return res
        }

        @JavascriptInterface
        fun getVault(): String = prefs.getString("pii_vault_json", "{}") ?: "{}"

        @JavascriptInterface
        fun saveVault(json: String) {
            prefs.edit().putString("pii_vault_json", json).apply()
            runOnUiThread { Toast.makeText(this@MainActivity, "Local Vault saved on device", Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun getSettings(): String = prefs.getString("ai_settings_json", "{}") ?: "{}"

        @JavascriptInterface
        fun saveSettings(json: String) {
            prefs.edit().putString("ai_settings_json", json).apply()
            runOnUiThread {
                syncBackgroundService()
                Toast.makeText(this@MainActivity, "Settings saved", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun getAddons(): String = prefs.getString("addons_json", "[]") ?: "[]"

        @JavascriptInterface
        fun saveAddons(json: String) {
            prefs.edit().putString("addons_json", json).apply()
        }

        @JavascriptInterface
        fun scanSanitizedSchema(callbackName: String) {
            runOnUiThread {
                injectCoreHelpers()
                webView.evaluateJavascript("window.__privAI_scanSchema ? JSON.stringify(window.__privAI_scanSchema()) : '{"error":"scan_unavailable"}'") { result ->
                    aiDrawerWebView.evaluateJavascript("if(window.$callbackName) window.$callbackName($result);", null)
                }
            }
        }

        @JavascriptInterface
        fun executeLocalFill(mappingJson: String, callbackName: String) {
            val vaultJson = prefs.getString("pii_vault_json", "{}") ?: "{}"
            runOnUiThread {
                injectCoreHelpers()
                val js = "window.__privAI_fillForm ? JSON.stringify(window.__privAI_fillForm($mappingJson, $vaultJson)) : '{"filled":0}'"
                webView.evaluateJavascript(js) { result ->
                    aiDrawerWebView.evaluateJavascript("if(window.$callbackName) window.$callbackName($result);", null)
                }
            }
        }

        @JavascriptInterface
        fun extractStructuredData(selectorsJson: String, callbackName: String) {
            runOnUiThread {
                injectCoreHelpers()
                val js = "window.__privAI_extract ? JSON.stringify(window.__privAI_extract($selectorsJson)) : '[]'"
                webView.evaluateJavascript(js) { result ->
                    aiDrawerWebView.evaluateJavascript("if(window.$callbackName) window.$callbackName($result);", null)
                }
            }
        }

        @JavascriptInterface
        fun runJsOnPage(code: String) {
            runOnUiThread {
                webView.evaluateJavascript("try{
$code
}catch(e){alert('Add-on Error: '+e.message);}", null)
            }
        }

        @JavascriptInterface
        fun copyToClipboard(text: String) {
            runOnUiThread {
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("PrivAIBrowser", text))
                Toast.makeText(this@MainActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun callAiApi(promptText: String, callbackName: String) {
            thread {
                val settingsRaw = prefs.getString("ai_settings_json", "{}") ?: "{}"
                val responseJson = try {
                    val cfg = JSONObject(settingsRaw)
                    val provider = cfg.optString("provider", "gemini")
                    val apiKey = cfg.optString("apiKey", "").trim()
                    val model = cfg.optString("model", if (provider == "gemini") "gemini-2.5-flash" else "gpt-4o-mini")
                    val customUrl = cfg.optString("endpoint", "").trim()

                    if (provider != "ollama" && apiKey.isEmpty()) {
                        JSONObject().put("error", "Set your AI API key in the Settings tab first.").toString()
                    } else if (provider == "gemini") {
                        callGemini(apiKey, model, promptText)
                    } else {
                        val endpoint = if (customUrl.isNotEmpty()) customUrl else "https://api.openai.com/v1/chat/completions"
                        callOpenAICompat(endpoint, apiKey, model, promptText)
                    }
                } catch (e: Exception) {
                    JSONObject().put("error", e.message ?: "Request failed").toString()
                }

                runOnUiThread {
                    val quoted = JSONObject.quote(responseJson)
                    aiDrawerWebView.evaluateJavascript("if(window.$callbackName) window.$callbackName($quoted);", null)
                }
            }
        }

        private fun callGemini(apiKey: String, model: String, prompt: String): String {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            val body = JSONObject().put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 20000
                readTimeout = 30000
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val raw = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            val json = JSONObject(raw)
            if (conn.responseCode !in 200..299) {
                return JSONObject().put("error", json.optJSONObject("error")?.optString("message") ?: raw).toString()
            }
            val text = json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            return JSONObject().put("text", text).toString()
        }

        private fun callOpenAICompat(endpoint: String, apiKey: String, model: String, prompt: String): String {
            val url = URL(endpoint)
            val body = JSONObject()
                .put("model", model)
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject().put("role", "user").put("content", prompt)
                    )
                )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                if (apiKey.isNotEmpty()) setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
                connectTimeout = 20000
                readTimeout = 30000
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val raw = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            val json = JSONObject(raw)
            if (conn.responseCode !in 200..299) {
                return JSONObject().put("error", raw).toString()
            }
            val text = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            return JSONObject().put("text", text).toString()
        }
    }
}
