import type { RecentFile } from "../recent";
import { Icon } from "./Icon";
import { locale, t } from "../i18n";

interface Props {
  recent: RecentFile[];
  onOpen(): void;
  onOpenRecent(file: RecentFile): void;
  onClearRecent(): void;
  onAbout(): void;
}

const isMac = navigator.userAgent.includes("Mac");

export function Welcome({ recent, onOpen, onOpenRecent, onClearRecent, onAbout }: Props) {
  return (
    <div className="welcome">
      <div className="welcome-card">
        <img src="/papy.svg" alt="" width={56} height={56} />
        <h1>Papy</h1>
        <p className="muted">{t.welcome.lead}</p>
        <button className="primary" onClick={onOpen}>
          <Icon name="open" /> {t.welcome.open}
        </button>
        <p className="hint">
          {t.welcome.dropHint} <kbd>{isMac ? "⌘" : "Ctrl"}</kbd>+<kbd>O</kbd>
        </p>
      </div>

      {recent.length > 0 && (
        <section className="recent" aria-labelledby="recent-title">
          <div className="recent-head">
            <h2 id="recent-title">{t.welcome.recent}</h2>
            <button className="ghost small" onClick={onClearRecent} title={t.welcome.clearRecentHint}>
              <Icon name="trash" size={14} /> {t.welcome.clearRecent}
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
      <button className="ghost small about-link" onClick={onAbout}>
        {t.welcome.about}
      </button>
    </div>
  );
}

function formatDate(time: number) {
  return new Intl.DateTimeFormat(locale, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(time);
}
