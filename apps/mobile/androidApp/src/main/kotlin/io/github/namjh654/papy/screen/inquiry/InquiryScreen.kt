package io.github.namjh654.papy.screen.inquiry

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.component.message
import io.github.namjh654.papy.designsystem.component.PapyAlert
import io.github.namjh654.papy.designsystem.component.PapyAlertDialog
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyChip
import io.github.namjh654.papy.designsystem.component.PapyFormColumn
import io.github.namjh654.papy.designsystem.component.PapyInlineMessage
import io.github.namjh654.papy.designsystem.component.PapyMessageType
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.component.PapyTextField
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.settings.inquiry.InquiryCategory
import io.github.namjh654.papy.feature.settings.inquiry.InquiryEvent
import io.github.namjh654.papy.feature.settings.inquiry.InquiryViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 설정 → 문의하기(25-3): 종류(칩) → 내용(여러 줄, 글자 수) → 답장 받을 이메일 → 하단 고정 "보내기".
 * 보내면 안내 후 닫는다. iOS `inquiry.screen.swift`
 */
@Composable
fun InquiryScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val viewModel: InquiryViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var alert by remember { mutableStateOf<PapyAlert?>(null) }
    // 보낸 뒤 안내는 확인·바깥 누르기 어느 쪽으로 닫아도 한 번만 닫는다
    var sent by remember { mutableStateOf(false) }

    val sentTitle = stringResource(R.string.inquiry_sent_title)
    val sentMessage = stringResource(R.string.inquiry_sent_message)
    EventEffect(viewModel.events) { event ->
        when (event) {
            InquiryEvent.Submitted -> {
                sent = true
                alert = PapyAlert(title = sentTitle, message = sentMessage)
            }
        }
    }

    PapyScaffold(onBack = onBack) { padding ->
        PapyFormColumn(
            modifier = Modifier.padding(padding),
            bottomBar = {
                PapyButton(
                    text = stringResource(R.string.inquiry_submit),
                    onClick = viewModel::submit,
                    enabled = state.canSubmit || state.isSubmitting,
                    isLoading = state.isSubmitting,
                )
            },
        ) {
            PapyScreenHeader(title = stringResource(R.string.inquiry_title), description = stringResource(R.string.inquiry_description))
            InquiryCategories(selected = state.category, onSelect = viewModel::categorySelected)
            PapyTextField(
                value = state.content,
                onValueChange = viewModel::contentChanged,
                label = stringResource(R.string.inquiry_content),
                placeholder = stringResource(R.string.inquiry_content_placeholder),
                singleLine = false,
                minLines = 6,
                maxLength = state.maxContent,
            )
            PapyTextField(
                value = state.email,
                onValueChange = viewModel::emailChanged,
                label = stringResource(R.string.field_email),
                placeholder = stringResource(R.string.field_email_placeholder),
                helperText = stringResource(R.string.inquiry_email_helper),
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            )
            state.error?.let { PapyInlineMessage(message = it.message(), type = PapyMessageType.Error) }
        }
    }

    PapyAlertDialog(
        alert = alert,
        onDismiss = {
            alert = null
            if (sent) onDone()
        },
    )
}

/** 문의 종류 칩(미리보기 대상) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InquiryCategories(selected: InquiryCategory?, onSelect: (InquiryCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PapySpacing.sm)) {
        Text(text = stringResource(R.string.inquiry_category), style = PapyTheme.typography.label, color = PapyTheme.colors.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PapySpacing.xs), verticalArrangement = Arrangement.spacedBy(PapySpacing.xs)) {
            InquiryCategory.entries.forEach { category ->
                PapyChip(label = stringResource(category.label), selected = category == selected, onClick = { onSelect(category) })
            }
        }
    }
}

private val InquiryCategory.label: Int
    get() = when (this) {
        InquiryCategory.Account -> R.string.inquiry_category_account
        InquiryCategory.Bug -> R.string.inquiry_category_bug
        InquiryCategory.Suggestion -> R.string.inquiry_category_suggestion
        InquiryCategory.Other -> R.string.inquiry_category_other
    }

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InquiryCategoriesPreview() = PapyTheme {
    Column(modifier = Modifier.background(PapyTheme.colors.background).padding(PapySpacing.lg)) {
        InquiryCategories(selected = InquiryCategory.Bug, onSelect = {})
    }
}
