import { useEffect, useRef, useState } from "react";
import { extractText, outline as loadOutline, type OpenedDocument, type OutlineItem } from "../engine";
import type { RenderQueue } from "../renderQueue";
import { Icon } from "./Icon";
import { PageCanvas } from "./PageCanvas";

const THUMB_WIDTH = 112;

type Tab = "thumbs" | "outline" | "text";

interface Props {
  doc: OpenedDocument;
  current: number;
  queue: RenderQueue;
  onGoTo(page: number): void;
}

export function Sidebar({ doc, current, queue, onGoTo }: Props) {
  const [tab, setTab] = useState<Tab>("thumbs");
  const [items, setItems] = useState<OutlineItem[]>([]);

  useEffect(() => {
    setItems([]);
    if (doc.hasOutline) loadOutline(doc.id).then(setItems, () => setItems([]));
  }, [doc]);

  const tabs: [Tab, string, boolean][] = [
    ["thumbs", "쪽", true],
    ["outline", "목차", items.length > 0],
    ["text", "텍스트", doc.canExtractText],
  ];

  return (
    <aside className="sidebar" aria-label="문서 탐색">
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
        {tab === "thumbs" && <Thumbnails doc={doc} current={current} queue={queue} onGoTo={onGoTo} />}
        {tab === "outline" && <OutlineTree items={items} current={current} onGoTo={onGoTo} />}
        {tab === "text" && <PageText doc={doc} page={current} />}
      </div>
    </aside>
  );
}

function Thumbnails({ doc, current, queue, onGoTo }: Props) {
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
  }, [doc]);

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
            aria-label={`${page + 1}쪽으로 이동`}
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
          <button className={`twisty ${open ? "open" : ""}`} onClick={() => setOpen(!open)} aria-label={open ? "접기" : "펼치기"}>
            <Icon name="chevron" size={14} />
          </button>
        ) : (
          <span className="twisty" />
        )}
        <button className="outline-title" disabled={item.page === null} onClick={() => item.page !== null && onGoTo(item.page)}>
          {item.title || "(제목 없음)"}
        </button>
      </div>
      {hasChildren && open && <OutlineTree items={item.children} current={current} onGoTo={onGoTo} />}
    </li>
  );
}

function PageText({ doc, page }: { doc: OpenedDocument; page: number }) {
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
  }, [doc, page]);

  const copy = async () => {
    if (!text) return;
    await navigator.clipboard.writeText(text);
    setCopied(true);
  };

  return (
    <div className="page-text">
      <div className="page-text-head">
        <span>{page + 1}쪽 텍스트</span>
        <button className="ghost small" onClick={copy} disabled={!text}>
          <Icon name="copy" size={14} /> {copied ? "복사됨" : "복사"}
        </button>
      </div>
      {text === null ? (
        <p className="muted">읽는 중…</p>
      ) : text.trim() === "" ? (
        <p className="muted">이 쪽에는 추출할 텍스트가 없습니다. 스캔한 이미지일 수 있습니다.</p>
      ) : (
        <pre>{text}</pre>
      )}
    </div>
  );
}
