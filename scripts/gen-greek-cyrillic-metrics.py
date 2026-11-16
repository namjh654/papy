"""Papy: SFNT cmap/hmtx 에서 그리스·키릴(U+0370..U+04FF) 전진폭을 뽑아 rhwp 보충 표를 만든다.

   python scripts/gen-greek-cyrillic-metrics.py C:/Windows/Fonts <rhwp>/src/renderer/font_metric_greek_cyrillic.rs

그 뒤 engine/patches/rhwp/0026 패치를 다시 만든다(docs/development/next-steps.md "rhwp 패치").
"""
import hashlib, os, sys
from fontTools.ttLib import TTFont, TTCollection

FONT_DIR, OUT = sys.argv[1], sys.argv[2]
START, END = 0x0370, 0x04FF
N = END - START + 1
DB_EM = 2048
# (rhwp 이름, bold, italic, 파일[, ttc 번호])
FACES = []
def add(name, files):
    for (bold, italic), f in zip([(False, False), (True, False), (False, True), (True, True)], files):
        if f:
            FACES.append((name, bold, italic, f))
add("Times New Roman", ["times.ttf", "timesbd.ttf", "timesi.ttf", "timesbi.ttf"])
add("Arial", ["arial.ttf", "arialbd.ttf", "ariali.ttf", "arialbi.ttf"])
add("Courier New", ["cour.ttf", "courbd.ttf", "couri.ttf", "courbi.ttf"])
add("Verdana", ["verdana.ttf", "verdanab.ttf", "verdanai.ttf", "verdanaz.ttf"])
add("Tahoma", ["tahoma.ttf", "tahomabd.ttf", None, None])
add("Georgia", ["georgia.ttf", "georgiab.ttf", "georgiai.ttf", "georgiaz.ttf"])
add("Trebuchet MS", ["trebuc.ttf", "trebucbd.ttf", "trebucit.ttf", "trebucbi.ttf"])
add("Calibri", ["calibri.ttf", "calibrib.ttf", "calibrii.ttf", "calibriz.ttf"])
add("Cambria", ["cambria.ttc", "cambriab.ttf", "cambriai.ttf", "cambriaz.ttf"])
add("Segoe UI", ["segoeui.ttf", "segoeuib.ttf", "segoeuii.ttf", "segoeuiz.ttf"])
add("Microsoft Sans Serif", ["micross.ttf", None, None, None])

def load(path):
    if path.endswith(".ttc"):
        return TTCollection(path).fonts[0]
    return TTFont(path)

out = ["// [Papy] Papy scripts/gen-greek-cyrillic-metrics.py: SFNT cmap/hmtx 직접 추출(Windows 11 글꼴). U+0370..U+04FF(그리스·키릴).",
       "// 기본 표(Latin-1 등)에 이 구간이 없어 반각(0.5em)으로 재던 글자를 글꼴 전진폭으로 잰다.",
       "// 결측 글리프는 None(기존 폴백 그대로). 폭은 DB em(2048)으로 맞춘다.",
       "use super::FontMetric;", ""]
arms = []
for k, (name, bold, italic, f) in enumerate(FACES):
    path = os.path.join(FONT_DIR, f)
    if not os.path.exists(path):
        print("skip", name, bold, italic, f); continue
    data = open(path, "rb").read()
    font = load(path)
    upem = font["head"].unitsPerEm
    cmap = font.getBestCmap(); hmtx = font["hmtx"].metrics
    widths = []
    for cp in range(START, END + 1):
        g = cmap.get(cp)
        widths.append(round(hmtx[g][0] * DB_EM / upem) if g else 0)
    have = sum(1 for w in widths if w)
    print(f"{name} b={bold} i={italic} upem={upem} glyphs={have}")
    out.append(f"// face: {name}; file: {f}; SHA-256: {hashlib.sha256(data).hexdigest()}; program em: {upem}; DB em: {DB_EM}.")
    out.append(f"static FACE_{k}: [u16; {N}] = [")
    for i in range(0, N, 16):
        out.append("    " + ", ".join(str(w) for w in widths[i:i + 16]) + ",")
    out.append("];")
    arms.append(f'        ("{name}", {DB_EM}, {str(bold).lower()}, {str(italic).lower()}) => &FACE_{k},')
out += ["",
        "fn entry(metric: &FontMetric, ch: char) -> Option<u16> {",
        f"    if !(0x{START:04X}..=0x{END:04X}).contains(&(ch as u32)) {{",
        "        return None;",
        "    }",
        "    let widths: &[u16] = match (metric.name, metric.em_size, metric.bold, metric.italic) {"]
out += arms
out += ["        _ => return None,",
        "    };",
        f"    Some(widths[(ch as u32 - 0x{START:04X}) as usize])",
        "}",
        "",
        "pub(super) fn has_glyph(metric: &FontMetric, ch: char) -> Option<bool> {",
        "    entry(metric, ch).map(|width| width > 0)",
        "}",
        "",
        "pub(super) fn width(metric: &FontMetric, ch: char) -> Option<u16> {",
        "    entry(metric, ch).filter(|width| *width > 0)",
        "}",
        "",
        ""]
open(OUT, "w", encoding="utf8", newline="\n").write("\n".join(out))
