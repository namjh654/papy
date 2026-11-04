package io.github.namjh654.papy.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import io.github.namjh654.papy.feature.documents.Caret
import io.github.namjh654.papy.feature.documents.CaretMove
import io.github.namjh654.papy.feature.documents.DocumentEngine
import io.github.namjh654.papy.feature.documents.DocumentError
import io.github.namjh654.papy.feature.documents.DocumentKind
import io.github.namjh654.papy.feature.documents.EditResult
import io.github.namjh654.papy.feature.documents.OpenResult
import io.github.namjh654.papy.feature.documents.OpenedDocument
import io.github.namjh654.papy.feature.documents.PageBox
import io.github.namjh654.papy.feature.documents.PageSizePt
import io.github.namjh654.papy.feature.documents.ParagraphHit
import io.github.namjh654.papy.feature.documents.TextPos
import io.github.namjh654.papy.feature.documents.TextRange
import io.github.namjh654.papy.feature.documents.TextStyle
import io.github.namjh654.papy.feature.documents.UndoResult
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import uniffi.papy_ffi.CaretDirection
import uniffi.papy_ffi.EngineCaret
import uniffi.papy_ffi.EngineEdit
import uniffi.papy_ffi.EnginePosition
import uniffi.papy_ffi.EngineRange
import uniffi.papy_ffi.EngineTextStyle
import uniffi.papy_ffi.PageRegion
import uniffi.papy_ffi.PapyEngine
import uniffi.papy_ffi.PapyException

/** 문서 하나의 최대 크기(엔진 상한과 같은 1 GiB 보다 작게: 휴대폰 메모리) */
private const val MAX_BYTES = 256L * 1024 * 1024

/**
 * Android 문서 엔진: HWP·HWPX·DOCX 는 Rust 엔진(UniFFI), PDF 는 운영체제 PdfRenderer(ADR 0006).
 * 문서 내용·경로는 기록하지 않는다.
 */
class AndroidDocumentEngine(private val context: Context) : DocumentEngine {

    /** Rust 엔진은 처음 쓸 때 만든다(라이브러리 로드·글꼴 복사) */
    private val rust: PapyEngine by lazy { PapyEngine(koreanFont()?.absolutePath) }

    private class Pdf(val file: ParcelFileDescriptor, val renderer: PdfRenderer)

    /** PDF 세션: Rust 세션 번호와 겹치지 않게 큰 수부터 */
    private val pdfs = ConcurrentHashMap<Long, Pdf>()
    private val nextPdf = AtomicLong(1L shl 62)

    override fun open(ref: String, password: String?): OpenResult {
        val uri = Uri.parse(ref)
        val head = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ByteArray(8).let { buffer -> buffer.copyOf(input.read(buffer).coerceAtLeast(0)) }
            } ?: return OpenResult.failure(DocumentError.Unreadable)
        } catch (_: Exception) {
            return OpenResult.failure(DocumentError.Unreadable)
        }
        return if (head.decodeToString().startsWith("%PDF-")) openPdf(uri, password) else openRust(uri, password)
    }

    private fun openRust(uri: Uri, password: String?): OpenResult {
        val bytes = try {
            val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (size > MAX_BYTES) return OpenResult.failure(DocumentError.TooLarge)
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return OpenResult.failure(DocumentError.Unreadable)
        } catch (_: OutOfMemoryError) {
            return OpenResult.failure(DocumentError.TooLarge)
        } catch (_: Exception) {
            return OpenResult.failure(DocumentError.Unreadable)
        }
        if (bytes.size > MAX_BYTES) return OpenResult.failure(DocumentError.TooLarge)
        return try {
            val summary = rust.open(bytes, password)
            val kind = when (summary.format) {
                "hwp" -> DocumentKind.Hwp
                "hwpx" -> DocumentKind.Hwpx
                else -> DocumentKind.Docx
            }
            OpenResult.success(
                OpenedDocument(
                    session = summary.session.toLong(),
                    kind = kind,
                    pageCount = summary.pageCount.toInt(),
                    title = summary.title,
                    droppedObjects = summary.droppedObjects.toInt(),
                    canEdit = summary.canEdit,
                ),
            )
        } catch (error: PapyException) {
            OpenResult.failure(error.toDocumentError())
        } catch (_: Exception) {
            OpenResult.failure(DocumentError.Internal)
        }
    }

    private fun openPdf(uri: Uri, password: String?): OpenResult {
        val file = try {
            context.contentResolver.openFileDescriptor(uri, "r") ?: return OpenResult.failure(DocumentError.Unreadable)
        } catch (_: Exception) {
            return OpenResult.failure(DocumentError.Unreadable)
        }
        val renderer = try {
            if (password != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                PdfRenderer(file, LoadParams.Builder().setPassword(password).build())
            } else {
                PdfRenderer(file)
            }
        } catch (_: SecurityException) {
            file.close()
            // 암호 PDF 는 Android 15 부터 열 수 있다.
            return OpenResult.failure(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) DocumentError.PasswordRequired else DocumentError.Unsupported,
            )
        } catch (_: Exception) {
            file.close()
            return OpenResult.failure(DocumentError.Invalid)
        }
        val session = nextPdf.getAndIncrement()
        pdfs[session] = Pdf(file, renderer)
        return OpenResult.success(
            OpenedDocument(session = session, kind = DocumentKind.Pdf, pageCount = renderer.pageCount, title = null, droppedObjects = 0),
        )
    }

    override fun pageSize(session: Long, page: Int): PageSizePt? {
        pdfs[session]?.let { pdf ->
            return synchronized(pdf) {
                if (page !in 0 until pdf.renderer.pageCount) return null
                // PdfRenderer 크기 단위는 1/72 인치(pt)
                pdf.renderer.openPage(page).use { PageSizePt(it.width.toFloat(), it.height.toFloat()) }
            }
        }
        return try {
            val size = rust.pageSize(session.toULong(), page.toUInt())
            PageSizePt(size.width, size.height)
        } catch (_: Exception) {
            null
        }
    }

    /** 쪽을 [widthPx] 너비로 그린다(흰 배경). 실패하면 null. 백그라운드 스레드에서 부른다. */
    fun render(session: Long, page: Int, widthPx: Int): Bitmap? {
        pdfs[session]?.let { pdf ->
            return synchronized(pdf) {
                if (page !in 0 until pdf.renderer.pageCount) return null
                pdf.renderer.openPage(page).use { pdfPage ->
                    val height = (widthPx.toFloat() * pdfPage.height / pdfPage.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        bitmap.eraseColor(Color.WHITE)
                        pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
        return try {
            val size = rust.pageSize(session.toULong(), page.toUInt())
            val scale = widthPx / size.width.coerceAtLeast(1f)
            val image = rust.renderPage(session.toULong(), page.toUInt(), scale)
            // ARGB_8888 의 메모리 배치는 R,G,B,A 바이트 순서라 엔진 RGBA 를 그대로 넣는다.
            Bitmap.createBitmap(image.width.toInt(), image.height.toInt(), Bitmap.Config.ARGB_8888).also {
                it.copyPixelsFromBuffer(ByteBuffer.wrap(image.rgba))
            }
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    override fun pageText(session: Long, page: Int): String? {
        pdfs[session]?.let { pdf ->
            // PDF 글자 읽기는 Android 15 부터
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
            return synchronized(pdf) {
                if (page !in 0 until pdf.renderer.pageCount) return null
                pdf.renderer.openPage(page).use { pdfPage -> pdfPage.textContents.joinToString("\n") { it.text } }
            }
        }
        return runCatching { rust.extractText(session.toULong(), page.toUInt()) }.getOrNull()
    }

    override fun pageCount(session: Long): Int? {
        pdfs[session]?.let { return synchronized(it) { it.renderer.pageCount } }
        return runCatching { rust.pageCount(session.toULong()).toInt() }.getOrNull()
    }

    override fun paragraphAt(session: Long, page: Int, x: Float, y: Float): ParagraphHit? {
        if (pdfs.containsKey(session) || page < 0) return null
        return runCatching {
            val hit = rust.paragraphAt(session.toULong(), page.toUInt(), x, y)
            ParagraphHit(
                section = hit.section.toInt(),
                paragraph = hit.paragraph.toInt(),
                text = hit.text,
                boxes = hit.boxes.map { PageBox(it.page.toInt(), it.x, it.y, it.width, it.height) },
            )
        }.getOrNull()
    }

    override fun replaceParagraph(session: Long, section: Int, paragraph: Int, text: String): Int {
        if (pdfs.containsKey(session) || section < 0 || paragraph < 0) return -1
        return runCatching { rust.replaceParagraph(session.toULong(), section.toUInt(), paragraph.toUInt(), text).toInt() }
            .getOrDefault(-1)
    }

    /** Rust 문서만 편집한다. PDF 세션이거나 실패하면 null */
    private inline fun <T> editing(session: Long, block: (ULong) -> T): T? =
        if (pdfs.containsKey(session)) null else runCatching { block(session.toULong()) }.getOrNull()

    override fun caretAt(session: Long, page: Int, x: Float, y: Float): Caret? =
        if (page < 0) null else editing(session) { rust.caretAtPoint(it, page.toUInt(), x, y).toCaret() }

    override fun caretAtPosition(session: Long, position: TextPos): Caret? =
        editing(session) { rust.caretAt(it, position.toEngine()).toCaret() }

    override fun moveCaret(session: Long, position: TextPos, move: CaretMove, preferredX: Float): Caret? =
        editing(session) { rust.moveCaret(it, position.toEngine(), move.toEngine(), preferredX).toCaret() }

    override fun insertText(session: Long, position: TextPos, text: String): EditResult? =
        editing(session) { rust.insertText(it, position.toEngine(), text).toResult() }

    override fun deleteBackward(session: Long, position: TextPos): EditResult? =
        editing(session) { rust.deleteBackward(it, position.toEngine()).toResult() }

    override fun deleteRange(session: Long, range: TextRange): EditResult? =
        editing(session) { rust.deleteRange(it, range.toEngine()).toResult() }

    override fun selectionBoxes(session: Long, range: TextRange): List<PageBox> =
        editing(session) { rust.selectionRegions(it, range.toEngine()).map { r -> r.toBox() } }.orEmpty()

    override fun wordAt(session: Long, position: TextPos): TextRange? =
        editing(session) { rust.wordAt(it, position.toEngine()).let { r -> TextRange(r.start.toPos(), r.end.toPos()) } }

    override fun textStyle(session: Long, position: TextPos): TextStyle? =
        editing(session) {
            rust.textStyle(it, position.toEngine()).let { s ->
                TextStyle(s.fontSize, s.bold, s.italic, s.underline, s.strikethrough, s.color)
            }
        }

    override fun applyTextStyle(session: Long, range: TextRange, style: TextStyle): Int =
        editing(session) {
            val engineStyle = EngineTextStyle(
                style.fontSize, style.bold, style.italic, style.underline, style.strikethrough, style.color,
            )
            rust.applyTextStyle(it, range.toEngine(), engineStyle).toInt()
        } ?: -1

    override fun undo(session: Long): UndoResult? =
        editing(session) { rust.undo(it).let { u -> UndoResult(u.caret?.toCaret(), u.undoDepth.toInt()) } }

    override fun exportPdf(session: Long, target: String): DocumentError? {
        if (pdfs.containsKey(session)) return DocumentError.Unsupported
        val bytes = try {
            rust.export(session.toULong(), "pdf")
        } catch (error: PapyException) {
            return error.toDocumentError()
        } catch (_: OutOfMemoryError) {
            return DocumentError.TooLarge
        } catch (_: Exception) {
            return DocumentError.Internal
        }
        return write(target, bytes)
    }

    override fun save(session: Long, ref: String): DocumentError? {
        if (pdfs.containsKey(session)) return DocumentError.Unsupported
        val bytes = try {
            rust.save(session.toULong())
        } catch (error: PapyException) {
            return error.toDocumentError()
        } catch (_: Exception) {
            return DocumentError.Internal
        }
        return write(ref, bytes)
    }

    /** content URI 에 바이트를 쓴다. "wt": 파일을 비우고 처음부터 쓴다(남은 꼬리가 없게) */
    private fun write(ref: String, bytes: ByteArray): DocumentError? = try {
        val stream = context.contentResolver.openOutputStream(Uri.parse(ref), "wt")
        stream?.use { it.write(bytes) }
        if (stream == null) DocumentError.ReadOnly else null
    } catch (_: SecurityException) {
        DocumentError.ReadOnly
    } catch (_: java.io.FileNotFoundException) {
        DocumentError.ReadOnly
    } catch (_: Exception) {
        DocumentError.Unreadable
    }

    override fun close(session: Long) {
        pdfs.remove(session)?.let { pdf ->
            synchronized(pdf) {
                pdf.renderer.close()
                pdf.file.close()
            }
            return
        }
        runCatching { rust.close(session.toULong()) }
    }

    /** 앱에 넣은 한글 글꼴(assets/fonts)을 파일로 꺼낸다. 없으면 null(시스템 글꼴만) */
    private fun koreanFont(): File? {
        val target = File(context.noBackupFilesDir, "fonts/NotoSansKR-Regular.ttf")
        if (target.exists()) return target
        return runCatching {
            target.parentFile?.mkdirs()
            context.assets.open("fonts/NotoSansKR-Regular.ttf").use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            target
        }.getOrNull()
    }
}

private fun TextPos.toEngine() = EnginePosition(section.toUInt(), paragraph.toUInt(), offset.toUInt())
private fun EnginePosition.toPos() = TextPos(section.toInt(), paragraph.toInt(), offset.toInt())
private fun TextRange.toEngine() = EngineRange(start.toEngine(), end.toEngine())
private fun PageRegion.toBox() = PageBox(page.toInt(), x, y, width, height)
private fun EngineCaret.toCaret() = Caret(position.toPos(), rect.toBox())
private fun EngineEdit.toResult() = EditResult(caret.toCaret(), undoDepth.toInt())
private fun CaretMove.toEngine() = when (this) {
    CaretMove.Left -> CaretDirection.LEFT
    CaretMove.Right -> CaretDirection.RIGHT
    CaretMove.Up -> CaretDirection.UP
    CaretMove.Down -> CaretDirection.DOWN
    CaretMove.LineStart -> CaretDirection.LINE_START
    CaretMove.LineEnd -> CaretDirection.LINE_END
}

private fun PapyException.toDocumentError(): DocumentError = when (this) {
    is PapyException.PasswordRequired -> DocumentError.PasswordRequired
    is PapyException.UnsupportedFormat -> DocumentError.Unsupported
    is PapyException.InvalidDocument -> DocumentError.Invalid
    is PapyException.LimitExceeded -> DocumentError.TooLarge
    is PapyException.Io -> DocumentError.Unreadable
    else -> DocumentError.Internal
}
