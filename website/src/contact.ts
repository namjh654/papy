// 문의 폼을 /api/contact 로 보내고 결과를 보여 준다. 스크립트가 없어도 폼은 일반 POST 로 동작한다.

const form = document.getElementById("contact-form") as HTMLFormElement | null;
const status = document.getElementById("contact-status");

function show(message: string, ok: boolean) {
  if (!status) return;
  status.hidden = false;
  status.textContent = message;
  status.classList.toggle("success", ok);
  status.scrollIntoView({ block: "nearest" });
}

const SENT = "문의를 보냈습니다. 확인 후 적어 주신 이메일로 답장드리겠습니다.";
const FAILED = "보내지 못했습니다. 잠시 후 다시 시도하거나 GitHub Issues 를 이용해 주세요.";

const params = new URLSearchParams(location.search);
if (params.has("sent")) show(SENT, true);
else if (params.has("error")) show(FAILED, false);

if (form) {
  const startedAt = form.elements.namedItem("startedAt") as HTMLInputElement;
  startedAt.value = String(Date.now());

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    const button = form.querySelector<HTMLButtonElement>("button[type=submit]");
    if (button) button.disabled = true;
    try {
      const res = await fetch(form.action, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify(Object.fromEntries(new FormData(form).entries())),
      });
      const body = (await res.json().catch(() => ({}))) as { ok?: boolean; error?: string };
      if (res.ok && body.ok) {
        form.reset();
        startedAt.value = String(Date.now());
        show(SENT, true);
      } else {
        show(body.error ?? FAILED, false);
      }
    } catch {
      show(FAILED, false);
    } finally {
      if (button) button.disabled = false;
    }
  });
}
