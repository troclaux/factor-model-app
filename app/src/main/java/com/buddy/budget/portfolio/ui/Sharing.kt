package com.buddy.budget.portfolio.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.core.content.FileProvider
import com.buddy.budget.portfolio.model.AnalysisResult
import com.buddy.budget.portfolio.model.PortfolioSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.buddy.budget.i18n.tr

/** Records what the modifier's content draws into [layer] so it can be exported later. */
fun Modifier.recordInto(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Portfolio analysis", text))
    Toast.makeText(context, tr("Copied to clipboard"), Toast.LENGTH_SHORT).show()
}

/** Shares the text as a .txt file attachment only. */
fun shareText(context: Context, text: String, fileName: String) {
    val file = sharedFile(context, fileName).apply { writeText(text) }
    shareFile(context, file, "text/plain", tr("Share analysis"))
}

/** Writes the styled PDF report off the main thread, then shares it. */
suspend fun sharePdf(
    context: Context,
    result: AnalysisResult,
    settings: PortfolioSettings,
    riskAversion: Double?,
    capital: Double?,
    fileName: String,
) {
    val file = sharedFile(context, fileName)
    withContext(Dispatchers.Default) { PdfReport(context, result, settings, riskAversion, capital).writeTo(file) }
    shareFile(context, file, "application/pdf", tr("Share PDF report"))
}

fun shareFile(context: Context, file: File, mimeType: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

/** Shares a chart as a PNG. The chart is drawn over [background] since the on-screen canvas is transparent. */
fun shareImage(context: Context, image: ImageBitmap, background: Int, fileName: String) {
    val source = image.asAndroidBitmap()
    val opaque = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    Canvas(opaque).apply {
        drawColor(background)
        drawBitmap(source, 0f, 0f, null)
    }
    val file = sharedFile(context, fileName)
    file.outputStream().use { opaque.compress(Bitmap.CompressFormat.PNG, 100, it) }
    shareFile(context, file, "image/png", tr("Share chart"))
}

/** A file in the cache folder that FileProvider shares. */
fun sharedFile(context: Context, fileName: String) =
    File(context.cacheDir, "shared").apply { mkdirs() }.resolve(fileName)

private fun uriFor(context: Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
