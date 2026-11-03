// 문의 폼 → Resend 로 개발자에게 메일을 보낸다 (Vercel Function).
// 필요한 환경 변수: RESEND_API_KEY, CONTACT_TO. 선택: CONTACT_FROM, ALLOWED_ORIGINS(쉼표 구분).
// 문의 내용은 로그에 남기지 않는다. 오류는 코드(error)로 돌려주고, 화면 문구는 페이지 언어로 브라우저가 고른다(i18n/<언어>.ts).

// 함수 번들이 사이트 코드(i18n/)에 기대지 않도록 언어는 형식만 본다. 목록에 없는 언어면 그 주소가 404 로 끝날 뿐이다.
const DEFAULT_LOCALE = "ko"; // i18n/index.ts 의 DEFAULT_LOCALE 과 같아야 한다
const LOCALE_RE = /^[a-z]{2,3}(-[a-z0-9]{2,8})?$/;
const localeBase = (locale: string) => (locale === DEFAULT_LOCALE ? "" : `/${locale}`);

const TYPES = { bug: "버그 신고", feature: "기능 제안", question: "사용 문의", other: "기타" } as const;
type ContactType = keyof typeof TYPES;

const LIMITS = { name: 100, email: 200, subject: 150, message: 5000, minMessage: 10 };
const MIN_FILL_MS = 3000;
const EMAIL_RE = /^[^\s@<>()]+@[^\s@<>()]+\.[^\s@<>()]+$/;

interface ContactInput {
  type: ContactType;
  name: string;
  email: string;
  subject: string;
  message: string;
}

/** 브라우저 문구 키(ClientText.contact.errors)와 같다. */
type ErrorCode = "forbidden" | "badRequest" | "type" | "email" | "message" | "failed";

type Parsed = { ok: true; value: ContactInput } | { ok: false; error: ErrorCode; spam?: boolean };

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json; charset=utf-8" } });

const text = (value: unknown, max: number) => (typeof value === "string" ? value.trim().slice(0, max) : "");

function parse(raw: Record<string, unknown>): Parsed {
  // 봇 차단: 숨은 칸(website)이 채워졌거나, 폼을 연 뒤 너무 빨리 보냈으면 스팸으로 본다.
  if (text(raw.website, 200)) return { ok: false, error: "badRequest", spam: true };
  const startedAt = Number(raw.startedAt);
  if (!Number.isFinite(startedAt) || Date.now() - startedAt < MIN_FILL_MS) return { ok: false, error: "badRequest", spam: true };

  const type = text(raw.type, 20) as ContactType;
  if (!(type in TYPES)) return { ok: false, error: "type" };
  const email = text(raw.email, LIMITS.email);
  if (!EMAIL_RE.test(email)) return { ok: false, error: "email" };
  const message = text(raw.message, LIMITS.message);
  if (message.length < LIMITS.minMessage) return { ok: false, error: "message" };
  return {
    ok: true,
    value: { type, email, message, name: text(raw.name, LIMITS.name), subject: text(raw.subject, LIMITS.subject) },
  };
}

function allowedOrigin(origin: string | null, host: string | null): boolean {
  if (!origin) return false;
  const extra = (process.env.ALLOWED_ORIGINS ?? "").split(",").map((s) => s.trim()).filter(Boolean);
  try {
    const url = new URL(origin);
    return url.host === host || extra.includes(url.origin);
  } catch {
    return false;
  }
}

async function readBody(request: Request): Promise<Record<string, unknown>> {
  const type = request.headers.get("content-type") ?? "";
  if (type.includes("application/json")) return (await request.json()) as Record<string, unknown>;
  return Object.fromEntries((await request.formData()).entries());
}

async function send(input: ContactInput, locale: string): Promise<boolean> {
  const key = process.env.RESEND_API_KEY;
  const to = process.env.CONTACT_TO;
  if (!key || !to) return false;
  const label = TYPES[input.type];
  const subject = `[Papy ${label}] ${input.subject || input.message.slice(0, 40)}`;
  const body = [
    `유형: ${label}`,
    `이름: ${input.name || "(없음)"}`,
    `이메일: ${input.email}`,
    `사이트 언어: ${locale}`,
    "",
    input.message,
    "",
    "— papy 웹사이트 문의 폼에서 보냄. 이 메일에 답장하면 문의자에게 갑니다.",
  ].join("\n");
  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      from: process.env.CONTACT_FROM || "Papy 문의 <onboarding@resend.dev>",
      to: [to],
      reply_to: input.email,
      subject,
      text: body,
    }),
  });
  return res.ok;
}

export async function POST(request: Request): Promise<Response> {
  const wantsJson = (request.headers.get("accept") ?? "").includes("application/json");
  // 스크립트 없이 보낸 폼은 그 언어의 문의 페이지로 돌려보낸다. 언어 값은 목록에 있는 것만 쓴다.
  let locale: string = DEFAULT_LOCALE;
  const done = (status: number, body: { ok: boolean; error?: ErrorCode }) =>
    wantsJson
      ? json(status, body)
      : Response.redirect(new URL(`${localeBase(locale)}/contact/?${body.ok ? "sent=1" : "error=1"}`, request.url), 303);

  if (!allowedOrigin(request.headers.get("origin"), request.headers.get("host"))) {
    return done(403, { ok: false, error: "forbidden" });
  }
  let raw: Record<string, unknown>;
  try {
    raw = await readBody(request);
  } catch {
    return done(400, { ok: false, error: "badRequest" });
  }
  if (typeof raw.lang === "string" && LOCALE_RE.test(raw.lang)) locale = raw.lang;
  const parsed = parse(raw);
  // 스팸은 성공처럼 응답해 봇이 재시도하지 않게 한다.
  if (!parsed.ok) return parsed.spam ? done(200, { ok: true }) : done(400, { ok: false, error: parsed.error });
  try {
    if (await send(parsed.value, locale)) return done(200, { ok: true });
  } catch {
    // 아래 공통 실패 응답
  }
  return done(502, { ok: false, error: "failed" });
}
