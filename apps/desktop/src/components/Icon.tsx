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
