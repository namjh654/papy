package io.github.namjh654.papy.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing

/*
 * 키보드 처리 규칙 (앱 전체)
 * 1. MainActivity: enableEdgeToEdge() + Manifest windowSoftInputMode="adjustResize"
 * 2. 루트에서 WindowInsets.safeDrawing(키보드 포함) 만큼 padding → 키보드가 올라오면 화면이 줄어든다
 * 3. 폼 화면은 PapyFormColumn(스크롤) 을 쓴다 → 포커스된 필드가 키보드 위로 스크롤된다
 * 4. 빈 곳을 탭하면 키보드가 닫힌다
 */

/**
 * 키보드 대응 스크롤 폼 컨테이너. 입력 화면의 기본 레이아웃으로 사용한다.
 *
 * @param bottomBar 화면 하단에 고정되는 주요 동작(전폭 CTA 등). 키보드가 올라오면 키보드 바로 위에 붙는다.
 */
@Composable
fun PapyFormColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(PapySize.screenPadding),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(PapySpacing.lg),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clearFocusOnTap()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
        if (bottomBar != null) {
            Column(
                modifier = Modifier.padding(
                    start = PapySize.screenPadding,
                    end = PapySize.screenPadding,
                    top = PapySpacing.sm,
                    bottom = PapySpacing.lg,
                ),
                verticalArrangement = Arrangement.spacedBy(PapySpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = bottomBar,
            )
        }
    }
}

/** 빈 영역을 탭하면 포커스를 해제해 키보드를 닫는다. (자식이 탭을 소비하면 동작하지 않음) */
fun Modifier.clearFocusOnTap(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    pointerInput(focusManager) {
        detectTapGestures(onTap = { focusManager.clearFocus() })
    }
}

/**
 * 포커스를 받았거나 키보드 높이가 바뀌면 자신을 화면 안으로 스크롤한다.
 * PapyTextField 에 기본 적용되어 있으므로 직접 만든 입력 컴포넌트에만 붙이면 된다.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bringIntoViewOnFocus(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(focused, imeBottom) {
        if (focused) requester.bringIntoView()
    }

    this
        .bringIntoViewRequester(requester)
        .onFocusEvent { focused = it.isFocused }
}
