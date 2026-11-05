# 포맷 문서

| 포맷 | 상태 | 문서 |
|---|---|---|
| PDF | 보기·주석·쪽 편집·저장 (PDFium) | [ADR 0003](../decisions/0003-pdf-engine.md) |
| HWPX · HWP 5.x | 보기·편집·저장·변환 (rhwp), HWP 3.x 는 보기·PDF 내보내기만 | [hwp-hwpx.md](hwp-hwpx.md) |
| DOCX | 보기·편집·저장·변환 (문서 구조로 바꿔 rhwp 로 조판) | [word.md](word.md) |
| DOC (Word 97–2003) | 보기·편집·변환. 같은 형식으로는 저장하지 않는다(DOCX·HWP·HWPX·PDF 로 저장) | [word.md](word.md) |
| XLSX · PPTX | 보류 — 연구 중 | [ADR 0007](../decisions/0007-xlsx-pptx.md) |
| XLS · PPT | 후순위 | — |

엔진은 파일 앞부분으로 형식을 판별한다(`papy-core::detect_format`): `%PDF-` → PDF, ZIP 첫 항목 `mimetype` = `application/hwp+zip` → HWPX,
그 밖의 ZIP → Office 후보(`word/document.xml` 이 있으면 DOCX), OLE CFB → HWP 5.x/3.x 또는 Word 97–2003(`WordDocument` 스트림).

## PDF 로 변환

PDF 가 아닌 문서(HWP·HWPX·DOCX·DOC)는 모두 PDF 로 내보낼 수 있다(데스크톱 "내보내기", 모바일 "PDF 로 내보내기"). rhwp 의 SVG → PDF
경로(svg2pdf)를 쓰고, 글은 글꼴을 넣어 고를 수 있게 둔다. 문서 모음 점검은 `PAPY_CORPUS_PDF=1`([test-corpus.md](test-corpus.md)).
