# 환경별 Firebase 설정 (커밋하지 않음)

```text
Firebase/dev/GoogleService-Info.plist       번들 ID <base>.dev
Firebase/staging/GoogleService-Info.plist   번들 ID <base>.staging
Firebase/prod/GoogleService-Info.plist      번들 ID <base>
```

빌드 단계 "Copy GoogleService-Info.plist" 가 현재 Build Configuration 의 APP_ENV 에 맞는 파일을 앱에 넣는다.
파일이 없으면 넣지 않고 푸시만 비활성(NoOp)이 된다. 절차: docs/environments.md, docs/template-setup.md §3.
