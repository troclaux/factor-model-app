package com.buddy.budget.portfolio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddy.budget.portfolio.model.AnalysisResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

val TangencyColor = Color(0xFFE53935)
val CmlColor = Color(0xFFFB8C00)
val CurrentColor = Color(0xFF8E24AA)
val HypotheticalColor = Color(0xFF00897B)

fun shortTicker(ticker: String) = ticker.removeSuffix(".SA")

/** Colors the charts are drawn with: the theme's on screen, fixed print colors in the PDF. */
data class ChartPalette(
    val frontier: Color,
    val asset: Color,
    val grid: Color,
    val label: Color,
    val text: Color,
    val neutral: Color,
)

@Composable
fun themeChartPalette() = ChartPalette(
    frontier = MaterialTheme.colorScheme.primary,
    asset = MaterialTheme.colorScheme.onSurfaceVariant,
    grid = MaterialTheme.colorScheme.outlineVariant,
    label = MaterialTheme.colorScheme.onSurfaceVariant,
    text = MaterialTheme.colorScheme.onSurface,
    neutral = MaterialTheme.colorScheme.surfaceVariant,
)

/** Efficient frontier, CML, assets, tangency point and the user's portfolios on the risk-return plane. */
@Composable
fun MeanVarianceChart(result: AnalysisResult, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val palette = themeChartPalette()
    Canvas(modifier = modifier.fillMaxWidth().height(320.dp)) { drawMeanVariance(result, measurer, palette) }
}

fun DrawScope.drawMeanVariance(result: AnalysisResult, measurer: TextMeasurer, palette: ChartPalette) {
    val frontierColor = palette.frontier
    val assetColor = palette.asset
    val gridColor = palette.grid
    val labelStyle = TextStyle(fontSize = 10.sp, color = palette.label)
    val pointLabelStyle = labelStyle.copy(color = palette.text)

    val tangency = result.tangency.stats
    val portfolios = listOfNotNull(result.current, result.hypothetical).map { it.stats }
    val pointVols = result.volatilities.toList() + tangency.volatility + portfolios.map { it.volatility }
    val pointRets = result.expectedReturns.toList() + tangency.expectedReturn + portfolios.map { it.expectedReturn } + result.riskFreeRate
    val xMax = pointVols.max() * 1.15
    // Fit the frontier where it is inside the horizontal range, without letting it blow up the vertical one.
    val visibleFrontier = result.frontier.returns.indices.filter { result.frontier.volatilities[it] <= xMax }
    val yValues = pointRets + visibleFrontier.map { result.frontier.returns[it] }
    val yPadding = (yValues.max() - yValues.min()) * 0.08
    val yMin = yValues.min() - yPadding
    val yMax = yValues.max() + yPadding

    val left = 44.dp.toPx()
    val bottom = 28.dp.toPx()
    val top = 8.dp.toPx()
    val right = 8.dp.toPx()
    val plot = Rect(left, top, size.width - right, size.height - bottom)
    fun x(vol: Double) = plot.left + (vol / xMax * plot.width).toFloat()
    fun y(ret: Double) = plot.bottom - ((ret - yMin) / (yMax - yMin) * plot.height).toFloat()

    // Grid and axis labels.
    for (step in 0..4) {
        val vol = xMax * step / 4
        val ret = yMin + (yMax - yMin) * step / 4
        drawLine(gridColor, Offset(x(vol), plot.top), Offset(x(vol), plot.bottom), 1f)
        drawLine(gridColor, Offset(plot.left, y(ret)), Offset(plot.right, y(ret)), 1f)
        val xLabel = measurer.measure("%.0f%%".format(vol * 100), labelStyle)
        drawText(xLabel, topLeft = Offset(x(vol) - xLabel.size.width / 2, plot.bottom + 4.dp.toPx()))
        val yLabel = measurer.measure("%.0f%%".format(ret * 100), labelStyle)
        drawText(yLabel, topLeft = Offset(plot.left - yLabel.size.width - 4.dp.toPx(), y(ret) - yLabel.size.height / 2))
    }

    clipRect(plot.left, plot.top, plot.right, plot.bottom) {
        // Frontier: solid above the minimum-variance point (efficient), dashed below.
        val vols = result.frontier.volatilities
        val rets = result.frontier.returns
        val minVarianceIndex = vols.indices.minBy { vols[it] }
        val inefficient = Path()
        val efficient = Path()
        for (index in vols.indices) {
            val point = Offset(x(vols[index]), y(rets[index]))
            if (index <= minVarianceIndex) {
                if (index == 0) inefficient.moveTo(point.x, point.y) else inefficient.lineTo(point.x, point.y)
            }
            if (index >= minVarianceIndex) {
                if (index == minVarianceIndex) efficient.moveTo(point.x, point.y) else efficient.lineTo(point.x, point.y)
            }
        }
        drawPath(inefficient, frontierColor, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        drawPath(efficient, frontierColor, style = Stroke(3.dp.toPx()))

        // Capital Market Line from the risk-free rate through the tangency portfolio.
        drawLine(
            CmlColor,
            Offset(x(0.0), y(result.riskFreeRate)),
            Offset(x(xMax), y(result.riskFreeRate + tangency.sharpeRatio * xMax)),
            strokeWidth = 2.dp.toPx(),
        )
    }

    val placed = mutableListOf<Rect>()
    val labelled = result.tickers.indices.map { index ->
        Triple(shortTicker(result.tickers[index]), Offset(x(result.volatilities[index]), y(result.expectedReturns[index])), assetColor)
    }
    for ((_, center, color) in labelled) drawCircle(color, 4.dp.toPx(), center)
    drawCircle(CmlColor, 5.dp.toPx(), Offset(x(0.0), y(result.riskFreeRate)))
    result.current?.let { drawSquare(CurrentColor, Offset(x(it.stats.volatility), y(it.stats.expectedReturn)), 6.dp.toPx()) }
    result.hypothetical?.let { drawDiamond(HypotheticalColor, Offset(x(it.stats.volatility), y(it.stats.expectedReturn)), 7.dp.toPx()) }
    val tangencyCenter = Offset(x(tangency.volatility), y(tangency.expectedReturn))
    drawCircle(TangencyColor, 7.dp.toPx(), tangencyCenter)
    drawCircle(Color.White, 7.dp.toPx(), tangencyCenter, style = Stroke(2.dp.toPx()))

    // Ticker labels: try right, left, above, below; keep the first spot that does not overlap.
    for ((label, center, _) in labelled) {
        placeLabel(measurer, label, pointLabelStyle, center, placed, plot)
    }
}

private fun DrawScope.drawSquare(color: Color, center: Offset, half: Float) =
    drawRect(color, Offset(center.x - half, center.y - half), Size(half * 2, half * 2))

private fun DrawScope.drawDiamond(color: Color, center: Offset, half: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y - half)
        lineTo(center.x + half, center.y)
        lineTo(center.x, center.y + half)
        lineTo(center.x - half, center.y)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.placeLabel(
    measurer: TextMeasurer,
    label: String,
    style: TextStyle,
    center: Offset,
    placed: MutableList<Rect>,
    bounds: Rect,
) {
    val layout = measurer.measure(label, style)
    val width = layout.size.width.toFloat()
    val height = layout.size.height.toFloat()
    val gap = 6.dp.toPx()
    val candidates = listOf(
        Offset(center.x + gap, center.y - height / 2),
        Offset(center.x - gap - width, center.y - height / 2),
        Offset(center.x - width / 2, center.y - gap - height),
        Offset(center.x - width / 2, center.y + gap),
    ).map { Rect(it, Size(width, height)) }
    val inside = candidates.filter { it.left >= bounds.left && it.right <= size.width && it.top >= 0 && it.bottom <= bounds.bottom }
    val choice = inside.firstOrNull { candidate -> placed.none { it.overlaps(candidate) } } ?: return
    placed += choice
    drawText(layout, topLeft = choice.topLeft)
}

/** Pearson correlation heatmap; scrolls sideways when there are many tickers. */
@Composable
fun CorrelationHeatmap(result: AnalysisResult, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val palette = themeChartPalette()
    val count = result.tickers.size

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelSpace = 52.dp
        val cell = max(36f, (maxWidth - labelSpace).value / count).dp
        val totalWidth = labelSpace + cell * count
        Canvas(
            Modifier
                .horizontalScroll(rememberScrollState())
                .width(totalWidth)
                .height(labelSpace + cell * count)
                .then(modifier)
        ) {
            drawCorrelation(result, measurer, palette, labelSpace.toPx(), cell.toPx())
        }
    }
}

/** Heatmap cells of [cell] px with ticker labels in a [labelSpace] px margin on the top and left. */
fun DrawScope.drawCorrelation(result: AnalysisResult, measurer: TextMeasurer, palette: ChartPalette, labelSpace: Float, cell: Float) {
    val labelStyle = TextStyle(fontSize = 10.sp, color = palette.label)
    val neutral = palette.neutral
    val neutralText = palette.label
    val negative = Color(0xFF1E88E5)
    val positive = Color(0xFFE53935)
    val labels = result.tickers.map(::shortTicker)
    val count = labels.size
    val offset = labelSpace
    val side = cell
    for (row in 0 until count) {
        val rowLabel = measurer.measure(labels[row], labelStyle)
        drawText(rowLabel, topLeft = Offset(offset - rowLabel.size.width - 4.dp.toPx(), offset + side * row + (side - rowLabel.size.height) / 2))
        val columnLabel = measurer.measure(labels[row], labelStyle)
        val anchor = Offset(offset + side * row + side / 2, offset - 4.dp.toPx())
        rotate(-90f, anchor) {
            drawText(columnLabel, topLeft = Offset(anchor.x, anchor.y - columnLabel.size.height / 2))
        }
        for (col in 0 until count) {
            val value = result.correlation[row][col]
            val tint = lerp(neutral, if (value >= 0) positive else negative, min(1.0, abs(value)).toFloat())
            val topLeft = Offset(offset + side * col, offset + side * row)
            drawRect(tint, topLeft, Size(side - 1, side - 1))
            val text = measurer.measure("%.2f".format(value), TextStyle(fontSize = 9.sp, color = if (abs(value) > 0.6) Color.White else neutralText))
            drawText(text, topLeft = Offset(topLeft.x + (side - text.size.width) / 2, topLeft.y + (side - text.size.height) / 2))
        }
    }
}
