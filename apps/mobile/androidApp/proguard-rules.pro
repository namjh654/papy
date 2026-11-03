# prodRelease 에만 적용된다 (papy.android.environments). 라이브러리 규칙(kotlinx.serialization·Ktor·OkHttp·Koin·Compose)은
# 각 라이브러리의 consumer rules 가 처리한다. 앱 코드에서 리플렉션을 쓰면 여기에 keep 을 추가한다.

# 릴리스에서 android.util.Log 의 v/d/i 호출 제거 (w/e 는 남긴다)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
