//! 암호 걸린 Word 97–2003 문서 풀기(MS-OFFCRYPTO 2.3.5 RC4 CryptoAPI·2.3.6 RC4).
//!
//! 암호 머리는 Table 스트림 앞에 평문으로 있다. WordDocument(앞 68바이트 FibBase 빼고)·Table·Data 스트림은
//! 스트림 처음부터 이어지는 RC4 로 암호화되어 있고, 512바이트 블록마다 블록 번호로 키를 새로 만든다.
//! XOR 난독화(`fObfuscated`, MS-DOC 2.2.6.1)는 MS-OFFCRYPTO 2.3.7.4~2.3.7.6(Method 2)으로 푼다 — 암호 검증값은
//! FibBase `lKey`, 16바이트 배열을 스트림 위치에 맞춰 XOR 한다(0 과 배열 값과 같은 바이트는 그대로).

use papy_model::{Error, ErrorCode, Result};
use sha1::{Digest, Sha1};

/// 블록 크기(바이트): 이만큼마다 키를 새로 만든다.
const BLOCK: usize = 512;
/// 암호화되지 않는 WordDocument 앞부분(FibBase)
const FIB_BASE: usize = 68;

fn password_error(message: &str) -> Error {
    Error::new(ErrorCode::PasswordRequired, message)
}

/// 키 만드는 방식
enum Scheme {
    /// RC4(버전 1.1): MD5, 키 5바이트를 16바이트로 늘림
    Rc4 { h1: [u8; 5] },
    /// RC4 CryptoAPI(버전 2·3·4 . 2): SHA-1, 키 길이는 머리에 적힌 대로
    CryptoApi { h0: [u8; 20], key_bytes: usize },
}

impl Scheme {
    fn block_key(&self, block: u32) -> Vec<u8> {
        match self {
            Scheme::Rc4 { h1 } => {
                let mut input = h1.to_vec();
                input.extend_from_slice(&block.to_le_bytes());
                md5(&input).to_vec()
            }
            Scheme::CryptoApi { h0, key_bytes } => {
                let mut hasher = Sha1::new();
                hasher.update(h0);
                hasher.update(block.to_le_bytes());
                let digest = hasher.finalize();
                let mut key = digest[..*key_bytes].to_vec();
                // 40비트 키는 0 으로 채운 128비트 키로 쓴다.
                if *key_bytes == 5 {
                    key.resize(16, 0);
                }
                key
            }
        }
    }

    /// `data` 를 스트림 처음부터 블록마다 키를 바꿔 가며 풀거나 암호화한다(RC4 는 같은 연산).
    fn apply(&self, data: &mut [u8]) {
        for (index, chunk) in data.chunks_mut(BLOCK).enumerate() {
            Rc4::new(&self.block_key(index as u32)).apply(chunk);
        }
    }
}

/// 암호를 확인하고 키 만드는 방식을 돌려준다. `table` 은 Table 스트림(앞에 암호 머리).
fn scheme(table: &[u8], password: &str) -> Result<Scheme> {
    let short = || Error::new(ErrorCode::InvalidDocument, "encryption header is too short");
    let u16_at = |i: usize| -> Result<u16> {
        Ok(u16::from_le_bytes(
            table
                .get(i..i + 2)
                .ok_or_else(short)?
                .try_into()
                .unwrap_or_default(),
        ))
    };
    let u32_at = |i: usize| -> Result<u32> {
        Ok(u32::from_le_bytes(
            table
                .get(i..i + 4)
                .ok_or_else(short)?
                .try_into()
                .unwrap_or_default(),
        ))
    };
    let bytes = |a: usize, b: usize| -> Result<&[u8]> { table.get(a..b).ok_or_else(short) };
    let utf16: Vec<u8> = password
        .encode_utf16()
        .take(255)
        .flat_map(u16::to_le_bytes)
        .collect();
    let (major, minor) = (u16_at(0)?, u16_at(2)?);
    let (scheme, verifier, hash, hash_len) = match (major, minor) {
        (1, 1) => {
            let salt = bytes(4, 20)?;
            let mut h0 = [0u8; 5];
            h0.copy_from_slice(&md5(&utf16)[..5]);
            let mut intermediate = Vec::with_capacity(21 * 16);
            for _ in 0..16 {
                intermediate.extend_from_slice(&h0);
                intermediate.extend_from_slice(salt);
            }
            let mut h1 = [0u8; 5];
            h1.copy_from_slice(&md5(&intermediate)[..5]);
            (Scheme::Rc4 { h1 }, bytes(20, 36)?, bytes(36, 52)?, 16)
        }
        (2..=4, 2) => {
            let header_size = u32_at(8)? as usize;
            let header = bytes(12, 12usize.saturating_add(header_size))?;
            let alg = u32::from_le_bytes(
                header
                    .get(8..12)
                    .ok_or_else(short)?
                    .try_into()
                    .unwrap_or_default(),
            );
            // 0x6801 = RC4. AES 는 Word 97–2003 이진 문서에 쓰이지 않는다.
            if alg != 0x6801 && alg != 0 {
                return Err(Error::new(
                    ErrorCode::NotImplemented,
                    "unsupported Word encryption algorithm",
                ));
            }
            let bits = u32::from_le_bytes(
                header
                    .get(16..20)
                    .ok_or_else(short)?
                    .try_into()
                    .unwrap_or_default(),
            );
            let key_bytes = match bits {
                0 | 40 => 5,
                bits if bits % 8 == 0 && (48..=128).contains(&bits) => bits as usize / 8,
                _ => {
                    return Err(Error::new(
                        ErrorCode::InvalidDocument,
                        "bad encryption key size",
                    ));
                }
            };
            let at = 12 + header_size;
            let salt_size = u32_at(at)? as usize;
            if salt_size != 16 {
                return Err(Error::new(
                    ErrorCode::InvalidDocument,
                    "bad encryption salt size",
                ));
            }
            let salt = bytes(at + 4, at + 20)?;
            let verifier = bytes(at + 20, at + 36)?;
            let hash_size = u32_at(at + 36)? as usize;
            if hash_size != 20 {
                return Err(Error::new(
                    ErrorCode::InvalidDocument,
                    "bad verifier hash size",
                ));
            }
            let hash = bytes(at + 40, at + 60)?;
            let mut hasher = Sha1::new();
            hasher.update(salt);
            hasher.update(&utf16);
            let mut h0 = [0u8; 20];
            h0.copy_from_slice(&hasher.finalize());
            (Scheme::CryptoApi { h0, key_bytes }, verifier, hash, 20)
        }
        _ => {
            return Err(Error::new(
                ErrorCode::NotImplemented,
                "unsupported Word encryption version",
            ));
        }
    };
    // 확인값(16)과 그 해시를 블록 0 키 하나로 이어서 푼다.
    let mut check = verifier.to_vec();
    check.extend_from_slice(hash);
    Rc4::new(&scheme.block_key(0)).apply(&mut check);
    let (plain, expected) = check.split_at(16);
    let actual: Vec<u8> = if hash_len == 16 {
        md5(plain).to_vec()
    } else {
        Sha1::digest(plain).to_vec()
    };
    if actual != expected[..hash_len] {
        return Err(password_error("password required or incorrect"));
    }
    Ok(scheme)
}

/// 암호 걸린 DOC 의 스트림을 제자리에서 푼다. 암호가 없거나 틀리면 `PasswordRequired`.
pub(crate) fn decrypt(
    password: Option<&str>,
    word: &mut [u8],
    table: &mut [u8],
    data: Option<&mut Vec<u8>>,
) -> Result<()> {
    let password = password
        .filter(|p| !p.is_empty())
        .ok_or_else(|| password_error("encrypted Word document"))?;
    let scheme = scheme(table, password)?;
    let mut base = [0u8; FIB_BASE];
    let keep = FIB_BASE.min(word.len());
    base[..keep].copy_from_slice(&word[..keep]);
    scheme.apply(word);
    word[..keep].copy_from_slice(&base[..keep]);
    scheme.apply(table);
    if let Some(data) = data {
        scheme.apply(data);
    }
    Ok(())
}

/// XOR 난독화 표(MS-OFFCRYPTO 2.3.7.2)
const PAD: [u8; 15] = [
    0xBB, 0xFF, 0xFF, 0xBA, 0xFF, 0xFF, 0xB9, 0x80, 0x00, 0xBE, 0x0F, 0x00, 0xBF, 0x0F, 0x00,
];
const INITIAL_CODE: [u16; 15] = [
    0xE1F0, 0x1D0F, 0xCC9C, 0x84C0, 0x110C, 0x0E10, 0xF1CE, 0x313E, 0x1872, 0xE139, 0xD40F, 0x84F9,
    0x280C, 0xA96A, 0x4EC3,
];
const XOR_MATRIX: [u16; 105] = [
    0xAEFC, 0x4DD9, 0x9BB2, 0x2745, 0x4E8A, 0x9D14, 0x2A09, 0x7B61, 0xF6C2, 0xFDA5, 0xEB6B, 0xC6F7,
    0x9DCF, 0x2BBF, 0x4563, 0x8AC6, 0x05AD, 0x0B5A, 0x16B4, 0x2D68, 0x5AD0, 0x0375, 0x06EA, 0x0DD4,
    0x1BA8, 0x3750, 0x6EA0, 0xDD40, 0xD849, 0xA0B3, 0x5147, 0xA28E, 0x553D, 0xAA7A, 0x44D5, 0x6F45,
    0xDE8A, 0xAD35, 0x4A4B, 0x9496, 0x390D, 0x721A, 0xEB23, 0xC667, 0x9CEF, 0x29FF, 0x53FE, 0xA7FC,
    0x5FD9, 0x47D3, 0x8FA6, 0x0F6D, 0x1EDA, 0x3DB4, 0x7B68, 0xF6D0, 0xB861, 0x60E3, 0xC1C6, 0x93AD,
    0x377B, 0x6EF6, 0xDDEC, 0x45A0, 0x8B40, 0x06A1, 0x0D42, 0x1A84, 0x3508, 0x6A10, 0xAA51, 0x4483,
    0x8906, 0x022D, 0x045A, 0x08B4, 0x1168, 0x76B4, 0xED68, 0xCAF1, 0x85C3, 0x1BA7, 0x374E, 0x6E9C,
    0x3730, 0x6E60, 0xDCC0, 0xA9A1, 0x4363, 0x86C6, 0x1DAD, 0x3331, 0x6662, 0xCCC4, 0x89A9, 0x0373,
    0x06E6, 0x0DCC, 0x1021, 0x2042, 0x4084, 0x8108, 0x1231, 0x2462, 0x48C4,
];

/// 암호 → 바이트(최대 15자). UTF-16 글자마다 아래 바이트, 그것이 0 이면 위 바이트(2.3.7.4).
fn xor_password(password: &str) -> Vec<u8> {
    password
        .encode_utf16()
        .take(15)
        .map(|c| match (c & 0xFF) as u8 {
            0 => (c >> 8) as u8,
            low => low,
        })
        .collect()
}

/// CreateXorKey_Method1
fn xor_key(password: &[u8]) -> u16 {
    let Some(last) = password.len().checked_sub(1) else {
        return 0;
    };
    let mut key = INITIAL_CODE[last];
    let mut element = XOR_MATRIX.len();
    for &byte in password.iter().rev() {
        let mut ch = byte;
        for _ in 0..7 {
            element -= 1;
            if ch & 0x40 != 0 {
                key ^= XOR_MATRIX[element];
            }
            ch = ch.wrapping_mul(2);
        }
    }
    key
}

/// CreatePasswordVerifier_Method1(16비트, Excel 시트 보호 해시와 같다)
fn xor_verifier(password: &[u8]) -> u16 {
    let mut verifier: u16 = 0;
    let mut bytes = vec![password.len() as u8];
    bytes.extend_from_slice(password);
    for &byte in bytes.iter().rev() {
        let carry = u16::from(verifier & 0x4000 != 0);
        verifier = (((verifier << 1) & 0x7FFF) | carry) ^ u16::from(byte);
    }
    verifier ^ 0xCE4B
}

/// CreateXorArray_Method2: 16바이트 난독화 배열
fn xor_array(password: &[u8]) -> [u8; 16] {
    let key = xor_key(password);
    let (high, low) = ((key >> 8) as u8, (key & 0xFF) as u8);
    let mut array = [0u8; 16];
    for (index, slot) in array.iter_mut().enumerate() {
        *slot = password
            .get(index)
            .copied()
            .unwrap_or_else(|| PAD[index - password.len()]);
    }
    for (index, slot) in array.iter_mut().enumerate() {
        let key = if index % 2 == 0 { low } else { high };
        *slot = (*slot ^ key).rotate_right(1);
    }
    array
}

/// XOR Data Transformation Method 2 — 스트림 처음부터 잰 위치로 배열을 돌려 쓴다. 0 과, 배열 값과 같은 바이트는
/// 그대로 둔다(그래서 감추기와 풀기가 같은 연산이다). `skip` 앞 바이트는 바꾸지 않는다.
fn xor_apply(array: &[u8; 16], data: &mut [u8], skip: usize) {
    for (position, byte) in data.iter_mut().enumerate().skip(skip) {
        let key = array[position % 16];
        if *byte != 0 && *byte != key {
            *byte ^= key;
        }
    }
}

/// XOR 난독화한 DOC 의 스트림을 제자리에서 푼다(같은 연산으로 감추기도 한다). `l_key` 는 FibBase 의 암호 검증값.
/// 암호가 없거나 틀리면 `PasswordRequired`.
pub(crate) fn deobfuscate(
    password: Option<&str>,
    l_key: u32,
    word: &mut [u8],
    table: &mut [u8],
    data: Option<&mut Vec<u8>>,
) -> Result<()> {
    let password = password
        .filter(|p| !p.is_empty())
        .ok_or_else(|| password_error("obfuscated Word document"))?;
    let bytes = xor_password(password);
    if xor_verifier_method2(&bytes) != l_key {
        return Err(password_error("password required or incorrect"));
    }
    let array = xor_array(&bytes);
    xor_apply(&array, word, FIB_BASE.min(word.len()));
    xor_apply(&array, table, 0);
    if let Some(data) = data {
        xor_apply(&array, data, 0);
    }
    Ok(())
}

/// CreatePasswordVerifier_Method2: 위 16비트 = XOR 키, 아래 16비트 = 검증값(FibBase `lKey`)
fn xor_verifier_method2(password: &[u8]) -> u32 {
    (u32::from(xor_key(password)) << 16) | u32::from(xor_verifier(password))
}

/// 시험용: 암호의 `lKey` 값
#[cfg(test)]
pub(crate) fn xor_l_key(password: &str) -> u32 {
    xor_verifier_method2(&xor_password(password))
}

/// RC4 흐름 암호
struct Rc4 {
    s: [u8; 256],
    i: u8,
    j: u8,
}

impl Rc4 {
    fn new(key: &[u8]) -> Self {
        let mut s = [0u8; 256];
        for (k, v) in s.iter_mut().enumerate() {
            *v = k as u8;
        }
        let mut j = 0u8;
        for i in 0..256 {
            j = j.wrapping_add(s[i]).wrapping_add(key[i % key.len()]);
            s.swap(i, j as usize);
        }
        Rc4 { s, i: 0, j: 0 }
    }

    fn apply(&mut self, data: &mut [u8]) {
        for byte in data {
            self.i = self.i.wrapping_add(1);
            self.j = self.j.wrapping_add(self.s[self.i as usize]);
            self.s.swap(self.i as usize, self.j as usize);
            let k = self.s[self.s[self.i as usize].wrapping_add(self.s[self.j as usize]) as usize];
            *byte ^= k;
        }
    }
}

/// MD5(RFC 1321). RC4(버전 1.1) 키 만들기에만 쓴다 — 보안 용도가 아니라 형식 호환용이다.
fn md5(input: &[u8]) -> [u8; 16] {
    const S: [u32; 64] = [
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5, 9, 14, 20, 5,
        9, 14, 20, 5, 9, 14, 20, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10,
        15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    ];
    let k: [u32; 64] =
        std::array::from_fn(|i| ((i as f64 + 1.0).sin().abs() * 4_294_967_296.0) as u32);
    let mut state: [u32; 4] = [0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476];
    let mut message = input.to_vec();
    let bit_len = (input.len() as u64).wrapping_mul(8);
    message.push(0x80);
    while message.len() % 64 != 56 {
        message.push(0);
    }
    message.extend_from_slice(&bit_len.to_le_bytes());
    for chunk in message.chunks_exact(64) {
        let m: [u32; 16] = std::array::from_fn(|i| {
            u32::from_le_bytes([
                chunk[i * 4],
                chunk[i * 4 + 1],
                chunk[i * 4 + 2],
                chunk[i * 4 + 3],
            ])
        });
        let [mut a, mut b, mut c, mut d] = state;
        for i in 0..64 {
            let (f, g) = match i / 16 {
                0 => ((b & c) | (!b & d), i),
                1 => ((d & b) | (!d & c), (5 * i + 1) % 16),
                2 => (b ^ c ^ d, (3 * i + 5) % 16),
                _ => (c ^ (b | !d), (7 * i) % 16),
            };
            let rotated = a
                .wrapping_add(f)
                .wrapping_add(k[i])
                .wrapping_add(m[g])
                .rotate_left(S[i]);
            a = d;
            d = c;
            c = b;
            b = b.wrapping_add(rotated);
        }
        state[0] = state[0].wrapping_add(a);
        state[1] = state[1].wrapping_add(b);
        state[2] = state[2].wrapping_add(c);
        state[3] = state[3].wrapping_add(d);
    }
    let mut out = [0u8; 16];
    for (i, word) in state.iter().enumerate() {
        out[i * 4..i * 4 + 4].copy_from_slice(&word.to_le_bytes());
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn md5_known_values() {
        let hex = |b: [u8; 16]| b.iter().map(|x| format!("{x:02x}")).collect::<String>();
        assert_eq!(hex(md5(b"")), "d41d8cd98f00b204e9800998ecf8427e");
        assert_eq!(
            hex(md5(b"The quick brown fox jumps over the lazy dog")),
            "9e107d9d372bb6826bd81d3542a419d6"
        );
        assert_eq!(hex(md5(&[0x61; 200])).len(), 32);
    }

    #[test]
    fn xor_verifier_matches_excel_sheet_hash() {
        // Excel 시트 보호 해시(같은 Method 1 검증값)의 널리 알려진 값: "password" → 0x83AF
        assert_eq!(xor_verifier(b"password"), 0x83AF);
        // 감추기와 풀기는 같은 연산이다
        let array = xor_array(b"password");
        let plain: Vec<u8> = (0..=255u8).cycle().take(600).collect();
        let mut data = plain.clone();
        xor_apply(&array, &mut data, 68);
        assert_ne!(data, plain);
        assert_eq!(data[..68], plain[..68]);
        xor_apply(&array, &mut data, 68);
        assert_eq!(data, plain);
    }

    #[test]
    fn rc4_known_value() {
        // RFC 6229 이전 흔한 시험값: key "Key", plaintext "Plaintext"
        let mut data = *b"Plaintext";
        Rc4::new(b"Key").apply(&mut data);
        assert_eq!(data, [0xBB, 0xF3, 0x16, 0xE8, 0xD9, 0x40, 0xAF, 0x0A, 0xD3]);
    }
}
