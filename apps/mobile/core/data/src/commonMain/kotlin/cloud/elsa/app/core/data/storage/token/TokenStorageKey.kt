package cloud.elsa.app.core.data.storage.token

/** 키 이름을 바꾸면 기존 저장 값을 잃는다. */
internal object TokenStorageKey {
    const val ACCESS_TOKEN = "token.access"
    const val REFRESH_TOKEN = "token.refresh"
    val ALL = listOf(ACCESS_TOKEN, REFRESH_TOKEN)
}
