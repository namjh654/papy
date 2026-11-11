//! 그림 파일 머리에서 픽셀 크기 읽기.

/// 그림 파일의 픽셀 크기(PNG·GIF·BMP·JPEG 머리).
pub fn pixel_size(bytes: &[u8]) -> Option<(u32, u32)> {
    let be16 = |i: usize| {
        Some(u32::from(u16::from_be_bytes([
            *bytes.get(i)?,
            *bytes.get(i + 1)?,
        ])))
    };
    let le16 = |i: usize| {
        Some(u32::from(u16::from_le_bytes([
            *bytes.get(i)?,
            *bytes.get(i + 1)?,
        ])))
    };
    let be32 = |i: usize| Some(u32::from_be_bytes(bytes.get(i..i + 4)?.try_into().ok()?));
    let le32 =
        |i: usize| Some(i32::from_le_bytes(bytes.get(i..i + 4)?.try_into().ok()?).unsigned_abs());
    let size = if bytes.starts_with(b"\x89PNG") {
        (be32(16)?, be32(20)?)
    } else if bytes.starts_with(b"GIF8") {
        (le16(6)?, le16(8)?)
    } else if bytes.starts_with(b"BM") {
        (le32(18)?, le32(22)?)
    } else if bytes.starts_with(&[0xFF, 0xD8]) {
        // SOFn 표지를 찾는다.
        let mut i = 2;
        loop {
            if *bytes.get(i)? != 0xFF {
                return None;
            }
            let marker = *bytes.get(i + 1)?;
            let length = be16(i + 2)? as usize;
            if matches!(marker, 0xC0..=0xCF) && !matches!(marker, 0xC4 | 0xC8 | 0xCC) {
                break (be16(i + 7)?, be16(i + 5)?);
            }
            i += 2 + length;
        }
    } else {
        return None;
    };
    (size.0 > 0 && size.1 > 0).then_some(size)
}
