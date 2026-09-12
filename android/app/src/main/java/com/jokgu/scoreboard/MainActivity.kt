package com.jokgu.scoreboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
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

    /* JK_HID_PAD_LOCK: 리모컨 한 번 누름이 포인터·볼륨 두 경로로 동시에 들어와도 한 번만 반영.
     * 되돌리: 이 2필드·scoreLocked/markScored/undoLocked/markUndone·호출부 잠금 분기 삭제 */
    private var lastScoreSignalAt = 0L
    private var lastUndoSignalAt = 0L

    /* JK_HID_BAR_GUARD: 위 버튼으로 판정된 뒤에만 시스템 바를 다시 숨김. 손가락으로 연 바는 그대로.
     * 되돌리: hideBarsAfterPadUp 호출 삭제, onCreate insets 리스너+항상 재숨김으로 복원 */
    private val barGuardHandler = Handler(Looper.getMainLooper())
    private var pendingBarRehide: Runnable? = null
    private var barRehideUntilElapsed = 0L

    /* JK_PAD_GESTURE: 이 리모컨은 좌표 클릭이 아니라 눌림-뗌 사이 경과시간·이동거리로 구분되는
     * 제스처(탭·상하 스와이프)를 보낸다. 위 버튼은 좌표 없는 단독 ACTION_OUTSIDE로만 온다
     * (FLAG_WATCH_OUTSIDE_TOUCH 필요). 되돌리: 이 3필드·classifyPadGesture/maybePadEvent 삭제,
     * dispatch*를 laneHit 좌표 판정으로 복원 */
    private var padGestureActive = false
    private var padGestureStartY = 0f
    private var padGestureStartAt = 0L

    private companion object {
        private const val REQ_BLE = 2001
        private const val PREF = "jokgu_pad_marks"
        private const val MIN_SPAN = 72f
        private const val HOVER_FRESH_MS = 48L
        private const val VOL_HOLD_MS = 480L
        private const val SCAN_SKIP = 320
        /* JK_HID_PAD_LOCK */
        private const val CROSS_INPUT_LOCK_MS = 400L
        /* JK_HID_BAR_GUARD: 위 판정 직후 짧게 반복 숨김. OS가 늦게 켜도 한 프레임 안에 닫음 */
        private const val BAR_REHIDE_TICK_MS = 16L
        private const val BAR_REHIDE_WINDOW_MS = 200L
        /* JK_PAD_GESTURE: 탭(가운데)=250ms 이내·화면 긴 축 8% 이내 이동. 스와이프(아래)=20% 이상 이동 */
        private const val PAD_GESTURE_TAP_MAX_MS = 250L
        private const val PAD_GESTURE_TAP_MAX_DY_FRAC = 0.08f
        private const val PAD_GESTURE_SWIPE_MIN_DY_FRAC = 0.20f
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
                applyPadPointerHide()
                boardJs("jkPadNote('mid')")
            }
        }

        @JavascriptInterface
        fun clearMarks() {
            runOnUiThread {
                alignStep = 0
                padPrefs().edit().clear().apply()
                applyPadPointerHide()
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
        /* JK_PAD_GESTURE: 위 버튼 눌림이 상태 바 제스처로 앱 밖까지 나가도 단독 ACTION_OUTSIDE로
         * 받기 위함. 되돌리: 이 한 줄 삭제 */
        window.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            /* JK_NO_WEBVIEW_CACHE: 번들 자산 HTML은 앱 업데이트마다 바뀌므로 캐시하지 않음.
             * 되돌리: WebSettings.LOAD_DEFAULT로 복원 */
            cacheMode = WebSettings.LOAD_NO_CACHE
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
        /* JK_PAD_POINTER_HIDE: 맞춤이 이미 켜져 있으면 커서 숨김. 되돌리: 이 호출·hover 리스너 삭제 */
        applyPadPointerHide()
        webView.setOnHoverListener { _, _ ->
            applyPadPointerHide()
            false
        }
        requestBleAndStart()
    }

    private fun padPrefs() = getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun boardJs(code: String) {
        if (!::webView.isInitialized) return
        webView.evaluateJavascript(code, null)
    }

    /* JK_HID_PAD_LOCK: 포인터·볼륨 중 먼저 들어온 쪽만 반영 */
    private fun scoreLocked(): Boolean =
        System.currentTimeMillis() - lastScoreSignalAt < CROSS_INPUT_LOCK_MS

    private fun markScored() {
        lastScoreSignalAt = System.currentTimeMillis()
    }

    private fun undoLocked(): Boolean =
        System.currentTimeMillis() - lastUndoSignalAt < CROSS_INPUT_LOCK_MS

    private fun markUndone() {
        lastUndoSignalAt = System.currentTimeMillis()
    }

    /* JK_HID_BAR_GUARD: 위 버튼 판정 직후 숨김. OS가 바를 늦게 켜면 짧은 간격으로 창이 끝날 때까지 다시 숨김. */
    private fun scheduleBarRehide() {
        pendingBarRehide?.let { barGuardHandler.removeCallbacks(it) }
        barRehideUntilElapsed = SystemClock.uptimeMillis() + BAR_REHIDE_WINDOW_MS
        val task = object : Runnable {
            override fun run() {
                applyImmersive()
                if (SystemClock.uptimeMillis() < barRehideUntilElapsed) {
                    barGuardHandler.postDelayed(this, BAR_REHIDE_TICK_MS)
                }
            }
        }
        pendingBarRehide = task
        barGuardHandler.post(task)
    }

    private fun hideBarsAfterPadUp() {
        applyImmersive()
        webView.post { applyImmersive() }
        scheduleBarRehide()
    }

    /* JK_PAD_POINTER_HIDE: 맞춤이 켜진 뒤에만 3버튼 리모컨 마우스 커서를 숨김. 맞추기 중·끈 상태는 기본 커서.
     * 되돌리: 이 함수·호출부 삭제 */
    private fun applyPadPointerHide() {
        if (!::webView.isInitialized) return
        val hide = padPrefs().getBoolean("on", false) && alignStep == 0
        val type = if (hide) PointerIcon.TYPE_NULL else PointerIcon.TYPE_ARROW
        val icon = PointerIcon.getSystemIcon(this, type)
        window.decorView.pointerIcon = icon
        webView.pointerIcon = icon
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

    /* JK_PAD_CAL_MIDLOW: 위 버튼은 좌표 보정이 없음(JK_PAD_GESTURE가 전담 인식). 가운데·아래만
     * 눌러서 이 기기를 "켬" 상태로 저장(좌표 자체는 판정에 쓰지 않고 참고용). 되돌리: alignStep
     * 3단계(hi→mid→lo)로, laneHit 좌표 판정으로 복원 */
    private fun takeAlign(x: Float, y: Float): Boolean {
        val p = padPrefs()
        if (alignStep == 1) {
            p.edit().putFloat("midX", x).putFloat("midY", y).apply()
            alignStep = 2
            boardJs("jkPadNote('lo')")
            return true
        }
        if (alignStep == 2) {
            val midY = p.getFloat("midY", 0f)
            if (abs(y - midY) < MIN_SPAN) {
                boardJs("jkPadNote('gap')")
                return true
            }
            p.edit()
                .putFloat("loX", x)
                .putFloat("loY", y)
                .putBoolean("on", true)
                .apply()
            alignStep = 0
            applyPadPointerHide()
            boardJs("jkPadNote('ok')")
            return true
        }
        return false
    }

    /* JK_PAD_GESTURE: 이 기기(외부 리모컨)의 실제 눌림인지 판별. 폰 자체 화면 손가락 터치는 제외 */
    private fun isExternalOrUnknownMotion(ev: MotionEvent): Boolean {
        val device = ev.device ?: return true
        if (device.isVirtual) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !device.isExternal) return false
        return true
    }

    private fun padPointerSpaceHeight(): Float =
        maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
            .toFloat()
            .coerceAtLeast(1f)

    /* 탭(짧고 거의 안 움직임)=가운데, 아래로 크게 스와이프=아래, 위로 크게 스와이프=위 */
    private fun classifyPadGesture(startY: Float, endY: Float, elapsedMs: Long): String {
        val h = padPointerSpaceHeight()
        val deltaY = endY - startY
        if (elapsedMs <= PAD_GESTURE_TAP_MAX_MS && abs(deltaY) <= h * PAD_GESTURE_TAP_MAX_DY_FRAC) {
            return "U"
        }
        val swipeMin = h * PAD_GESTURE_SWIPE_MIN_DY_FRAC
        if (deltaY >= swipeMin) return "L"
        if (deltaY <= -swipeMin) return "R"
        return ""
    }

    private fun emitPadZone(zone: String): Boolean {
        when (zone) {
            "U" -> if (!undoLocked()) {
                markUndone()
                boardJs("jkHidUndo(false)")
            }
            "L" -> if (!scoreLocked()) {
                markScored()
                boardJs("jkHidPoint('L')")
            }
            "R" -> if (!scoreLocked()) {
                markScored()
                boardJs("jkHidPoint('R')")
            }
            else -> return false
        }
        return true
    }

    /* JK_PAD_GESTURE: 가운데·아래는 눌림→뗌 사이 제스처로, 위는 단독 ACTION_OUTSIDE로 판정.
     * 되돌리: 이 함수 삭제, dispatch*를 laneHit 좌표 판정 호출로 복원 */
    private fun maybePadEvent(event: MotionEvent): Boolean {
        if (!isExternalOrUnknownMotion(event)) return false
        val masked = event.actionMasked

        if (alignStep != 0) {
            if (masked == MotionEvent.ACTION_DOWN ||
                masked == MotionEvent.ACTION_POINTER_DOWN ||
                masked == MotionEvent.ACTION_BUTTON_PRESS
            ) {
                val xy = pressXY(event)
                if (xy != null) takeAlign(xy.first, xy.second)
            }
            return true
        }

        if (masked == MotionEvent.ACTION_OUTSIDE) {
            if (!padPrefs().getBoolean("on", false)) return false
            if (padGestureActive) {
                padGestureActive = false
                val elapsed = System.currentTimeMillis() - padGestureStartAt
                return emitPadZone(classifyPadGesture(padGestureStartY, event.rawY, elapsed))
            }
            val applied = emitPadZone("R")
            hideBarsAfterPadUp()
            return applied
        }

        if (!padPrefs().getBoolean("on", false)) return false

        if (masked == MotionEvent.ACTION_DOWN ||
            masked == MotionEvent.ACTION_POINTER_DOWN ||
            masked == MotionEvent.ACTION_BUTTON_PRESS
        ) {
            padGestureActive = true
            padGestureStartY = event.rawY
            padGestureStartAt = System.currentTimeMillis()
            applyPadPointerHide()
            return true
        }
        if (masked == MotionEvent.ACTION_MOVE) {
            if (padGestureActive) applyPadPointerHide()
            return padGestureActive
        }
        if (masked == MotionEvent.ACTION_UP ||
            masked == MotionEvent.ACTION_POINTER_UP ||
            masked == MotionEvent.ACTION_BUTTON_RELEASE ||
            masked == MotionEvent.ACTION_CANCEL
        ) {
            if (!padGestureActive) return false
            padGestureActive = false
            val elapsed = System.currentTimeMillis() - padGestureStartAt
            return emitPadZone(classifyPadGesture(padGestureStartY, event.rawY, elapsed))
        }
        return false
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
                /* JK_HID_PAD_LOCK: 같은 눌림이 포인터 되돌리기로도 들어왔으면 한 번만 */
                if (event.action == KeyEvent.ACTION_UP && !undoLocked()) {
                    markUndone()
                    boardJs("jkHidUndo(false)")
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (event.action == KeyEvent.ACTION_UP && !undoLocked()) {
                    markUndone()
                    boardJs("jkHidUndo(false)")
                }
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
                    /* JK_HID_PAD_LOCK: 포인터·볼륨 중 먼저 반영된 쪽만 인정, 나머지는 삼킴 */
                    if (held >= VOL_HOLD_MS) {
                        if (!undoLocked()) {
                            markUndone()
                            boardJs("jkHidUndo(true)")
                        }
                    } else if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        if (!scoreLocked()) {
                            markScored()
                            boardJs("jkHidPoint('R')")
                        }
                    } else {
                        if (!scoreLocked()) {
                            markScored()
                            boardJs("jkHidPoint('L')")
                        }
                    }
                    return true
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE ||
            event.actionMasked == MotionEvent.ACTION_HOVER_ENTER
        ) {
            if (!fingerTouch(event)) rememberHover(event)
        }
        if (maybePadEvent(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE ||
            event.actionMasked == MotionEvent.ACTION_HOVER_ENTER
        ) {
            if (!fingerTouch(event)) rememberHover(event)
            return super.dispatchTouchEvent(event)
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            (event.buttonState and MotionEvent.BUTTON_SECONDARY != 0)
        ) {
            return super.dispatchTouchEvent(event)
        }
        if (fingerTouch(event) && event.actionMasked != MotionEvent.ACTION_OUTSIDE) {
            return super.dispatchTouchEvent(event)
        }
        if (maybePadEvent(event)) return true
        return super.dispatchTouchEvent(event)
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

    /* JK_IMMERSIVE_REAPPLY: 설정 화면 등에서 돌아와 포커스를 되찾을 때도 몰입 모드 유지.
     * 되돌리: 이 오버라이드 삭제 */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersive()
            applyPadPointerHide()
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersive()
        applyPadPointerHide()
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
        pendingBarRehide?.let { barGuardHandler.removeCallbacks(it) }
        bleScoreSpikeServer?.stop()
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
