package com.buddy.budget.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.File
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

/**
 * A styled, paginated A4 PDF (units are points): green header band, section titles, paragraphs,
 * stat tiles, zebra tables, pie charts and Compose drawings, with a footer and page numbers.
 */
abstract class PdfWriter(context: Context, private val footerTitle: String) {
    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    protected lateinit var canvas: Canvas
    private var pageNumber = 0
    protected var cursor = 0f

    // Density 1 makes one dp (and one sp) equal one PDF point, so on-screen drawings keep their proportions.
    protected val measurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)

    /** Writes the content between [startPage] and the last page, then saves and closes the document. */
    protected fun finish(file: File) {
        finishPage()
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }

    /** The green band at the top of the first page. */
    protected fun header(title: String, subtitle: String) {
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), HEADER_HEIGHT, fill(ACCENT))
        canvas.drawText(title, MARGIN, 46f, text(22f, WHITE, bold = true))
        canvas.drawText(subtitle, MARGIN, 68f, text(10f, ACCENT_LIGHT))
        cursor = HEADER_HEIGHT + 24f
    }

    /**
     * A pie of [slices] (label to value; non-positive values are left out) with every slice listed
     * in a legend of labels and percentages beside it, largest first. [colorOf] picks each label's color;
     * pass a fixed mapping to keep a label the same color across several pies.
     */
    protected fun pie(slices: List<Pair<String, Double>>, colorOf: (String) -> Int = defaultPieColors(slices)) {
        val shown = slices.filter { it.second > 0 }.sortedByDescending { it.second }
        if (shown.isEmpty()) return
        val total = shown.sumOf { it.second }
        val diameter = 170f
        val legendRow = 15f
        val height = maxOf(diameter, legendRow * shown.size)
        ensure(height + 10f)
        val oval = RectF(MARGIN + 10f, cursor, MARGIN + 10f + diameter, cursor + diameter)
        var angle = -90f
        shown.forEach { (label, value) ->
            val sweep = (value / total * 360).toFloat()
            canvas.drawArc(oval, angle, sweep, true, fill(colorOf(label)))
            angle += sweep
        }
        // White separators between slices.
        val separator = stroke(WHITE).apply { strokeWidth = 1.5f; style = Paint.Style.STROKE }
        canvas.drawOval(oval, separator)
        val legendLeft = MARGIN + diameter + 40f
        var top = cursor + (height - legendRow * shown.size) / 2
        shown.forEach { (label, value) ->
            canvas.drawRoundRect(RectF(legendLeft, top + 2f, legendLeft + 10f, top + 12f), 2f, 2f, fill(colorOf(label)))
            canvas.drawText(label, legendLeft + 16f, top + 11f, text(9f, TEXT))
            val percent = text(9f, MUTED, bold = true).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("%.1f%%".format(value / total * 100), MARGIN + CONTENT_WIDTH, top + 11f, percent)
            top += legendRow
        }
        cursor += height + 10f
    }

    // ---------- layout helpers ----------

    protected fun startPage() {
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        canvas = page!!.canvas
        cursor = MARGIN
    }

    protected fun finishPage() {
        val footer = text(8f, MUTED)
        val baseline = PAGE_HEIGHT - 24f
        canvas.drawLine(MARGIN, baseline - 12f, MARGIN + CONTENT_WIDTH, baseline - 12f, stroke(RULE))
        canvas.drawText(footerTitle, MARGIN, baseline, footer)
        footer.textAlign = Paint.Align.RIGHT
        canvas.drawText("Page $pageNumber", MARGIN + CONTENT_WIDTH, baseline, footer)
        document.finishPage(page)
    }

    /** Starts a new page when [height] points do not fit above the footer. */
    protected fun ensure(height: Float) {
        if (cursor + height > PAGE_HEIGHT - FOOTER_SPACE) {
            finishPage()
            startPage()
        }
    }

    protected fun section(title: String) {
        // Keep the title with at least the first lines of its content.
        ensure(80f)
        cursor += 14f
        canvas.drawRect(MARGIN, cursor, MARGIN + 4f, cursor + 18f, fill(ACCENT))
        canvas.drawText(title, MARGIN + 12f, cursor + 14f, text(14f, TEXT, bold = true))
        cursor += 30f
    }

    protected fun paragraph(content: String, size: Float = 9f, color: Int = MUTED, bold: Boolean = false) {
        val layout = staticLayout(content, textPaint(size, color, bold), CONTENT_WIDTH)
        ensure(layout.height.toFloat())
        drawLayout(layout, MARGIN, cursor)
        cursor += layout.height + 6f
    }

    protected fun statTiles(tiles: List<Pair<String, String>>) {
        val perRow = 3
        val gap = 10f
        val width = (CONTENT_WIDTH - gap * (perRow - 1)) / perRow
        val height = 48f
        tiles.chunked(perRow).forEach { row ->
            ensure(height + gap)
            row.forEachIndexed { index, (label, value) ->
                val left = MARGIN + index * (width + gap)
                canvas.drawRoundRect(RectF(left, cursor, left + width, cursor + height), 8f, 8f, fill(ACCENT_LIGHT))
                canvas.drawText(label, left + 10f, cursor + 17f, text(8f, MUTED))
                canvas.drawText(value, left + 10f, cursor + 37f, text(15f, TEXT, bold = true))
            }
            cursor += height + gap
        }
    }

    /**
     * A table with a shaded header (repeated on each new page), zebra rows and right-aligned numbers.
     * Without headers it is a key/value list: values are left-aligned so long text has room.
     */
    protected fun table(headers: List<String>?, rows: List<List<String>>, highlightColumn: Int? = null) {
        val columns = headers?.size ?: rows.first().size
        val keyValue = headers == null
        val firstWidth = CONTENT_WIDTH * if (keyValue) 0.3f else if (columns <= 2) 0.4f else 0.28f
        val otherWidth = (CONTENT_WIDTH - firstWidth) / (columns - 1)
        fun columnLeft(index: Int) = MARGIN + if (index == 0) 0f else firstWidth + otherWidth * (index - 1)
        fun drawRow(cells: List<String>, background: Int?, bold: Boolean, color: Int) {
            background?.let { canvas.drawRect(MARGIN, cursor, MARGIN + CONTENT_WIDTH, cursor + ROW_HEIGHT, fill(it)) }
            cells.forEachIndexed { index, cell ->
                val emphasized = bold || index == highlightColumn
                val paint = text(9f, if (index == highlightColumn && !bold) ACCENT else color, bold = emphasized)
                if (index == 0 || keyValue) {
                    canvas.drawText(cell, columnLeft(index) + 6f, cursor + 12f, paint)
                } else {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(cell, columnLeft(index) + otherWidth - 6f, cursor + 12f, paint)
                }
            }
            cursor += ROW_HEIGHT
        }
        fun drawHeader() {
            if (headers != null) drawRow(headers, ACCENT_LIGHT, bold = true, color = TEXT)
        }

        ensure(ROW_HEIGHT * 2)
        drawHeader()
        rows.forEachIndexed { index, row ->
            if (cursor + ROW_HEIGHT > PAGE_HEIGHT - FOOTER_SPACE) {
                finishPage()
                startPage()
                drawHeader()
            }
            drawRow(row, if (index % 2 == 1) ZEBRA else null, bold = false, color = TEXT)
        }
        canvas.drawLine(MARGIN, cursor, MARGIN + CONTENT_WIDTH, cursor, stroke(RULE))
        cursor += 12f
    }

    /** Runs a Compose drawing (the same code as the on-screen charts) on the PDF page. */
    protected fun drawing(height: Float, block: DrawScope.() -> Unit) {
        ensure(height)
        canvas.save()
        canvas.translate(MARGIN, cursor)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, ComposeCanvas(canvas), Size(CONTENT_WIDTH, height), block)
        canvas.restore()
        cursor += height
    }

    protected fun staticLayout(content: String, paint: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(content, 0, content.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1f)
            .build()

    protected fun drawLayout(layout: StaticLayout, left: Float, top: Float) {
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }

    protected fun text(size: Float, color: Int, bold: Boolean = false) = textPaint(size, color, bold)

    protected fun textPaint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    protected fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    protected fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = 0.75f
    }

    protected fun money(value: Double) = "%,.2f".format(value)

    companion object {
        const val PAGE_WIDTH = 595 // A4 in points
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val HEADER_HEIGHT = 90f
        const val FOOTER_SPACE = 52f
        const val ROW_HEIGHT = 17f

        // The app's green theme, on white paper.
        const val ACCENT = 0xFF388E3C.toInt()
        const val ACCENT_LIGHT = 0xFFE8F5E9.toInt()
        const val TEXT = 0xFF212121.toInt()
        const val MUTED = 0xFF616161.toInt()
        const val RULE = 0xFFE0E0E0.toInt()
        const val ZEBRA = 0xFFF7F7F7.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val ERROR = 0xFFD32F2F.toInt()
        const val WARNING_BACKGROUND = 0xFFFFF3E0.toInt()
        const val WARNING_TEXT = 0xFFBF360C.toInt()

        /** Distinct, print-friendly slice colors; neighbors contrast so adjacent slices stay apart. */
        val PIE_COLORS = intArrayOf(
            0xFF388E3C.toInt(), 0xFF1E88E5.toInt(), 0xFFFB8C00.toInt(), 0xFF8E24AA.toInt(), 0xFFE53935.toInt(),
            0xFF00897B.toInt(), 0xFFFDD835.toInt(), 0xFF6D4C41.toInt(), 0xFF3949AB.toInt(), 0xFF9E9E9E.toInt(),
            0xFF7CB342.toInt(), 0xFF64B5F6.toInt(), 0xFFFFB74D.toInt(), 0xFFBA68C8.toInt(), 0xFFF06292.toInt(),
            0xFF4DB6AC.toInt(), 0xFFAFB42B.toInt(), 0xFFA1887F.toInt(), 0xFF7986CB.toInt(), 0xFF455A64.toInt(),
        )

        /** Colors by size rank: the largest slice gets the first palette color. */
        fun defaultPieColors(slices: List<Pair<String, Double>>): (String) -> Int {
            val ranks = slices.filter { it.second > 0 }.sortedByDescending { it.second }.withIndex().associate { it.value.first to it.index }
            return { label -> pieColor(ranks[label] ?: 0) }
        }

        /** The palette first, then hues spread by the golden angle so even long lists get distinct colors. */
        fun pieColor(index: Int): Int =
            if (index < PIE_COLORS.size) {
                PIE_COLORS[index]
            } else {
                android.graphics.Color.HSVToColor(floatArrayOf((index * 137.508f) % 360f, 0.55f, 0.8f))
            }
    }
}
