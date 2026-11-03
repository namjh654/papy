# @rhwp/editor (vendored)

rhwp 저장소 `npm/editor` 의 SDK 파일(MIT, `LICENSE`)을 그대로 가져왔다. 출처: edwardkim/rhwp `1a76570e`(v0.8.7).
`scripts/fetch-rhwp-studio.mjs` 와 같은 커밋으로 맞춘다.

바꾼 곳(검색: `Papy:`)
- `transport.js`: 같은 출처의 studio 는 `tauri://` 스킴이어도 허용하고, 그때는 `location.origin` 을 대상 origin 으로 쓴다.
