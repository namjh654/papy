// 최근 문서 목록. 이 기기의 앱 저장소(localStorage)에만 남고 밖으로 보내지 않는다. 사용자가 지울 수 있다.

export interface RecentFile {
  path: string;
  name: string;
  format: string;
  openedAt: number;
}

const KEY = "papy.recent.v1";
const MAX = 12;

export function loadRecent(): RecentFile[] {
  try {
    const value = JSON.parse(localStorage.getItem(KEY) ?? "[]");
    return Array.isArray(value) ? value.filter((item) => typeof item?.path === "string") : [];
  } catch {
    return [];
  }
}

function save(list: RecentFile[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(list));
  } catch {
    // 저장소를 못 쓰면 목록 없이 동작한다.
  }
}

export function rememberRecent(file: Omit<RecentFile, "openedAt">): RecentFile[] {
  const list = [{ ...file, openedAt: Date.now() }, ...loadRecent().filter((item) => item.path !== file.path)].slice(0, MAX);
  save(list);
  return list;
}

export function forgetRecent(path: string): RecentFile[] {
  const list = loadRecent().filter((item) => item.path !== path);
  save(list);
  return list;
}

export function clearRecent(): RecentFile[] {
  save([]);
  return [];
}
