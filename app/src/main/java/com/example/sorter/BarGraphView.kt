package com.example.sorter

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * BarGraphView is a custom [View] that visualizes a list of integers as a bar graph.
 * It supports highlighting elements during sorting and animating swapped elements.
 */
class BarGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * The list of numbers to be displayed as bars.
     * Setting this property triggers a redraw of the view.
     */
    var numbers: List<Int> = emptyList()
        set(value) {
            field = value.toList() // Ensure a new list instance to trigger updates
            invalidate() // Request a redraw
        }

    /**
     * A pair of indices representing bars that should be highlighted (e.g., during comparison).
     * Setting this property triggers a redraw.
     */
    var highlightIndices: Pair<Int, Int>? = null
        set(value) {
            field = value
            invalidate() // Request a redraw
        }

    /**
     * The index of a bar that was recently swapped, triggering a special highlight.
     * Setting this property triggers a redraw.
     */
    var swappedIndex: Int? = null
        set(value) {
            field = value
            invalidate() // Request a redraw
        }

    // Paint Objects for different bar states and drawing elements
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
        color = ContextCompat.getColor(context, R.color.bar_negative) // Color for negative values
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
        setShadowLayer(4f, 2f, 2f, Color.BLACK) // Text shadow for better readability
    }

    // Paint for drawing a glow effect around highlighted bars
    private val glowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
        maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.OUTER) // Creates a blur/glow effect
    }

    /**
     * Called when the view should render its content.
     * Draws the bar graph based on the current `numbers` list and highlight states.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (numbers.isEmpty()) return

        val paddingHorizontal = 16f
        val paddingVertical = 12f
        // Calculate dynamic bar width based on view width and number of bars
        val barWidth = (width.toFloat() - 2 * paddingHorizontal) / numbers.size
        val maxValue = numbers.maxOrNull() ?: 1
        val minValue = numbers.minOrNull() ?: 0
        val valueRange = maxValue - minValue
        // Scale factor to map number values to bar heights within the view's drawing area
        val scaleFactor = (height * 0.8f - 2 * paddingVertical) / (if (valueRange == 0) 1 else valueRange)
        // Y-coordinate representing the "zero" line for bars (important for negative numbers)
        val zeroY = height - paddingVertical - ((0 - minValue) * scaleFactor)

        textPaint.textSize = min(barWidth * 0.4f, 28f) // Adjust text size based on bar width

        // Setup gradient shaders for different bar colors
        setupGradients(height.toFloat())

        // Draw the zero line for reference
        canvas.drawLine(paddingHorizontal, zeroY, width.toFloat() - paddingHorizontal, zeroY, borderPaint)

        numbers.forEachIndexed { index, value ->
            val left = paddingHorizontal + index * barWidth + 2f
            val right = paddingHorizontal + (index + 1) * barWidth - 2f

            // Determine which paint to use based on bar state (swapped, comparing, negative, or normal)
            val paintToUse = when {
                swappedIndex == index -> swappedPaint
                highlightIndices?.let { index == it.first || index == it.second } == true -> comparingPaint
                value < 0 -> negativePaint
                else -> normalPaint
            }

            // Calculate bar top and bottom coordinates, handling both positive and negative values
            val barTop: Float
            val barBottom: Float
            if (value >= 0) {
                barTop = zeroY - (value * scaleFactor)
                barBottom = zeroY
            } else {
                barTop = zeroY
                barBottom = zeroY - (value * scaleFactor)
            }

            val cornerRadius = min(barWidth * 0.2f, 16f) // Rounded corners for bars

            // Create a path for the rounded rectangle bar shape
            val path = Path()
            path.addRoundRect(
                RectF(left, barTop, right, barBottom),
                cornerRadius,
                cornerRadius,
                Path.Direction.CW
            )

            // Apply glow and pulsing animation for highlighted/swapped bars
            val isHighlighted = swappedIndex == index ||
                    highlightIndices?.let { index == it.first || index == it.second } == true

            if (isHighlighted) {
                // Set glow color based on the type of highlight
                glowPaint.color = when {
                    swappedIndex == index -> ContextCompat.getColor(context, R.color.dark_red)
                    else -> ContextCompat.getColor(context, R.color.accent_orange)
                }

                canvas.drawPath(path, glowPaint) // Draw the glow effect

                // Apply a subtle pulsing scale animation
                val pulseScale = 1f + 0.1f * kotlin.math.sin(System.currentTimeMillis() / 200f)
                canvas.save() // Save canvas state before scaling
                canvas.scale(pulseScale, pulseScale, left + barWidth/2, (barTop + barBottom)/2)
                canvas.drawPath(path, paintToUse) // Draw the bar itself
                canvas.restore() // Restore canvas state

                invalidate() // Request continuous redraw for the pulsing animation
            } else {
                canvas.drawPath(path, paintToUse) // Draw the bar without special effects
            }

            canvas.drawPath(path, borderPaint) // Draw the border around the bar

            // Position and draw the number text on top of or below the bar
            val textY = if (value >= 0) {
                maxOf(barTop - 8f, paddingVertical + textPaint.textSize)
            } else {
                minOf(barBottom + textPaint.textSize + 4f, height - paddingVertical)
            }

            canvas.drawText(value.toString(), left + (barWidth / 2), textY, textPaint)
        }
    }

    /** Configures [LinearGradient] shaders for different bar paint objects. */
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
