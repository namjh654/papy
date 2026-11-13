// 선 아이콘 모음(24px 격자, currentColor). 외부 아이콘 패키지를 쓰지 않는다.

const paths = {
  open: "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
  sidebar: "M4 5h16v14H4zM9 5v14",
  prev: "M15 6l-6 6 6 6",
  next: "M9 6l6 6-6 6",
  zoomIn: "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4M11 8v6M8 11h6",
  zoomOut: "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4M8 11h6",
  fitWidth: "M4 6v12M20 6v12M8 12h8M10 9l-2 3 2 3M14 9l2 3-2 3",
  fitPage: "M6 3h12v18H6zM9 8h6M9 12h6M9 16h4",
  close: "M6 6l12 12M18 6L6 18",
  copy: "M9 9h10v11H9zM5 15V4h10",
  lock: "M6 11h12v9H6zM9 11V8a3 3 0 0 1 6 0v3",
  file: "M6 3h8l4 4v14H6zM14 3v4h4",
  trash: "M5 7h14M10 11v6M14 11v6M7 7l1 13h8l1-13M9 7V4h6v3",
  chevron: "M9 6l6 6-6 6",
  undo: "M9 14L4 9l5-5M4 9h10a6 6 0 0 1 0 12h-3",
  redo: "M15 14l5-5-5-5M20 9H10a6 6 0 0 0 0 12h3",
  "align-left": "M4 6h16M4 10h10M4 14h16M4 18h10",
  "align-center": "M4 6h16M7 10h10M4 14h16M7 18h10",
  "align-right": "M4 6h16M10 10h10M4 14h16M10 18h10",
  "align-justify": "M4 6h16M4 10h16M4 14h16M4 18h16",
  save: "M5 4h11l3 3v13H5zM8 4v5h7V4M8 20v-6h8v6",
  edit: "M4 20h4L19 9l-4-4L4 16zM13 7l4 4",
  eye: "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z",
  export: "M12 3v12M7 8l5-5 5 5M5 14v6h14v-6",
  cursor: "M5 3l14 8-6 2-2 6z",
  highlighter: "M9 15l-3 3H3v-3l3-3M7 13l8-8 4 4-8 8zM4 21h16",
  underline: "M7 4v6a5 5 0 0 0 10 0V4M5 20h14",
  strike: "M5 12h14M16 7a4 3 0 0 0-8 0c0 4 8 2 8 6a4 3 0 0 1-8 0",
  pen: "M12 19l7-7 3 3-7 7zM18 13l-1.5-7.5L2 2l3.5 14.5L13 18zM2 2l7.6 7.6M11 11a2 2 0 1 0 0-4 2 2 0 0 0 0 4z",
  textbox: "M4 4h16v16H4zM8 9h8M12 9v7",
  note: "M5 4h14v11l-4 4H5zM15 19v-4h4",
  "rotate-left": "M4 9a8 8 0 1 1 1 7M4 4v5h5",
  "rotate-right": "M20 9a8 8 0 1 0-1 7M20 4v5h-5",
  "move-up": "M12 19V5M6 11l6-6 6 6",
  "move-down": "M12 5v14M6 13l6 6 6-6",
  sparkle: "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8z",
  send: "M4 12l16-8-6 16-2.5-6.5z",
  stop: "M7 7h10v10H7z",
  gear: "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM12 2v3M12 19v3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M2 12h3M19 12h3M4.9 19.1L7 17M17 7l2.1-2.1",
  plus: "M12 5v14M5 12h14",
} as const;

export type IconName = keyof typeof paths;

export function Icon({ name, size = 18 }: { name: IconName; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  );
}
