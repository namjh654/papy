package cloud.elsa.app.screen.inquiry

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
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.component.message
import cloud.elsa.app.designsystem.component.ElsaAlert
import cloud.elsa.app.designsystem.component.ElsaAlertDialog
import cloud.elsa.app.designsystem.component.ElsaButton
import cloud.elsa.app.designsystem.component.ElsaChip
import cloud.elsa.app.designsystem.component.ElsaFormColumn
import cloud.elsa.app.designsystem.component.ElsaInlineMessage
import cloud.elsa.app.designsystem.component.ElsaMessageType
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaScreenHeader
import cloud.elsa.app.designsystem.component.ElsaTextField
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.settings.inquiry.InquiryCategory
import cloud.elsa.app.feature.settings.inquiry.InquiryEvent
import cloud.elsa.app.feature.settings.inquiry.InquiryViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 설정 → 문의하기(25-3): 종류(칩) → 내용(여러 줄, 글자 수) → 답장 받을 이메일 → 하단 고정 "보내기".
 * 보내면 안내 후 닫는다. iOS `inquiry.screen.swift`
 */
@Composable
fun InquiryScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val viewModel: InquiryViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var alert by remember { mutableStateOf<ElsaAlert?>(null) }
    // 보낸 뒤 안내는 확인·바깥 누르기 어느 쪽으로 닫아도 한 번만 닫는다
    var sent by remember { mutableStateOf(false) }

    val sentTitle = stringResource(R.string.inquiry_sent_title)
    val sentMessage = stringResource(R.string.inquiry_sent_message)
    EventEffect(viewModel.events) { event ->
        when (event) {
            InquiryEvent.Submitted -> {
                sent = true
                alert = ElsaAlert(title = sentTitle, message = sentMessage)
            }
        }
    }

    ElsaScaffold(onBack = onBack) { padding ->
        ElsaFormColumn(
            modifier = Modifier.padding(padding),
            bottomBar = {
                ElsaButton(
                    text = stringResource(R.string.inquiry_submit),
                    onClick = viewModel::submit,
                    enabled = state.canSubmit || state.isSubmitting,
                    isLoading = state.isSubmitting,
                )
            },
        ) {
            ElsaScreenHeader(title = stringResource(R.string.inquiry_title), description = stringResource(R.string.inquiry_description))
            InquiryCategories(selected = state.category, onSelect = viewModel::categorySelected)
            ElsaTextField(
                value = state.content,
                onValueChange = viewModel::contentChanged,
                label = stringResource(R.string.inquiry_content),
                placeholder = stringResource(R.string.inquiry_content_placeholder),
                singleLine = false,
                minLines = 6,
                maxLength = state.maxContent,
            )
            ElsaTextField(
                value = state.email,
                onValueChange = viewModel::emailChanged,
                label = stringResource(R.string.field_email),
                placeholder = stringResource(R.string.field_email_placeholder),
                helperText = stringResource(R.string.inquiry_email_helper),
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            )
            state.error?.let { ElsaInlineMessage(message = it.message(), type = ElsaMessageType.Error) }
        }
    }

    ElsaAlertDialog(
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
    Column(verticalArrangement = Arrangement.spacedBy(ElsaSpacing.sm)) {
        Text(text = stringResource(R.string.inquiry_category), style = ElsaTheme.typography.label, color = ElsaTheme.colors.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.xs), verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xs)) {
            InquiryCategory.entries.forEach { category ->
                ElsaChip(label = stringResource(category.label), selected = category == selected, onClick = { onSelect(category) })
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
private fun InquiryCategoriesPreview() = ElsaTheme {
    Column(modifier = Modifier.background(ElsaTheme.colors.background).padding(ElsaSpacing.lg)) {
        InquiryCategories(selected = InquiryCategory.Bug, onSelect = {})
    }
}
