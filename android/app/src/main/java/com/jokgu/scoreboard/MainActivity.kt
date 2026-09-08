package com.jokgu.scoreboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
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
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null
    @Volatile private var isTtsReady: Boolean = false
    private var bleScoreSpikeServer: BleScoreSpikeServer? = null

    /* JK_HID_PAD: 외부 BT 키·포인터만 보드에 연결. 되돌리: 이 필드·dispatch* 삭제 */
    @Volatile private var alignStep = 0
    private var lastHoverX = 0f
    private var lastHoverY = 0f
    private var lastHoverAt = 0L
    private var volDownAt = 0L
    private var volDownCode = 0

    private companion object {
        private const val REQ_BLE = 2001
        private const val PREF = "jokgu_pad_marks"
        private const val MIN_SPAN = 72f
        private const val HOVER_FRESH_MS = 48L
        private const val VOL_HOLD_MS = 480L
        private const val SCAN_SKIP = 320
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

    inner class AndroidPadBridge {
        @JavascriptInterface
        fun markState(): String {
            val p = padPrefs()
            val o = JSONObject()
            o.put("on", p.getBoolean("on", false))
            o.put("step", alignStep)
            return o.toString()
        }

        @JavascriptInterface
        fun beginMarks() {
            runOnUiThread {
                alignStep = 1
                boardJs("jkPadNote('hi')")
            }
        }

        @JavascriptInterface
        fun clearMarks() {
            runOnUiThread {
                alignStep = 0
                padPrefs().edit().clear().apply()
                boardJs("jkPadNote('off')")
            }
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
        webView.addJavascriptInterface(AndroidPadBridge(), "AndroidPad")

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

    private fun padPrefs() = getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun boardJs(code: String) {
        if (!::webView.isInitialized) return
        webView.evaluateJavascript(code, null)
    }

    private fun phoneSideKeys(device: InputDevice?): Boolean {
        if (device == null || device.isVirtual) return true
        val n = device.name.lowercase()
        return n.contains("gpio") || n.contains("sec_key") || n.contains("qpnp") ||
            n.contains("pmic") || n.contains("uinput-fbsensor") || n.contains("mtk-kpd") ||
            n.contains("sprd") || n.contains("h2w")
    }

    private fun outsideKeys(ev: KeyEvent): Boolean {
        val d = ev.device ?: return false
        if (phoneSideKeys(d)) return false
        return true
    }

    private fun fingerTouch(ev: MotionEvent): Boolean {
        if (ev.pointerCount < 1) return true
        val tool = ev.getToolType(0)
        if (tool == MotionEvent.TOOL_TYPE_FINGER) return true
        val src = ev.source
        return (src and InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN &&
            (src and InputDevice.SOURCE_MOUSE) == 0
    }

    private fun rememberHover(ev: MotionEvent) {
        val x = ev.rawX
        val y = ev.rawY
        if (x >= 1f || y >= 1f) {
            lastHoverX = x
            lastHoverY = y
            lastHoverAt = System.currentTimeMillis()
        }
    }

    private fun pressXY(ev: MotionEvent): Pair<Float, Float>? {
        var x = ev.rawX
        var y = ev.rawY
        val now = System.currentTimeMillis()
        if (x < 1f && y < 1f) {
            if (now - lastHoverAt > HOVER_FRESH_MS) return null
            x = lastHoverX
            y = lastHoverY
            if (x < 1f && y < 1f) return null
        }
        return x to y
    }

    private fun takeAlign(x: Float, y: Float): Boolean {
        val p = padPrefs()
        if (alignStep == 1) {
            p.edit().putFloat("hiX", x).putFloat("hiY", y).apply()
            alignStep = 2
            boardJs("jkPadNote('lo')")
            return true
        }
        if (alignStep == 2) {
            val hiY = p.getFloat("hiY", 0f)
            if (abs(y - hiY) < MIN_SPAN) {
                boardJs("jkPadNote('gap')")
                return true
            }
            p.edit()
                .putFloat("loX", x)
                .putFloat("loY", y)
                .putBoolean("on", true)
                .apply()
            alignStep = 0
            boardJs("jkPadNote('ok')")
            return true
        }
        return false
    }

    private fun laneHit(x: Float, y: Float): String {
        val p = padPrefs()
        if (!p.getBoolean("on", false)) return ""
        val hiX = p.getFloat("hiX", 0f)
        val hiY = p.getFloat("hiY", 0f)
        val loX = p.getFloat("loX", 0f)
        val loY = p.getFloat("loY", 0f)
        val midX = (hiX + loX) * 0.5f
        val midY = (hiY + loY) * 0.5f
        val spanY = abs(loY - hiY)
        if (spanY < MIN_SPAN) return ""
        val lane = maxOf(48f, abs(hiX - loX) + 52f)
        if (abs(x - midX) > lane) return ""
        val dHi = abs(y - hiY)
        val dLo = abs(y - loY)
        val dMid = abs(y - midY)
        val band = spanY * 0.35f
        if (dHi < dMid && dHi < dLo) return "R"
        if (dLo < dMid && dLo < dHi) return "L"
        if (dMid <= dHi && dMid <= dLo && dMid <= band) return "U"
        return "X"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.scanCode == SCAN_SKIP) {
            return super.dispatchKeyEvent(event)
        }
        if (!outsideKeys(event)) return super.dispatchKeyEvent(event)
        if (event.repeatCount > 0 && event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            ) {
                return true
            }
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (event.action == KeyEvent.ACTION_UP) boardJs("jkHidUndo(false)")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (event.action == KeyEvent.ACTION_UP) boardJs("jkHidUndo(false)")
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    volDownAt = System.currentTimeMillis()
                    volDownCode = event.keyCode
                    return true
                }
                if (event.action == KeyEvent.ACTION_UP && volDownCode == event.keyCode) {
                    val held = System.currentTimeMillis() - volDownAt
                    volDownCode = 0
                    if (held >= VOL_HOLD_MS) {
                        boardJs("jkHidUndo(true)")
                    } else if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        boardJs("jkHidPoint('R')")
                    } else {
                        boardJs("jkHidPoint('L')")
                    }
                    return true
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!fingerTouch(event)) {
            if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE ||
                event.actionMasked == MotionEvent.ACTION_HOVER_ENTER
            ) {
                rememberHover(event)
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (fingerTouch(event)) return super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE ||
            event.actionMasked == MotionEvent.ACTION_HOVER_ENTER
        ) {
            rememberHover(event)
            return super.dispatchTouchEvent(event)
        }
        if (event.actionMasked != MotionEvent.ACTION_DOWN) {
            if (alignStep != 0 || padPrefs().getBoolean("on", false)) {
                val p = pressXY(event) ?: return super.dispatchTouchEvent(event)
                if (alignStep != 0) return true
                val hit = laneHit(p.first, p.second)
                if (hit.isNotEmpty()) return true
            }
            return super.dispatchTouchEvent(event)
        }
        if (event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) {
            return super.dispatchTouchEvent(event)
        }
        val xy = pressXY(event) ?: return super.dispatchTouchEvent(event)
        if (takeAlign(xy.first, xy.second)) return true
        val hit = laneHit(xy.first, xy.second)
        if (hit.isEmpty()) return super.dispatchTouchEvent(event)
        when (hit) {
            "R" -> boardJs("jkHidPoint('R')")
            "L" -> boardJs("jkHidPoint('L')")
            "U" -> boardJs("jkHidUndo(false)")
        }
        return true
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
