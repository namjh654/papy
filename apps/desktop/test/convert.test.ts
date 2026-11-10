// 변환 오차 고지 대상 판별. 실행: pnpm --filter @papy/desktop test
import assert from "node:assert/strict";
import { test } from "node:test";
import { crossesFamily, familyOf, opensWithOtherLayout } from "../src/convert.ts";

test("형식 묶음", () => {
  assert.equal(familyOf("HWPX"), "hangul");
  assert.equal(familyOf("hwp"), "hangul");
  assert.equal(familyOf("DOC"), "word");
  assert.equal(familyOf(".docx"), "word");
  assert.equal(familyOf("PDF"), "pdf");
  assert.equal(familyOf("txt"), "other");
});

test("한글 ↔ 워드로 건너 저장할 때만 고지한다", () => {
  assert.ok(crossesFamily("HWP", "docx"));
  assert.ok(crossesFamily("DOCX", "hwpx"));
  assert.ok(crossesFamily("DOC", "hwp"));
  assert.ok(!crossesFamily("HWP", "hwpx"));
  assert.ok(!crossesFamily("DOC", "docx"));
  assert.ok(!crossesFamily("HWPX", "pdf"));
  assert.ok(!crossesFamily("PDF", "pdf"));
  assert.ok(!crossesFamily("DOCX", "txt"));
});

test("Word 문서를 열 때 고지한다", () => {
  assert.ok(opensWithOtherLayout("DOCX"));
  assert.ok(opensWithOtherLayout("DOC"));
  assert.ok(!opensWithOtherLayout("HWP"));
  assert.ok(!opensWithOtherLayout("PDF"));
});
