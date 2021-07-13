package io.github.karino2.kakioku

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

class DrawingCanvas(context: Context, var background: Bitmap? = null, var initialBmp: Bitmap? = null) : View(context) {
    lateinit var bitmap: Bitmap
    private lateinit var bmpCanvas: Canvas
    private var clearCount = 0

    val pathPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        color = 0xFF000000.toInt()
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f
    }

    private val bmpPaint = Paint(Paint.DITHER_FLAG)

    private val path = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmpCanvas = Canvas(bitmap)
        drawInitialBitmap()
        onUpdate(bitmap)
    }

    fun setStrokeColor(newColor: Int) {
        pathPaint.color = newColor
    }

    private fun clearCanvas() {
        bitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888);
        bmpCanvas = Canvas(bitmap)
        drawInitialBitmap()
        onUpdate(bitmap)
        invalidate()
    }

    private fun drawInitialBitmap() {
        drawBitmap(background)
        drawBitmap(initialBmp)
    }

    fun clearCanvas(count: Int) {
        if (clearCount == count)
            return
        clearCount = count
        initialBmp = null
        clearCanvas()
    }

    fun maybeNewBackground(bgbmp: Bitmap) {
        if (background == bgbmp)
            return
        background = bgbmp
        clearCanvas()
    }

    private fun drawBitmap(bitmap: Bitmap?) {
        bitmap?.let { bmp ->
            bmpCanvas.drawBitmap(
                bmp,
                Rect(0, 0, bmp.width, bmp.height),
                Rect(0, 0, this.bitmap.width, this.bitmap.height),
                bmpPaint
            )
        }
    }

    private var downHandled = false
    private var prevX = 0f
    private var prevY = 0f
    private val TOUCH_TOLERANCE = 4f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downHandled = true
                path.reset()
                path.moveTo(x, y)
                prevX = x
                prevY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (downHandled) {
                    val dx = Math.abs(x - prevX)
                    val dy = Math.abs(y - prevY)
                    if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                        path.quadTo(prevX, prevY, (x + prevX) / 2, (y + prevY) / 2)
                        prevX = x
                        prevY = y
                        invalidate()
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (downHandled) {
                    downHandled = false
                    path.lineTo(x, y)
                    bmpCanvas.drawPath(path, pathPaint)
                    onUpdate(bitmap)
                    path.reset()
                    invalidate()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, bmpPaint)
        canvas.drawPath(path, pathPaint)
    }

    var onUpdate: (bmp: Bitmap) -> Unit = {}

    fun setOnUpdateListener(updateBmpListener: (bmp: Bitmap) -> Unit) {
        onUpdate = updateBmpListener
    }


}