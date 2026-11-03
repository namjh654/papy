package cloud.elsa.app.attachment

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import cloud.elsa.app.core.data.media.AttachmentInput
import cloud.elsa.app.core.data.media.AttachmentKind
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 첨부를 못 가져온 이유 — 화면이 문구로 바꾼다 */
enum class AttachmentFailure { PermissionDenied, Unavailable, TooLarge, Unreadable }

/** 첨부 메뉴의 세 동작(DEC-125). 시스템 화면을 쓰므로 사진·파일은 권한이 필요 없고, 카메라만 권한을 묻는다. 메시지·커뮤니티가 같이 쓴다 */
class AttachmentPicker internal constructor(
    val pickPhoto: () -> Unit,
    val takePhoto: () -> Unit,
    val pickFile: () -> Unit,
)

/**
 * 사진(Photo Picker)·카메라(TakePicture → FileProvider 임시 파일)·파일(SAF OpenDocument) 을 고르면 내용을 읽어
 * [onPicked] (KMP [AttachmentInput] + 미리보기 Uri) 로 넘긴다. 읽기는 IO 스레드에서, [MAX_BYTES] 를 넘으면 [onFailed].
 * [maxPhotos] 가 2 이상이면 사진을 여러 장 고르고 장마다 [onPicked] 가 불린다(커뮤니티 글).
 */
@Composable
fun rememberAttachmentPicker(
    onPicked: (input: AttachmentInput, preview: Uri) -> Unit,
    onFailed: (AttachmentFailure) -> Unit,
    maxPhotos: Int = 1,
): AttachmentPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 카메라가 찍은 사진이 들어갈 자리 — 카메라 앱에 다녀오는 사이 Activity 가 다시 만들어져도 남게 저장
    var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }

    fun read(uri: Uri, forcedKind: AttachmentKind?) = scope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching { context.readAttachment(uri, forcedKind) }.getOrDefault(ReadResult.Unreadable)
        }
        when (result) {
            is ReadResult.Ok -> onPicked(result.input, uri)
            ReadResult.TooLarge -> onFailed(AttachmentFailure.TooLarge)
            ReadResult.Unreadable -> onFailed(AttachmentFailure.Unreadable)
        }
    }

    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { read(it, AttachmentKind.Image) }
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxPhotos.coerceAtLeast(2))) { uris ->
        uris.forEach { read(it, AttachmentKind.Image) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = cameraTarget?.let(Uri::parse)
        if (saved && target != null) read(target, AttachmentKind.Image)
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { read(it, null) }
    }

    fun launchCamera() {
        val target = context.newCameraFile()
        cameraTarget = target.toString()
        try {
            camera.launch(target)
        } catch (_: ActivityNotFoundException) {
            onFailed(AttachmentFailure.Unavailable) // 카메라 앱이 없는 기기
        }
    }

    // 매니페스트에 CAMERA 가 있으면(권한 화면 예시) 허용 전에는 TakePicture 가 실패한다 → 먼저 묻는다
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else onFailed(AttachmentFailure.PermissionDenied)
    }

    return remember(photo, photos, camera, file, permission) {
        AttachmentPicker(
            pickPhoto = {
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (maxPhotos > 1) photos.launch(request) else photo.launch(request)
            },
            takePhoto = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted) launchCamera() else permission.launch(Manifest.permission.CAMERA)
            },
            pickFile = { file.launch(arrayOf("*/*")) },
        )
    }
}

/** 한 번에 보낼 수 있는 크기(메모리에 올려 업로드하므로 제한한다). 서버 제한과 맞춘다 */
private const val MAX_BYTES = 20L * 1024 * 1024

private sealed interface ReadResult {
    data class Ok(val input: AttachmentInput) : ReadResult
    data object TooLarge : ReadResult
    data object Unreadable : ReadResult
}

private fun Context.readAttachment(uri: Uri, forcedKind: AttachmentKind?): ReadResult {
    val mime = contentResolver.getType(uri) ?: "application/octet-stream"
    var name = uri.lastPathSegment ?: "attachment"
    var size = -1L
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            c.getString(0)?.let { name = it }
            if (!c.isNull(1)) size = c.getLong(1)
        }
    }
    // 크기를 알면 읽기 전에, 모르면 읽은 뒤에 거른다
    if (size > MAX_BYTES) return ReadResult.TooLarge
    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ReadResult.Unreadable
    if (bytes.size > MAX_BYTES) return ReadResult.TooLarge
    val kind = forcedKind ?: if (mime.startsWith("image/")) AttachmentKind.Image else AttachmentKind.File
    if (kind == AttachmentKind.Image) {
        // 사진은 긴 변 2048px JPEG 로 줄여 보낸다(iOS 와 같다). 줄일 수 없는 형식이면 원본 그대로
        downscaledJpeg(bytes)?.let { jpeg ->
            return ReadResult.Ok(AttachmentInput(name = name.substringBeforeLast('.') + ".jpg", mimeType = "image/jpeg", kind = kind, bytes = jpeg))
        }
    }
    return ReadResult.Ok(AttachmentInput(name = name, mimeType = mime, kind = kind, bytes = bytes))
}

private const val MAX_IMAGE_SIDE = 2048

private fun downscaledJpeg(bytes: ByteArray): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val scale = minOf(1f, MAX_IMAGE_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
    val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
}

private fun Context.newCameraFile(): Uri {
    val dir = File(cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(this, "$packageName.files", file)
}
