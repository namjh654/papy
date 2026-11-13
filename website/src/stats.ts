// GitHub Releases 의 설치 파일 다운로드 횟수(download_count)를 모아 버전·운영체제별로 보여 준다.
// 요청은 api.github.com 한 번뿐이다(로그인 없는 요청은 IP 당 시간당 60번).
import { fill, lang, t } from "./i18n.ts";

const REPO = "namjh654/papy";
const RELEASES_URL = `https://github.com/${REPO}/releases`;

interface Asset {
  name: string;
  download_count: number;
}

interface Release {
  tag_name: string;
  name: string | null;
  html_url: string;
  draft: boolean;
  prerelease: boolean;
  published_at: string | null;
  assets: Asset[];
}

type Os = "Windows" | "macOS" | "Linux" | "other";
const OS_ORDER: Os[] = ["Windows", "macOS", "Linux", "other"];

function osOf(name: string): Os {
  if (/\.(exe|msi)$/i.test(name)) return "Windows";
  if (/\.(dmg|app\.tar\.gz)$/i.test(name)) return "macOS";
  if (/\.(AppImage|deb|rpm)$/i.test(name)) return "Linux";
  return "other";
}

const num = (n: number) => n.toLocaleString(lang);
const sum = (assets: Asset[]) => assets.reduce((total, a) => total + a.download_count, 0);

function cell(text: string, numeric = false): HTMLTableCellElement {
  const td = document.createElement("td");
  td.textContent = text;
  if (numeric) td.className = "num";
  return td;
}

function row(...cells: HTMLTableCellElement[]): HTMLTableRowElement {
  const tr = document.createElement("tr");
  tr.append(...cells);
  return tr;
}

function setStat(key: string, text: string) {
  const el = document.querySelector<HTMLElement>(`[data-stat="${key}"]`);
  if (el) el.textContent = text;
}

function table(key: string): HTMLElement | null {
  return document.querySelector<HTMLElement>(`[data-table="${key}"]`);
}

async function allReleases(): Promise<Release[]> {
  const releases: Release[] = [];
  for (let page = 1; page <= 5; page++) {
    const res = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=100&page=${page}`, {
      headers: { Accept: "application/vnd.github+json" },
    });
    if (!res.ok) throw new Error(`GitHub API ${res.status}`);
    const batch = (await res.json()) as Release[];
    releases.push(...batch);
    if (batch.length < 100) break;
  }
  // 앱 버전(v…)만 센다. fonts-v1 같은 빌드용 자료 릴리스는 뺀다.
  return releases.filter((r) => !r.draft && /^v\d/.test(r.tag_name) && r.assets.length > 0);
}

function render(releases: Release[]) {
  const assets = releases.flatMap((r) => r.assets);
  const total = sum(assets);
  const latest = releases.find((r) => !r.prerelease);

  setStat("total", num(total));
  setStat("releases", num(releases.length));
  setStat("latest", latest ? num(sum(latest.assets)) : "–");
  setStat("latest-name", latest ? latest.tag_name : "");

  const byOs = new Map<Os, number>();
  for (const a of assets) byOs.set(osOf(a.name), (byOs.get(osOf(a.name)) ?? 0) + a.download_count);
  table("os")?.replaceChildren(
    ...OS_ORDER.filter((os) => os !== "other" || byOs.get("other")).map((os) => {
      const count = byOs.get(os) ?? 0;
      const share = total ? `${((count / total) * 100).toFixed(1)}%` : "–";
      return row(cell(os === "other" ? t.stats.other : os), cell(num(count), true), cell(share, true));
    }),
  );

  table("releases")?.replaceChildren(
    ...releases.map((r) => {
      const per = (os: Os) => sum(r.assets.filter((a) => osOf(a.name) === os));
      const name = document.createElement("td");
      const link = document.createElement("a");
      link.href = r.html_url;
      link.textContent = r.tag_name;
      name.append(link);
      const date = r.published_at ? new Date(r.published_at).toLocaleDateString(lang) : "";
      return row(name, cell(date), cell(num(per("Windows")), true), cell(num(per("macOS")), true), cell(num(per("Linux")), true), cell(num(sum(r.assets)), true));
    }),
  );

  const files = [...(latest?.assets ?? [])].sort((a, b) => b.download_count - a.download_count);
  table("files")?.replaceChildren(
    ...files.map((a) => {
      const label = /\.app\.tar\.gz$/i.test(a.name) ? `${a.name} (${t.stats.terminal})` : a.name;
      return row(cell(label), cell(num(a.download_count), true));
    }),
  );
}

async function main() {
  const status = document.getElementById("stats-status");
  try {
    render(await allReleases());
    if (status) status.textContent = fill(t.stats.updated, { date: new Date().toLocaleString(lang) });
  } catch {
    if (!status) return;
    const [before, after = ""] = t.stats.failed.split("{link}");
    const link = document.createElement("a");
    link.href = RELEASES_URL;
    link.textContent = "GitHub Releases";
    status.replaceChildren(before ?? "", link, after);
  }
}

void main();
