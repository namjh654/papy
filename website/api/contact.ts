// 문의 폼 → Resend 로 개발자에게 메일을 보낸다 (Vercel Function).
// 필요한 환경 변수: RESEND_API_KEY, CONTACT_TO. 선택: CONTACT_FROM, ALLOWED_ORIGINS(쉼표 구분).
// 문의 내용은 로그에 남기지 않는다.

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

type Parsed = { ok: true; value: ContactInput } | { ok: false; error: string; spam?: boolean };

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json; charset=utf-8" } });

const text = (value: unknown, max: number) => (typeof value === "string" ? value.trim().slice(0, max) : "");

function parse(raw: Record<string, unknown>): Parsed {
  // 봇 차단: 숨은 칸(website)이 채워졌거나, 폼을 연 뒤 너무 빨리 보냈으면 스팸으로 본다.
  if (text(raw.website, 200)) return { ok: false, error: "spam", spam: true };
  const startedAt = Number(raw.startedAt);
  if (!Number.isFinite(startedAt) || Date.now() - startedAt < MIN_FILL_MS) return { ok: false, error: "spam", spam: true };

  const type = text(raw.type, 20) as ContactType;
  if (!(type in TYPES)) return { ok: false, error: "문의 유형을 골라 주세요." };
  const email = text(raw.email, LIMITS.email);
  if (!EMAIL_RE.test(email)) return { ok: false, error: "답장 받을 이메일 주소를 확인해 주세요." };
  const message = text(raw.message, LIMITS.message);
  if (message.length < LIMITS.minMessage) return { ok: false, error: `내용을 ${LIMITS.minMessage}자 이상 적어 주세요.` };
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

async function send(input: ContactInput): Promise<boolean> {
  const key = process.env.RESEND_API_KEY;
  const to = process.env.CONTACT_TO;
  if (!key || !to) return false;
  const label = TYPES[input.type];
  const subject = `[Papy ${label}] ${input.subject || input.message.slice(0, 40)}`;
  const body = [
    `유형: ${label}`,
    `이름: ${input.name || "(없음)"}`,
    `이메일: ${input.email}`,
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
  const done = (status: number, body: { ok: boolean; error?: string }) =>
    wantsJson
      ? json(status, body)
      : Response.redirect(new URL(body.ok ? "/contact/?sent=1" : "/contact/?error=1", request.url), 303);

  if (!allowedOrigin(request.headers.get("origin"), request.headers.get("host"))) {
    return done(403, { ok: false, error: "허용되지 않은 요청입니다." });
  }
  let raw: Record<string, unknown>;
  try {
    raw = await readBody(request);
  } catch {
    return done(400, { ok: false, error: "요청 형식이 올바르지 않습니다." });
  }
  const parsed = parse(raw);
  // 스팸은 성공처럼 응답해 봇이 재시도하지 않게 한다.
  if (!parsed.ok) return parsed.spam ? done(200, { ok: true }) : done(400, { ok: false, error: parsed.error });
  try {
    if (await send(parsed.value)) return done(200, { ok: true });
  } catch {
    // 아래 공통 실패 응답
  }
  return done(502, { ok: false, error: "메일을 보내지 못했습니다. 잠시 후 다시 시도하거나 GitHub Issues 를 이용해 주세요." });
}
