package com.example.sorter

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

class BarGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var numbers: List<Int> = emptyList()
        set(value) {
            field = value.toList() // Always create new instance
            invalidate()
        }

    var highlightIndices: Pair<Int, Int>? = null
        set(value) {
            field = value
            invalidate()
        }

    var swappedIndex: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    // Enhanced Paint Objects
    private val normalPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val comparingPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val swappedPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val negativePaint = Paint().apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.bar_negative)
        isAntiAlias = true
    }

    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.background_light)
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.white)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    // Glow effect paint - preserved for highlighting
    private val glowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
        maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.OUTER)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (numbers.isEmpty()) return

        val paddingHorizontal = 16f
        val paddingVertical = 12f
        val barWidth = (width.toFloat() - 2 * paddingHorizontal) / numbers.size
        val maxValue = numbers.maxOrNull() ?: 1
        val minValue = numbers.minOrNull() ?: 0
        val valueRange = maxValue - minValue
        val scaleFactor = (height * 0.8f - 2 * paddingVertical) / (if (valueRange == 0) 1 else valueRange)
        val zeroY = height - paddingVertical - ((0 - minValue) * scaleFactor)

        textPaint.textSize = min(barWidth * 0.4f, 28f)

        // Setup dynamic gradients
        setupGradients(height.toFloat())

        // Draw zero line
        canvas.drawLine(paddingHorizontal, zeroY, width.toFloat() - paddingHorizontal, zeroY, borderPaint)

        numbers.forEachIndexed { index, value ->
            val left = paddingHorizontal + index * barWidth + 2f
            val right = paddingHorizontal + (index + 1) * barWidth - 2f

            val paintToUse = when {
                swappedIndex == index -> swappedPaint
                highlightIndices?.let { index == it.first || index == it.second } == true -> comparingPaint
                value < 0 -> negativePaint
                else -> normalPaint
            }

            val barTop: Float
            val barBottom: Float
            if (value >= 0) {
                barTop = zeroY - (value * scaleFactor)
                barBottom = zeroY
            } else {
                barTop = zeroY
                barBottom = zeroY - (value * scaleFactor)
            }

            val cornerRadius = min(barWidth * 0.2f, 16f)

            // Create path with rounded corners
            val path = Path()
            path.addRoundRect(
                RectF(left, barTop, right, barBottom),
                cornerRadius,
                cornerRadius,
                Path.Direction.CW
            )

            // Enhanced glow effect for highlighted bars
            val isHighlighted = swappedIndex == index ||
                    highlightIndices?.let { index == it.first || index == it.second } == true

            if (isHighlighted) {
                // Set glow color based on highlight type
                glowPaint.color = when {
                    swappedIndex == index -> ContextCompat.getColor(context, R.color.dark_red)
                    else -> ContextCompat.getColor(context, R.color.accent_orange)
                }

                // Draw glow effect
                canvas.drawPath(path, glowPaint)

                // Additional pulsing effect for enhanced visibility
                val pulseScale = 1f + 0.1f * kotlin.math.sin(System.currentTimeMillis() / 200f)
                canvas.save()
                canvas.scale(pulseScale, pulseScale, left + barWidth/2, (barTop + barBottom)/2)
                canvas.drawPath(path, paintToUse)
                canvas.restore()

                // Force continuous redraw for pulsing animation
                invalidate()
            } else {
                canvas.drawPath(path, paintToUse)
            }

            canvas.drawPath(path, borderPaint)

            // Text positioning
            val textY = if (value >= 0) {
                maxOf(barTop - 8f, paddingVertical + textPaint.textSize)
            } else {
                minOf(barBottom + textPaint.textSize + 4f, height - paddingVertical)
            }

            canvas.drawText(value.toString(), left + (barWidth / 2), textY, textPaint)
        }
    }

    private fun setupGradients(canvasHeight: Float) {
        normalPaint.shader = LinearGradient(
            0f, 0f, 0f, canvasHeight,
            ContextCompat.getColor(context, R.color.bar_normal),
            ContextCompat.getColor(context, R.color.accent_green),
            Shader.TileMode.CLAMP
        )

        comparingPaint.shader = LinearGradient(
            0f, 0f, 0f, canvasHeight,
            ContextCompat.getColor(context, R.color.bar_comparing),
            ContextCompat.getColor(context, R.color.accent_orange),
            Shader.TileMode.CLAMP
        )

        swappedPaint.shader = LinearGradient(
            0f, 0f, 0f, canvasHeight,
            ContextCompat.getColor(context, R.color.dark_red),
            ContextCompat.getColor(context, R.color.accent_pink),
            Shader.TileMode.CLAMP
        )
    }
}


