/**
 * 화면 글자(다국어). 언어는 앱을 열 때 한 번 정한다: 사용자가 고른 언어 → 운영체제 언어 → 한국어.
 * 언어를 바꾸면 창을 다시 불러온다(모든 글자가 한 번에 바뀐다).
 *
 * 언어 추가: `xx.ts` 에 `Messages` 를 채워 `MESSAGES` 에 넣으면 된다(빠진 키는 타입 검사에서 걸린다).
 */
import { en } from "./en.ts";
import { ko, type Messages } from "./ko.ts";

export type { Messages };
export const MESSAGES = { ko, en } satisfies Record<string, Messages>;
export type Locale = keyof typeof MESSAGES;
export const DEFAULT_LOCALE: Locale = "ko";
const STORAGE_KEY = "papy.locale";

const isLocale = (value: unknown): value is Locale => typeof value === "string" && value in MESSAGES;

/** 저장된 선택 → 브라우저(운영체제) 언어 → 기본 언어 */
export function detectLocale(stored: string | null, languages: readonly string[]): Locale {
  if (isLocale(stored)) return stored;
  for (const language of languages) {
    const base = language.toLowerCase().split("-")[0];
    if (isLocale(base)) return base;
  }
  return DEFAULT_LOCALE;
}

function storedLocale(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export const locale: Locale = detectLocale(
  storedLocale(),
  typeof navigator === "undefined" ? [] : navigator.languages ?? [navigator.language],
);
export const t: Messages = MESSAGES[locale];

/** 언어를 바꾸고 창을 다시 불러온다. */
export function setLocale(next: Locale) {
  try {
    localStorage.setItem(STORAGE_KEY, next);
  } catch {
    // 저장하지 못해도 이번 창에서는 운영체제 언어를 따른다.
  }
  location.reload();
}
