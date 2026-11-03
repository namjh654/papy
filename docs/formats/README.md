# 포맷 문서

| 포맷 | 우선순위 | 상태 |
|---|---|---|
| PDF | P0 | 엔진 후보(PDFium/MuPDF) 조사 전 |
| HWPX | P0 | 파서 후보(OpenHWP 등) 조사 전 |
| HWP 5.x | 검증 게이트 | 검증 전 지원을 약속하지 않음 |
| DOCX / XLSX | P1 | — |
| PPTX | P2 | — |
| DOC / XLS / PPT | 후순위 | — |

현재 엔진은 파일 앞부분 시그니처로 후보만 판별한다(`papy-core::detect_format`): `%PDF-` → PDF, ZIP 첫 항목 `mimetype` = `application/hwp+zip` → HWPX,
그 밖의 ZIP → Office 후보, OLE CFB → HWP 5.x/구형 Office 후보.
