package com.example.data.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

object PdfAndShareManager {

    fun copyText(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun shareText(context: Context, subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Generates a multi-page A4 PDF from structured statement lines using android.graphics.pdf.PdfDocument.
     */
    fun generateStatementPdfFile(
        context: Context,
        fileName: String,
        title: String,
        subtitle: String,
        bodyText: String
    ): File {
        val pdfDir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val outFile = File(pdfDir, if (safeName.endsWith(".pdf")) safeName else "$safeName.pdf")

        val pageWidth = 595 // A4 points
        val pageHeight = 842
        val margin = 40f
        val contentWidth = pageWidth - 2 * margin

        val headerBgPaint = Paint().apply {
            color = "#0F172A".toColorInt()
            style = Paint.Style.FILL
        }
        val goldAccentPaint = Paint().apply {
            color = "#F59E0B".toColorInt()
            style = Paint.Style.FILL
        }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = "#CBD5E1".toColorInt()
            textSize = 11f
            typeface = Typeface.SANS_SERIF
            isAntiAlias = true
        }
        val sectionPaint = Paint().apply {
            color = "#1E293B".toColorInt()
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = "#0F172A".toColorInt()
            textSize = 10.5f
            typeface = Typeface.SANS_SERIF
            isAntiAlias = true
        }
        val dividerPaint = Paint().apply {
            color = "#CBD5E1".toColorInt()
            strokeWidth = 1f
        }
        val footerPaint = Paint().apply {
            color = "#64748B".toColorInt()
            textSize = 9f
            isAntiAlias = true
        }

        val wrappedLines = wrapLines(bodyText, bodyPaint, contentWidth)
        val nativeResult = runCatching {
            val document = PdfDocument()
            try {
                var lineIndex = 0
                var pageNumber = 1

                while (lineIndex < wrappedLines.size || pageNumber == 1) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    val page = document.startPage(pageInfo)
                    val canvas: Canvas = page.canvas

                    // Page Header
                    canvas.drawRect(0f, 0f, pageWidth.toFloat(), 84f, headerBgPaint)
                    canvas.drawRect(0f, 84f, pageWidth.toFloat(), 88f, goldAccentPaint)
                    canvas.drawText(title, margin, 36f, titlePaint)
                    canvas.drawText(subtitle, margin, 58f, subtitlePaint)

                    var y = 112f
                    val bottomLimit = pageHeight - 48f

                    while (lineIndex < wrappedLines.size && y < bottomLimit) {
                        val line = wrappedLines[lineIndex]
                        when {
                            line.startsWith("════") || line.startsWith("────") -> {
                                canvas.drawLine(margin, y - 4f, pageWidth - margin, y - 4f, dividerPaint)
                                y += 10f
                            }
                            line.endsWith(":") && !line.startsWith(" ") -> {
                                canvas.drawText(line, margin, y, sectionPaint)
                                y += 17f
                            }
                            else -> {
                                canvas.drawText(line, margin, y, bodyPaint)
                                y += 15f
                            }
                        }
                        lineIndex++
                    }

                    // Footer
                    canvas.drawLine(margin, pageHeight - 36f, pageWidth - margin, pageHeight - 36f, dividerPaint)
                    canvas.drawText("CREDIT LOGGER • Page $pageNumber", margin, pageHeight - 20f, footerPaint)

                    document.finishPage(page)
                    pageNumber++
                }

                FileOutputStream(outFile).use { fos ->
                    document.writeTo(fos)
                }
            } finally {
                document.close()
            }
        }

        if (nativeResult.isFailure) {
            writeSimplePdfFallback(outFile, title, subtitle, bodyText)
        }
        return outFile
    }

    private fun writeSimplePdfFallback(outFile: File, title: String, subtitle: String, bodyText: String) {
        val cleanLines = listOf(title, subtitle, "----------------------------------------") + bodyText.lines()
        val streamContent = buildString {
            appendLine("BT")
            appendLine("/F1 11 Tf")
            var y = 780
            for (line in cleanLines.take(48)) {
                val safe = line
                    .replace("\\", "\\\\")
                    .replace("(", "\\(")
                    .replace(")", "\\)")
                    .replace(Regex("[^\\x20-\\x7E]"), "?")
                appendLine("1 0 0 1 40 $y Tm ($safe) Tj")
                y -= 15
                if (y < 40) break
            }
            appendLine("ET")
        }
        val objs = listOf(
            "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n",
            "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n",
            "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n",
            "4 0 obj << /Length ${streamContent.toByteArray().size} >> stream\n$streamContent\nendstream endobj\n",
            "5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n"
        )
        val pdf = buildString {
            append("%PDF-1.4\n")
            val offsets = mutableListOf(0)
            for (o in objs) {
                offsets.add(length)
                append(o)
            }
            val xrefOffset = length
            append("xref\n0 ${objs.size + 1}\n0000000000 65535 f \n")
            for (i in 1..objs.size) {
                append(String.format(Locale.US, "%010d 00000 n \n", offsets[i]))
            }
            append("trailer << /Size ${objs.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        }
        outFile.writeText(pdf, Charsets.UTF_8)
    }

    fun sharePdfFile(context: Context, file: File, chooserTitle: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun writeFileToUri(context: Context, sourceFile: File, targetUri: Uri) {
        context.contentResolver.openOutputStream(targetUri)?.use { output ->
            FileInputStream(sourceFile).use { input ->
                input.copyTo(output)
            }
        }
    }

    fun writeStringToUri(context: Context, content: String, targetUri: Uri) {
        context.contentResolver.openOutputStream(targetUri)?.use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    fun readStringFromUri(context: Context, uri: Uri, maxBytes: Int = JsonBackupManager.MAX_IMPORT_BYTES): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= maxBytes) { "File is larger than 10 MB limit." }
            return String(bytes, Charsets.UTF_8)
        } ?: throw IllegalStateException("Unable to open selected file")
    }

    fun printPdfFile(context: Context, file: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                val fd = destination?.fileDescriptor
                if (fd == null) {
                    callback?.onWriteFailed("Invalid print destination")
                    return
                }
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(fd).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }

    private fun wrapLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        for (rawLine in text.lines()) {
            if (rawLine.isEmpty()) {
                result.add("")
                continue
            }
            var remaining = rawLine
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, maxWidth, null)
                if (count <= 0) break
                if (count >= remaining.length) {
                    result.add(remaining)
                    remaining = ""
                } else {
                    val spaceIdx = remaining.lastIndexOf(' ', count - 1)
                    val splitAt = if (spaceIdx > 0) spaceIdx else count
                    result.add(remaining.substring(0, splitAt).trimEnd())
                    remaining = remaining.substring(splitAt).trimStart()
                }
            }
        }
        return result
    }
}
