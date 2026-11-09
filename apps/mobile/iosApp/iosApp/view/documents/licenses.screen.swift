import SwiftUI
import Core

/// 오픈소스 라이선스: 앱에 들어 있는 서드파티 구성요소와 라이선스 전문(Resources/Licenses, 빌드 때 만든다). (Android `LicensesScreen`)
struct LicensesScreen: View {

    @State private var notices: Notices?
    @State private var missing = false
    @State private var query = ""

    private var shown: [Notice] {
        let items = notices?.items ?? []
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        return trimmed.isEmpty ? items : items.filter { $0.title.localizedCaseInsensitiveContains(trimmed) }
    }

    var body: some View {
        Group {
            if missing {
                Text(String(localized: "licenses_missing"))
                    .papyFont(PapyFont.body)
                    .foregroundStyle(PapyColor.onSurfaceVariant)
                    .padding()
            } else if let notices {
                List {
                    Section {
                        Text(String(format: String(localized: "licenses_intro"), notices.items.count))
                            .papyFont(PapyFont.body)
                        Text(String(localized: "licenses_notice"))
                            .papyFont(PapyFont.caption)
                            .foregroundStyle(PapyColor.onSurfaceVariant)
                    }
                    ForEach(shown, id: \.title) { notice in
                        DisclosureGroup {
                            Text(notice.text)
                                .font(.system(.caption, design: .monospaced))
                                .textSelection(.enabled)
                        } label: {
                            VStack(alignment: .leading) {
                                Text(notice.title).papyFont(PapyFont.subtitle)
                                Text(notice.license)
                                    .papyFont(PapyFont.caption)
                                    .foregroundStyle(PapyColor.onSurfaceVariant)
                            }
                        }
                    }
                }
                .searchable(text: $query, prompt: String(localized: "licenses_search"))
            } else {
                PapyLoadingIndicator()
            }
        }
        .navigationTitle(String(localized: "licenses_title"))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            let loaded = await Task.detached(priority: .userInitiated) { () -> Notices? in
                guard let url = Bundle.main.url(forResource: "THIRD_PARTY_NOTICES", withExtension: "txt"),
                      let text = try? String(contentsOf: url, encoding: .utf8)
                else { return nil }
                return NoticesKt.parseNotices(text: text)
            }.value
            notices = loaded
            missing = loaded == nil
        }
    }
}
