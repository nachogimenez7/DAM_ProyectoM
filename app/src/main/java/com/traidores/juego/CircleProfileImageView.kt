package com.traidores.juego

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class CircleProfileImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val clipPath = Path()
    private val bounds = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = minOf(w, h).toFloat()
        val left = (w - size) / 2f
        val top = (h - size) / 2f
        bounds.set(left, top, left + size, top + size)
        clipPath.reset()
        clipPath.addOval(bounds, Path.Direction.CW)
        clipPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        val checkpoint = canvas.save()
        canvas.clipPath(clipPath)
        super.onDraw(canvas)
        canvas.restoreToCount(checkpoint)
    }
}
