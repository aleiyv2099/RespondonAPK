package com.mickaell.respondon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ServiceCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.concurrent.thread
import kotlin.math.abs

class BubbleService : Service() {

    companion object {
        const val PREFS = "respondon"
        const val PREF_API_KEY = "api_key"
        private const val CHANNEL_ID = "bubble"
        private const val NOTIF_ID = 1
        // ventana de tiempo para considerar "nueva" una imagen del MediaStore
        private const val RECENT_WINDOW_S = 15L
        // espera para que el sistema termine de escribir el archivo del screenshot
        private const val WRITE_SETTLE_MS = 700L
        // burbuja discreta y translucida: azul en reposo, naranja al procesar
        private const val IDLE_COLOR = 0x552196F3
        private const val BUSY_COLOR = 0xAAEF6C00.toInt()
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: View? = null

    private var observer: ContentObserver? = null
    private var lastProcessedId = -1L
    private var busy = false
    private var lastAnswer: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startInForeground()
        addBubble()
        lastProcessedId = latestImageId() // ignorar imagenes previas al arranque
        registerObserver()
    }

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        removePanel()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    // ---------- foreground ----------

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Burbuja activa", NotificationManager.IMPORTANCE_LOW)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Esperando capturas de pantalla")
            .setContentIntent(pi)
            .build()
        ServiceCompat.startForeground(
            this, NOTIF_ID, notif,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
    }

    // ---------- burbuja ----------

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun setBubbleState(text: String, color: Int) {
        bubble?.text = text
        (bubble?.background as? GradientDrawable)?.setColor(color)
    }

    private fun addBubble() {
        val size = dp(42f)
        val view = TextView(this).apply {
            text = "R"
            setTextColor(0xCCFFFFFF.toInt()) // blanco semi para que combine con el fondo translucido
            textSize = 16f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(IDLE_COLOR)
            }
            elevation = dp(2f).toFloat()
        }
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dp(160f)
        }

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        view.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    startX = params.x; startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (ev.rawX - downX).toInt()
                    params.y = startY + (ev.rawY - downY).toInt()
                    wm.updateViewLayout(v, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(ev.rawX - downX) < slop && abs(ev.rawY - downY) < slop) togglePanel()
                    true
                }
                else -> false
            }
        }

        wm.addView(view, params)
        bubble = view
        bubbleParams = params
    }

    // ---------- panel de respuesta ----------

    private fun togglePanel() {
        if (panel != null) removePanel() else showPanel(lastAnswer ?: getString(R.string.panel_hint))
    }

    private fun removePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }

    private fun showPanel(text: String) {
        removePanel()
        val v = LayoutInflater.from(this).inflate(R.layout.panel, null)
        v.findViewById<TextView>(R.id.answer).apply {
            this.text = text
            movementMethod = android.text.method.ScrollingMovementMethod()
        }
        v.findViewById<View>(R.id.btn_copy).setOnClickListener {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("Respondon", lastAnswer ?: text))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
        v.findViewById<View>(R.id.btn_hide).setOnClickListener { removePanel() }
        v.findViewById<View>(R.id.btn_exit).setOnClickListener { stopSelf() }

        val params = WindowManager.LayoutParams(
            dp(300f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (bubbleParams?.x ?: 0)
            y = (bubbleParams?.y ?: 0) + dp(50f)
        }
        wm.addView(v, params)
        panel = v
    }

    // ---------- deteccion de screenshots ----------

    private fun registerObserver() {
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // esperar a que el archivo termine de escribirse antes de leerlo
                handler.postDelayed({ checkForScreenshot() }, WRITE_SETTLE_MS)
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer!!
        )
    }

    private fun latestImageId(): Long {
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID), null, null,
            "${MediaStore.Images.Media._ID} DESC"
        )?.use { if (it.moveToFirst()) return it.getLong(0) }
        return -1L
    }

    private fun checkForScreenshot() {
        if (busy) return
        val proj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        val since = System.currentTimeMillis() / 1000 - RECENT_WINDOW_S
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Images.Media.DATE_ADDED} >= ?", arrayOf(since.toString()),
            "${MediaStore.Images.Media._ID} DESC"
        )?.use { c ->
            if (!c.moveToFirst()) return
            val id = c.getLong(0)
            if (id <= lastProcessedId) return
            val name = (c.getString(1) ?: "").lowercase()
            val path = (c.getString(2) ?: "").lowercase()
            if ("screenshot" !in name && "screenshot" !in path && "captura" !in name && "captura" !in path) return
            lastProcessedId = id
            processScreenshot(
                Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
            )
        }
    }

    // ---------- pipeline OCR -> DeepSeek ----------

    private fun processScreenshot(uri: Uri) {
        val apiKey = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_API_KEY, "").orEmpty()
        if (apiKey.isBlank()) {
            showResult(getString(R.string.no_api_key))
            return
        }
        busy = true
        setBubbleState("...", BUSY_COLOR)

        val bitmap = runCatching {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        if (bitmap == null) {
            finishWith(getString(R.string.read_error))
            return
        }

        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                if (text.isBlank()) {
                    finishWith(getString(R.string.no_text))
                } else {
                    thread {
                        val answer = runCatching { DeepSeekClient.ask(apiKey, text) }
                            .getOrElse { getString(R.string.api_error, it.message ?: "?") }
                        handler.post { finishWith(answer) }
                    }
                }
            }
            .addOnFailureListener { finishWith(getString(R.string.ocr_error, it.message ?: "?")) }
    }

    private fun finishWith(answer: String) {
        busy = false
        lastAnswer = answer
        setBubbleState("R", IDLE_COLOR)
        showResult(answer)
    }

    private fun showResult(text: String) {
        showPanel(text)
    }
}
