import XCTest

/// 앱 전 흐름 UI 테스트 — 설치된 앱(번들 ID)을 조작한다. 실행: `scripts/orca.sh uitest`
///
/// - 서버는 로컬 목업(`mockServer`), 앱은 새로 설치한 상태에서 시작한다(온보딩부터)
/// - 테스트는 이름 순서로 돈다: 첫 실행 → 가입·재설정 → 로그인 → 각 화면 → 세션 만료 → 탈퇴
/// - 문구는 한국어로 찾는다(앱을 ko 로 실행). 화면 문구를 바꾸면 여기의 문자열도 바꾼다
/// - 화면 캡처는 결과 번들(.xcresult)에 남는다
///
/// 환경(스크립트가 TEST_RUNNER_ 접두어로 넘긴다): APP_BUNDLE_ID, MOCK_URL, CAPTURE(`orca.sh capture` 에서만 1)
final class AppFlowTests: XCTestCase {
    private let env = ProcessInfo.processInfo.environment
    private lazy var app = XCUIApplication(bundleIdentifier: env["APP_BUNDLE_ID"] ?? "io.github.namjh654.papy.dev")
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    private let username = "tester"
    private let password = "abcd1234"

    override func setUp() {
        continueAfterFailure = false
        app.launchArguments = ["-AppleLanguages", "(ko)", "-AppleLocale", "ko_KR"]
    }

    // MARK: - 흐름

    /// 첫 실행 온보딩(밀기·다음·시작하기) → 로그인. 다시 실행하면 바로 로그인
    func test01_onboarding() {
        app.launch()
        XCTAssertTrue(has("한눈에 보는 홈", 15))
        app.swipeLeft()
        XCTAssertTrue(has("필요한 정보만 빠르게"))
        el("다음").tap()
        XCTAssertTrue(has("내게 맞는 설정"))
        shot("onboarding")
        el("시작하기").tap()
        XCTAssertTrue(has("아이디와 비밀번호를 입력해 주세요"))
        app.terminate()
        app.launch()
        XCTAssertTrue(has("아이디와 비밀번호를 입력해 주세요", 10))
        XCTAssertFalse(has("한눈에 보는 홈", 1))
    }

    /// 약관(전체 동의·전문 보기) → 가입(8자 미만 오류) → 로그인 화면 토스트
    func test02_signup() {
        app.launch()
        el("회원가입").tap()
        XCTAssertTrue(has("약관에 동의해 주세요"))
        el("보기").tap()
        sleep(2)
        shot("terms-web")
        back()
        el("전체 동의").tap()
        el("동의하고 계속").tap()
        type("아이디 입력", "newuser")
        type("8자 이상", "1234")
        type("비밀번호 다시 입력", "1234")
        dismissKeyboard()
        el("가입하기").tap()
        XCTAssertTrue(has("8자 이상이어야"))
        type("8자 이상", password)
        type("비밀번호 다시 입력", password)
        dismissKeyboard()
        el("가입하기").tap()
        XCTAssertTrue(hasToast("가입했어요"))
    }

    /// 코드 요청 → 둘째 단계에서 뒤로 → 첫 단계 → 완료 → 로그인 화면 토스트
    func test03_passwordReset() {
        toLogin()
        el("비밀번호를 잊으셨나요?").tap()
        type("아이디 입력", username)
        el("인증 코드 받기").tap()
        XCTAssertTrue(has("계정으로 보낸 인증 코드"))
        back()
        XCTAssertTrue(has("가입한 아이디를 입력하면"))
        el("인증 코드 받기").tap()
        type("인증 코드 입력", "123456")
        type("8자 이상", password)
        type("비밀번호 다시 입력", password)
        dismissKeyboard()
        el("비밀번호 바꾸기").tap()
        XCTAssertTrue(hasToast("비밀번호를 바꿨어요"))
    }

    func test04_login() {
        app.launch()
        login()
        shot("home")
    }

    /// 홈 → 공지사항 목록 → 상세 → 뒤로
    func test05_notice() {
        home()
        el("공지사항").tap()
        XCTAssertTrue(has("Notice 1"))
        app.buttons.matching(label(beginsWith: "Notice 1")).firstMatch.tap()
        XCTAssertTrue(has("This is the body of notice 1"))
        back()
    }

    /// 예시 탭: 항목 → 상세 → 즐겨찾기 → 목록에 배지
    func test06_exampleDetail() {
        home()
        app.tabBars.buttons["예시"].tap()
        app.buttons.matching(label(beginsWith: "Sample 1")).firstMatch.tap()
        el("즐겨찾기에 추가").tap()
        XCTAssertTrue(has("즐겨찾기 해제"))
        back()
        XCTAssertTrue(app.buttons.matching(label(contains: "즐겨찾기")).firstMatch.waitForExistence(timeout: 3))
        shot("example-badge")
    }

    /// 알림함: 눌러서 공지 상세, 모두 읽음
    func test07_inbox() {
        home()
        el("알림").tap()
        app.buttons.matching(label(beginsWith: "New notice")).firstMatch.tap()
        XCTAssertTrue(has("Notice 3"))
        back()
        el("모두 읽음").tap()
        shot("inbox-all-read")
    }

    /// 검색: 입력이 멈추면 결과, 검색 버튼 → 최근 검색어, 결과 없음
    func test08_search() {
        home()
        el("검색").tap()
        let field = self.field("검색어 입력")
        field.tap()
        field.typeText("Sample")
        XCTAssertTrue(has("Example item 1"))
        field.typeText("\n")
        field.tap()
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 6))
        XCTAssertTrue(has("최근 검색어"))
        field.typeText("zzzq")
        XCTAssertTrue(has("검색 결과가 없어요"))
    }

    /// 권한: 카메라 거부 → 다시 누르면 "설정에서 허용해 주세요"
    func test09_permission() {
        home()
        el("권한").tap()
        let allow = app.buttons.matching(NSPredicate(format: "label == '허용하기'"))
        allow.element(boundBy: 1).tap()
        let deny = springboard.buttons.matching(NSPredicate(format: "label IN {'허용 안 함', \"Don’t Allow\", \"Don't Allow\"}")).firstMatch
        XCTAssertTrue(deny.waitForExistence(timeout: 5))
        deny.tap()
        XCTAssertTrue(has("거부됨"))
        el("설정 열기").tap()
        XCTAssertTrue(has("설정에서 허용해 주세요"))
        el("취소").tap()
    }

    /// 프로필: 사진 선택 → 저장(업로드), 이름 바꾼 뒤 뒤로 → 나가기 확인
    func test10_profile() {
        home()
        app.tabBars.buttons["설정"].tap()
        app.buttons.matching(label(contains: "tester@example.com")).firstMatch.tap()
        let change = el("사진 변경")
        change.tap()
        sleep(3)
        shot("photo-picker")
        // 사진 선택기는 다른 프로세스라 좌표로 사진을 누른다(시뮬레이터 기본 사진). 안내 배너 유무로 위치가 달라 차례로 시도
        for y in [0.43, 0.35, 0.52] where !change.isHittable {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.17, dy: y)).tap()
            sleep(2)
        }
        XCTAssertTrue(change.isHittable, "사진 선택기가 닫히지 않음")
        el("저장").tap()
        XCTAssertTrue(has("저장했어요", 8))
        type("이름 입력", "바뀐이름")
        dismissKeyboard()
        back()
        XCTAssertTrue(has("저장하지 않고 나갈까요?"))
        el("나가기").tap()
    }

    /// 외부 링크: 실행 중 papy://notice/5 → 상세, papy://settings
    func test11_deepLink() {
        home()
        open("papy://notice/5")
        XCTAssertTrue(has("Notice 5"))
        open("papy://settings")
        XCTAssertTrue(has("앱 버전"))
    }

    /// 테마: 앱에서 라이트·다크를 고정했다가 시스템 설정으로 돌린다
    func test12_theme() {
        home()
        app.tabBars.buttons["설정"].tap()
        for option in ["다크", "라이트", "시스템 설정"] {
            app.buttons.matching(label(beginsWith: "테마")).firstMatch.tap()
            el(option).tap()
            sleep(1)
            shot("theme-\(option)")
        }
        XCTAssertTrue(app.buttons.matching(label(contains: "시스템 설정")).firstMatch.exists)
    }

    /// 문의하기(25-3): 설정 → 문의하기 → 종류·내용 → 보내기(목업 서버 204) → 안내 → 설정으로
    func test12b_inquiry() {
        home()
        app.tabBars.buttons["설정"].tap()
        let row = el("문의하기")
        if !row.isHittable { app.swipeUp() }
        row.tap()
        el("오류 신고").tap()
        // 여러 줄 입력칸은 textView 로 보일 수 있다
        let content = app.textViews.firstMatch.waitForExistence(timeout: 2) ? app.textViews.firstMatch : field("어떤 상황인지 자세히 적어 주세요")
        content.tap()
        content.typeText("채팅을 열면 앱이 꺼져요.")
        dismissKeyboard()
        el("보내기").tap()
        XCTAssertTrue(has("문의를 보냈어요", 10))
        el("확인").tap()
        XCTAssertTrue(el("문의하기").waitForExistence(timeout: 5))
    }

    /// 알림 설정(26-2): 설정 → 알림 설정 → 광고성 스위치 → 처리 날짜 안내(목업 서버 저장)
    func test12c_notificationSettings() {
        home()
        app.tabBars.buttons["설정"].tap()
        el("알림 설정").tap()
        let marketing = app.switches.matching(label(contains: "광고성")).firstMatch
        XCTAssertTrue(marketing.waitForExistence(timeout: 8))
        marketing.tap()
        XCTAssertTrue(hasToast("광고성 정보 수신", 8))
    }

    /// 파일 받기·보관(30-1): 대화방의 예시 PDF(목업 서버) → 받기 → 미리 보기 → "저장됨" → 설정 › 저장한 파일 목록
    func test12d_fileDownload() {
        home()
        app.tabBars.buttons["메시지"].tap()
        el("Papy").tap()
        // 예시 PDF 는 가장 최근 메시지(맨 아래). 저장된 메시지를 먼저 보여 준 뒤 새로 받으면 위치가 위에 남을 수 있어 아래로 내린다
        let file = app.descendants(matching: .any).matching(label(contains: "guide.pdf")).firstMatch
        for _ in 0..<8 where !file.waitForExistence(timeout: 2) { app.swipeUp() }
        XCTAssertTrue(file.exists, "guide.pdf 말풍선 없음")
        file.tap()
        XCTAssertTrue(has("저장됨", 10))
        // QuickLook 미리 보기는 다른 프로세스라 테스트가 볼 수 없다. 닫기(✕)가 대화방 뒤로 버튼과 같은 자리라
        // 탭 바가 보일 때까지 뒤로 — 처음은 미리 보기 닫기, 다음은 대화방 나가기
        for _ in 0..<3 where !app.tabBars.buttons["설정"].waitForExistence(timeout: 3) { back() }
        app.tabBars.buttons["설정"].tap()
        let row = el("저장한 파일")
        if !row.isHittable { app.swipeUp() }
        row.tap()
        XCTAssertTrue(has("guide.pdf", 8))
        // 다음 테스트가 설정 맨 위(프로필)를 쓰므로 설정 목록을 맨 위로 되돌린다
        back()
        app.swipeDown()
    }

    /// 세션 만료: 인증 요청 401 + 재발급 401 → 로그인 화면
    func test13_sessionExpired() {
        home()
        app.tabBars.buttons["설정"].tap()
        app.buttons.matching(label(contains: "tester@example.com")).firstMatch.tap()
        type("이름 입력", "만료")
        dismissKeyboard()
        mock("expire")
        defer { mock("reset") }
        el("저장").tap()
        XCTAssertTrue(has("아이디와 비밀번호를 입력해 주세요", 10))
    }

    /// 소셜 로그인(dev mock, DEC-108): 카카오 버튼 → 목업 서버와 교환 → 홈 → 로그아웃하면 로그인 화면(SDK 세션 정리 경로)
    func test13b_socialLogin() {
        toLogin()
        let kakao = el("카카오로 시작하기")
        if !kakao.isHittable { app.swipeUp() }
        kakao.tap()
        XCTAssertTrue(has("반가워요", 15))
        app.tabBars.buttons["설정"].tap()
        let logout = el("로그아웃")
        if !logout.isHittable { app.swipeUp() }
        logout.tap()
        let confirm = app.alerts.buttons["로그아웃"]
        if confirm.waitForExistence(timeout: 3) { confirm.tap() }
        XCTAssertTrue(has("아이디와 비밀번호를 입력해 주세요", 10))
    }

    /// 처음 가입한 소셜 사용자(18-4, 목업은 네이버를 신규로 응답): 약관 화면에서 뒤로 → 로그아웃·로그인 화면,
    /// 다시 → 동의 → 메인 → 탈퇴는 비밀번호 없이. (Android `test13c_socialSignupConsentAndWithdraw`)
    func test13c_socialSignupConsentAndWithdraw() {
        toLogin()
        func tapNaver() {
            let naver = el("네이버로 시작하기")
            // 마지막 버튼은 하단 고정 로그인 버튼 뒤에 가려져도 isHittable 이 true — 항상 끝까지 올린다
            app.swipeUp()
            naver.tap()
        }
        tapNaver()
        XCTAssertTrue(has("약관에 동의해 주세요", 15))
        app.navigationBars.buttons["뒤로"].tap()
        XCTAssertTrue(has("아이디와 비밀번호를 입력해 주세요", 10))

        tapNaver()
        XCTAssertTrue(has("약관에 동의해 주세요", 15))
        el("전체 동의").tap()
        el("동의하고 계속").tap()
        XCTAssertTrue(has("반가워요", 15))

        app.tabBars.buttons["설정"].tap()
        let row = el("회원 탈퇴")
        if !row.isHittable { app.swipeUp() }
        row.tap()
        XCTAssertTrue(has("안내를 확인했고 탈퇴에 동의해요"))
        XCTAssertFalse(app.secureTextFields.firstMatch.exists, "소셜 사용자에게 비밀번호 칸")
        el("안내를 확인했고 탈퇴에 동의해요").tap()
        el("탈퇴하기").tap()
        let confirm = app.alerts.buttons.element(boundBy: app.alerts.buttons.count - 1)
        if confirm.waitForExistence(timeout: 2) { confirm.tap() } else { el("탈퇴하기").tap() }
        XCTAssertTrue(has("아이디와 비밀번호를 입력해 주세요", 10))
    }

    /// 회원 탈퇴 → 로그인 화면 (마지막에 둔다)
    func test99_withdraw() {
        home()
        app.tabBars.buttons["설정"].tap()
        let row = el("회원 탈퇴")
        if !row.isHittable { app.swipeUp() }
        row.tap()
        type("비밀번호 입력", password)
        dismissKeyboard()
        el("안내를 확인했고 탈퇴에 동의해요").tap()
        el("탈퇴하기").tap()
        let confirm = app.alerts.buttons.element(boundBy: app.alerts.buttons.count - 1)
        if confirm.waitForExistence(timeout: 2) { confirm.tap() } else { el("탈퇴하기").tap() }
        XCTAssertTrue(has("아이디와 비밀번호를 입력해 주세요", 10))
    }

    /// 디자인 비교 캡처(DEC-112) — Android `testCapture_auth` 와 같은 순서·같은 이름, 한국어.
    /// 온보딩 → 로그인 → 로그인 실패(틀린 비밀번호) → 약관 → 회원가입 → 재설정 1·2단계. 앱이 새로 설치된 상태에서 시작한다
    func testCapture_auth() throws {
        try XCTSkipUnless(env["CAPTURE"] == "1", "orca.sh capture 에서만")
        app.launch()
        XCTAssertTrue(has("한눈에 보는 홈", 15))
        capture("01-onboarding")
        app.swipeLeft()
        XCTAssertTrue(has("필요한 정보만 빠르게"))
        el("다음").tap()
        XCTAssertTrue(has("내게 맞는 설정"))
        el("시작하기").tap()
        XCTAssertTrue(has("아이디와 비밀번호를 입력해 주세요"))
        capture("02-login")
        type("아이디 입력", username)
        type("비밀번호 입력", "wrong") // 목업 서버가 401 로 거절한다
        dismissKeyboard()
        el("로그인").tap()
        sleep(1)
        capture("03-login-error")
        type("아이디 입력", "")
        type("비밀번호 입력", "")
        dismissKeyboard()
        el("회원가입").tap()
        XCTAssertTrue(has("약관에 동의해 주세요"))
        capture("04-terms")
        el("전체 동의").tap()
        el("동의하고 계속").tap()
        XCTAssertTrue(field("아이디 입력").exists)
        capture("05-signup")
        for _ in 0..<3 where !has("비밀번호를 잊으셨나요?", 1) { back() }
        el("비밀번호를 잊으셨나요?").tap()
        XCTAssertTrue(has("가입한 아이디를 입력하면"))
        capture("06-reset-request")
        type("아이디 입력", username)
        el("인증 코드 받기").tap() // test03 과 같게(키보드 위 하단 버튼을 바로 누른다)
        XCTAssertTrue(has("계정으로 보낸 인증 코드"))
        dismissKeyboard()
        capture("07-reset-confirm")

        // 메인(21-5): 로그인 → 메시지 목록·대화방 → 커뮤니티 목록·글 상세 (Android 와 같은 순서·이름)
        for _ in 0..<4 where !has("아이디와 비밀번호를 입력해 주세요", 1) { back() }
        login()
        app.tabBars.buttons["메시지"].tap()
        XCTAssertTrue(has("Papy", 10))
        capture("08-chat-list")
        el("Papy").tap()
        XCTAssertTrue(has("Sample message 60", 10))
        capture("09-chat-room")
        back()
        app.tabBars.buttons["커뮤니티"].tap()
        XCTAssertTrue(has("Sample post 1", 10))
        capture("10-community")
        el("Sample post 2. This is what a longer post looks like in the list. ").tap()
        XCTAssertTrue(has("Sample comment 1", 10))
        capture("11-post")
    }

    // MARK: - 도우미

    /// 로그인 화면까지 — 새로 설치했으면 온보딩을 건너뛰고, 로그인돼 있으면 로그아웃한다. `--only` 로 하나만 돌려도 된다(DEC-121)
    private func toLogin() {
        app.launch()
        let skip = app.buttons.matching(NSPredicate(format: "label == '건너뛰기'")).firstMatch
        if skip.waitForExistence(timeout: 3) { skip.tap() }
        if has("아이디와 비밀번호를 입력해 주세요", 3) { return }
        app.tabBars.buttons["설정"].tap()
        let logout = el("로그아웃")
        if !logout.isHittable { app.swipeUp() }
        logout.tap()
        let confirm = app.alerts.buttons["로그아웃"]
        if confirm.waitForExistence(timeout: 3) { confirm.tap() }
        XCTAssertTrue(has("아이디와 비밀번호를 입력해 주세요", 10))
    }

    /// 전환이 끝난 화면을 이름 그대로 결과 번들에 남긴다(`orca.sh capture` 가 꺼낸다)
    private func capture(_ name: String) {
        sleep(1)
        shot(name)
    }

    /// 로그인 상태로 홈 탭까지(필요하면 로그인, 열린 화면은 닫는다)
    private func home() {
        app.activate()
        let skip = app.buttons.matching(NSPredicate(format: "label == '건너뛰기'")).firstMatch
        if skip.waitForExistence(timeout: 2) { skip.tap() }
        if has("아이디와 비밀번호를 입력해 주세요", 3) { login() }
        for _ in 0..<4 {
            let b = app.navigationBars.buttons.firstMatch
            guard b.waitForExistence(timeout: 1) else { break }
            b.tap()
            let leave = app.buttons.matching(NSPredicate(format: "label == '나가기'")).firstMatch
            if leave.waitForExistence(timeout: 1) { leave.tap() }
        }
        let tab = app.tabBars.buttons["홈"]
        XCTAssertTrue(tab.waitForExistence(timeout: 5), "로그인 상태가 아님")
        tab.tap()
    }

    private func login() {
        type("아이디 입력", username)
        type("비밀번호 입력", password)
        dismissKeyboard()
        el("로그인").tap()
        XCTAssertTrue(has("반가워요", 10))
    }

    private func el(_ text: String, _ timeout: TimeInterval = 5) -> XCUIElement {
        let button = app.buttons.matching(NSPredicate(format: "label == %@", text)).firstMatch
        if button.waitForExistence(timeout: 1) { return button }
        let any = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@ OR identifier == %@", text, text)).firstMatch
        XCTAssertTrue(any.waitForExistence(timeout: timeout), "없음: \(text)")
        return any
    }

    /// 토스트(`papy.toast`)는 곧 사라지므로 식별자로 바로 찾는다
    private func hasToast(_ text: String, _ timeout: TimeInterval = 5) -> Bool {
        // 글자 요소로 바로 묻는다(전체 트리를 훑는 .any 는 느려서, 앞 테스트가 많이 돈 뒤에는 2.5초 안에 못 찾을 때가 있었다)
        let toast = app.staticTexts["papy.toast"]
        return toast.waitForExistence(timeout: timeout) && toast.label.contains(text)
    }

    private func has(_ text: String, _ timeout: TimeInterval = 5) -> Bool {
        app.descendants(matching: .any).matching(label(contains: text)).firstMatch.waitForExistence(timeout: timeout)
    }

    private func field(_ placeholder: String) -> XCUIElement {
        let p = NSPredicate(format: "placeholderValue == %@", placeholder)
        let text = app.textFields.matching(p).firstMatch
        if text.waitForExistence(timeout: 2) { return text }
        let secure = app.secureTextFields.matching(p).firstMatch
        XCTAssertTrue(secure.waitForExistence(timeout: 3), "입력칸 없음: \(placeholder)")
        return secure
    }

    /// 입력칸을 비우고 새로 입력한다
    private func type(_ placeholder: String, _ text: String) {
        let f = field(placeholder)
        f.tap()
        // 새 비밀번호 칸(.newPassword)에는 시스템이 "강력한 암호를 사용하겠습니까?" 판을 띄울 때가 있다 — 닫고 직접 입력
        if app.buttons["GenerateStrongPasswordButton"].waitForExistence(timeout: 1) {
            app.buttons["xmark"].firstMatch.tap()
            f.tap()
        }
        // 앞 칸의 강력한 암호 제안 등이 떠 있으면 첫 탭에 포커스가 안 옮겨질 때가 있다 — 확인하고 한 번 더
        if !(f.value(forKey: "hasKeyboardFocus") as? Bool ?? true) {
            sleep(1)
            f.tap()
        }
        if let value = f.value as? String, !value.isEmpty, value != placeholder {
            f.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: value.count))
        }
        f.typeText(text)
    }

    /// 키보드 위 "완료" 버튼, 없으면 빈 곳을 누른다
    private func dismissKeyboard() {
        guard app.keyboards.firstMatch.exists else { return }
        let done = app.buttons.matching(NSPredicate(format: "label == '완료'")).firstMatch
        if done.exists { done.tap() } else { app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap() }
        sleep(1)
    }

    private func back() {
        let b = app.navigationBars.buttons.firstMatch
        XCTAssertTrue(b.waitForExistence(timeout: 3))
        b.tap()
    }

    /// 외부 링크로 열기 — 시스템 "열기" 확인을 누른다
    private func open(_ link: String) {
        XCUIDevice.shared.system.open(URL(string: link)!)
        let openButton = springboard.buttons.matching(NSPredicate(format: "label == '열기' OR label == 'Open'")).firstMatch
        if openButton.waitForExistence(timeout: 3) { openButton.tap() }
    }

    /// 목업 서버 제어(`/__mock/<command>`)
    private func mock(_ command: String) {
        guard let base = env["MOCK_URL"], let url = URL(string: "\(base)/__mock/\(command)") else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        let done = expectation(description: "mock \(command)")
        URLSession.shared.dataTask(with: request) { _, _, _ in done.fulfill() }.resume()
        wait(for: [done], timeout: 5)
    }

    private func shot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func label(contains text: String) -> NSPredicate { NSPredicate(format: "label CONTAINS %@", text) }
    private func label(beginsWith text: String) -> NSPredicate { NSPredicate(format: "label BEGINSWITH %@", text) }
}
