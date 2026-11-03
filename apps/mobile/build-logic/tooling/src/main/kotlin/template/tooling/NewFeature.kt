package template.tooling

/**
 * 새 feature 뼈대 생성 (docs/conventions.md §4).
 *
 * 만드는 것
 *   KMP     feature/<name>/ — UiState·Event·ViewModel·Repository·Api(Remote + Sample)·Koin 모듈·테스트
 *   등록    settings.gradle.kts · shared exported · AppKoin.appModules · IOSAppGraph
 *   Android screen/<name>/<Name>Screen.kt · AndroidRoute · AndroidNavHost · navigateTo<Name>() · DeepLinkRouter · strings.xml(ko/en)
 *   iOS     view/<name>/<name>.screen.swift · MainRoute · AppNavigation · DeepLinkRouter · Localizable.xcstrings(ko/en)
 *
 * 화면은 목록 패턴(PapyPagedList: 당겨서 새로고침·다음 페이지)이다.
 * [tab] 이면 하단 탭으로 붙인다: Android MainTab · iOS MainTab 에 탭을 추가하고 뒤로가기 없는 탭 루트로 등록한다.
 * 아니면 탭 위에 쌓이는 화면(Android AndroidRoute, iOS MainRoute)으로 등록한다.
 *
 * [db] 면 로컬 DB(SQLDelight, DEC-130)를 붙인다: `papy.kmp.database`, `<Name>.sq`(첫 페이지 테이블), `cache/<Name>Cache`(LocalCache),
 * Repository 가 첫 페이지를 저장하고 ViewModel 은 `Paginator(cache = …)` 로 저장된 목록을 먼저 보인다, 테스트용 메모리 DB(docs/local-db.md).
 *
 * 처음에는 Sample<Name>Api 로 서버 없이 동작한다. 루트 Gradle 빌드 밖(build-logic 의 tooling)에서 실행하므로
 * 실행 중인 빌드의 settings 를 고치는 문제가 없다(DEC-057).
 */
class NewFeature(
    private val repo: Repo,
    private val name: String,
    titleKo: String?,
    titleEn: String?,
    private val dryRun: Boolean,
    private val tab: Boolean = false,
    private val db: Boolean = false,
) {
    private val cap = name.replaceFirstChar { it.uppercase() }
    private val titleKo = titleKo ?: cap
    private val titleEn = titleEn ?: cap
    private val base = repo.basePackage
    private val ctx = linkedMapOf(
        "__Name__" to cap,
        "__name__" to name,
        "__TITLE_KO__" to this.titleKo,
        "__TITLE_EN__" to this.titleEn,
        "__pkgpath__" to repo.basePath,
        "__pkg__" to base,
    )
    private val created = mutableListOf<String>()
    private val edited = sortedSetOf<String>()

    init {
        require(Regex("[a-z][a-z0-9]*").matches(name)) { "name 은 소문자로 시작하는 영문·숫자 한 단어여야 한다 (예: notice)" }
        require(!repo.file("feature/$name").exists()) { "이미 있는 feature: $name" }
    }

    fun run(): String {
        val app = repo.androidSrc
        val ios = "iosApp/iosApp"
        val kmp = "feature/$name/src/commonMain/kotlin/${repo.basePath}/feature/$name"
        val test = "feature/$name/src/commonTest/kotlin/${repo.basePath}/feature/$name"
        val b = Regex.escape(base)

        // KMP
        val gradle = template("build.gradle.kts")
        create(
            "feature/$name/build.gradle.kts",
            if (!db) gradle
            else gradle.replace("    id(\"papy.kmp.feature\")\n", "    id(\"papy.kmp.feature\")\n    // 로컬 DB(DEC-130): ${cap}Database — src/commonMain/sqldelight/…/$cap.sq\n    id(\"papy.kmp.database\")\n"),
        )
        create("$kmp/${cap}Api.kt", template("Api.kt"))
        create("$kmp/${cap}Repository.kt", template(if (db) "Repository.db.kt" else "Repository.kt"))
        create("$kmp/${cap}UiState.kt", template("UiState.kt"))
        create("$kmp/${cap}Event.kt", template("Event.kt"))
        val viewModel = template("ViewModel.kt")
        create(
            "$kmp/${cap}ViewModel.kt",
            if (!db) viewModel
            else viewModel.replace(
                "    private val paginator = Paginator { page -> repository.items(page) }",
                "    // 저장된 첫 페이지를 먼저 보여 주고 받는다(DEC-130)\n    private val paginator = Paginator(cache = repository::cachedItems) { page -> repository.items(page) }",
            ),
        )
        create("$kmp/di/${cap}Module.kt", template(if (db) "Module.db.kt" else "Module.kt"))
        create("$test/${cap}RepositoryTest.kt", template(if (db) "RepositoryTest.db.kt" else "RepositoryTest.kt"))
        val viewModelTest = template("ViewModelTest.kt")
        create("$test/${cap}ViewModelTest.kt", if (db) viewModelTest.replace("${cap}Repository(", "${cap}Repository(cache = testCache(), api = ") else viewModelTest)
        if (db) {
            val sqlPath = "feature/$name/src/commonMain/sqldelight/${repo.basePath}/feature/$name/db"
            create("$sqlPath/$cap.sq", template("Database.sq"))
            create("$kmp/cache/${cap}Cache.kt", template("Cache.kt"))
            create("$test/TestDatabase.kt", template("TestDatabase.kt"))
            create("feature/$name/src/androidHostTest/kotlin/${repo.basePath}/feature/$name/TestDatabase.android.kt", template("TestDatabase.android.kt"))
            create("feature/$name/src/iosTest/kotlin/${repo.basePath}/feature/$name/TestDatabase.ios.kt", template("TestDatabase.ios.kt"))
        }

        // 등록
        edit("settings.gradle.kts", afterLast("""^include\(":feature:[\w-]+"\)""", """include(":feature:$name")"""))
        edit("shared/build.gradle.kts", afterLast("""^\s+":feature:[\w-]+",""", """    ":feature:$name","""))
        val appKoin = "shared/src/commonMain/kotlin/${repo.basePath}/di/AppKoin.kt"
        edit(appKoin, afterLast("""^import $b\.feature\.[\w.]+Module$""", "import $base.feature.$name.di.${name}Module"))
        edit(appKoin, afterLast("""^\s+\w+Module,$""", "    ${name}Module,"))
        val graph = "shared/src/iosMain/kotlin/${repo.basePath}/di/IOSAppGraph.kt"
        edit(graph, afterLast("""^import $b\.feature\.[\w.]+ViewModel$""", "import $base.feature.$name.${cap}ViewModel"))
        edit(graph, afterLast("""^\s+fun \w+ViewModel\(\): \w+ = get\(\)$""", "\n    fun ${name}ViewModel(): ${cap}ViewModel = get()"))

        // Android
        create("$app/screen/$name/${cap}Screen.kt", template("AndroidScreen.kt"))
        edit("$app/navigation/AndroidRoute.kt", beforeMarker("scaffold:routes", "    @Serializable\n    data object $cap : AndroidRoute"))
        val navHost = "$app/navigation/AndroidNavHost.kt"
        edit(navHost, beforeMarker("scaffold:destinations", fill(
            if (tab) {
                "                composable<AndroidRoute.__Name__> {\n" +
                    "                    __Name__Screen(\n" +
                    "                        onItemSelected = { /* 상세 화면이 생기면 여기서 이동 */ },\n" +
                    "                    )\n" +
                    "                }"
            } else {
                "                composable<AndroidRoute.__Name__> {\n" +
                    "                    __Name__Screen(\n" +
                    "                        onItemSelected = { /* 상세 화면이 생기면 여기서 이동 */ },\n" +
                    "                        onBack = { navController.popBackStack() },\n" +
                    "                    )\n" +
                    "                }"
            },
        )))
        edit(navHost, afterLast("""^import $b\.screen\.[\w.]+$""", "import $base.screen.$name.${cap}Screen"))
        edit("$app/navigation/DeepLinkRouter.kt", beforeMarker("scaffold:deeplinks", """        "$name" -> navigateTo$cap()""", blankLine = false))
        edit("$app/navigation/AndroidNavigator.kt") { t ->
            t.trimEnd('\n') + fill(
                if (tab) "\n\nfun NavController.navigateTo__Name__() {\n    navigateToTab(MainTab.__Name__)\n}\n"
                else "\n\nfun NavController.navigateTo__Name__() {\n    navigate(AndroidRoute.__Name__) {\n        launchSingleTop = true\n    }\n}\n",
            )
        }
        if (tab) {
            edit("$app/navigation/MainTab.kt", beforeMarker(
                "scaffold:tabs",
                "    $cap(AndroidRoute.$cap, R.string.tab_$name, R.drawable.papy_ic_tab_default),",
                blankLine = false,
            ))
        }

        val stringsKo = linkedMapOf("${name}_title" to titleKo, "${name}_description" to "$titleKo 목록이에요", "${name}_empty" to "표시할 항목이 없어요")
        val stringsEn = linkedMapOf("${name}_title" to titleEn, "${name}_description" to "Your ${titleEn.lowercase()} list", "${name}_empty" to "Nothing to show yet")
        if (tab) {
            stringsKo["tab_$name"] = titleKo
            stringsEn["tab_$name"] = titleEn
        }
        val res = "androidApp/src/main/res"
        for ((values, entries, retry) in listOf(Triple("values", stringsKo, "다시 시도"), Triple("values-en", stringsEn, "Try again"))) {
            val all = LinkedHashMap<String, String>()
            if (!repo.read("$res/$values/strings.xml").contains("name=\"common_retry\"")) all["common_retry"] = retry
            all.putAll(entries)
            edit("$res/$values/strings.xml") { t ->
                val block = "\n    <!-- $name (newFeature) -->\n" + all.entries.joinToString("") { (k, v) -> "    <string name=\"$k\">$v</string>\n" }
                t.replaceFirst("\n</resources>", "\n$block</resources>")
            }
        }

        // iOS
        create("$ios/view/$name/$name.screen.swift", template("IosScreen.swift"))
        val iosNav = "$ios/navigation/app.navigation.swift"
        val screen = fill("__Name__Screen(onItemSelected: { _ in /* 상세 화면이 생기면 여기서 이동 */ })")
        if (tab) {
            val tabs = "$ios/navigation/main.tab.swift"
            edit(tabs, beforeMarker("scaffold:tabs", "    case $name", blankLine = false))
            edit(tabs, beforeMarker("scaffold:tab-titles", """        case .$name: "tab_$name"""", blankLine = false))
            edit(tabs, beforeMarker("scaffold:tab-images", """        case .$name: "papy_ic_tab_default"""", blankLine = false))
            edit(iosNav, beforeMarker("scaffold:tab-roots", "        case .$name:\n            $screen"))
            edit("$ios/navigation/deeplink.router.swift", beforeMarker("scaffold:deeplinks", """        case "$name": return .tab(.$name)""", blankLine = false))
        } else {
            edit("$ios/navigation/app.route.swift", beforeMarker("scaffold:main-routes", "    /// $titleKo\n    case $name"))
            edit(iosNav, beforeMarker("scaffold:main-destinations", "        case .$name:\n            $screen"))
            edit("$ios/navigation/deeplink.router.swift", beforeMarker("scaffold:deeplinks", """        case "$name": return .route(.$name)""", blankLine = false))
        }
        val xc = stringsKo.keys.associateWith { stringsKo.getValue(it) to stringsEn.getValue(it) } + ("common_retry" to ("다시 시도" to "Try again"))
        edit("$ios/Localizable.xcstrings") { t -> addXcstrings(t, xc) }

        return buildString {
            appendLine((if (dryRun) "[dry-run] " else "") + "feature '$name' 생성")
            created.forEach { appendLine("  + $it") }
            edited.forEach { appendLine("  ~ $it") }
            appendLine()
            appendLine("다음 단계")
            if (tab) {
                appendLine("  1. 하단 탭으로 추가됨. 아이콘: design/icons/tab_$name.svg(Lucide) 를 넣고 ./gradlew generateDesignTokens → Android MainTab 의 R.drawable.papy_ic_tab_$name, iOS MainTab.icon \"papy_ic_tab_$name\" 로 바꾼다")
            } else {
                appendLine("  1. 진입점 연결: Android navController.navigateTo$cap() / iOS AppNavigation 의 open(.$name)  (예: 홈 메뉴 카드)")
            }
            appendLine("     푸시·링크로도 열린다: data {\"screen\": \"$name\"} 또는 link <scheme>://$name")
            appendLine("  2. 서버가 준비되면 feature/$name/.../di/${cap}Module.kt 에서 Remote${cap}Api 로 교체")
            append("  3. scripts/orca.sh check  (또는 ./gradlew templateCheck testAndroidHostTest :androidApp:assembleDevDebug)")
        }
    }

    private fun fill(template: String): String = ctx.entries.fold(template) { acc, (k, v) -> acc.replace(k, v) }

    private fun template(name: String): String {
        val stream = javaClass.getResourceAsStream("/templates/feature/$name.tmpl") ?: error("템플릿 없음: $name")
        return fill(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private fun create(path: String, text: String) {
        require(!repo.file(path).exists()) { "이미 있음: $path" }
        created += path
        if (!dryRun) repo.write(path, text)
    }

    private fun edit(path: String, change: (String) -> String) {
        val before = repo.read(path)
        val after = change(before)
        require(after != before) { "수정 위치를 찾지 못함: $path" }
        edited += path
        if (!dryRun) repo.write(path, after)
    }

    companion object {
        fun beforeMarker(marker: String, block: String, blankLine: Boolean = true): (String) -> String = { text ->
            val lines = text.split("\n")
            val i = lines.indexOfFirst { marker in it }
            if (i < 0) text
            else (lines.take(i) + block.trimEnd('\n').split("\n") + (if (blankLine) listOf("") else emptyList()) + lines.drop(i))
                .joinToString("\n")
        }

        fun afterLast(pattern: String, line: String): (String) -> String = { text ->
            val last = Regex(pattern, RegexOption.MULTILINE).findAll(text).lastOrNull()
            if (last == null) text
            else {
                val end = text.indexOf('\n', last.range.last + 1) + 1
                text.substring(0, end) + line + "\n" + text.substring(end)
            }
        }

        /** 없는 키만 추가(기존 값 유지)하고 Xcode 형식으로 다시 쓴다 */
        @Suppress("UNCHECKED_CAST")
        fun addXcstrings(text: String, entries: Map<String, Pair<String, String>>): String {
            val data = Json.parse(text) as MutableMap<String, Any?>
            val strings = data["strings"] as MutableMap<String, Any?>
            entries.forEach { (key, value) ->
                strings.putIfAbsent(key, linkedMapOf(
                    "extractionState" to "manual",
                    "localizations" to linkedMapOf(
                        "en" to linkedMapOf("stringUnit" to linkedMapOf("state" to "translated", "value" to value.second)),
                        "ko" to linkedMapOf("stringUnit" to linkedMapOf("state" to "translated", "value" to value.first)),
                    ),
                ))
            }
            return Json.writeXcode(data)
        }
    }
}
