import type { RecentFile } from "../recent";
import { Icon } from "./Icon";

interface Props {
  recent: RecentFile[];
  onOpen(): void;
  onOpenRecent(file: RecentFile): void;
  onClearRecent(): void;
}

const isMac = navigator.userAgent.includes("Mac");

export function Welcome({ recent, onOpen, onOpenRecent, onClearRecent }: Props) {
  return (
    <div className="welcome">
      <div className="welcome-card">
        <img src="/papy.svg" alt="" width={56} height={56} />
        <h1>Papy</h1>
        <p className="muted">PDF·한글(HWP·HWPX)·Word(DOCX) 문서를 인터넷 없이 열고 편집합니다.</p>
        <button className="primary" onClick={onOpen}>
          <Icon name="open" /> 문서 열기
        </button>
        <p className="hint">
          또는 파일을 이 창으로 끌어다 놓으세요 · <kbd>{isMac ? "⌘" : "Ctrl"}</kbd>+<kbd>O</kbd>
        </p>
      </div>

      {recent.length > 0 && (
        <section className="recent" aria-labelledby="recent-title">
          <div className="recent-head">
            <h2 id="recent-title">최근 문서</h2>
            <button className="ghost small" onClick={onClearRecent} title="이 기기에 저장된 최근 문서 목록을 지웁니다">
              <Icon name="trash" size={14} /> 목록 지우기
            </button>
          </div>
          <ul>
            {recent.map((file) => (
              <li key={file.path}>
                <button onClick={() => onOpenRecent(file)} title={file.path}>
                  <Icon name="file" />
                  <span className="recent-name">{file.name}</span>
                  <span className="recent-meta">
                    {file.format === "OLE compound file" ? "HWP" : file.format} · {formatDate(file.openedAt)}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

function formatDate(time: number) {
  return new Intl.DateTimeFormat("ko-KR", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(time);
}
