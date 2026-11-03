import { useEffect, useRef, useState } from "react";
import { renderPage } from "../engine";
import type { RenderQueue } from "../renderQueue";

interface Props {
  docId: number;
  page: number;
  /** 화면 크기(CSS px) */
  width: number;
  height: number;
  /** 엔진 렌더 배율(pt → 기기 px) */
  scale: number;
  queue: RenderQueue;
  priority: number;
  /** 큐 안에서 페이지·썸네일 요청을 구분 */
  kind: "page" | "thumb";
  /** 문서가 편집될 때마다 올라가는 번호. 바뀌면 다시 그린다(그동안 이전 그림을 보여 준다). */
  revision?: number;
}

/**
 * 한 페이지를 캔버스에 그린다. 배율이 바뀌면 다시 그리는 동안 이전 그림을 늘려 보여 준다.
 */
export function PageCanvas({ docId, page, width, height, scale, queue, priority, kind, revision = 0 }: Props) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [state, setState] = useState<"pending" | "ready" | "error">("pending");
  const key = `${kind}:${docId}:${page}`;

  useEffect(() => {
    let alive = true;
    queue.request(key, priority, async () => {
      if (!alive) return;
      try {
        const image = await renderPage(docId, page, scale);
        const element = canvas.current;
        if (!alive || !element) return;
        element.width = image.width;
        element.height = image.height;
        element.getContext("2d")?.putImageData(image, 0, 0);
        setState("ready");
      } catch {
        if (alive) setState("error");
      }
    });
    return () => {
      alive = false;
      queue.cancel(key);
    };
    // priority 변화만으로 다시 그리지 않는다(스크롤마다 바뀐다).
  }, [key, docId, page, scale, queue, revision]);

  return (
    <div className={`page-canvas ${state}`} style={{ width, height }}>
      <canvas ref={canvas} style={{ width, height }} />
      {state === "error" && <span className="page-error">이 쪽을 그리지 못했습니다</span>}
    </div>
  );
}
