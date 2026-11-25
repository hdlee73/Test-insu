package io.github.hdlee73.insu.data.source

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import io.github.hdlee73.insu.domain.parser.TextFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class PasswordRequiredException(val wrongPassword: Boolean) : Exception("PDF 암호가 필요합니다")

/** 읽기 결과: 글자 조각(좌표 포함)과 검수 화면용 쪽 미리보기. */
class ReadResult(val fragments: List<TextFragment>, val previews: List<Bitmap>, val usedOcr: Boolean)

/**
 * 증권 파일을 읽어 글자를 얻는다. 모든 처리는 기기 안에서 한다.
 * - PDF: 글자층이 있으면 PdfBox로 좌표와 함께 바로 추출(빠르고 정확), 없으면 쪽을 렌더링해 OCR.
 * - 이미지: 긴 변 [OCR_EDGE]px로 줄여 디코딩한 뒤 OCR.
 * 큰 비트맵은 쪽마다 OCR 후 즉시 해제해서 한 번에 한 쪽만 메모리에 둔다.
 */
class DocumentReader(private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }

    @Volatile
    private var pdfBoxReady = false

    fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "증권"

    suspend fun readImages(uris: List<Uri>, onProgress: (Int, Int) -> Unit): ReadResult = withContext(Dispatchers.Default) {
        val fragments = ArrayList<TextFragment>()
        val previews = ArrayList<Bitmap>()
        uris.take(MAX_PAGES).forEachIndexed { index, uri ->
            coroutineContext.ensureActive()
            onProgress(index + 1, uris.size.coerceAtMost(MAX_PAGES))
            val bitmap = decodeImage(uri, OCR_EDGE)
            try {
                fragments += ocr(bitmap, index)
                previews += scaled(bitmap, PREVIEW_EDGE)
            } finally {
                bitmap.recycle()
            }
        }
        ReadResult(fragments, previews, usedOcr = true)
    }

    suspend fun readPdf(uri: Uri, password: String?, onProgress: (Int, Int) -> Unit): ReadResult = withContext(Dispatchers.IO) {
        ensurePdfBox()
        val dir = File(context.cacheDir, "import").apply { mkdirs() }
        val source = File(dir, "${UUID.randomUUID()}.pdf")
        var decrypted: File? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> source.outputStream().use { input.copyTo(it) } }
                ?: throw IllegalStateException("파일을 열 수 없습니다")

            val doc = try {
                PDDocument.load(source, password ?: "")
            } catch (e: InvalidPasswordException) {
                throw PasswordRequiredException(wrongPassword = password != null)
            }
            val (textFragments, pageCount) = doc.use { d ->
                val count = d.numberOfPages
                val stripper = PositionStripper().apply {
                    setSortByPosition(true)
                    setStartPage(1)
                    setEndPage(min(count, MAX_PAGES))
                }
                stripper.getText(d)
                if (d.isEncrypted) {
                    // PdfRenderer는 암호 PDF를 못 연다. 앱 캐시에 암호를 푼 사본을 잠깐 만들고 끝나면 지운다.
                    d.isAllSecurityToBeRemoved = true
                    decrypted = File(dir, "${UUID.randomUUID()}.pdf").also { d.save(it) }
                }
                stripper.fragments to count
            }
            coroutineContext.ensureActive()

            val renderFile = decrypted ?: source
            val hasTextLayer = textFragments.sumOf { f -> f.text.count { !it.isWhitespace() } } >= MIN_TEXT_CHARS
            val pages = min(pageCount, MAX_PAGES)
            val previews = ArrayList<Bitmap>()
            val fragments = if (hasTextLayer) ArrayList(textFragments) else ArrayList()
            ParcelFileDescriptor.open(renderFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until min(pages, renderer.pageCount)) {
                        coroutineContext.ensureActive()
                        onProgress(i + 1, pages)
                        renderer.openPage(i).use { page ->
                            if (hasTextLayer) {
                                previews += render(page, PREVIEW_EDGE)
                            } else {
                                val big = render(page, OCR_EDGE)
                                try {
                                    fragments += withContext(Dispatchers.Default) { ocr(big, i) }
                                    previews += scaled(big, PREVIEW_EDGE)
                                } finally {
                                    big.recycle()
                                }
                            }
                        }
                    }
                }
            }
            ReadResult(fragments, previews, usedOcr = !hasTextLayer)
        } finally {
            // 증권 원본 사본은 처리 직후 지운다(개인정보 최소 보관).
            source.delete()
            decrypted?.delete()
        }
    }

    private fun ensurePdfBox() {
        if (!pdfBoxReady) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfBoxReady = true
        }
    }

    private suspend fun ocr(bitmap: Bitmap, page: Int): List<TextFragment> {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        return result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val b = line.boundingBox ?: return@mapNotNull null
            TextFragment(line.text, page, b.left / w, b.top / h, b.right / w, b.bottom / h)
        }
    }

    private fun decodeImage(uri: Uri, maxEdge: Int): Bitmap {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        // ImageDecoder는 EXIF 회전을 자동으로 반영한다. ML Kit 입력용으로 소프트웨어 비트맵을 쓴다.
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = min(1f, maxEdge.toFloat() / max(w, h))
            decoder.setTargetSize(max(1, (w * scale).toInt()), max(1, (h * scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun render(page: PdfRenderer.Page, longEdge: Int): Bitmap {
        val scale = longEdge.toFloat() / max(page.width, page.height)
        val bmp = Bitmap.createBitmap(max(1, (page.width * scale).toInt()), max(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE) // 투명 배경이면 OCR이 글자를 놓친다.
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bmp
    }

    private fun scaled(src: Bitmap, longEdge: Int): Bitmap {
        val scale = min(1f, longEdge.toFloat() / max(src.width, src.height))
        if (scale >= 1f) return src.copy(Bitmap.Config.ARGB_8888, false)
        return Bitmap.createScaledBitmap(src, max(1, (src.width * scale).toInt()), max(1, (src.height * scale).toInt()), true)
    }

    /** PdfBox 글자 추출기: 한 줄(writeString 호출)마다 좌표를 기록한다. */
    private class PositionStripper : PDFTextStripper() {
        val fragments = ArrayList<TextFragment>()
        private var pageW = 1f
        private var pageH = 1f

        override fun startPage(page: PDPage?) {
            super.startPage(page)
            val box = page?.cropBox
            pageW = box?.width?.takeIf { it > 0f } ?: 1f
            pageH = box?.height?.takeIf { it > 0f } ?: 1f
        }

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            if (text.isNullOrBlank() || textPositions.isNullOrEmpty()) return
            var l = Float.MAX_VALUE
            var t = Float.MAX_VALUE
            var r = 0f
            var b = 0f
            for (p in textPositions) {
                l = min(l, p.xDirAdj)
                r = max(r, p.xDirAdj + p.widthDirAdj)
                t = min(t, p.yDirAdj - p.heightDir)
                b = max(b, p.yDirAdj)
            }
            fragments += TextFragment(
                text, currentPageNo - 1,
                (l / pageW).coerceIn(0f, 1f), (t / pageH).coerceIn(0f, 1f),
                (r / pageW).coerceIn(0f, 1f), (b / pageH).coerceIn(0f, 1f),
            )
        }
    }

    private companion object {
        const val MAX_PAGES = 10
        const val OCR_EDGE = 2000
        const val PREVIEW_EDGE = 1100
        const val MIN_TEXT_CHARS = 40
    }
}
