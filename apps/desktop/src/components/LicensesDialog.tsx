// 정보·오픈소스 라이선스: Papy 라이선스 요약과, 함께 배포되는 구성요소의 라이선스 고지 전문.
import { useEffect, useMemo, useState } from "react";
import { engineVersion, licenseNotices } from "../engine";
import { locale, MESSAGES, setLocale, t, type Locale } from "../i18n";

interface Props {
  onClose(): void;
}

/** 고지 파일(scripts/third-party-notices.mjs)의 구성요소 머리: 구분선, 제목, "License: …", 구분선 */
const HEADER = /\n={78}\n([^\n]+)\nLicense: ([^\n]*)\n={78}\n/g;

/** 고지 전문을 구성요소별로 나눈다. 라이선스 본문 안에 구분선이 있어도 머리 형식으로만 자른다. */
export function splitNotices(text: string): { title: string; license: string; body: string }[] {
  const heads = [...text.matchAll(HEADER)];
  return heads.map((head, i) => {
    const start = head.index! + head[0].length;
    const end = i + 1 < heads.length ? heads[i + 1]!.index! : text.length;
    return { title: head[1]!, license: head[2]!, body: text.slice(start, end).trim() };
  });
}

export function LicensesDialog({ onClose }: Props) {
  const [text, setText] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const [version, setVersion] = useState("");
  const [query, setQuery] = useState("");

  useEffect(() => {
    void licenseNotices().then(setText, () => setFailed(true));
    void engineVersion().then(setVersion, () => undefined);
  }, []);

  const sections = useMemo(() => (text ? splitNotices(text) : []), [text]);
  const shown = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return needle ? sections.filter((section) => section.title.toLowerCase().includes(needle)) : sections;
  }, [sections, query]);

  return (
    <div className="backdrop" onKeyDown={(event) => event.key === "Escape" && onClose()}>
      <div className="dialog licenses" role="dialog" aria-modal="true" aria-labelledby="licenses-title">
        <h2 id="licenses-title">{t.about.title}</h2>
        <p className="muted">
          {t.about.engine} {version || "…"} · {t.about.licenseBefore}
          <strong>PolyForm Noncommercial 1.0.0</strong>
          {t.about.licenseAfter}
        </p>
        <div className="notices muted">
          {t.about.notices.map((line) => (
            <p key={line}>{line}</p>
          ))}
        </div>
        <label className="language-pick">
          {t.about.language}{" "}
          <select value={locale} onChange={(event) => setLocale(event.target.value as Locale)}>
            {(Object.keys(MESSAGES) as Locale[]).map((code) => (
              <option key={code} value={code}>
                {MESSAGES[code].languageName}
              </option>
            ))}
          </select>
        </label>
        <h3>{t.about.openSource}</h3>
        {failed ? (
          <p className="muted">{t.about.noticesMissing}</p>
        ) : !text ? (
          <p className="muted">{t.common.loading}</p>
        ) : (
          <>
            <input
              type="search"
              placeholder={t.about.search(sections.length)}
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              autoFocus
            />
            <div className="licenses-list">
              {shown.slice(0, 200).map((section) => (
                <details key={section.title}>
                  <summary>
                    {section.title} <span className="muted">· {section.license}</span>
                  </summary>
                  <pre>{section.body}</pre>
                </details>
              ))}
              {shown.length > 200 && <p className="muted">{t.about.more(shown.length - 200)}</p>}
            </div>
          </>
        )}
        <div className="dialog-actions">
          <button className="primary" onClick={onClose} autoFocus={failed}>
            {t.common.close}
          </button>
        </div>
      </div>
    </div>
  );
}
