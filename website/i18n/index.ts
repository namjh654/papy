// 사이트 언어 목록. 언어를 추가하는 방법은 website/README.md 의 "언어 추가" 를 본다.
import { en } from "./en.ts";
import { ko } from "./ko.ts";
import type { Messages } from "./types.ts";

export type { ClientText, Messages, PageId, PageText } from "./types.ts";

/** 기본 언어. 이 언어의 페이지는 주소 앞에 언어 코드가 붙지 않는다(/, /download/ …). */
export const DEFAULT_LOCALE = "ko";

/** 지원 언어. 순서대로 언어 선택 메뉴에 나온다. 키 = 주소 앞부분(/en/ …)이자 페이지 폴더 이름. */
export const MESSAGES = { ko, en } satisfies Record<string, Messages>;

export type Locale = keyof typeof MESSAGES;
export const LOCALES = Object.keys(MESSAGES) as Locale[];

export const isLocale = (value: unknown): value is Locale => typeof value === "string" && value in MESSAGES;

/** 언어의 주소 앞부분: 기본 언어는 "", 그 밖은 "/en" 처럼. */
export const localeBase = (locale: Locale) => (locale === DEFAULT_LOCALE ? "" : `/${locale}`);

/** 언어의 페이지 파일 폴더(website/ 기준): 기본 언어는 "", 그 밖은 "en/" 처럼. */
export const localeDir = (locale: Locale) => (locale === DEFAULT_LOCALE ? "" : `${locale}/`);
