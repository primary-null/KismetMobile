package com.kismet.mobile.sdr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

class SpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Band(val label: String, val signalDbm: Int, val packets: Long = 0)

    private var bands: List<Band> = emptyList()
    private var waterfallBitmap: Bitmap? = null
    private var selectedIndex = -1

    val hasSelection: Boolean get() = selectedIndex >= 0
    var onChannelSelected: ((Band?) -> Unit)? = null

    private val density       = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity

    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = v * scaledDensity

    private val minBarWidthPx = dp(44f).toInt()

    // ── Paints ─────────────────────────────────────────────────────────────

    private val bgPaint = Paint().apply { color = Color.parseColor("#0F1214") }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#1E2D33")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val dividerPaint = Paint().apply {
        color = Color.parseColor("#2C3539")
        strokeWidth = 2f
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val selectedBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#546E7A")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    private val selectedLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A7FFEB")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    private val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#546E7A")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
    }

    private val noDataPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2C3539")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    private val waterfallLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#37474F")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
    }

    private val noiseFloorPaint = Paint().apply {
        color = Color.parseColor("#546E7A")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(4f)), 0f)
    }

    private val noiseFloorLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#546E7A")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.RIGHT
    }

    private val peakMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A7FFEB")
        style = Paint.Style.FILL
    }

    // Update text sizes once density is known
    init {
        labelPaint.textSize          = sp(11f)
        selectedLabelPaint.textSize  = sp(11f)
        axisLabelPaint.textSize      = sp(10f)
        noDataPaint.textSize         = sp(14f)
        waterfallLabelPaint.textSize = sp(9f)
        noiseFloorLabelPaint.textSize = sp(9f)
    }

    private val floorDbm  = -100f
    private val ceilDbm   = -20f
    private val topPad    = dp(20f)

    // ── GestureDetector for tap-to-select ─────────────────────────────────

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (bands.isEmpty()) return false
                val specH = height * SPECTRUM_RATIO
                if (e.y > specH) return false
                val barW = width.toFloat() / bands.size
                val i = (e.x / barW).toInt().coerceIn(0, bands.size - 1)
                selectedIndex = if (selectedIndex == i) {
                    onChannelSelected?.invoke(null)
                    -1
                } else {
                    onChannelSelected?.invoke(bands[i])
                    i
                }
                invalidate()
                return true
            }
        })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return event.actionMasked == MotionEvent.ACTION_DOWN || super.onTouchEvent(event)
    }

    // ── Measure ────────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentW = MeasureSpec.getSize(widthMeasureSpec)
        val desiredW = if (bands.isEmpty()) parentW
                       else maxOf(parentW, bands.size * minBarWidthPx)
        setMeasuredDimension(desiredW, MeasureSpec.getSize(heightMeasureSpec))
    }

    // ── Data ───────────────────────────────────────────────────────────────

    fun update(data: List<Band>) {
        val sorted = data.sortedBy { channelToSortKey(it.label) }
        if (sorted.size != bands.size) requestLayout()
        bands = sorted
        if (selectedIndex >= bands.size) {
            selectedIndex = -1
            onChannelSelected?.invoke(null)
        } else if (selectedIndex >= 0) {
            onChannelSelected?.invoke(bands[selectedIndex])
        }
        appendWaterfallRow()
        invalidate()
    }

    // ── Drawing ────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (bands.isEmpty()) {
            canvas.drawText("NO SPECTRUM DATA", width / 2f, height / 2f, noDataPaint)
            return
        }

        val specH = height * SPECTRUM_RATIO
        drawSpectrum(canvas, specH)
        canvas.drawLine(0f, specH, width.toFloat(), specH, dividerPaint)
        drawWaterfall(canvas, specH + 2f)
    }

    private fun drawSpectrum(canvas: Canvas, specH: Float) {
        val dbmLines = listOf(-20, -40, -60, -80, -100)
        dbmLines.forEach { dbm ->
            val y = dbmToY(dbm.toFloat(), specH)
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            canvas.drawText("$dbm", dp(4f), y - dp(3f), axisLabelPaint)
        }

        val peakIdx = bands.indexOfFirst { it.signalDbm == bands.maxOf { b -> b.signalDbm } }
        val barW = width.toFloat() / bands.size

        bands.forEachIndexed { i, band ->
            val norm  = normalize(band.signalDbm)
            val left  = i * barW + dp(1f)
            val right = left + barW - dp(2f)
            val top   = dbmToY(band.signalDbm.toFloat(), specH)

            barPaint.shader = LinearGradient(
                left, top, left, specH,
                if (i == peakIdx) Color.WHITE else signalTopColor(norm),
                signalBottomColor(norm),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(left, top, right, specH, barPaint)

            if (i == selectedIndex) canvas.drawRect(left, top, right, specH, selectedBarPaint)

            val lp = if (i == selectedIndex) selectedLabelPaint else labelPaint
            lp.textAlign = Paint.Align.CENTER
            val barLabel = band.label.removeSuffix(" MHz")
            canvas.drawText(barLabel, left + barW / 2f, specH - dp(4f), lp)
        }

        // Noise floor
        if (bands.size >= 3) {
            val avgDbm = bands.map { it.signalDbm }.average().toFloat()
            val avgY   = dbmToY(avgDbm, specH)
            canvas.drawLine(0f, avgY, width.toFloat(), avgY, noiseFloorPaint)
            canvas.drawText("AVG ${avgDbm.toInt()}", width.toFloat() - dp(4f), avgY - dp(4f), noiseFloorLabelPaint)
        }

        // Peak triangle — clamp so apex never escapes the chart top boundary
        if (peakIdx >= 0) {
            val cx      = peakIdx * barW + barW / 2f
            val rawTopY = dbmToY(bands[peakIdx].signalDbm.toFloat(), specH)
            val triY    = rawTopY.coerceAtLeast(topPad + dp(10f))
            val path = Path()
            path.moveTo(cx, triY - dp(10f))
            path.lineTo(cx - dp(5f), triY - dp(1f))
            path.lineTo(cx + dp(5f), triY - dp(1f))
            path.close()
            canvas.drawPath(path, peakMarkerPaint)
        }
    }

    private fun drawWaterfall(canvas: Canvas, top: Float) {
        val bm = waterfallBitmap
        if (bm != null) canvas.drawBitmap(bm, 0f, top, null)
        canvas.drawText("WATERFALL", dp(6f), top + sp(10f) + dp(2f), waterfallLabelPaint)
    }

    // ── Waterfall bitmap ───────────────────────────────────────────────────

    private fun appendWaterfallRow() {
        if (width == 0 || height == 0 || bands.isEmpty()) return

        val wfH = ((height * (1f - SPECTRUM_RATIO)) - 2).toInt().coerceAtLeast(1)
        val wfW = width

        val bm = if (waterfallBitmap?.width == wfW && waterfallBitmap?.height == wfH) {
            waterfallBitmap!!
        } else {
            Bitmap.createBitmap(wfW, wfH, Bitmap.Config.ARGB_8888).also { waterfallBitmap = it }
        }

        val pixels = IntArray(wfW * wfH)
        bm.getPixels(pixels, 0, wfW, 0, 0, wfW, wfH)

        val rowH = ROW_HEIGHT_PX
        if (wfH > rowH) System.arraycopy(pixels, 0, pixels, wfW * rowH, wfW * (wfH - rowH))

        val barW = wfW.toFloat() / bands.size
        bands.forEachIndexed { i, band ->
            val color  = thermalColor(normalize(band.signalDbm))
            val startX = (i * barW).toInt()
            val endX   = ((i + 1) * barW).toInt().coerceAtMost(wfW)
            for (row in 0 until rowH) {
                for (x in startX until endX) pixels[row * wfW + x] = color
            }
        }

        bm.setPixels(pixels, 0, wfW, 0, 0, wfW, wfH)
    }

    // ── Colour helpers ─────────────────────────────────────────────────────

    private fun normalize(dbm: Int) = ((dbm - floorDbm) / (ceilDbm - floorDbm)).coerceIn(0f, 1f)

    private fun dbmToY(dbm: Float, specH: Float): Float =
        topPad + (specH - topPad) * (1f - normalize(dbm.toInt()))

    private fun signalTopColor(norm: Float): Int = when {
        norm < 0.25f -> Color.parseColor("#004D3A")
        norm < 0.55f -> Color.parseColor("#00897B")
        norm < 0.80f -> Color.parseColor("#00BFA5")
        else         -> Color.parseColor("#A7FFEB")
    }

    private fun signalBottomColor(norm: Float): Int = Color.parseColor("#0A1512")

    private fun thermalColor(norm: Float): Int {
        val stops = arrayOf(
            0.00f to Color.BLACK,
            0.20f to Color.parseColor("#3B0066"),
            0.40f to Color.parseColor("#0000CC"),
            0.55f to Color.parseColor("#00AACC"),
            0.70f to Color.parseColor("#00CC44"),
            0.82f to Color.parseColor("#CCCC00"),
            0.91f to Color.parseColor("#CC5500"),
            1.00f to Color.parseColor("#CC0000"),
        )
        for (i in 0 until stops.size - 1) {
            val (lo, c1) = stops[i]
            val (hi, c2) = stops[i + 1]
            if (norm <= hi) {
                val t = (norm - lo) / (hi - lo)
                return interpolateColor(c1, c2, t)
            }
        }
        return Color.RED
    }

    private fun interpolateColor(c1: Int, c2: Int, t: Float): Int {
        val r = (Color.red(c1)   + (Color.red(c2)   - Color.red(c1))   * t).toInt().coerceIn(0, 255)
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt().coerceIn(0, 255)
        val b = (Color.blue(c1)  + (Color.blue(c2)  - Color.blue(c1))  * t).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    // ── Channel sorting ────────────────────────────────────────────────────

    private fun channelToSortKey(ch: String): Int {
        val n = ch.toIntOrNull() ?: return Int.MAX_VALUE
        return when {
            n in 1..14   -> 2407 + n * 5
            n in 32..177 -> 5000 + n * 5
            n in 1..233 && ch.length <= 3 -> 5950 + n * 5
            n > 2400     -> n
            else         -> n
        }
    }

    companion object {
        private const val SPECTRUM_RATIO = 0.55f
        private const val ROW_HEIGHT_PX  = 2
    }
}
