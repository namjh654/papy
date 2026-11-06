import { useEffect, useRef, useState } from "react";
import {
  ErrorCode,
  EngineError,
  extractText,
  findText,
  MAX_MATCHES,
  outline as loadOutline,
  review as loadReview,
  type OpenedDocument,
  type OutlineItem,
  type Review,
  type TextMatch,
} from "../engine";
import type { RenderQueue } from "../renderQueue";
import { Icon } from "./Icon";
import { PageCanvas } from "./PageCanvas";
import { t } from "../i18n";

const THUMB_WIDTH = 112;

type Tab = "thumbs" | "outline" | "review" | "find" | "text";

interface Props {
  doc: OpenedDocument;
  current: number;
  queue: RenderQueue;
  onGoTo(page: number): void;
  /** 편집될 때마다 올라가는 번호 */
  revision: number;
}

export function Sidebar({ doc, current, queue, onGoTo, revision }: Props) {
  const [tab, setTab] = useState<Tab>("thumbs");
  const [items, setItems] = useState<OutlineItem[]>([]);
  const [reviewInfo, setReviewInfo] = useState<Review | null>(null);

  // 메모·변경 추적: 열 때 한 번(편집해도 메모는 그대로 둔다).
  useEffect(() => {
    let alive = true;
    setReviewInfo(null);
    loadReview(doc.id).then(
      (value) => alive && setReviewInfo(value),
      () => alive && setReviewInfo(null),
    );
    return () => {
      alive = false;
    };
  }, [doc]);
  const hasReview = !!reviewInfo && (reviewInfo.comments.length > 0 || reviewInfo.insertions + reviewInfo.deletions > 0);

  // 편집하면 제목이 바뀌거나 쪽이 밀리므로 다시 읽는다.
  useEffect(() => {
    if (!doc.hasOutline) {
      setItems([]);
      return;
    }
    let alive = true;
    const timer = setTimeout(() => {
      loadOutline(doc.id).then(
        (value) => alive && setItems(value),
        () => alive && setItems([]),
      );
    }, 300);
    return () => {
      alive = false;
      clearTimeout(timer);
    };
  }, [doc, revision]);

  const tabs: [Tab, string, boolean][] = [
    ["thumbs", t.sidebar.pages, true],
    ["outline", t.sidebar.outline, items.length > 0],
    ["review", t.sidebar.review, hasReview],
    ["find", t.sidebar.find, doc.canExtractText],
    ["text", t.sidebar.text, doc.canExtractText],
  ];

  return (
    <aside className="sidebar" aria-label={t.sidebar.label}>
      <div className="tabs" role="tablist">
        {tabs
          .filter(([, , shown]) => shown)
          .map(([id, label]) => (
            <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? "active" : ""} onClick={() => setTab(id)}>
              {label}
            </button>
          ))}
      </div>
      <div className="sidebar-body">
        {tab === "thumbs" && <Thumbnails doc={doc} current={current} queue={queue} onGoTo={onGoTo} revision={revision} />}
        {tab === "outline" && <OutlineTree items={items} current={current} onGoTo={onGoTo} />}
        {tab === "review" && reviewInfo && <ReviewList review={reviewInfo} current={current} onGoTo={onGoTo} />}
        {tab === "find" && <Find doc={doc} current={current} onGoTo={onGoTo} revision={revision} />}
        {tab === "text" && <PageText doc={doc} page={current} revision={revision} />}
      </div>
    </aside>
  );
}

function Thumbnails({ doc, current, queue, onGoTo, revision }: Props) {
  const list = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState<Set<number>>(new Set([0, 1, 2, 3, 4]));

  useEffect(() => {
    const root = list.current;
    if (!root) return;
    const observer = new IntersectionObserver(
      (entries) =>
        setVisible((old) => {
          const next = new Set(old);
          for (const entry of entries) {
            const page = Number((entry.target as HTMLElement).dataset.page);
            if (entry.isIntersecting) next.add(page);
            else next.delete(page);
          }
          return next;
        }),
      { root, rootMargin: "200px 0px" },
    );
    root.querySelectorAll("[data-page]").forEach((element) => observer.observe(element));
    return () => observer.disconnect();
  }, [doc.id, doc.pageCount]);

  // 현재 쪽 썸네일이 보이게 따라간다.
  useEffect(() => {
    list.current?.querySelector(`[data-page="${current}"]`)?.scrollIntoView({ block: "nearest" });
  }, [current]);

  return (
    <div className="thumbs" ref={list}>
      {doc.pages.map((size, page) => {
        const scale = THUMB_WIDTH / size.width;
        const height = size.height * scale;
        return (
          <button
            key={page}
            data-page={page}
            className={`thumb ${page === current ? "active" : ""}`}
            onClick={() => onGoTo(page)}
            aria-label={t.sidebar.goTo(page + 1)}
            aria-current={page === current ? "page" : undefined}
          >
            {visible.has(page) ? (
              <PageCanvas
                docId={doc.id}
                page={page}
                width={THUMB_WIDTH}
                height={height}
                scale={Math.min(16, Math.max(0.05, scale * window.devicePixelRatio))}
                queue={queue}
                priority={1000 + Math.abs(page - current)}
                kind="thumb"
                revision={revision}
              />
            ) : (
              <div className="page-canvas pending" style={{ width: THUMB_WIDTH, height }} />
            )}
            <span>{page + 1}</span>
          </button>
        );
      })}
    </div>
  );
}

function ReviewList({ review, current, onGoTo }: { review: Review; current: number; onGoTo(page: number): void }) {
  const changes = review.insertions + review.deletions;
  return (
    <div className="review">
      {changes > 0 && <p className="review-changes">{t.sidebar.trackedChanges(review.insertions, review.deletions)}</p>}
      <ul className="review-list">
        {review.comments.map((comment, index) => (
          <li key={index}>
            <button
              className={`review-item ${comment.page === current ? "active" : ""}`}
              disabled={comment.page === null}
              onClick={() => comment.page !== null && onGoTo(comment.page)}
            >
              <span className="review-meta">
                {[comment.author, comment.date.slice(0, 10), comment.page !== null ? t.sidebar.matchPage(comment.page + 1) : ""]
                  .filter(Boolean)
                  .join(" · ")}
              </span>
              <span className="review-text">{comment.text}</span>
              {comment.anchor && <span className="review-anchor">{comment.anchor}</span>}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

function OutlineTree({ items, current, onGoTo }: { items: OutlineItem[]; current: number; onGoTo(page: number): void }) {
  return (
    <ul className="outline">
      {items.map((item, index) => (
        <OutlineNode key={index} item={item} current={current} onGoTo={onGoTo} />
      ))}
    </ul>
  );
}

function OutlineNode({ item, current, onGoTo }: { item: OutlineItem; current: number; onGoTo(page: number): void }) {
  const [open, setOpen] = useState(false);
  const hasChildren = item.children.length > 0;
  return (
    <li>
      <div className={`outline-row ${item.page === current ? "active" : ""}`}>
        {hasChildren ? (
          <button className={`twisty ${open ? "open" : ""}`} onClick={() => setOpen(!open)} aria-label={open ? t.sidebar.collapse : t.sidebar.expand}>
            <Icon name="chevron" size={14} />
          </button>
        ) : (
          <span className="twisty" />
        )}
        <button className="outline-title" disabled={item.page === null} onClick={() => item.page !== null && onGoTo(item.page)}>
          {item.title || t.sidebar.untitled}
        </button>
      </div>
      {hasChildren && open && <OutlineTree items={item.children} current={current} onGoTo={onGoTo} />}
    </li>
  );
}

function Find({ doc, current, onGoTo, revision }: { doc: OpenedDocument; current: number; onGoTo(page: number): void; revision: number }) {
  const [query, setQuery] = useState("");
  const [matches, setMatches] = useState<TextMatch[] | null>([]);
  const [chosen, setChosen] = useState(-1);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => input.current?.focus(), []);

  useEffect(() => {
    setChosen(-1);
    if (query.trim() === "") {
      setMatches([]);
      return;
    }
    let alive = true;
    setMatches(null);
    // 글자를 칠 때마다 찾지 않도록 잠시 기다린다. 새 찾기는 엔진에서 앞 것을 멈춘다.
    const timer = setTimeout(() => {
      findText(doc.id, query).then(
        (value) => alive && setMatches(value),
        (error) => {
          if (alive && !(error instanceof EngineError && error.code === ErrorCode.Cancelled)) setMatches([]);
        },
      );
    }, 250);
    return () => {
      alive = false;
      clearTimeout(timer);
    };
  }, [doc, query, revision]);

  const choose = (index: number) => {
    if (!matches || matches.length === 0) return;
    const next = (index + matches.length) % Math.min(matches.length, MAX_MATCHES);
    const target = matches[next];
    if (!target) return;
    setChosen(next);
    onGoTo(target.page);
  };

  const shown = matches?.slice(0, MAX_MATCHES) ?? [];
  const more = (matches?.length ?? 0) > MAX_MATCHES;
  return (
    <div className="find">
      <div className="find-box">
        <input
          ref={input}
          type="search"
          value={query}
          placeholder={t.sidebar.findPlaceholder}
          aria-label={t.sidebar.findPlaceholder}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              choose(chosen + (event.shiftKey ? -1 : 1));
            }
          }}
        />
      </div>
      {query.trim() !== "" && (
        <div className="find-status" aria-live="polite">
          {matches === null ? t.sidebar.searching : shown.length === 0 ? t.sidebar.noMatch : t.sidebar.matchCount(shown.length, more)}
        </div>
      )}
      <ul className="find-list">
        {shown.map((match, index) => (
          <li key={index}>
            <button
              className={index === chosen ? "active" : ""}
              aria-current={match.page === current ? "page" : undefined}
              onClick={() => {
                setChosen(index);
                onGoTo(match.page);
              }}
            >
              <span className="find-page">{t.sidebar.matchPage(match.page + 1)}</span>
              <span className="find-snippet">
                {match.before}
                <mark>{match.matched}</mark>
                {match.after}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

function PageText({ doc, page, revision }: { doc: OpenedDocument; page: number; revision: number }) {
  const [text, setText] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let alive = true;
    setText(null);
    setCopied(false);
    // 스크롤 중 연달아 부르지 않도록 잠시 기다린다.
    const timer = setTimeout(() => {
      extractText(doc.id, page).then(
        (value) => alive && setText(value),
        () => alive && setText(""),
      );
    }, 150);
    return () => {
      alive = false;
      clearTimeout(timer);
    };
  }, [doc, page, revision]);

  const copy = async () => {
    if (!text) return;
    await navigator.clipboard.writeText(text);
    setCopied(true);
  };

  return (
    <div className="page-text">
      <div className="page-text-head">
        <span>{t.sidebar.pageText(page + 1)}</span>
        <button className="ghost small" onClick={copy} disabled={!text}>
          <Icon name="copy" size={14} /> {copied ? t.sidebar.copied : t.sidebar.copy}
        </button>
      </div>
      {text === null ? (
        <p className="muted">{t.sidebar.reading}</p>
      ) : text.trim() === "" ? (
        <p className="muted">{t.sidebar.noText}</p>
      ) : (
        <pre>{text}</pre>
      )}
    </div>
  );
}
