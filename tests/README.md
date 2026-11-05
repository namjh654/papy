# 테스트

| 폴더 | 용도 |
|---|---|
| `fixtures/` | 공개·배포 권리가 확인된 테스트 문서만. 파일마다 출처를 아래 표에 기록 |
| `corpus/` | 문서 모음 회귀 기준값(`rhwp-samples.golden.tsv`). 문서는 `node scripts/fetch-corpus.mjs` 로 받는다(git 제외) — [test-corpus.md](../docs/formats/test-corpus.md) |
| `pdf/`, `hwpx/`, `office/` | 포맷별 통합·golden/회귀 테스트(엔진 PoC 시작 시 추가) |
| `private/` | 비공개 문서 자리. `.gitignore` 로 커밋되지 않는다 |

단위 테스트는 각 crate 안(`#[cfg(test)]`)에 둔다.

## fixtures 출처 기록

| 파일 | 출처 URL | 라이선스/배포 허용 근거 | 확인일 |
|---|---|---|---|
| `rhwp/hwp3-curve.hwp` | https://github.com/edwardkim/rhwp/blob/1a76570e833917d15817415a53c09ad61ab3203f/samples/hwp3-curve.hwp | MIT (rhwp 저장소 `samples/`, 같은 폴더 `LICENSE`) | 2026-11-04 |
| `poi/DiffFirstPageHeadFoot.doc`, `poi/table-merges.doc` | https://github.com/apache/poi/tree/7f4d36854109efa17152b27a5fb1b2c7d842c314/test-data/document | Apache-2.0 (Apache POI `test-data/`, 저장소 `legal/LICENSE` → `poi/LICENSE`) | 2026-11-05 |

직접 만든 문서는 "자체 제작"으로 적는다. 저작권이 불명확하거나 개인정보가 있는 문서는 커밋하지 않는다.
