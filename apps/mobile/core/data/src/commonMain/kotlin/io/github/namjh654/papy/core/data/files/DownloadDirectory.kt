package io.github.namjh654.papy.core.data.files

/** 받은 파일을 두는 앱 전용 폴더(플랫폼이 정한다 — Android filesDir/downloads, iOS Application Support/downloads) */
data class DownloadDirectory(val path: String)
