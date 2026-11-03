package io.github.namjh654.papy.screen.profile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.component.message
import io.github.namjh654.papy.designsystem.component.PapyAlert
import io.github.namjh654.papy.designsystem.component.PapyAlertDialog
import io.github.namjh654.papy.designsystem.component.PapyAvatar
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyButtonSize
import io.github.namjh654.papy.designsystem.component.PapyButtonStyle
import io.github.namjh654.papy.designsystem.component.PapyFormColumn
import io.github.namjh654.papy.designsystem.component.PapyInlineMessage
import io.github.namjh654.papy.designsystem.component.PapyMessageType
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyTextField
import io.github.namjh654.papy.designsystem.component.rememberPapyToastState
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.settings.profile.ProfileEvent
import io.github.namjh654.papy.feature.settings.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

/**
 * 프로필 편집: 사진(선택 → 저장 때 업로드) + 이름·이메일, 하단 저장 버튼.
 * 저장하지 않은 변경이 있으면 나갈 때(위쪽 화살표·시스템 뒤로가기) 확인한다.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
) {
    val viewModel: ProfileViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberPapyToastState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var alert by remember { mutableStateOf<PapyAlert?>(null) }
    // 방금 고른 사진(저장되면 서버 주소로 바뀐다)
    var pickedUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // 시스템 사진 선택기(권한 불필요). 고른 사진은 바이트로 읽어 ViewModel 에 넘긴다
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            pickedUri = uri
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                }
                if (bytes != null) viewModel.avatarPicked(bytes)
            }
        }
    }

    val savedText = stringResource(R.string.profile_saved)
    val discardTitle = stringResource(R.string.profile_discard_title)
    val discardMessage = stringResource(R.string.profile_discard_message)
    val discardConfirm = stringResource(R.string.profile_discard_confirm)
    val cancelText = stringResource(R.string.common_cancel)

    EventEffect(viewModel.events) { event ->
        when (event) {
            ProfileEvent.Saved -> {
                pickedUri = null
                scope.launch { toast.show(savedText, PapyMessageType.Success) }
            }
        }
    }

    /** 저장하지 않은 변경이 있으면 확인 다이얼로그, 없으면 바로 나간다 */
    fun leave() {
        if (!state.isDirty) {
            onBack()
        } else {
            alert = PapyAlert(
                title = discardTitle,
                message = discardMessage,
                confirmText = discardConfirm,
                dismissText = cancelText,
                isDestructive = true,
                onConfirm = onBack,
            )
        }
    }

    // 변경이 있을 때만 시스템 뒤로가기를 가로챈다
    BackHandler(enabled = state.isDirty) { leave() }

    PapyScaffold(title = stringResource(R.string.profile_title), onBack = ::leave, toastState = toast) { padding ->
        PapyFormColumn(
            modifier = Modifier.padding(padding),
            bottomBar = {
                PapyButton(
                    text = stringResource(R.string.profile_save),
                    onClick = viewModel::save,
                    enabled = state.canSave || state.isSaving,
                    isLoading = state.isSaving,
                )
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PapySpacing.sm),
            ) {
                PapyAvatar(name = state.name, url = state.avatarUrl, picked = pickedUri, size = 96.dp)
                PapyButton(
                    text = stringResource(R.string.profile_change_photo),
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    style = PapyButtonStyle.Text,
                    size = PapyButtonSize.Small,
                    fullWidth = false,
                    enabled = !state.isSaving,
                )
            }

            PapyTextField(
                value = state.name,
                onValueChange = viewModel::nameChanged,
                label = stringResource(R.string.field_name),
                placeholder = stringResource(R.string.field_name_placeholder),
                maxLength = 20,
            )

            PapyTextField(
                value = state.email,
                onValueChange = viewModel::emailChanged,
                label = stringResource(R.string.field_email),
                placeholder = stringResource(R.string.field_email_placeholder),
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            )

            state.error?.let { error ->
                PapyInlineMessage(message = error.message(), type = PapyMessageType.Error)
            }
        }
    }

    PapyAlertDialog(alert = alert, onDismiss = { alert = null })
}
