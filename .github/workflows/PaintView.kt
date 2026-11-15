package com.your.eraser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

class PaintView(c: Context) : View(c) {
    var bmp: Bitmap? = null
    var mask: Bitmap? = null
    var brushPct = 3f
    private var mc: Canvas? = null
    private val m = Matrix()
    private val inv = Matrix()
    private var lx = 0f
    private var ly = 0f
    private val sp = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val op = Paint().apply { alpha = 130; isFilterBitmap = true }
    private val bp = Paint().apply { isFilterBitmap = true }

    fun setImage(b: Bitmap) {
        bmp = b
        if (mask?.width != b.width || mask?.height != b.height) {
            mask = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
            mc = Canvas(mask!!)
        }
        mask!!.eraseColor(0)
        fit()
        invalidate()
    }

    fun clearMask() {
        mask?.eraseColor(0)
        invalidate()
    }

    private fun fit() {
        val b = bmp ?: return
        if (width == 0) return
        val s = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
        m.setScale(s, s)
        m.postTranslate((width - b.width * s) / 2, (height - b.height * s) / 2)
        m.invert(inv)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { fit() }

    override fun onDraw(c: Canvas) {
        val b = bmp ?: return
        c.drawBitmap(b, m, bp)
        c.drawBitmap(mask!!, m, op)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val b = bmp ?: return false
        val p = floatArrayOf(e.x, e.y)
        inv.mapPoints(p)
        sp.strokeWidth = brushPct / 100f * maxOf(b.width, b.height)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                mc!!.drawPoint(p[0], p[1], sp)
            }
            MotionEvent.ACTION_MOVE -> mc!!.drawLine(lx, ly, p[0], p[1], sp)
        }
        lx = p[0]
        ly = p[1]
        invalidate()
        return true
    }
}
