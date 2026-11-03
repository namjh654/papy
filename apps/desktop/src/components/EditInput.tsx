import { forwardRef, useRef } from "react";
import type { Editor } from "../editor/useEditor";

interface Props {
  editor: Editor;
  /** 커서 자리(viewer-content 기준 CSS px). 없으면 화면 밖에 둔다. */
  position: { left: number; top: number; height: number } | null;
}

const isMac = navigator.userAgent.includes("Mac");

/**
 * 키보드·한글 입력기(IME)·붙여넣기를 받는 숨은 입력란.
 *
 * 조합 중인 글자(예: "하" → "한")는 확정될 때까지 넣지 않고 커서 옆에 미리 보여 준다.
 * 확정된 글자는 입력란 값에서 꺼내 엔진에 넣고 입력란을 비운다 — 브라우저마다 compositionend 와
 * input 이벤트 순서가 달라도 같은 글자를 두 번 넣지 않는다.
 */
export const EditInput = forwardRef<HTMLTextAreaElement, Props>(function EditInput({ editor, position }, ref) {
  const composing = useRef(false);
  const own = useRef<HTMLTextAreaElement | null>(null);

  const flush = () => {
    const element = own.current;
    if (!element || composing.current) return;
    const text = element.value;
    if (!text) return;
    element.value = "";
    void editor.insert(text.replace(/\r\n?/g, "\n"));
  };

  const onKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.nativeEvent.isComposing || event.keyCode === 229) return;
    const mod = isMac ? event.metaKey : event.ctrlKey;
    const shift = event.shiftKey;
    const key = event.key;
    const handled = () => {
      event.preventDefault();
      event.stopPropagation();
    };

    if (key === "Backspace") {
      handled();
      void editor.deleteBackward();
    } else if (key === "Delete") {
      handled();
      void editor.deleteForward();
    } else if (key === "Enter") {
      handled();
      void editor.insert("\n");
    } else if (key === "Tab") {
      handled();
    } else if (key === "ArrowLeft" || key === "ArrowRight") {
      handled();
      const left = key === "ArrowLeft";
      // macOS: ⌘← → 줄 처음/끝. Windows·Linux 는 Home/End 를 쓴다.
      void editor.move(mod && isMac ? (left ? "lineStart" : "lineEnd") : left ? "left" : "right", shift);
    } else if (key === "ArrowUp" || key === "ArrowDown") {
      handled();
      const up = key === "ArrowUp";
      void editor.move(mod ? (up ? "documentStart" : "documentEnd") : up ? "up" : "down", shift);
    } else if (key === "Home" || key === "End") {
      handled();
      const start = key === "Home";
      void editor.move(mod ? (start ? "documentStart" : "documentEnd") : start ? "lineStart" : "lineEnd", shift);
    } else if (mod && !event.altKey) {
      const letter = key.toLowerCase();
      if (letter === "a") {
        handled();
        void editor.selectAll();
      } else if (letter === "z") {
        handled();
        void (shift ? editor.redo() : editor.undo());
      } else if (letter === "y" && !isMac) {
        handled();
        void editor.redo();
      } else if (letter === "b" || letter === "i" || letter === "u") {
        handled();
        void editor.toggleChar(letter === "b" ? "bold" : letter === "i" ? "italic" : "underline");
      } else if (letter === "c" || letter === "x") {
        handled();
        void (async () => {
          const text = await editor.selectedText();
          if (!text) return;
          await navigator.clipboard.writeText(text);
          if (letter === "x") await editor.deleteBackward();
        })();
      }
      // ⌘V 는 paste 이벤트로 받는다.
    }
  };

  return (
    <>
      <textarea
        ref={(element) => {
          own.current = element;
          if (typeof ref === "function") ref(element);
          else if (ref) ref.current = element;
        }}
        className="edit-input"
        aria-label="문서 편집"
        autoCapitalize="off"
        autoComplete="off"
        autoCorrect="off"
        spellCheck={false}
        style={
          position
            ? { left: position.left, top: position.top, height: position.height }
            : { left: -9999, top: 0, height: 16 }
        }
        onKeyDown={onKeyDown}
        onInput={(event) => {
          if (!(event.nativeEvent as InputEvent).isComposing) flush();
        }}
        onCompositionStart={() => {
          composing.current = true;
        }}
        onCompositionUpdate={(event) => editor.setComposing(event.data)}
        onCompositionEnd={() => {
          composing.current = false;
          editor.setComposing("");
          // 브라우저에 따라 확정 글자가 이 뒤의 input 에서 들어온다 — 다음 틱에 비운다.
          setTimeout(flush, 0);
        }}
        onPaste={(event) => {
          event.preventDefault();
          const text = event.clipboardData.getData("text/plain");
          if (text) void editor.insert(text.replace(/\r\n?/g, "\n"));
        }}
      />
      {position && editor.state.composing && (
        <span
          className="composing"
          style={{ left: position.left, top: position.top, height: position.height, fontSize: position.height * 0.8 }}
        >
          {editor.state.composing}
        </span>
      )}
    </>
  );
});
