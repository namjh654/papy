// GitHub Releases 에서 최신 버전의 설치 파일 목록을 읽어 운영체제별 카드에 채운다.
// 실패하면 Releases 페이지 링크만 보여 준다. 이 요청 외에 다른 외부 요청은 하지 않는다.

const REPO = "namjh654/papy";
const RELEASES_URL = `https://github.com/${REPO}/releases`;

type Os = "windows" | "macos" | "linux";

interface Asset {
  name: string;
  browser_download_url: string;
  size: number;
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

const RULES: { os: Os; test: RegExp; label: string }[] = [
  { os: "windows", test: /-setup\.exe$/i, label: "설치 프로그램 (.exe, 권장)" },
  { os: "windows", test: /\.msi$/i, label: "MSI 패키지" },
  { os: "macos", test: /aarch64\.dmg$/i, label: "Apple Silicon (.dmg)" },
  { os: "macos", test: /x64\.dmg$/i, label: "Intel (.dmg)" },
  { os: "macos", test: /universal\.dmg$/i, label: "Universal (.dmg)" },
  { os: "linux", test: /\.AppImage$/i, label: "AppImage" },
  { os: "linux", test: /\.deb$/i, label: "Debian/Ubuntu (.deb)" },
  { os: "linux", test: /\.rpm$/i, label: "Fedora/openSUSE (.rpm)" },
];

function detectOs(): Os | undefined {
  const ua = navigator.userAgent;
  if (/Windows/i.test(ua)) return "windows";
  if (/Macintosh|Mac OS X/i.test(ua) && !/iPhone|iPad/i.test(ua)) return "macos";
  if (/Linux/i.test(ua) && !/Android/i.test(ua)) return "linux";
  return undefined;
}

const mb = (bytes: number) => `${(bytes / 1024 / 1024).toFixed(1)} MB`;

async function latestRelease(): Promise<Release | undefined> {
  const res = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=10`, {
    headers: { Accept: "application/vnd.github+json" },
  });
  if (!res.ok) throw new Error(`GitHub API ${res.status}`);
  const releases = (await res.json()) as Release[];
  return releases.find((r) => !r.draft && r.assets.length > 0);
}

function renderFiles(release: Release | undefined) {
  for (const card of document.querySelectorAll<HTMLElement>("[data-os]")) {
    const os = card.dataset.os as Os;
    const box = card.querySelector<HTMLElement>("[data-files]");
    if (!box) continue;
    box.replaceChildren();
    const links = RULES.filter((r) => r.os === os).flatMap((rule) =>
      (release?.assets ?? [])
        .filter((a) => rule.test.test(a.name))
        .map((a) => {
          const link = document.createElement("a");
          link.className = "btn";
          link.href = a.browser_download_url;
          link.textContent = `${rule.label} · ${mb(a.size)}`;
          link.title = a.name;
          return link;
        }),
    );
    if (links.length === 0) {
      const link = document.createElement("a");
      link.href = RELEASES_URL;
      link.textContent = "GitHub Releases 에서 보기";
      links.push(link);
    }
    box.append(...links);
  }
}

async function main() {
  const status = document.getElementById("release-status");
  const os = detectOs();
  if (os) document.querySelector(`[data-os="${os}"]`)?.classList.add("recommended");
  try {
    const release = await latestRelease();
    renderFiles(release);
    if (!status) return;
    if (!release) {
      status.innerHTML = `아직 공개된 설치 파일이 없습니다. 첫 빌드가 올라오면 이곳에 자동으로 표시됩니다. <a href="${RELEASES_URL}">GitHub Releases</a>`;
      return;
    }
    const date = release.published_at ? new Date(release.published_at).toLocaleDateString("ko-KR") : "";
    const pre = release.prerelease ? " (미리보기 빌드 — 아직 문서를 열 수 없습니다)" : "";
    status.textContent = `최신 버전: ${release.name || release.tag_name}${date ? ` · ${date}` : ""}${pre}`;
  } catch {
    renderFiles(undefined);
    if (status) status.innerHTML = `버전 정보를 불러오지 못했습니다. <a href="${RELEASES_URL}">GitHub Releases</a>에서 직접 받아 주세요.`;
  }
}

void main();
