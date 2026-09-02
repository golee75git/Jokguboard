package com.jokgu.scoreboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null
    @Volatile private var isTtsReady: Boolean = false
    private var bleScoreSpikeServer: BleScoreSpikeServer? = null

    private companion object {
        private const val REQ_BLE = 2001
    }

    inner class AndroidShellBridge {
        @JavascriptInterface
        fun ping(): String = "ok"
    }

    inner class AndroidTtsBridge {
        @JavascriptInterface
        fun isReady(): Boolean = isTtsReady && tts != null

        @JavascriptInterface
        fun setSpeechRate(rate: Double): Boolean {
            val engine = tts ?: return false
            return try {
                engine.setSpeechRate(rate.toFloat().coerceIn(0.5f, 2.0f))
                true
            } catch (_: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun speak(text: String?, langTag: String?, utteranceId: String?): Boolean {
            val content = text?.trim().orEmpty()
            if (content.isEmpty()) return false
            val uid = utteranceId?.trim().orEmpty().ifEmpty { "jk" }
            val engine = tts ?: return false
            if (!isTtsReady) return false
            val targetLocale = when (langTag) {
                "ko-KR" -> Locale.KOREAN
                "en-US" -> Locale.US
                "zh-CN", "zh" -> Locale.SIMPLIFIED_CHINESE
                else -> Locale.getDefault()
            }
            val setLangResult = engine.setLanguage(targetLocale)
            if (setLangResult == TextToSpeech.LANG_MISSING_DATA ||
                setLangResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                engine.setLanguage(Locale.getDefault())
            }
            return engine.speak(content, TextToSpeech.QUEUE_FLUSH, null, uid) ==
                TextToSpeech.SUCCESS
        }
    }

    inner class AndroidBleScoreBridge {
        @JavascriptInterface
        fun setBoardScore(leftGame: Int, rightGame: Int, leftSet: Int, rightSet: Int) {
            bleScoreSpikeServer?.updateScore(leftGame, rightGame, leftSet, rightSet, false)
        }

        @JavascriptInterface
        fun setBoardScoreForce(leftGame: Int, rightGame: Int, leftSet: Int, rightSet: Int) {
            bleScoreSpikeServer?.updateScore(leftGame, rightGame, leftSet, rightSet, true)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this)
        setContentView(webView)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyImmersive()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            builtInZoomControls = false
        }

        tts = TextToSpeech(this) { status ->
            isTtsReady = status == TextToSpeech.SUCCESS
            if (isTtsReady) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                    override fun onError(utteranceId: String?, errorCode: Int) {}
                })
            }
        }

        webView.addJavascriptInterface(AndroidShellBridge(), "AndroidShell")
        webView.addJavascriptInterface(AndroidTtsBridge(), "AndroidTTS")
        bleScoreSpikeServer = BleScoreSpikeServer(this)
        webView.addJavascriptInterface(AndroidBleScoreBridge(), "AndroidBleScore")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                if (result == null) return false
                val text = message?.trim().orEmpty()
                if (text.isEmpty()) {
                    result.cancel()
                    return true
                }
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(text)
                    .setNegativeButton(R.string.common_cancel) { _, _ -> result.cancel() }
                    .setPositiveButton(R.string.common_ok) { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage(R.string.exit_confirm)
                        .setNegativeButton(R.string.common_cancel, null)
                        .setPositiveButton(R.string.common_ok) { _, _ -> finish() }
                        .show()
                }
            }
        )

        webView.loadUrl("https://appassets.androidplatform.net/assets/www/jokgu_scoreboard.html")
        requestBleAndStart()
    }

    private fun requestBleAndStart() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            bleScoreSpikeServer?.start()
            return
        }
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )
        if (permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            bleScoreSpikeServer?.start()
        } else {
            ActivityCompat.requestPermissions(this, permissions, REQ_BLE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BLE &&
            grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            bleScoreSpikeServer?.start()
        }
    }

    private fun applyImmersive() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersive()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) ==
                PackageManager.PERMISSION_GRANTED)
        ) {
            bleScoreSpikeServer?.start()
        }
    }

    override fun onDestroy() {
        bleScoreSpikeServer?.stop()
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
