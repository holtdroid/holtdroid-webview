package io.github.holtdroid.webview.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.min
import kotlin.math.roundToInt

class ViewportLayout(context: Context) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private var frameWidth = 0
    private var frameHeight = 0
    private var scalePercent = 0
    private var reportedWidth = -1
    private var reportedHeight = -1
    private var reportedScale = -1
    var onMetrics: (width: Int, height: Int, scale: Int) -> Unit = { _, _, _ -> }

    init {
        clipChildren = true
    }

    fun show(view: View) {
        if (getChildAt(0) === view) return
        removeAllViews()
        (view.parent as? ViewGroup)?.removeView(view)
        addView(view)
    }

    fun configure(width: Int, height: Int, scale: Int) {
        if (width == frameWidth && height == frameHeight && scale == scalePercent) return
        frameWidth = width
        frameHeight = height
        scalePercent = scale
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(available, availableHeight)
        val child = getChildAt(0) ?: return
        val w = if (frameWidth > 0) (frameWidth * density).roundToInt() else available
        val h = if (frameHeight > 0) (frameHeight * density).roundToInt() else availableHeight
        child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0) ?: return
        val w = child.measuredWidth
        val h = child.measuredHeight
        val framed = frameWidth > 0 || frameHeight > 0
        val raw = if (w == 0 || h == 0) 1f else min(1f, min(width.toFloat() / w, height.toFloat() / h))
        val margin = 24 * density
        val fit = if (framed && raw < 1f) min((width - margin) / w, (height - margin) / h) else raw
        val scale = if (scalePercent > 0) scalePercent / 100f else fit.coerceAtLeast(0.05f)
        child.layout(0, 0, w, h)
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = scale
        child.scaleY = scale
        child.translationX = (width - w * scale) / 2f
        child.translationY = if (framed) (height - h * scale) / 2f else 0f
        child.elevation = if (framed) 12 * density else 0f
        val cssWidth = (w / density).roundToInt()
        val cssHeight = (h / density).roundToInt()
        val percent = (scale * 100).roundToInt()
        if (cssWidth != reportedWidth || cssHeight != reportedHeight || percent != reportedScale) {
            reportedWidth = cssWidth
            reportedHeight = cssHeight
            reportedScale = percent
            post { onMetrics(cssWidth, cssHeight, percent) }
        }
    }
}
