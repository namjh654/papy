// 형식 묶음(한글·워드·PDF)과 변환 오차 고지(ADR 0008).
// 한글과 Word 는 조판 규칙이 달라, 형식 묶음을 건너 저장하거나 Word 문서를 열면 배치·쪽 수가 원본 프로그램과 다를 수 있다.

export type Family = "hangul" | "word" | "pdf" | "other";

/** 문서 형식 이름("HWPX")이나 확장자("hwpx")의 형식 묶음 */
export function familyOf(format: string): Family {
  switch (format.toLowerCase().replace(/^\./, "")) {
    case "hwp":
    case "hwpx":
      return "hangul";
    case "doc":
    case "docx":
      return "word";
    case "pdf":
      return "pdf";
    default:
      return "other";
  }
}

/** 한글 ↔ 워드로 건너 저장하는가(같은 묶음 안, PDF·글로 내보내기는 아니다). */
export function crossesFamily(sourceFormat: string, targetExtension: string): boolean {
  const from = familyOf(sourceFormat);
  const to = familyOf(targetExtension);
  return (from === "hangul" && to === "word") || (from === "word" && to === "hangul");
}

/** Word 문서는 한글 조판 엔진으로 보여 주므로 열 때 알린다. */
export const opensWithOtherLayout = (format: string) => familyOf(format) === "word";

const KEY = "papy.notice.hidden";

function hidden(): string[] {
  try {
    const value = JSON.parse(localStorage.getItem(KEY) ?? "[]");
    return Array.isArray(value) ? value.filter((item) => typeof item === "string") : [];
  } catch {
    return [];
  }
}

/** 사용자가 "다시 보지 않기"를 고른 고지인가 */
export const noticeHidden = (name: "convert" | "wordLayout") => hidden().includes(name);

export function hideNotice(name: "convert" | "wordLayout") {
  try {
    localStorage.setItem(KEY, JSON.stringify([...new Set([...hidden(), name])]));
  } catch {
    // 저장소를 못 쓰면 다음에도 보여 준다.
  }
}
