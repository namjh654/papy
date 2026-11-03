// 열린 PR·Issue 최근 5개를 GitHub API 에서 읽어 보여 준다. 실패하면 "전체 보기" 링크만 남긴다.
import { lang, t } from "./i18n.ts";

const REPO = "namjh654/papy";

interface Item {
  number: number;
  title: string;
  html_url: string;
  user: { login: string } | null;
  updated_at: string;
  pull_request?: unknown;
}

async function load(kind: "pulls" | "issues"): Promise<Item[]> {
  const res = await fetch(`https://api.github.com/repos/${REPO}/${kind}?state=open&per_page=10&sort=updated`, {
    headers: { Accept: "application/vnd.github+json" },
  });
  if (!res.ok) throw new Error(`GitHub API ${res.status}`);
  const items = (await res.json()) as Item[];
  // issues API 는 PR 도 돌려주므로 뺀다.
  return (kind === "issues" ? items.filter((i) => !i.pull_request) : items).slice(0, 5);
}

function render(list: HTMLElement, items: Item[]) {
  list.replaceChildren();
  if (items.length === 0) {
    const li = document.createElement("li");
    li.className = "meta";
    li.textContent = t.developers.empty;
    list.append(li);
    return;
  }
  for (const item of items) {
    const li = document.createElement("li");
    const link = document.createElement("a");
    link.href = item.html_url;
    link.textContent = `#${item.number} ${item.title}`;
    const meta = document.createElement("span");
    meta.className = "meta";
    meta.textContent = `${item.user?.login ?? ""} · ${new Date(item.updated_at).toLocaleDateString(lang)}`;
    li.append(link, meta);
    list.append(li);
  }
}

for (const list of document.querySelectorAll<HTMLElement>("[data-feed]")) {
  const kind = list.dataset.feed as "pulls" | "issues";
  load(kind).then(
    (items) => render(list, items),
    () => {
      list.replaceChildren();
    },
  );
}
