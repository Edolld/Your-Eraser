package com.your.eraser

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.ContentValues
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.R as M
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {
    private lateinit var pv: PaintView
    private lateinit var status: TextView
    private lateinit var eraseBtn: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var empty: View
    private lateinit var rootView: View
    private val undo = ArrayList<Bitmap>()
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    @Volatile private var busy = false

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) openImage(uri)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun col(attr: Int) = MaterialColors.getColor(this, attr, Color.GRAY)
    private fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    private fun fab(res: Int, desc: String, f: () -> Unit) = FloatingActionButton(this).apply {
        setImageResource(res)
        contentDescription = desc
        setCustomSize(dp(64))
        backgroundTintList = ColorStateList.valueOf(col(M.attr.colorSecondaryContainer))
        imageTintList = ColorStateList.valueOf(col(M.attr.colorOnSecondaryContainer))
        shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(RelativeCornerSize(0.3f)).build()
        setCompatElevation(0f)
        setOnClickListener { f() }
    }

    private fun pill(t: String, size: Float, h: Int, bg: Int, fg: Int, f: () -> Unit) = MaterialButton(this).apply {
        text = t
        isAllCaps = false
        textSize = size
        setTypeface(null, Typeface.BOLD)
        cornerRadius = dp(40)
        minimumHeight = dp(h)
        backgroundTintList = ColorStateList.valueOf(bg)
        setTextColor(fg)
        iconTint = ColorStateList.valueOf(fg)
        setOnClickListener { f() }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        DynamicColors.applyToActivityIfAvailable(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        val cOn = col(M.attr.colorOnSurface)
        val cOnVar = col(M.attr.colorOnSurfaceVariant)
        val cPri = col(M.attr.colorPrimary)
        val cOnPri = col(M.attr.colorOnPrimary)
        val cSec = col(M.attr.colorSecondaryContainer)
        val cOnSec = col(M.attr.colorOnSecondaryContainer)

        pv = PaintView(this)

        // Empty state
        val openBig = pill("Выбрать фото", 18f, 64, cPri, cOnPri) { pick() }.apply { setIconResource(R.drawable.ic_photo) }
        empty = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            addView(TextView(context).apply {
                text = "Стирайте лишнее"
                textSize = 30f
                setTypeface(null, Typeface.BOLD)
                setTextColor(cOn)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "Объекты, надписи, провода. Всё обрабатывается прямо на телефоне."
                textSize = 16f
                setTextColor(cOnVar)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(28))
            })
            addView(openBig)
        }

        val card = MaterialCardView(this).apply {
            radius = dp(36).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(col(M.attr.colorSurfaceContainerHigh))
            addView(pv, FrameLayout.LayoutParams(-1, -1))
            addView(empty, FrameLayout.LayoutParams(-1, -1))
        }

        // Top bar
        val topBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "Your Eraser"
                textSize = 28f
                setTypeface(null, Typeface.BOLD)
                setTextColor(cOn)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(fab(R.drawable.ic_photo, "Выбрать фото") { pick() })
        }

        // Bottom panel
        status = TextView(this).apply {
            text = "Выберите фото, чтобы начать"
            textSize = 14f
            setTextColor(cOnVar)
        }
        progress = LinearProgressIndicator(this).apply {
            isIndeterminate = true
            visibility = View.INVISIBLE
        }
        val slider = Slider(this).apply {
            valueFrom = 2f
            valueTo = 100f
            value = 30f
            setLabelFormatter { it.toInt().toString() }
            addOnChangeListener { _, v, _ -> pv.brushPct = v / 10f }
        }
        val reset = pill("Сбросить", 14f, 40, Color.TRANSPARENT, cPri) { pv.clearMask() }.apply {
            stateListAnimator = null
        }
        val sliderRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply { text = "Кисть"; textSize = 14f; setTextColor(cOn) })
            addView(slider, LinearLayout.LayoutParams(0, -2, 1f))
            addView(reset)
        }
        eraseBtn = pill("Стереть", 22f, 64, cPri, cOnPri) { erase() }
        val actions = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(fab(R.drawable.ic_undo, "Отменить") { undoLast() })
            addView(eraseBtn, LinearLayout.LayoutParams(0, dp(64), 1f).apply {
                setMargins(dp(12), 0, dp(12), 0)
            })
            addView(fab(R.drawable.ic_save, "Сохранить") { save() })
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(col(M.attr.colorSurfaceContainer))
                val r = dp(36).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            addView(status)
            addView(progress, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(8), 0, 0) })
            addView(sliderRow)
            addView(actions)
        }

        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(col(M.attr.colorSurface))
            addView(topBar)
            addView(card, LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(dp(16), dp(4), dp(16), dp(16)) })
            addView(panel)
        }
        setContentView(rootView)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.setPadding(dp(24), b.top + dp(12), dp(16), dp(8))
            panel.setPadding(dp(24), dp(20), dp(20), b.bottom + dp(20))
            ins
        }
    }

    private fun openImage(uri: Uri) {
        try {
            val src = ImageDecoder.createSource(contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                val sc = minOf(1f, 2048f / maxOf(info.size.width, info.size.height))
                dec.setTargetSize((info.size.width * sc).toInt(), (info.size.height * sc).toInt())
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            undo.clear()
            pv.setImage(bmp)
            empty.visibility = View.GONE
            status.text = "Закрасьте объект пальцем и нажмите «Стереть»"
        } catch (e: Throwable) {
            status.text = "Ошибка: ${e.message}"
        }
    }

    private fun undoLast() {
        if (undo.isEmpty()) {
            status.text = "Отменять пока нечего"
            return
        }
        pv.setImage(undo.removeAt(undo.size - 1))
        status.text = "Последний шаг отменён"
    }

    private fun setBusy(on: Boolean, msg: String) {
        runOnUiThread {
            busy = on
            eraseBtn.isEnabled = !on
            eraseBtn.text = if (on) "Стираю…" else "Стереть"
            progress.visibility = if (on) View.VISIBLE else View.INVISIBLE
            status.text = msg
        }
    }

    private fun load(): OrtSession {
        val f = File(filesDir, "migan.onnx")
        if (!f.exists() || f.length() < 1000000) {
            assets.open("migan_pipeline_v2.onnx").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        }
        return env.createSession(f.absolutePath, OrtSession.SessionOptions())
    }

    private fun erase() {
        val b = pv.bmp
        val m = pv.mask
        if (b == null || m == null) {
            status.text = "Сначала выберите фото"
            return
        }
        if (busy) return
        setBusy(true, "Обрабатываю на устройстве…")
        Thread {
            var done = "Готово. Можно стирать дальше или сохранить"
            try {
                val w = b.width
                val h = b.height
                val n = w * h
                val px = IntArray(n)
                b.getPixels(px, 0, w, 0, 0, w, h)
                val mp = IntArray(n)
                m.getPixels(mp, 0, w, 0, 0, w, h)
                val img = ByteBuffer.allocateDirect(3 * n)
                val mk = ByteBuffer.allocateDirect(n)
                var any = false
                for (i in 0 until n) {
                    val c = px[i]
                    img.put(i, (c shr 16).toByte())
                    img.put(n + i, (c shr 8).toByte())
                    img.put(2 * n + i, c.toByte())
                    val hole = (mp[i] ushr 24) != 0
                    if (hole) any = true
                    mk.put(i, if (hole) 0 else 255.toByte())
                }
                if (!any) {
                    done = "Сначала закрасьте объект"
                } else {
                    val ses = session ?: load().also { session = it }
                    val names = ses.inputNames.toList()
                    val mn = names.firstOrNull { it.contains("mask", true) } ?: names[1]
                    val inn = names.first { it != mn }
                    val t1 = OnnxTensor.createTensor(env, img, longArrayOf(1, 3, h.toLong(), w.toLong()), OnnxJavaType.UINT8)
                    val t2 = OnnxTensor.createTensor(env, mk, longArrayOf(1, 1, h.toLong(), w.toLong()), OnnxJavaType.UINT8)
                    val res = ses.run(mapOf(inn to t1, mn to t2))
                    val out = (res.get(0) as OnnxTensor).byteBuffer
                    val outPx = IntArray(n)
                    for (i in 0 until n) {
                        outPx[i] = if ((mp[i] ushr 24) != 0) {
                            val r = out.get(i).toInt() and 255
                            val g = out.get(n + i).toInt() and 255
                            val bl = out.get(2 * n + i).toInt() and 255
                            (255 shl 24) or (r shl 16) or (g shl 8) or bl
                        } else px[i]
                    }
                    val result = Bitmap.createBitmap(outPx, w, h, Bitmap.Config.ARGB_8888)
                    res.close()
                    t1.close()
                    t2.close()
                    runOnUiThread {
                        undo.add(b)
                        if (undo.size > 5) undo.removeAt(0)
                        pv.setImage(result)
                    }
                }
            } catch (e: Throwable) {
                done = "Ошибка: ${e.message}"
            }
            setBusy(false, done)
        }.start()
    }

    private fun save() {
        val b = pv.bmp
        if (b == null) {
            status.text = "Нечего сохранять"
            return
        }
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "eraser_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YourEraser")
        }
        val u = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return
        contentResolver.openOutputStream(u)?.use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        Snackbar.make(rootView, "Сохранено в Pictures/YourEraser", Snackbar.LENGTH_LONG).show()
    }
}
