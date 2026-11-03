package template.tooling

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import javax.imageio.ImageIO

/**
 * 로컬 목업 서버 — 템플릿이 가정한 서버 계약(docs/template-setup.md §2)대로 응답한다. 실서버 없이 앱 전 흐름을 돌려 볼 때,
 * iOS UI 테스트(`scripts/orca.sh uitest`)가 쓴다. JDK 만 쓴다(라이브러리 없음).
 *
 *   ./gradlew -p build-logic :tooling:run --args="mockServer [--port 8765]"
 *   앱 쪽: env/dev.local.properties 에 api.baseUrl=http://127.0.0.1:8765 (Android 에뮬레이터는 http://10.0.2.2:8765)
 *
 * 테스트 제어(앱은 부르지 않는다):
 * - `POST /__mock/expire` — 이후 인증이 붙은 요청과 토큰 재발급이 401 (세션 만료 흐름 확인)
 * - `POST /__mock/reset`  — 만료 해제, 프로필 초기화
 * - `GET  /__mock/health` — 떠 있는지 확인
 * - 비밀번호가 `wrong` 이면 로그인 401
 * - 메시지 `/api/chats…` 는 [chat] (앱 기본은 예시 API — 목업으로 보려면 ChatModule 을 RemoteChatApi 로)
 */
class MockServer(private val port: Int) {
    private val defaultUser = mapOf("id" to "u1", "name" to "테스터", "email" to "tester@example.com", "avatarUrl" to null)
    private val user = defaultUser.toMutableMap()
    /** 알림 설정(26-2): 종류별 받기 + 광고성 동의 시각 */
    private val defaultPreferences = mapOf<String, Any?>("chat" to true, "comment" to true, "notice" to true, "marketing" to false, "marketingUpdatedAt" to null)
    private val preferences = defaultPreferences.toMutableMap()

    @Volatile private var expired = false

    fun start(): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/") { exchange ->
            runCatching { handle(exchange) }.onFailure { send(exchange, 500, """{"message":"${it.message}"}""") }
            exchange.close()
        }
        server.start()
        return server
    }

    private fun handle(ex: HttpExchange) {
        val method = ex.requestMethod
        val path = ex.requestURI.path
        val body = ex.requestBody.readBytes()
        val auth = ex.requestHeaders.getFirst("Authorization") != null
        println("$method ${ex.requestURI} auth=$auth ${if (body.size in 1..500) body.decodeToString() else "(${body.size} bytes)"}")

        if (path.startsWith("/__mock/")) return control(ex, method, path)
        if (expired && (auth || path == "/api/auth/refresh")) return send(ex, 401, """{"message":"expired"}""")
        if (path.startsWith("/api/chats")) return chat(ex, method, path, body)
        if (path.startsWith("/api/posts") || path.startsWith("/api/comments") || path.startsWith("/api/blocks") ||
            path == "/api/reports" || path == "/api/uploads"
        ) return community(ex, method, path, body)

        when ("$method $path") {
            "GET /api/app/version" -> send(ex, 200, """{"minimumVersion":null,"latestVersion":null,"storeUrl":null}""")
            "POST /api/auth/login" -> {
                val req = Json.obj(Json.parse(body.decodeToString()))
                if (req["password"] == "wrong") return send(ex, 401, """{"message":"invalid credentials"}""")
                send(
                    ex, 200,
                    """{"accessToken":"mock-access","refreshToken":"mock-refresh","user":{"id":"${user["id"]}",""" +
                        """"displayName":${str(user["name"])},"username":${str(req["id"] as? String)},"email":${str(user["email"])}}}""",
                )
            }
            // 소셜 로그인(가정 계약 DEC-109): token "reject" → 401, "new" 로 시작하거나 네이버면 처음 가입한 사용자
            // (네이버 = 앱의 social.mock 로 약관 동의 흐름을 확인하는 경로, 18-4)
            "POST /api/auth/social" -> {
                val req = Json.obj(Json.parse(body.decodeToString()))
                val provider = req["provider"] as? String ?: "unknown"
                val token = req["token"] as? String ?: ""
                if (token == "reject") return send(ex, 401, """{"message":"invalid social token"}""")
                send(
                    ex, 200,
                    """{"accessToken":"mock-access","refreshToken":"mock-refresh","isNewUser":${token.startsWith("new") || provider == "naver"},""" +
                        """"user":{"id":"social-$provider","displayName":${str("$provider 사용자")},"email":null}}""",
                )
            }
            "POST /api/auth/refresh" -> send(ex, 200, """{"accessToken":"mock-access-2","refreshToken":"mock-refresh-2"}""")
            "POST /api/signup" -> send(ex, 200, """{"success":true,"message":null}""")
            "POST /api/auth/password/reset", "POST /api/auth/password/reset/confirm",
            "POST /api/account/withdraw", "POST /api/push/tokens", "POST /api/auth/social/agreements", "POST /api/inquiries",
            -> send(ex, 204, null)
            "GET /api/push/preferences" -> send(ex, 200, preferencesJson())
            "PUT /api/push/preferences" -> {
                val req = Json.obj(Json.parse(body.decodeToString()))
                if (req["marketing"] != preferences["marketing"]) preferences["marketingUpdatedAt"] = System.currentTimeMillis()
                listOf("chat", "comment", "notice", "marketing").forEach { key -> (req[key] as? Boolean)?.let { preferences[key] = it } }
                send(ex, 200, preferencesJson())
            }
            "GET /api/me" -> send(ex, 200, userJson())
            "PUT /api/me" -> {
                val req = Json.obj(Json.parse(body.decodeToString()))
                listOf("name", "email", "avatarUrl").forEach { key -> if (req.containsKey(key)) user[key] = req[key] as String? }
                send(ex, 200, userJson())
            }
            "POST /api/me/avatar" -> send(ex, 200, """{"avatarUrl":"http://127.0.0.1:$port/__mock/avatar.png"}""")
            "GET /api/example" -> {
                val page = ex.requestURI.query?.substringAfter("page=", "1")?.substringBefore('&')?.toIntOrNull() ?: 1
                val items = (1..20).joinToString(",") { i ->
                    val n = (page - 1) * 20 + i
                    """{"id":"$n","title":"Item $n","subtitle":"mock page $page"}"""
                }
                send(ex, 200, """{"items":[$items],"nextPage":${if (page < 3) page + 1 else null}}""")
            }
            else -> if (method == "DELETE" && path.startsWith("/api/push/tokens/")) send(ex, 204, null)
            else send(ex, 404, """{"message":"mock: $method $path 없음"}""")
        }
    }

    // ---------------------------------------------------------------- 메시지(19-5, 가정 계약 DEC-125)

    private data class ChatMessage(
        val id: String, val senderId: String, val text: String?, val attachment: String?, val sentAt: Long, val clientId: String?,
        val replyTo: ChatMessage? = null,
    )

    private val chats = mapOf("c1" to "엘사", "c2" to "디자인팀")
    private val chatMessages = mutableMapOf<String, MutableList<ChatMessage>>()
    private val chatReadAt = mutableMapOf<String, Long>()
    private var chatSequence = 0

    @Synchronized
    private fun chatStore(id: String) = chatMessages.getOrPut(id) {
        val start = System.currentTimeMillis() - 40 * 60_000L
        MutableList(40) { i -> ChatMessage("$id-h$i", if (i % 3 == 2) "u1" else "peer", "목업 메시지 ${i + 1}", null, start + i * 60_000L, null) }
    }

    private fun chatJson(conversationId: String, m: ChatMessage) =
        """{"id":"${m.id}","conversationId":"$conversationId","senderId":"${m.senderId}","text":${str(m.text)},""" +
            """"attachment":${m.attachment ?: "null"},"sentAt":${m.sentAt},"clientId":${str(m.clientId)},""" +
            """"replyTo":${m.replyTo?.let { r -> """{"messageId":"${r.id}","senderId":"${r.senderId}","text":${str(r.text)}}""" } ?: "null"}}"""

    /**
     * 대화 목록·메시지(최신 30개, before=위로, after=폴링)·보내기(같은 clientId 는 한 번, 2초 뒤 상대 답장)·첨부 업로드·읽음.
     * 로그인 사용자 id(u1)가 "나"
     */
    @Synchronized
    private fun chat(ex: HttpExchange, method: String, path: String, body: ByteArray) {
        val parts = path.removePrefix("/api/chats").trim('/').split('/').filter { it.isNotEmpty() }
        val query = ex.requestURI.query.orEmpty().split('&').mapNotNull { it.split('=').takeIf { kv -> kv.size == 2 }?.let { kv -> kv[0] to kv[1] } }.toMap()
        val now = System.currentTimeMillis()
        if (method == "GET" && parts.isEmpty()) {
            val items = chats.entries.joinToString(",") { (id, title) ->
                val last = chatStore(id).lastOrNull { it.sentAt <= now }
                """{"id":"$id","title":${str(title)},"lastMessage":${str(last?.text ?: "첨부")},"lastMessageAt":${last?.sentAt},"unreadCount":${if (id == "c1") 2 else 0}}"""
            }
            return send(ex, 200, """{"items":[$items],"nextPage":null}""")
        }
        val id = parts.firstOrNull()?.takeIf { it in chats } ?: return send(ex, 404, """{"message":"mock: 대화 없음"}""")
        val all = chatStore(id)
        if (method == "GET" && parts.size == 1) {
            val last = all.lastOrNull { it.sentAt <= now }
            return send(ex, 200, """{"id":"$id","title":${str(chats[id])},"lastMessage":${str(last?.text)},"lastMessageAt":${last?.sentAt},"unreadCount":0}""")
        }
        if (method == "GET" && parts.getOrNull(1) == "messages" && parts.getOrNull(2) == "search") {
            val q = java.net.URLDecoder.decode(query["q"].orEmpty(), "UTF-8")
            val found = all.filter { it.sentAt <= now && it.text.orEmpty().contains(q, ignoreCase = true) }.reversed().take(50)
            return send(ex, 200, """{"items":[${found.joinToString(",") { chatJson(id, it) }}]}""")
        }
        when ("$method ${parts.getOrNull(1)}") {
            "GET messages" -> {
                val visible = all.filter { it.sentAt <= now }
                val after = query["after"]
                val (items, hasOlder) = if (after != null) {
                    visible.drop(visible.indexOfFirst { it.id == after } + 1) to false
                } else {
                    val end = query["before"]?.let { b -> visible.indexOfFirst { it.id == b } }?.takeIf { it >= 0 } ?: visible.size
                    val begin = (end - 30).coerceAtLeast(0)
                    visible.subList(begin, end) to (begin > 0)
                }
                val typing = all.any { it.sentAt > now }
                send(ex, 200, """{"items":[${items.joinToString(",") { chatJson(id, it) }}],"hasOlder":$hasOlder,"peerReadAt":${chatReadAt[id]},"peerTyping":$typing}""")
            }
            "POST messages" -> {
                val req = Json.obj(Json.parse(body.decodeToString()))
                val clientId = req["clientId"] as? String
                all.firstOrNull { clientId != null && it.clientId == clientId }?.let { return send(ex, 200, chatJson(id, it)) }
                @Suppress("UNCHECKED_CAST")
                val attachment = (req["attachment"] as? Map<String, Any?>)?.let { a ->
                    """{"kind":${str(a["kind"] as? String)},"name":${str(a["name"] as? String)},"url":${str(a["url"] as? String)},""" +
                        """"size":${(a["size"] as? Number)?.toLong() ?: 0},"mimeType":${str(a["mimeType"] as? String)}}"""
                }
                val replyTo = (req["replyToId"] as? String)?.let { r -> all.firstOrNull { it.id == r } }
                val sent = ChatMessage("$id-s${chatSequence++}", "u1", req["text"] as? String, attachment, now, clientId, replyTo)
                all += sent
                val replyAt = now + 2_000
                all += ChatMessage("$id-r${chatSequence++}", "peer", if (attachment != null) "잘 받았어요 👍" else "답장: ${sent.text}", null, replyAt, null)
                chatReadAt[id] = replyAt
                send(ex, 200, chatJson(id, sent))
            }
            "POST attachments" -> {
                val head = body.copyOfRange(0, minOf(body.size, 2_000)).decodeToString()
                val name = Regex("filename=\"([^\"]*)\"").find(head)?.groupValues?.get(1) ?: "file"
                val type = Regex("Content-Type: ([^\\r\\n]+)").find(head)?.groupValues?.get(1)?.trim() ?: "application/octet-stream"
                val image = type.startsWith("image/")
                val url = if (image) "http://127.0.0.1:$port/__mock/avatar.png" else ""
                send(ex, 200, """{"kind":"${if (image) "image" else "file"}","name":${str(name)},"url":"$url","size":${body.size},"mimeType":${str(type)}}""")
            }
            "POST read", "POST typing" -> send(ex, 204, null)
            else -> send(ex, 404, """{"message":"mock: $method $path 없음"}""")
        }
    }

    // ---------------------------------------------------------------- 커뮤니티(20-5, 가정 계약 known-limitations §1 #9)

    private data class MockPost(
        val id: String, val authorId: String, var content: String, var images: List<String>, val createdAt: Long,
        var likes: Int, var liked: Boolean,
    )
    private data class MockComment(val id: String, val postId: String, val authorId: String, val content: String, val createdAt: Long)

    private val people = mapOf("u1" to "테스터", "p1" to "엘사", "p2" to "안나")
    private val posts = mutableListOf<MockPost>().apply {
        val now = System.currentTimeMillis()
        repeat(15) { i -> add(MockPost("post$i", if (i == 0) "u1" else if (i % 2 == 0) "p1" else "p2", "목업 글 ${i + 1}", emptyList(), now - i * 3_600_000L, i % 6, false)) }
    }
    private val postComments = mutableListOf<MockComment>()
    private val blockedUsers = mutableSetOf<String>()
    private var communitySequence = 0

    private fun authorJson(id: String) = """{"id":"$id","name":${str(people[id] ?: id)},"avatarUrl":null}"""

    private fun postJson(p: MockPost) =
        """{"id":"${p.id}","author":${authorJson(p.authorId)},"content":${str(p.content)},"imageUrls":[${p.images.joinToString(",") { str(it) }}],""" +
            """"createdAt":${p.createdAt},"likeCount":${p.likes},"commentCount":${postComments.count { it.postId == p.id }},"likedByMe":${p.liked},"isMine":${p.authorId == "u1"}}"""

    private fun commentJson(c: MockComment) =
        """{"id":"${c.id}","postId":"${c.postId}","author":${authorJson(c.authorId)},"content":${str(c.content)},"createdAt":${c.createdAt},"isMine":${c.authorId == "u1"}}"""

    /** 글 목록(10개씩)·상세·쓰기·고치기·지우기·좋아요·댓글·신고·차단·사진 업로드. 로그인 사용자(u1)가 "나" */
    @Synchronized
    private fun community(ex: HttpExchange, method: String, path: String, body: ByteArray) {
        val parts = path.removePrefix("/api/").split('/').filter { it.isNotEmpty() }
        val page = ex.requestURI.query?.substringAfter("page=", "0")?.substringBefore('&')?.toIntOrNull() ?: 0
        fun req() = Json.obj(Json.parse(body.decodeToString()))
        fun findPost(id: String) = posts.firstOrNull { it.id == id }
        @Suppress("UNCHECKED_CAST")
        fun images(r: Map<String, Any?>) = (r["imageUrls"] as? List<Any?>)?.map { it as String } ?: emptyList()
        when {
            method == "POST" && parts == listOf("uploads") ->
                send(ex, 200, """{"url":"http://127.0.0.1:$port/__mock/avatar.png"}""")
            method == "POST" && parts == listOf("reports") -> send(ex, 204, null)
            method == "GET" && parts == listOf("blocks") -> send(ex, 200, "[${blockedUsers.joinToString(",") { authorJson(it) }}]")
            method == "POST" && parts == listOf("blocks") -> { blockedUsers += req()["userId"] as String; send(ex, 204, null) }
            method == "DELETE" && parts.size == 2 && parts[0] == "blocks" -> { blockedUsers -= parts[1]; send(ex, 204, null) }
            method == "DELETE" && parts.size == 2 && parts[0] == "comments" -> { postComments.removeAll { it.id == parts[1] }; send(ex, 204, null) }
            method == "GET" && parts == listOf("posts") -> {
                val mine = ex.requestURI.query.orEmpty().contains("mine=true")
                val visible = posts.filter { it.authorId !in blockedUsers && (!mine || it.authorId == "u1") }
                val items = visible.drop(page).take(10)
                val next = if (page + 10 < visible.size) "\"${page + 10}\"" else "null"
                send(ex, 200, """{"items":[${items.joinToString(",") { postJson(it) }}],"nextPage":$next}""")
            }
            method == "POST" && parts == listOf("posts") -> {
                val r = req()
                val post = MockPost("new${communitySequence++}", "u1", r["content"] as String, images(r), System.currentTimeMillis(), 0, false)
                posts.add(0, post)
                send(ex, 200, postJson(post))
            }
            parts.size >= 2 && parts[0] == "posts" -> {
                val post = findPost(parts[1]) ?: return send(ex, 404, """{"message":"mock: 글 없음"}""")
                when ("$method ${parts.getOrNull(2)}") {
                    "GET null" -> send(ex, 200, postJson(post))
                    "PUT null" -> { val r = req(); post.content = r["content"] as String; post.images = images(r); send(ex, 200, postJson(post)) }
                    "DELETE null" -> { posts.remove(post); send(ex, 204, null) }
                    "POST like", "DELETE like" -> {
                        val liked = method == "POST"
                        if (liked != post.liked) post.likes += if (liked) 1 else -1
                        post.liked = liked
                        send(ex, 200, """{"likeCount":${post.likes},"likedByMe":${post.liked}}""")
                    }
                    "GET comments" -> {
                        val items = postComments.filter { it.postId == post.id && it.authorId !in blockedUsers }
                        send(ex, 200, """{"items":[${items.joinToString(",") { commentJson(it) }}],"nextPage":null}""")
                    }
                    "POST comments" -> {
                        val c = MockComment("cm${communitySequence++}", post.id, "u1", req()["content"] as String, System.currentTimeMillis())
                        postComments += c
                        send(ex, 200, commentJson(c))
                    }
                    else -> send(ex, 404, """{"message":"mock: $method $path 없음"}""")
                }
            }
            else -> send(ex, 404, """{"message":"mock: $method $path 없음"}""")
        }
    }

    private fun control(ex: HttpExchange, method: String, path: String) {
        when ("$method $path") {
            "GET /__mock/health" -> send(ex, 200, """{"ok":true}""")
            // 채팅 예시 파일 첨부(30-1, SampleChatApi 의 guide.pdf) — 받기·열기 확인용
            "GET /__mock/files/guide.pdf" -> send(ex, 200, samplePdf, "application/pdf")
            "POST /__mock/expire" -> { expired = true; send(ex, 204, null) }
            "POST /__mock/reset" -> {
                expired = false
                user.clear(); user.putAll(defaultUser)
                preferences.clear(); preferences.putAll(defaultPreferences)
                send(ex, 204, null)
            }
            "GET /__mock/avatar.png" -> send(ex, 200, avatarPng, "image/png")
            else -> send(ex, 404, """{"message":"mock: $method $path 없음"}""")
        }
    }

    private fun preferencesJson() =
        preferences.entries.joinToString(",", "{", "}") { (key, value) -> "\"$key\":${value ?: "null"}" }

    private fun userJson() =
        """{"id":${str(user["id"])},"name":${str(user["name"])},"email":${str(user["email"])},"avatarUrl":${str(user["avatarUrl"])}}"""

    private fun str(value: String?): String =
        value?.let { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" } ?: "null"

    private fun send(ex: HttpExchange, code: Int, json: String?) =
        send(ex, code, json?.toByteArray(), "application/json; charset=utf-8")

    private fun send(ex: HttpExchange, code: Int, bytes: ByteArray?, type: String) {
        if (bytes == null) {
            ex.sendResponseHeaders(code, -1)
            return
        }
        ex.responseHeaders.add("Content-Type", type)
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    /** 한 쪽짜리 PDF(제목 한 줄) — 받은 파일 열기 확인용. xref 위치는 만들 때 계산한다 */
    private val samplePdf: ByteArray by lazy {
        val stream = "BT /F1 24 Tf 72 720 Td (Papy sample file) Tj ET"
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
            "<< /Length ${stream.length} >>\nstream\n$stream\nendstream",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        )
        val out = StringBuilder("%PDF-1.4\n")
        val offsets = objects.mapIndexed { i, body ->
            val offset = out.length
            out.append("${i + 1} 0 obj\n$body\nendobj\n")
            offset
        }
        val xref = out.length
        out.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { out.append(String.format("%010d 00000 n \n", it)) }
        out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        out.toString().toByteArray(Charsets.US_ASCII)
    }

    /** 업로드한 사진 대신 보여 줄 200×200 이미지 (외부 사이트에 기대지 않는다) */
    private val avatarPng: ByteArray by lazy {
        System.setProperty("java.awt.headless", "true")
        val image = BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            color = Color(0x33, 0x55, 0xDD); fillRect(0, 0, 200, 200)
            color = Color(0xFF, 0xD5, 0x4F); fillOval(50, 50, 100, 100)
            dispose()
        }
        ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
}
