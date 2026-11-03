package io.github.namjh654.papy.security

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.R
import io.github.namjh654.papy.core.data.security.AppLock
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing
import org.koin.compose.koinInject

/**
 * 앱 잠금(26-1): KMP [AppLock] 이 잠그라고 하면 [content] 위를 잠금 화면으로 덮고 바로 본인 확인을 띄운다.
 * Activity 생명 주기(onStop·onStart)를 AppLock 에 알린다 — 몇 초 안에 돌아오면(사진 고르기 등) 잠그지 않는 것은 KMP 가 정한다.
 * iOS `AppLockGate`.
 */
@Composable
fun AppLockGate(appLock: AppLock = koinInject(), content: @Composable () -> Unit) {
    val locked by appLock.isLocked.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current as? FragmentActivity
    val title = stringResource(R.string.app_lock_prompt)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> appLock.onBackground()
                Lifecycle.Event.ON_START -> appLock.onForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val unlock = { activity?.let { DeviceAuth.authenticate(it, title, onSuccess = appLock::unlock) } ?: Unit }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (locked) {
            // 잠긴 동안 뒤로가기는 앱을 내린다(아래 화면으로 가지 않는다)
            BackHandler { (activity as? Activity)?.moveTaskToBack(true) }
            LaunchedEffect(Unit) { unlock() }
            AppLockScreen(onUnlock = unlock)
        }
    }
}

/** 잠금 화면(미리보기 대상): 가운데 자물쇠 → 제목·설명 → 아래 "잠금 해제" */
@Composable
fun AppLockScreen(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PapyTheme.colors.background)
            .padding(PapySize.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(PapySpacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(PapyTheme.colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painter = painterResource(R.drawable.papy_ic_lock), contentDescription = null, tint = PapyTheme.colors.onPrimaryContainer, modifier = Modifier.size(32.dp))
            }
            Text(text = stringResource(R.string.app_lock_title), style = PapyTheme.typography.title, color = PapyTheme.colors.onBackground, textAlign = TextAlign.Center)
            Text(text = stringResource(R.string.app_lock_description), style = PapyTheme.typography.body, color = PapyTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        PapyButton(text = stringResource(R.string.app_lock_unlock), onClick = onUnlock)
    }
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppLockScreenPreview() = PapyTheme { AppLockScreen(onUnlock = {}) }
