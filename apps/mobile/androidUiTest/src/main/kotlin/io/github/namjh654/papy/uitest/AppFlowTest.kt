package io.github.namjh654.papy.uitest

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.MethodSorters
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * 앱 전 흐름 UI 테스트 — 설치된 dev 앱을 밖에서 조작한다. 실행: `scripts/orca.sh uitest android`
 *
 * - 서버는 로컬 목업(`mockServer`). 첫 테스트가 앱 데이터를 지워 온보딩부터 시작한다
 * - 테스트는 이름 순서로 돈다: 첫 실행 → 가입·재설정 → 로그인 → 각 화면 → 세션 만료 → 뒤로가기 → 탈퇴
 * - 앱 언어를 영어로 고정하고 영어 문구로 찾는다(`cmd locale set-app-locales`). 화면 문구를 바꾸면 여기의 문자열도 바꾼다
 * - iOS `iosApp/UITests`(XCUITest) 와 같은 흐름이다
 *
 * 인자(스크립트가 넘긴다): appPackage(빌드가 정함), mockUrl(기본 http://10.0.2.2:8765), capture(`orca.sh capture` 에서만 true)
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppFlowTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice = UiDevice.getInstance(instrumentation)
    private val args = InstrumentationRegistry.getArguments()
    private val pkg: String = args.getString("appPackage") ?: "io.github.namjh654.papy.dev"
    private val mockUrl: String = args.getString("mockUrl") ?: "http://10.0.2.2:8765"
    /** 디자인 비교용 화면 캡처(`orca.sh capture`) — 평소 uitest 에서는 [testCapture_auth] 를 건너뛴다 */
    private val capture: Boolean = args.getString("capture") == "true"
    /** 캡처 저장 위치. 테스트가 끝나면 Gradle 이 앱을 지우므로 셸 영역에 둔다(`orca.sh capture` 가 가져간다) */
    private val shotDir = "/data/local/tmp/papy-capture"
    /** 시스템 권한 창의 거부 버튼(언어와 무관) */
    private val denyButton = Pattern.compile(".*:id/permission_deny(_and_dont_ask_again)?_button")
    /** 시스템 사진 선택기(Photo Picker)와 그 안의 사진 */
    private val photoPicker = Pattern.compile(".*:id/(bottom_sheet|icon_thumbnail|empty_text)")
    private val photoThumbnail = Pattern.compile(".*:id/icon_thumbnail")

    /** 실패하면 그때 화면의 글자를 로그로 남긴다: `adb logcat -s AppFlowTest` */
    @get:Rule
    val screenOnFailure = object : TestWatcher() {
        override fun failed(e: Throwable, description: Description) {
            val texts = device.findObjects(By.textStartsWith("")).mapNotNull { it.text?.takeIf(String::isNotBlank) }
            Log.e("AppFlowTest", "${description.methodName} 실패 — 화면(${device.currentPackageName}): ${texts.joinToString(" | ")}")
        }
    }

    /** 테스트마다 앱 언어를 영어로 — `--only` 로 하나만 돌려도 같은 조건(DEC-121) */
    @Before
    fun fixLocale() {
        shell("cmd locale set-app-locales $pkg --locales en-US")
    }

    private val username = "tester"
    private val password = "abcd1234"

    // ============================================================ 흐름

    /** 첫 실행 온보딩(밀기·다음·시작하기) → 로그인. 다시 실행하면 바로 로그인 */
    @Test
    fun test01_onboarding() {
        shell("pm clear $pkg")
        shell("cmd locale set-app-locales $pkg --locales en-US")
        mock("reset")
        launch()
        assertTrue(has("Everything on Home", 20))
        device.findObject(By.text("Everything on Home")).swipe(Direction.LEFT, 0.8f)
        assertTrue(has("Just what you need"))
        settle()
        click("Next")
        assertTrue(has("Make it yours"))
        settle()
        click("Get started")
        assertTrue(has("Enter your username and password."))

        relaunch()
        assertTrue(has("Enter your username and password.", 15))
        assertFalse(has("Everything on Home", 1))
    }

    /** 약관(전체 동의·전문 보기) → 가입(8자 미만 오류) → 로그인 화면 토스트 */
    @Test
    fun test02_signup() {
        toLogin()
        click("Sign up")
        assertTrue(has("Please agree to the terms"))
        click("View")
        device.waitForIdle()
        back()
        click("Agree to all")
        click("Agree and continue")
        assertTrue(has("Choose a username"))
        fields(3).let { (id, pw, confirm) ->
            id.text = "newuser"
            pw.text = "1234"
            confirm.text = "1234"
        }
        click("Create account")
        assertTrue(has("at least 8 characters"))
        fields(3).let { (_, pw, confirm) ->
            pw.text = password
            confirm.text = password
        }
        click("Create account")
        assertTrue(has("You're signed up"))
    }

    /** 코드 요청 → 둘째 단계에서 뒤로 → 첫 단계 → 완료 → 로그인 화면 토스트 */
    @Test
    fun test03_passwordReset() {
        toLogin()
        click("Forgot your password?")
        assertTrue(has("send you a verification code"))
        fields(1)[0].text = username
        click("Get verification code")
        assertTrue(has("Enter the code sent to"))
        back()
        assertTrue(has("send you a verification code"))
        click("Get verification code")
        assertTrue(has("Enter the code sent to"))
        fields(3).let { (code, pw, confirm) ->
            code.text = "123456"
            pw.text = password
            confirm.text = password
        }
        click("Change password")
        assertTrue(has("Password changed"))
    }

    @Test
    fun test04_login() {
        toLogin()
        login()
    }

    /** 홈 → 공지사항 목록 → 상세 → 뒤로 */
    @Test
    fun test05_notice() {
        home()
        click("Notices")
        click("Notice 1")
        assertTrue(has("This is the body of notice 1"))
        back()
        assertTrue(has("Notice 2"))
    }

    /** 예시 탭: 항목 → 상세 → 즐겨찾기 → 목록에 배지 */
    @Test
    fun test06_exampleDetail() {
        home()
        tab("Example")
        click("Sample 1")
        click("Add to favorites")
        assertTrue(has("Remove from favorites"))
        back()
        assertTrue(hasExact("Favorite"))
    }

    /** 알림함: 눌러서 공지 상세, 모두 읽음 */
    @Test
    fun test07_inbox() {
        home()
        click("Notifications")
        click("New notice")
        assertTrue(has("Notice 3"))
        back()
        click("Mark all read")
        assertTrue(device.wait(Until.gone(By.text("Mark all read")), 5_000))
    }

    /** 검색: 입력이 멈추면 결과, 검색 버튼 → 최근 검색어, 결과 없음 */
    @Test
    fun test08_search() {
        home()
        click("Search")
        val field = fields(1)[0]
        field.text = "Sample"
        assertTrue(has("Example item 1"))
        device.pressEnter() // 키보드 검색 버튼 → 최근 검색어에 남는다
        fields(1)[0].text = ""
        assertTrue(has("Recent searches"))
        fields(1)[0].text = "zzzq"
        assertTrue(has("No results for"))
    }

    /** 권한: 카메라 두 번 거부 → "설정 열기" → 안내 다이얼로그 */
    @Test
    fun test09_permission() {
        home()
        click("Permissions")
        repeat(2) {
            allowButtons()[1].click() // 0: 알림, 1: 카메라
            // 권한 창은 기기마다 패키지가 다르고(com.android / com.google.android.permissioncontroller) 문구는 기기 언어를 따른다
            // → 버튼 id 로 찾는다. 두 번째 거부는 "다시 묻지 않음" 버튼일 수 있다
            val deny = device.wait(Until.findObject(By.res(denyButton)), 5_000)
            assertNotNull("권한 창이 뜨지 않음", deny)
            deny.click()
            assertTrue(has("Denied"))
        }
        click("Open settings")
        assertTrue(has("Allow it in Settings"))
        click("Cancel")
    }

    /** 프로필: (사진이 있으면) 사진 선택 → 저장(업로드), 이름 바꾼 뒤 뒤로 → 나가기 확인 */
    @Test
    fun test10_profile() {
        home()
        tab("Settings")
        click("tester@example.com")
        click("Change photo")
        // 사진 선택기가 뜰 때까지 기다린 뒤(처음 여는 선택기는 느리다) 첫 사진을 고른다. 설명 문구는 기기 언어를 따라 id 로 찾는다
        device.wait(Until.hasObject(By.res(photoPicker)), 10_000)
        val photo = device.findObject(By.res(photoThumbnail)) ?: device.findObject(By.descStartsWith("Photo taken"))
        if (photo != null) {
            photo.click()
            assertTrue(has("Change photo", 10))
            click("Save")
            assertTrue(has("Saved.", 10))
        } else {
            back() // 기기에 사진이 없다 — 사진 단계는 건너뛴다
        }
        fields(2)[0].text = "Changed"
        back()
        assertTrue(has("Leave without saving?"))
        click("Leave")
    }

    /** 외부 링크: 실행 중 papy://notice/5 → 상세, papy://settings */
    @Test
    fun test11_deepLink() {
        home()
        open("papy://notice/5")
        assertTrue(has("Notice 5"))
        open("papy://settings")
        assertTrue(has("App version"))
    }

    /** 테마: 다크·라이트를 고정했다가 시스템 설정으로 돌린다 */
    @Test
    fun test12_theme() {
        home()
        tab("Settings")
        for (option in listOf("Dark", "Light", "System")) {
            click("Theme")
            click(option)
            device.waitForIdle()
        }
        assertTrue(hasExact("System"))
    }

    /** 문의하기(25-3): 설정 → 문의하기 → 종류·내용 → 보내기(목업 서버 204) → 안내 → 설정으로 */
    @Test
    fun test12b_inquiry() {
        home()
        tab("Settings")
        scrollTo("Contact us").click()
        click("Report a bug")
        fields(2)[0].text = "The app closes when I open chat."
        click("Send")
        assertTrue(has("Message sent", 10))
        click("OK")
        assertTrue(has("Contact us"))
    }

    /** 알림 설정(26-2): 설정 → 알림 설정 → 광고성 스위치 → 처리 날짜 안내(목업 서버 저장) */
    @Test
    fun test12c_notificationSettings() {
        home()
        tab("Settings")
        click("Notifications")
        click("Offers & events (ads)")
        assertTrue(has("promotional messages", 8))
    }

    /** 파일 받기·보관(30-1): 대화방의 예시 PDF(목업 서버) → 받기 → (열 앱이 있으면 열렸다 돌아옴) "Saved" → 설정 › Saved files 목록 */
    @Test
    fun test12d_fileDownload() {
        home()
        tab("Messages")
        click("Papy")
        click("guide.pdf")
        // PDF 를 여는 다른 앱이 뜨면 돌아온다(에뮬레이터에 없으면 안내만 뜬다)
        Thread.sleep(3_000)
        if (device.currentPackageName != pkg) device.pressBack()
        assertTrue(has("Saved", 10))
        back()
        tab("Settings")
        scrollTo("Saved files").click()
        assertTrue(has("guide.pdf", 8))
        // 탭은 스크롤 위치를 기억한다 — 다음 테스트가 설정 맨 위(프로필)를 쓰므로 되돌린다
        back()
        scrollToTop()
    }

    /** 세션 만료: 인증 요청 401 + 재발급 401 → 로그인 화면 */
    @Test
    fun test13_sessionExpired() {
        home()
        tab("Settings")
        scrollToTop() // 앞 테스트가 설정을 아래로 내려 두었을 수 있다
        click("tester@example.com")
        fields(2)[0].text = "Expired"
        mock("expire")
        try {
            click("Save")
            assertTrue(has("Enter your username and password.", 15))
        } finally {
            mock("reset")
        }
    }

    /** 소셜 로그인(dev mock, DEC-108): 카카오 버튼 → 목업 서버와 교환 → 홈 → 로그아웃하면 로그인 화면(SDK 세션 정리 경로) */
    @Test
    fun test13b_socialLogin() {
        toLogin()
        scrollTo("Continue with Kakao").click()
        assertTrue(has("Welcome", 15))
        tab("Settings")
        scrollTo("Log out").click()
        assertTrue(has("Log out?"))
        device.findObjects(By.text("Log out")).last().click() // 설정 행과 확인 버튼이 같은 글자 → 대화상자 쪽
        assertTrue(has("Enter your username and password.", 10))
    }

    /**
     * 처음 가입한 소셜 사용자(18-4, 목업은 네이버를 신규로 응답): 약관 화면에서 뒤로 → 로그아웃·로그인 화면,
     * 다시 → 동의 → 메인 → 탈퇴는 비밀번호 없이
     */
    @Test
    fun test13c_socialSignupConsentAndWithdraw() {
        toLogin()
        scrollTo("Continue with Naver").click()
        assertTrue(has("Please agree to the terms", 15))
        back()
        assertTrue(has("Enter your username and password.", 10))

        scrollTo("Continue with Naver").click()
        assertTrue(has("Please agree to the terms", 15))
        click("Agree to all")
        click("Agree and continue")
        assertTrue(has("Welcome", 15))

        tab("Settings")
        scrollTo("Delete account").click()
        assertTrue(has("delete my account"))
        settle()
        assertTrue("소셜 사용자에게 비밀번호 칸", device.findObjects(By.clazz("android.widget.EditText").pkg(pkg)).isEmpty())
        click("I understand and agree to delete my account")
        device.findObjects(By.text("Delete account")).last().click() // 하단 버튼
        assertTrue(has("Delete this account?"))
        device.findObjects(By.text("Delete account")).last().click() // 확인 다이얼로그
        assertTrue(has("Enter your username and password.", 15))
    }

    /** Android 뒤로가기: 다른 탭 → 홈 탭 → "한 번 더 누르면 종료" → 종료 */
    @Test
    fun test14_backToHomeThenExit() {
        home()
        tab("Settings")
        back()
        assertTrue(has("Welcome"))
        back()
        assertTrue(has("Press back again to exit"))
        back()
        assertTrue("앱이 종료되지 않음", device.wait(Until.gone(By.pkg(pkg)), 5_000))
    }

    /** 회원 탈퇴 → 로그인 화면 (마지막에 둔다) */
    @Test
    fun test99_withdraw() {
        home()
        tab("Settings")
        scrollTo("Delete account").click()
        assertTrue(has("delete my account"))
        fields(1)[0].text = password
        click("I understand and agree to delete my account")
        device.findObjects(By.text("Delete account")).last().click() // 하단 버튼
        assertTrue(has("Delete this account?"))
        device.findObjects(By.text("Delete account")).last().click() // 확인 다이얼로그
        assertTrue(has("Enter your username and password.", 15))
    }

    /**
     * 디자인 비교 캡처(DEC-112) — iOS `testCapture_auth` 와 같은 순서·같은 이름, 한국어.
     * 온보딩 → 로그인 → 로그인 실패(틀린 비밀번호) → 약관 → 회원가입 → 재설정 1·2단계 → (21-5) 메시지 목록·대화방 → 커뮤니티 목록·글 상세
     */
    @Test
    fun testCapture_auth() {
        assumeTrue("orca.sh capture 에서만", capture)
        shell("pm clear $pkg")
        shell("cmd locale set-app-locales $pkg --locales ko-KR")
        shell("rm -rf $shotDir")
        shell("mkdir -p $shotDir")
        mock("reset")
        try {
            launch()
            assertTrue(has("한눈에 보는 홈", 20))
            shot("01-onboarding")
            device.findObject(By.text("한눈에 보는 홈")).swipe(Direction.LEFT, 0.8f)
            assertTrue(has("필요한 정보만 빠르게"))
            click("다음")
            assertTrue(has("내게 맞는 설정"))
            click("시작하기")
            assertTrue(has("아이디와 비밀번호를 입력해 주세요."))
            shot("02-login")
            fields(2).let { (id, pw) ->
                id.text = username
                pw.text = "wrong" // 목업 서버가 401 로 거절한다
            }
            device.findObjects(By.text("로그인")).last().click() // 제목과 버튼이 같은 글자 → 아래쪽(버튼)
            Thread.sleep(1_000)
            shot("03-login-error")
            fields(2).forEach { it.text = "" }
            click("회원가입")
            assertTrue(has("약관에 동의해 주세요"))
            shot("04-terms")
            click("전체 동의")
            click("동의하고 계속")
            fields(3)
            shot("05-signup")
            repeat(3) { if (!has("비밀번호를 잊으셨나요?", 1)) back() }
            click("비밀번호를 잊으셨나요?")
            assertTrue(has("가입한 아이디를 입력하면"))
            shot("06-reset-request")
            fields(1)[0].text = username
            click("인증 코드 받기")
            assertTrue(has("계정으로 보낸 인증 코드"))
            shot("07-reset-confirm")

            // 메인(21-5): 로그인 → 메시지 목록·대화방 → 커뮤니티 목록·글 상세 (예시 데이터라 시각만 다르다)
            repeat(4) { if (!has("아이디와 비밀번호를 입력해 주세요", 1)) back() }
            fields(2).let { (id, pw) ->
                id.text = username
                pw.text = password
            }
            device.findObjects(By.text("로그인")).last().click()
            assertTrue(has("반가워요", 15))
            tab("메시지")
            assertTrue(has("Papy", 10))
            shot("08-chat-list")
            click("Papy")
            assertTrue(has("Sample message 60", 10))
            shot("09-chat-room")
            back()
            tab("커뮤니티")
            assertTrue(has("Sample post 1", 10))
            shot("10-community")
            click("Sample post 2. This is what a longer post looks like in the list. ")
            assertTrue(has("Sample comment 1", 10))
            shot("11-post")
        } finally {
            shell("cmd locale set-app-locales $pkg --locales en-US")
        }
    }

    /** 전환이 끝난 화면을 [shotDir]/[name].png 로 저장 */
    private fun shot(name: String) {
        settle()
        shell("screencap -p $shotDir/$name.png")
    }

    // ============================================================ 도우미

    /** 로그인 상태로 홈 탭까지 — 어떤 화면에 있든(로그인 전·다른 화면·시스템 창) 홈으로 돌아온다 */
    private fun home() {
        launch()
        for (i in 0 until 6) {
            if (device.currentPackageName != pkg) {
                device.pressBack() // 남아 있는 시스템 창(권한 등)
                launch()
            }
            if (has("Skip", 1)) click("Skip")
            if (has("Enter your username and password.", 1)) login()
            if (tabsVisible()) break
            back()
            if (has("Leave without saving?", 1)) click("Leave")
        }
        tab("Home")
        // 탭마다 스택을 기억한다 — 홈 탭 위에 열려 있던 화면(딥링크 등)은 닫는다
        for (i in 0 until 3) {
            if (has("Welcome", 2)) break
            back()
        }
        assertTrue("로그인 상태가 아님", has("Welcome", 5))
    }

    private fun tabsVisible(): Boolean =
        device.hasObject(By.text("Home").pkg(pkg)) && device.hasObject(By.text("Settings").pkg(pkg))

    /** 로그인 화면까지(첫 실행이면 온보딩을 건너뛰고, 다른 인증 화면이면 닫는다) */
    private fun toLogin() {
        launch()
        for (i in 0 until 4) {
            if (has("Skip", 1)) click("Skip")
            if (has("Enter your username and password.", 2)) return
            back()
            if (device.currentPackageName != pkg) launch()
        }
        assertTrue(has("Enter your username and password."))
    }

    /** 쪽 넘김·화면 전환 애니메이션이 끝나기를 기다린다 */
    private fun settle() {
        device.waitForIdle()
        Thread.sleep(600)
    }

    private fun login() {
        assertTrue(has("Enter your username and password."))
        fields(2).let { (id, pw) ->
            id.text = username
            pw.text = password
        }
        device.findObjects(By.text("Log in")).last().click() // 제목과 버튼이 같은 글자 → 아래쪽(버튼)
        assertTrue(has("Welcome", 15))
    }

    /** 앱을 앞으로(이미 떠 있으면 그대로) */
    private fun launch() {
        val context = instrumentation.context
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: error("앱이 설치되지 않음: $pkg")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        assertTrue("앱이 뜨지 않음", device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 15_000))
    }

    private fun relaunch() {
        shell("am force-stop $pkg")
        launch()
    }

    private fun open(link: String) {
        shell("am start -a android.intent.action.VIEW -d $link $pkg")
        device.waitForIdle()
    }

    private fun tab(label: String) {
        // 하단 탭은 화면 맨 아래에 있는 같은 글자
        settle()
        val candidates = device.findObjects(By.text(label).pkg(pkg))
        assertTrue("탭 없음: $label", candidates.isNotEmpty())
        candidates.maxBy { it.visibleBounds.top }.click()
        device.waitForIdle()
    }

    private fun click(text: String) {
        settle() // 전환 애니메이션 중에 누르면 움직이는 중인 좌표를 누른다
        val target = device.wait(Until.findObject(By.text(text)), 8_000)
            ?: device.wait(Until.findObject(By.desc(text)), 2_000)
        assertNotNull("없음: $text", target)
        target.click()
        device.waitForIdle()
    }

    private fun allowButtons(): List<UiObject2> {
        device.wait(Until.hasObject(By.text("Allow")), 8_000)
        settle()
        return device.findObjects(By.text("Allow").pkg(pkg)).sortedBy { it.visibleBounds.top }
    }

    private fun has(text: String, seconds: Long = 8): Boolean =
        device.wait(Until.hasObject(By.textContains(text)), seconds * 1_000) == true

    private fun hasExact(text: String, seconds: Long = 8): Boolean =
        device.wait(Until.hasObject(By.text(text).pkg(pkg)), seconds * 1_000) == true

    /** 화면의 입력칸을 위에서부터 [count] 개 */
    private fun fields(count: Int): List<UiObject2> {
        // 화면 전환 중에는 이전 화면의 입력칸도 남아 있다 → 전환이 끝난 뒤 찾는다
        settle()
        val selector: BySelector = By.clazz("android.widget.EditText").pkg(pkg)
        device.wait(Until.hasObject(selector), 8_000)
        val found = device.findObjects(selector).sortedBy { it.visibleBounds.top }
        assertTrue("입력칸이 ${found.size}개 (필요 $count)", found.size >= count)
        return found
    }

    private fun scrollTo(text: String): UiObject2 {
        repeat(6) {
            settle() // 스크롤이 멈춘 뒤 찾는다(움직이는 중에 누르면 빗나간다)
            device.findObject(By.text(text))?.let { return it }
            device.findObject(By.scrollable(true).pkg(pkg))?.scroll(Direction.DOWN, 0.8f)
        }
        return device.findObject(By.text(text)) ?: error("없음: $text")
    }

    /** 화면의 스크롤 목록을 맨 위로 */
    private fun scrollToTop() {
        settle()
        device.findObject(By.scrollable(true).pkg(pkg))?.scroll(Direction.UP, 1f)
        device.findObject(By.scrollable(true).pkg(pkg))?.scroll(Direction.UP, 1f)
        settle()
    }

    private fun back() {
        settle()
        device.pressBack()
        device.waitForIdle()
    }

    private fun shell(command: String): String = device.executeShellCommand(command)

    /** 목업 서버 제어(`/__mock/<command>`) */
    private fun mock(command: String) {
        val connection = URL("$mockUrl/__mock/$command").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }
}
