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
import java.io.File

/** Records what the modifier's content draws into [layer] so it can be exported later. */
fun Modifier.recordInto(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Portfolio analysis", text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

/** Shares the text both inline and as a .txt attachment, so it can go to a chat or be saved as a file. */
fun shareText(context: Context, text: String, fileName: String) {
    val file = sharedFile(context, fileName).apply { writeText(text) }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share analysis"))
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
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share chart"))
}

private fun sharedFile(context: Context, fileName: String) =
    File(context.cacheDir, "shared").apply { mkdirs() }.resolve(fileName)

private fun uriFor(context: Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
