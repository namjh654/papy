package cloud.elsa.app.core.data.media

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 사진·카메라·파일 첨부의 공통 입력 — 메시지(19)·커뮤니티(20)가 같이 쓴다(DEC-129)

/** Swift: `AttachmentKind.image` / `.file` (SKIE enum) */
@Serializable
enum class AttachmentKind {
    @SerialName("image") Image,
    @SerialName("file") File,
}

/**
 * 플랫폼이 고른 첨부(사진·카메라·파일)의 내용. 업로드한 뒤 서버 주소가 된다(메시지 `Attachment`, 커뮤니티 사진).
 * Swift: `AttachmentInput(name:mimeType:kind:bytes:)` — bytes 는 `IOSAppGraph.shared.bytes(data:)` 로 만든다
 */
class AttachmentInput(
    val name: String,
    val mimeType: String,
    val kind: AttachmentKind,
    val bytes: ByteArray,
) {
    val size: Long get() = bytes.size.toLong()
}
