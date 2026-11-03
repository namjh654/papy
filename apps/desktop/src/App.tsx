import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";

export function App() {
  const [engineVersion, setEngineVersion] = useState<string>();

  useEffect(() => {
    invoke<string>("engine_version").then(setEngineVersion, () => setEngineVersion(undefined));
  }, []);

  return (
    <main style={{ fontFamily: "system-ui, sans-serif", padding: 24 }}>
      <h1>Papy (페이피)</h1>
      <p>초기 개발 단계입니다. 아직 문서를 열 수 없습니다.</p>
      <p>엔진 버전: {engineVersion ?? "확인 중…"}</p>
    </main>
  );
}
