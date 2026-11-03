package cloud.elsa.app.designsystem.component

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import cloud.elsa.app.R
import cloud.elsa.app.core.data.social.SocialProvider
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaRadius
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing

/**
 * 소셜 로그인 버튼(DEC-119). 배경·글자색과 문구는 각 사 가이드, 크기·모서리는 우리 토큰. iOS `ElsaSocialButton` 과 같다.
 * 로고는 템플릿에 넣지 않는다 — 각 사 공식 가이드의 로고를 받아 res/drawable 에 넣고 [SocialBrandLogo] 에 연결한다
 * (docs/template-setup.md "소셜 로그인").
 */
@Composable
fun ElsaSocialButton(
    provider: SocialProvider,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = ElsaTheme.colors
    val (container, content) = when (provider) {
        SocialProvider.Kakao -> c.kakao to c.onKakao
        SocialProvider.Naver -> c.naver to c.onNaver
        SocialProvider.Google -> c.google to c.onGoogle
        SocialProvider.Apple -> c.apple to c.onApple
    }
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = ElsaSize.controlHeight),
        enabled = enabled,
        shape = RoundedCornerShape(ElsaRadius.lg),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            // 비활성(다른 로그인 진행 중)이어도 브랜드 색은 유지하고 흐리게만
            disabledContainerColor = container.copy(alpha = 0.5f),
            disabledContentColor = content.copy(alpha = 0.5f),
        ),
        elevation = null,
        border = if (provider == SocialProvider.Google) BorderStroke(ElsaSize.border, c.googleOutline) else null,
        contentPadding = PaddingValues(horizontal = ElsaSpacing.xl, vertical = ElsaSpacing.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SocialBrandLogo.of(provider)?.let { logo ->
                Image(painter = painterResource(logo), contentDescription = null, modifier = Modifier.size(ElsaSize.icon))
            }
            Text(text = stringResource(provider.label), style = ElsaTheme.typography.button, maxLines = 1)
        }
    }
}

/** 로그인 화면의 소셜 영역: "또는" 구분선 + 제공자 버튼(표시 순서는 KMP `SocialConfig`) */
@Composable
fun ElsaSocialLoginSection(
    providers: List<SocialProvider>,
    onClick: (SocialProvider) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (providers.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ElsaSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = ElsaTheme.colors.divider)
            Text(
                text = stringResource(R.string.login_social_divider),
                style = ElsaTheme.typography.caption,
                color = ElsaTheme.colors.onSurfaceTertiary,
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = ElsaTheme.colors.divider)
        }
        providers.forEach { provider ->
            ElsaSocialButton(provider = provider, onClick = { onClick(provider) }, enabled = enabled)
        }
    }
}

/** 각 사 공식 로고(앱마다 추가). 넣기 전에는 null → 문구만 보인다 */
object SocialBrandLogo {
    @DrawableRes
    fun of(provider: SocialProvider): Int? = when (provider) {
        SocialProvider.Kakao -> null // R.drawable.social_logo_kakao
        SocialProvider.Naver -> null // R.drawable.social_logo_naver
        SocialProvider.Google -> null // R.drawable.social_logo_google
        SocialProvider.Apple -> null
    }
}

private val SocialProvider.label: Int
    @StringRes get() = when (this) {
        SocialProvider.Kakao -> R.string.social_kakao
        SocialProvider.Naver -> R.string.social_naver
        SocialProvider.Google -> R.string.social_google
        SocialProvider.Apple -> R.string.social_apple
    }

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ElsaSocialLoginSectionPreview() = ElsaTheme {
    Column(
        modifier = Modifier.background(ElsaTheme.colors.background).padding(ElsaSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(ElsaSpacing.lg),
    ) {
        ElsaSocialLoginSection(providers = SocialProvider.entries, onClick = {})
        ElsaSocialButton(provider = SocialProvider.Kakao, onClick = {}, enabled = false)
    }
}
