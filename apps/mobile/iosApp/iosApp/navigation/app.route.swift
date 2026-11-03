import Foundation

/// 앱 최상위 단계입니다. 세션 상태에 따라 루트 화면을 교체합니다.
enum AppPhase: Hashable {
    case splash
    /// 처음 실행 때 한 번
    case onboarding
    case auth
    case main
}

/// 인증 흐름(NavigationStack) 내부 경로입니다.
///
/// Route는 iOS에만 존재하며 KMP ViewModel은 이 타입을 알지 못합니다.
enum AppRoute: Hashable {
    /// 약관 동의(회원가입 전)
    case terms

    /// 처음 가입한 소셜 사용자의 약관 동의(세션은 이미 시작됨, 18-4)
    case socialConsent

    /// 회원가입 — 약관 화면의 선택 동의 값을 넘긴다
    case signup(marketingAgreed: Bool)

    case passwordReset

    /// 약관 전문(로그인 전에도 열린다)
    case web(url: URL, title: String)
}

/// 메인 흐름에서 탭 위에 쌓이는 경로입니다. 어느 탭에서든 같은 경로를 쓴다(`AppNavigation.destination(for:)`).
enum MainRoute: Hashable {
    /// 디자인 시스템 컴포넌트 갤러리 (템플릿 확인용)
    case componentGallery

    /// 웹 페이지(약관·공지). 신뢰하는 자체 페이지만 연다
    case web(url: URL, title: String)

    /// 공지사항
    case notice

    /// 공지 상세 — 인자는 경로의 값으로 넘긴다
    case noticeDetail(id: String)

    /// 예시 상세(수정 → 목록 반영)
    case exampleDetail(id: String)

    /// 프로필 편집(설정 맨 위)
    case profile

    /// 회원 탈퇴(설정 → 계정)
    case withdraw

    /// 권한
    case permission

    /// 알림
    case inbox

    /// 검색
    case search

    /// 글 상세
    case postDetail(id: String)

    /// 글쓰기·고치기 — 새 글은 nil
    case postEditor(id: String?)

    /// 설정 → 차단한 사용자
    case blockedUsers

    /// 대화방 — 목록에서 고른 대화의 id·이름
    case chatRoom(id: String, title: String)

    /// 프로필의 "메시지 보내기": 그 사람과의 1:1 대화방(대화 id 는 KMP 가 찾는다, 25-2)
    case directChat(userId: String, name: String)

    /// 사용자 프로필(글쓴이를 눌렀을 때, 25-1)
    case userProfile(id: String)

    /// 설정 → 문의하기(25-3)
    case inquiry

    /// 설정 → 알림 설정(26-2)
    case notificationSettings

    /// 설정 → 저장한 파일(30-1)
    case downloads

    // scaffold:main-routes — newFeature 가 이 줄 위에 경로를 추가한다
}
