import { useEffect, useState } from "react";
import { unsupportedObjects, type OpenedDocument, type Unsupported } from "../engine";
import { t } from "../i18n";

/** 지금 쪽에 표시하지 못한 개체(차트·OLE·없는 그림)가 있으면 알린다. 닫으면 그 문서에서는 다시 띄우지 않는다. */
export function PageIssues({ doc, page, revision }: { doc: OpenedDocument; page: number; revision: number }) {
  const [issues, setIssues] = useState<Unsupported | null>(null);
  const [dismissed, setDismissed] = useState<number | null>(null);

  useEffect(() => {
    let alive = true;
    setIssues(null);
    // 스크롤 중에는 부르지 않는다(쪽을 다시 배치하는 호출이다).
    const timer = setTimeout(() => {
      unsupportedObjects(doc.id, page).then(
        (value) => alive && setIssues(value),
        () => alive && setIssues(null),
      );
    }, 400);
    return () => {
      alive = false;
      clearTimeout(timer);
    };
  }, [doc.id, page, revision]);

  if (!issues || dismissed === doc.id) return null;
  const { charts, embedded, missingImages } = issues;
  if (charts + embedded + missingImages === 0) return null;
  return (
    <div className="loss-banner" role="status">
      <span>{t.app.unsupported(charts, embedded, missingImages)}</span>
      <button className="ghost small" onClick={() => setDismissed(doc.id)}>
        {t.common.close}
      </button>
    </div>
  );
}
