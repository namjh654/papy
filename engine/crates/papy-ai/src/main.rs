//! `papy-ai` 실행 파일.
//!
//! ```text
//! papy-ai serve                               앱이 띄운다(대화창 연결)
//! papy-ai mcp                                 MCP 서버(파일을 직접 연다)
//! papy-ai mcp --attach 주소 --token 값        MCP 서버(앱에 열린 문서, ACP 에이전트용)
//! papy-ai version
//! ```

use papy_ai::mcp::{self, Attached, Standalone};

fn usage() -> ! {
    eprintln!(
        "usage: papy-ai serve | papy-ai mcp [--attach ADDRESS --token TOKEN] | papy-ai version"
    );
    std::process::exit(2)
}

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    match args.first().map(String::as_str) {
        Some("serve") => {
            if let Err(error) = papy_ai::serve::serve_stdio() {
                eprintln!("papy-ai: {error}");
                std::process::exit(1);
            }
        }
        Some("mcp") => {
            let mut attach = None;
            let mut token = None;
            let mut rest = args[1..].iter();
            while let Some(arg) = rest.next() {
                match arg.as_str() {
                    "--attach" => attach = rest.next().cloned(),
                    "--token" => token = rest.next().cloned(),
                    _ => usage(),
                }
            }
            match (attach, token) {
                (Some(address), Some(token)) => match Attached::connect(&address, &token) {
                    Ok(backend) => mcp::serve_stdio(Box::new(backend)),
                    Err(error) => {
                        eprintln!("papy-ai: cannot reach Papy at {address}: {error}");
                        std::process::exit(1);
                    }
                },
                (None, None) => mcp::serve_stdio(Box::new(Standalone::new())),
                _ => usage(),
            }
        }
        Some("version" | "--version" | "-V") => println!("papy-ai {}", env!("CARGO_PKG_VERSION")),
        _ => usage(),
    }
}
