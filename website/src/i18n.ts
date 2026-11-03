// 페이지에 들어 있는 문구(<script id="papy-i18n" type="application/json">, vite.config.ts 가 넣는다)를 읽는다.
import type { ClientText } from "../i18n/types.ts";

export const t = JSON.parse(document.getElementById("papy-i18n")?.textContent ?? "{}") as ClientText;

/** 페이지 언어(<html lang>). 날짜 표시에 쓴다. */
export const lang = document.documentElement.lang || "ko";

/** "{name}" 자리표시자를 채운다. */
export const fill = (template: string, values: Record<string, string | number>) =>
  template.replace(/\{(\w+)\}/g, (all, key: string) => (key in values ? String(values[key]) : all));
