import SwiftUI
import Nuke
import NukeUI

// MARK: - Remote Image

/// 서버 이미지(URL). 불러오는 동안과 실패했을 때는 [placeholder] — 기본은 회색 바탕(자리 크기는 frame 으로 정한다).
/// 화면은 AsyncImage·LazyImage 를 직접 쓰지 않고 이 컴포넌트를 쓴다. (Android `ElsaRemoteImage`)
///
/// 불러오기는 Nuke(DEC-131): 디코딩된 이미지 메모리 캐시 + 디스크 캐시([ElsaImagePipeline]), 같은 주소 요청은 하나로 합친다,
/// 화면 밖으로 나가면 취소 — 목록을 스크롤해도 다시 받거나 깜박이지 않는다(Android Coil 과 같은 역할).
struct ElsaRemoteImage<Placeholder: View>: View {
    let url: String?
    var contentMode: ContentMode = .fill
    @ViewBuilder var placeholder: () -> Placeholder

    init(url: String?, contentMode: ContentMode = .fill, @ViewBuilder placeholder: @escaping () -> Placeholder) {
        self.url = url
        self.contentMode = contentMode
        self.placeholder = placeholder
    }

    var body: some View {
        LazyImage(url: url.flatMap(URL.init(string:))) { state in
            if let image = state.image {
                image.resizable().aspectRatio(contentMode: contentMode)
            } else {
                placeholder()
            }
        }
    }
}

extension ElsaRemoteImage where Placeholder == Color {
    init(url: String?, contentMode: ContentMode = .fill) {
        self.init(url: url, contentMode: contentMode) { ElsaColor.surfaceVariant }
    }
}

/// 앱 전체 이미지 파이프라인(시작 때 한 번, `iOSApp.init`). 디스크 캐시는 서버 캐시 헤더와 상관없이 이미지 원본을 둔다 —
/// 주소가 바뀌면 새 이미지라는 전제(업로드마다 새 주소). 용량을 넘으면 오래된 것부터 지운다.
enum ElsaImagePipeline {
    static func configure() {
        ImagePipeline.shared = ImagePipeline(configuration: .withDataCache(name: "cloud.elsa.app.images", sizeLimit: 200 * 1024 * 1024))
    }
}

// MARK: - Avatar

/// 동그란 프로필 사진. 우선순위: 방금 고른 사진(`picked`) → 서버 사진(`url`) → 이름 첫 글자. (Android `ElsaAvatar`)
struct ElsaAvatar: View {
    let name: String
    var url: String? = nil
    var picked: UIImage? = nil
    var size: CGFloat = 56

    var body: some View {
        ZStack {
            ElsaColor.primaryContainer
            if let picked {
                Image(uiImage: picked).resizable().scaledToFill()
            } else if let url, !url.isEmpty {
                ElsaRemoteImage(url: url)
            } else {
                Text(String(name.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased())
                    .elsaFont(size >= 72 ? ElsaFont.headline : ElsaFont.subtitle)
                    .foregroundStyle(ElsaColor.onPrimaryContainer)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityLabel(name)
    }
}
