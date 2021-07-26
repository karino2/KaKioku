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

    private val undoList = UndoList()

    val canUndo: Boolean
        get() = undoList.canUndo

    val canRedo: Boolean
        get() = undoList.canRedo

    private var undoCount = 0
    private var redoCount = 0

    fun undo(count: Int) {
        if (undoCount != count) {
            undoCount = count
            undoList.undo(bmpCanvas)

            refreshAfterUndoRedo()
        }
    }

    fun redo(count: Int) {
        if (redoCount != count) {
            redoCount = count
            undoList.redo(bmpCanvas)

            refreshAfterUndoRedo()
        }
    }

    private fun notifyUndoStateChanged() {
        undoStateListener(canUndo, canRedo)
    }


    fun refreshAfterUndoRedo() {
        notifyUndoStateChanged()
        updateBmpListener(bitmap)
        invalidate()
    }

    // use for short term temporary only.
    private val tempRegion = RectF()
    private val tempRect = Rect()
    private fun pathBound(path: Path): Rect {
        path.computeBounds(tempRegion, false)
        tempRegion.roundOut(tempRect)
        widen(tempRect, 5)
        return tempRect
    }

    private fun widen(tmpInval: Rect, margin: Int) {
        val newLeft = (tmpInval.left - margin).coerceAtLeast(0)
        val newTop = (tmpInval.top - margin).coerceAtLeast(0)
        val newRight = (tmpInval.right + margin).coerceAtMost(width)
        val newBottom = (tmpInval.bottom + margin).coerceAtMost(height)
        tmpInval.set(newLeft, newTop, newRight, newBottom)
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        setupNewCanvasBitmap(w, h)
        drawInitialBitmap()
        updateBmpListener(bitmap)
    }

    fun setStrokeColor(newColor: Int) {
        pathPaint.color = newColor
    }

    private fun clearCanvas() {
        setupNewCanvasBitmap(bitmap.width, bitmap.height)
        drawInitialBitmap()
        updateBmpListener(bitmap)
        invalidate()
    }

    private fun setupNewCanvasBitmap(w: Int, h: Int) {
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE)
        bmpCanvas = Canvas(bitmap)
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

        val old = bitmap.copy(bitmap.config, true)

        clearCanvas()

        undoList.pushUndoCommand(0, 0, old, bitmap.copy(bitmap.config, true))
        notifyUndoStateChanged()
    }

    fun maybeNewBackground(bgbmp: Bitmap) {
        if (background == bgbmp)
            return
        background = bgbmp
        clearCanvas()

        undoList.clear()
        notifyUndoStateChanged()
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


                    val region = pathBound(path)
                    val undo = Bitmap.createBitmap(
                        bitmap,
                        region.left,
                        region.top,
                        region.width(),
                        region.height()
                    )
                    bmpCanvas.drawPath(path, pathPaint)
                    val redo = Bitmap.createBitmap(
                        bitmap,
                        region.left,
                        region.top,
                        region.width(),
                        region.height()
                    )
                    undoList.pushUndoCommand(region.left, region.top, undo, redo)

                    notifyUndoStateChanged()
                    updateBmpListener(bitmap)
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

    var updateBmpListener: (bmp: Bitmap) -> Unit = {}

    fun setOnUpdateListener(updateBmpListener: (bmp: Bitmap) -> Unit) {
        this.updateBmpListener = updateBmpListener
    }

    private var undoStateListener: (undo: Boolean, redo: Boolean) -> Unit = { _, _ -> }
    fun setOnUndoStateListener(undoStateListener: (undo: Boolean, redo: Boolean) -> Unit) {
        this.undoStateListener = undoStateListener
    }


}