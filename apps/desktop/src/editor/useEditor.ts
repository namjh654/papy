// 편집 모드 상태: 커서·선택·서식·입력 처리. 엔진 호출은 순서대로 한 줄로 세운다(빠른 입력에도 순서 보장).
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  comparePos,
  edit,
  samePos,
  type CaretMotion,
  type Changed,
  type CharFormat,
  type Located,
  type PageRect,
  type ParaFormat,
  type TextPos,
} from "../engine";

export interface EditorCallbacks {
  /** 문서가 바뀌었다(다시 그리기·쪽 수 갱신). */
  onChanged(pageCount: number | undefined): void;
  /** 커서를 화면 안으로. */
  reveal(rect: PageRect): void;
  onError(error: unknown): void;
}

export interface EditorState {
  caret: TextPos | null;
  anchor: TextPos | null;
  caretRect: PageRect | null;
  selection: PageRect[];
  charFormat: CharFormat;
  paraFormat: ParaFormat;
  composing: string;
}

const EMPTY: EditorState = {
  caret: null,
  anchor: null,
  caretRect: null,
  selection: [],
  charFormat: {},
  paraFormat: {},
  composing: "",
};

export type Editor = ReturnType<typeof useEditor>;

export function useEditor(docId: number, active: boolean, callbacks: EditorCallbacks) {
  const [state, setState] = useState<EditorState>(EMPTY);
  /** 최신 상태. 렌더를 기다리지 않고 읽고 쓴다(`update` 만 고친다). */
  const stateRef = useRef(state);
  const cb = useRef(callbacks);
  cb.current = callbacks;
  const queue = useRef<Promise<unknown>>(Promise.resolve());
  const preferredX = useRef<number | null>(null);
  /** 선택 없이 고른 서식: 다음에 입력하는 글자에 적용한다. */
  const pending = useRef<CharFormat | null>(null);

  // 줄 선 다음 작업이 바로 최신 커서를 읽어야 하므로 ref 를 먼저(동기로) 고친다.
  // setState 의 갱신 함수는 다음 렌더 때에야 실행돼서 거기서 고치면 늦다.
  const update = useCallback((patch: Partial<EditorState>) => {
    const next = { ...stateRef.current, ...patch };
    stateRef.current = next;
    setState(next);
  }, []);

  /** 엔진 작업을 차례로 실행한다. 작업은 실행 시점의 최신 상태를 읽는다. */
  const enqueue = useCallback((job: () => Promise<void>) => {
    const run = queue.current.then(job).catch((error) => cb.current.onError(error));
    queue.current = run;
    return run;
  }, []);

  const range = (): [TextPos, TextPos] | null => {
    const { caret, anchor } = stateRef.current;
    if (!caret) return null;
    const other = anchor ?? caret;
    return comparePos(other, caret) <= 0 ? [other, caret] : [caret, other];
  };

  const hasSelection = () => {
    const { caret, anchor } = stateRef.current;
    return !!caret && !!anchor && !samePos(caret, anchor);
  };

  /** 커서(와 선택)를 옮기고 화면 표시를 갱신한다. */
  const place = useCallback(
    async (located: Located, extend: boolean) => {
      const { caret, anchor } = stateRef.current;
      const nextAnchor = extend ? (anchor ?? caret ?? located.pos) : null;
      update({ caret: located.pos, caretRect: located.rect, anchor: nextAnchor });
      cb.current.reveal(located.rect);
      if (nextAnchor && !samePos(nextAnchor, located.pos)) {
        const [start, end] = comparePos(nextAnchor, located.pos) <= 0 ? [nextAnchor, located.pos] : [located.pos, nextAnchor];
        const selection = await edit<PageRect[]>(docId, { kind: "selectionRects", start, end });
        update({ selection });
      } else {
        update({ selection: [] });
      }
    },
    [docId, update],
  );

  /** 바꾸는 작업의 응답을 반영한다. */
  const applyChange = useCallback(
    (reply: Changed) => {
      if (!reply.changed) return;
      if (reply.caret) {
        update({ caret: reply.caret, anchor: null, selection: [], caretRect: reply.caretRect ?? null });
        if (reply.caretRect) cb.current.reveal(reply.caretRect);
      }
      preferredX.current = null;
      cb.current.onChanged(reply.pageCount);
    },
    [update],
  );

  // 커서가 멈추면 서식 상태를 읽어 툴바에 보여 준다.
  useEffect(() => {
    if (!active || !state.caret) return;
    const pos = state.caret;
    const timer = setTimeout(() => {
      void enqueue(async () => {
        const [charFormat, paraFormat] = await Promise.all([
          edit<CharFormat>(docId, { kind: "charFormat", pos }),
          edit<ParaFormat>(docId, { kind: "paraFormat", pos }),
        ]);
        if (stateRef.current.caret && samePos(stateRef.current.caret, pos)) {
          update({ charFormat: { ...charFormat, ...pending.current }, paraFormat });
        }
      });
    }, 120);
    return () => clearTimeout(timer);
  }, [active, state.caret, docId, enqueue, update]);

  // 편집 모드를 끄거나 문서가 바뀌면 커서를 지운다.
  useEffect(() => {
    if (!active) update(EMPTY);
  }, [active, docId, update]);

  // ---- 동작 ----

  const clickAt = useCallback(
    (page: number, x: number, y: number, extend: boolean) =>
      enqueue(async () => {
        const located = await edit<Located>(docId, { kind: "hitTest", page, x, y });
        preferredX.current = null;
        pending.current = null;
        await place(located, extend);
      }),
    [docId, enqueue, place],
  );

  const insert = useCallback(
    (text: string) =>
      enqueue(async () => {
        if (!text) return;
        const r = range();
        if (!r) return;
        let at = r[0];
        if (hasSelection()) {
          const reply = await edit<Changed>(docId, { kind: "deleteRange", start: r[0], end: r[1] });
          at = reply.caret ?? r[0];
        }
        const reply = await edit<Changed>(docId, { kind: "insertText", pos: at, text });
        const format = pending.current;
        if (format && reply.caret && !text.includes("\n")) {
          // 고른 서식을 방금 넣은 글자에 입힌다.
          await edit<Changed>(docId, { kind: "applyCharFormat", start: at, end: reply.caret, format });
        }
        applyChange(reply);
      }),
    [docId, enqueue, applyChange],
  );

  const removeSelectionOr = useCallback(
    (kind: "deleteBackward" | "deleteForward") =>
      enqueue(async () => {
        const r = range();
        if (!r) return;
        pending.current = null;
        const reply = hasSelection()
          ? await edit<Changed>(docId, { kind: "deleteRange", start: r[0], end: r[1] })
          : await edit<Changed>(docId, { kind, pos: r[0] });
        applyChange(reply);
      }),
    [docId, enqueue, applyChange],
  );

  const move = useCallback(
    (motion: CaretMotion | "up" | "down", extend: boolean) =>
      enqueue(async () => {
        const { caret, caretRect } = stateRef.current;
        if (!caret) return;
        pending.current = null;
        // 선택이 있는데 Shift 없이 좌우로 가면 선택의 끝으로 접는다.
        const r = range();
        if (!extend && r && hasSelection() && (motion === "left" || motion === "right")) {
          const pos = motion === "left" ? r[0] : r[1];
          const rect = await edit<PageRect>(docId, { kind: "caretRect", pos });
          await place({ pos, rect }, false);
          return;
        }
        let located: Located;
        if (motion === "up" || motion === "down") {
          preferredX.current ??= caretRect?.x ?? 0;
          located = await edit<Located>(docId, {
            kind: "moveVertical",
            pos: caret,
            lines: motion === "up" ? -1 : 1,
            preferredX: preferredX.current,
          });
        } else {
          preferredX.current = null;
          located = await edit<Located>(docId, { kind: "moveCaret", pos: caret, motion });
        }
        await place(located, extend);
      }),
    [docId, enqueue, place],
  );

  const selectAll = useCallback(
    () =>
      enqueue(async () => {
        const { caret } = stateRef.current;
        const from = caret ?? { section: 0, paragraph: 0, offset: 0 };
        const start = await edit<Located>(docId, { kind: "moveCaret", pos: from, motion: "documentStart" });
        const end = await edit<Located>(docId, { kind: "moveCaret", pos: from, motion: "documentEnd" });
        update({ caret: start.pos, anchor: null });
        await place(end, true);
      }),
    [docId, enqueue, place, update],
  );

  const selectedText = useCallback(async () => {
    let text = "";
    await enqueue(async () => {
      const r = range();
      if (r && hasSelection()) {
        const raw = await edit<string>(docId, { kind: "textInRange", start: r[0], end: r[1] });
        // 그림·표 같은 개체 자리 표시(U+FFFC)는 복사하지 않는다
        text = raw.replace(/\uFFFC/g, "");
      }
    });
    return text;
  }, [docId, enqueue]);

  const formatChars = useCallback(
    (format: CharFormat) =>
      enqueue(async () => {
        const r = range();
        if (!r) return;
        if (!hasSelection()) {
          pending.current = { ...pending.current, ...format };
          update({ charFormat: { ...stateRef.current.charFormat, ...format } });
          return;
        }
        const reply = await edit<Changed>(docId, { kind: "applyCharFormat", start: r[0], end: r[1], format });
        update({ charFormat: { ...stateRef.current.charFormat, ...format } });
        const { caret, anchor } = stateRef.current;
        cb.current.onChanged(reply.pageCount);
        // 선택을 유지한 채 사각형만 다시 잰다(글자 크기가 바뀌었을 수 있다).
        if (caret && anchor) {
          const rect = await edit<PageRect>(docId, { kind: "caretRect", pos: caret });
          await place({ pos: caret, rect }, true);
        }
      }),
    [docId, enqueue, update, place],
  );

  /** 굵게·기울임 등을 켜고 끈다. 켤지 끌지는 실행 시점의 실제 서식(선택 시작 글자)으로 정한다 —
   *  툴바 상태는 잠깐 늦게 갱신되므로 빠르게 선택하고 누르면 틀릴 수 있다. */
  const toggleChar = useCallback(
    (key: "bold" | "italic" | "underline" | "strikethrough") =>
      enqueue(async () => {
        const r = range();
        if (!r) return;
        let current: boolean;
        if (hasSelection()) {
          const at = { ...r[0], offset: r[0].offset + 1 };
          current = !!(await edit<CharFormat>(docId, { kind: "charFormat", pos: at }))[key];
        } else {
          current = !!stateRef.current.charFormat[key];
        }
        void formatChars({ [key]: !current });
      }),
    [docId, enqueue, formatChars],
  );

  const formatParagraphs = useCallback(
    (format: ParaFormat) =>
      enqueue(async () => {
        const r = range();
        if (!r) return;
        const reply = await edit<Changed>(docId, { kind: "applyParaFormat", start: r[0], end: r[1], format });
        update({ paraFormat: { ...stateRef.current.paraFormat, ...format } });
        cb.current.onChanged(reply.pageCount);
        const { caret } = stateRef.current;
        if (caret) {
          const rect = await edit<PageRect>(docId, { kind: "caretRect", pos: caret });
          await place({ pos: caret, rect }, hasSelection());
        }
      }),
    [docId, enqueue, update, place],
  );

  const history = useCallback(
    (kind: "undo" | "redo") =>
      enqueue(async () => {
        pending.current = null;
        applyChange(await edit<Changed>(docId, { kind }));
      }),
    [docId, enqueue, applyChange],
  );

  const setComposing = useCallback((composing: string) => update({ composing }), [update]);

  return useMemo(
    () => ({
      state,
      clickAt,
      insert,
      deleteBackward: () => removeSelectionOr("deleteBackward"),
      deleteForward: () => removeSelectionOr("deleteForward"),
      move,
      selectAll,
      selectedText,
      formatChars,
      toggleChar,
      formatParagraphs,
      undo: () => history("undo"),
      redo: () => history("redo"),
      setComposing,
      hasSelection,
    }),
    [state, clickAt, insert, removeSelectionOr, move, selectAll, selectedText, formatChars, toggleChar, formatParagraphs, history, setComposing],
  );
}
