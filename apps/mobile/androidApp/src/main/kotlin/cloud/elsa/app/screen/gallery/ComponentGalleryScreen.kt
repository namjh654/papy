package cloud.elsa.app.screen.gallery

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cloud.elsa.app.R
import cloud.elsa.app.designsystem.component.ElsaAction
import cloud.elsa.app.designsystem.component.ElsaActionSheet
import cloud.elsa.app.designsystem.component.ElsaAlert
import cloud.elsa.app.designsystem.component.ElsaAlertDialog
import cloud.elsa.app.designsystem.component.ElsaAvatar
import cloud.elsa.app.designsystem.component.ElsaBottomSheet
import cloud.elsa.app.designsystem.component.ElsaButton
import cloud.elsa.app.designsystem.component.ElsaButtonSize
import cloud.elsa.app.designsystem.component.ElsaButtonStyle
import cloud.elsa.app.designsystem.component.ElsaCard
import cloud.elsa.app.designsystem.component.ElsaCheckbox
import cloud.elsa.app.designsystem.component.ElsaChip
import cloud.elsa.app.designsystem.component.ElsaChipGroup
import cloud.elsa.app.designsystem.component.ElsaDate
import cloud.elsa.app.designsystem.component.ElsaDatePickerField
import cloud.elsa.app.designsystem.component.ElsaFormColumn
import cloud.elsa.app.designsystem.component.ElsaImagePicker
import cloud.elsa.app.designsystem.component.ElsaInlineMessage
import cloud.elsa.app.designsystem.component.ElsaListItem
import cloud.elsa.app.designsystem.component.ElsaLoadingOverlay
import cloud.elsa.app.designsystem.component.ElsaMessageType
import cloud.elsa.app.designsystem.component.ElsaRadioGroup
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaSearchBar
import cloud.elsa.app.designsystem.component.ElsaSegmentedTabs
import cloud.elsa.app.designsystem.component.ElsaSettingsDivider
import cloud.elsa.app.designsystem.component.ElsaSkeletonRow
import cloud.elsa.app.designsystem.component.ElsaSwitch
import cloud.elsa.app.designsystem.component.ElsaTextField
import cloud.elsa.app.designsystem.component.ElsaTime
import cloud.elsa.app.designsystem.component.ElsaTimePickerField
import cloud.elsa.app.designsystem.component.rememberElsaToastState
import cloud.elsa.app.designsystem.format.ElsaFormat
import cloud.elsa.app.designsystem.format.currentLocale
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaRadius
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 디자인 시스템 컴포넌트 갤러리. 새 컴포넌트를 추가하면 여기에도 예시를 추가한다.
 * iOS `ComponentGalleryScreen` 과 같은 순서/내용을 유지한다.
 */
@Composable
fun ComponentGalleryScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toast = rememberElsaToastState()

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var notification by rememberSaveable { mutableStateOf(true) }
    var gender by rememberSaveable { mutableStateOf<Int?>(null) }
    var birthDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var alarmHour by rememberSaveable { mutableStateOf<Int?>(null) }
    var alarmMinute by rememberSaveable { mutableStateOf(0) }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var alert by remember { mutableStateOf<ElsaAlert?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var galleryQuery by remember { mutableStateOf("") }
    var chip by remember { mutableStateOf(0) }
    var segment by remember { mutableStateOf(0) }
    var showActions by remember { mutableStateOf(false) }

    val emailError = if (email.isNotEmpty() && !email.contains('@')) stringResource(R.string.gallery_email_error) else null
    val locale = currentLocale
    val now = remember { System.currentTimeMillis() }

    val savedText = stringResource(R.string.gallery_toast_saved)
    val deletedText = stringResource(R.string.gallery_toast_deleted)
    val restoredText = stringResource(R.string.gallery_toast_restored)
    val undoText = stringResource(R.string.gallery_undo)
    val dialogTitle = stringResource(R.string.gallery_dialog_title)
    val dialogMessage = stringResource(R.string.gallery_dialog_message)
    val deleteText = stringResource(R.string.gallery_delete)
    val listTapText = stringResource(R.string.gallery_list_title)
    val photoText = stringResource(R.string.attach_photo)
    val cameraText = stringResource(R.string.attach_camera)
    val fileText = stringResource(R.string.attach_file)
    val cancelText = stringResource(R.string.common_cancel)

    Box(modifier = Modifier.fillMaxSize()) {
        ElsaScaffold(title = stringResource(R.string.gallery_title), onBack = onBack, toastState = toast) { padding ->
            ElsaFormColumn(modifier = Modifier.padding(padding)) {
                Section(stringResource(R.string.gallery_section_button))
                ElsaButton(text = "Primary", onClick = {})
                ElsaButton(text = "Secondary", onClick = {}, style = ElsaButtonStyle.Secondary)
                ElsaButton(text = "Outline", onClick = {}, style = ElsaButtonStyle.Outline)
                ElsaButton(text = "Destructive", onClick = {}, style = ElsaButtonStyle.Destructive)
                ElsaButton(text = "Loading", onClick = {}, isLoading = true)
                ElsaButton(text = "Disabled", onClick = {}, enabled = false)
                Row(horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm)) {
                    ElsaButton(text = "Small", onClick = {}, size = ElsaButtonSize.Small, fullWidth = false)
                    ElsaButton(
                        text = "Text",
                        onClick = {},
                        size = ElsaButtonSize.Small,
                        style = ElsaButtonStyle.Text,
                        fullWidth = false,
                    )
                }

                Section(stringResource(R.string.gallery_section_text_field))
                ElsaTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.gallery_name), placeholder = stringResource(R.string.gallery_name_placeholder), maxLength = 20)
                ElsaTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = stringResource(R.string.gallery_email),
                    placeholder = "name@example.com",
                    helperText = stringResource(R.string.gallery_email_helper),
                    errorText = emailError,
                    keyboardType = KeyboardType.Email,
                )
                ElsaTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.field_password),
                    isSecure = true,
                )
                ElsaTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = stringResource(R.string.gallery_memo),
                    singleLine = false,
                    minLines = 3,
                    maxLength = 200,
                    imeAction = ImeAction.Default,
                )
                ElsaTextField(
                    value = stringResource(R.string.gallery_disabled_value),
                    onValueChange = {},
                    label = stringResource(R.string.gallery_disabled),
                    enabled = false,
                )

                Section(stringResource(R.string.gallery_section_selection))
                ElsaCheckbox(
                    checked = agreed,
                    onCheckedChange = { agreed = it },
                    label = stringResource(R.string.gallery_agree),
                    description = stringResource(R.string.gallery_required),
                )
                ElsaSwitch(
                    checked = notification,
                    onCheckedChange = { notification = it },
                    label = stringResource(R.string.gallery_notification),
                    description = stringResource(R.string.gallery_notification_description),
                )
                val genderLabels = mapOf(
                    R.string.gallery_gender_male to stringResource(R.string.gallery_gender_male),
                    R.string.gallery_gender_female to stringResource(R.string.gallery_gender_female),
                    R.string.gallery_gender_none to stringResource(R.string.gallery_gender_none),
                )
                ElsaRadioGroup(
                    label = stringResource(R.string.gallery_gender),
                    options = genderLabels.keys.toList(),
                    selected = gender,
                    onSelect = { gender = it },
                    optionLabel = { genderLabels.getValue(it) },
                )

                Section(stringResource(R.string.gallery_section_date_time))
                ElsaDatePickerField(
                    label = stringResource(R.string.gallery_birth_date),
                    selectedDateMillis = birthDate,
                    onDateSelected = { birthDate = it },
                    maxDateMillis = ElsaDate.todayUtcMillis(),
                )
                ElsaTimePickerField(
                    label = stringResource(R.string.gallery_alarm_time),
                    selectedTime = alarmHour?.let { ElsaTime(it, alarmMinute) },
                    onTimeSelected = {
                        alarmHour = it.hour
                        alarmMinute = it.minute
                    },
                )

                Section(stringResource(R.string.gallery_section_image))
                ElsaImagePicker(
                    label = stringResource(R.string.gallery_photos, 3),
                    images = images,
                    onImagesChange = { images = it },
                    maxCount = 3,
                    helperText = stringResource(R.string.gallery_photos_helper),
                )

                Section(stringResource(R.string.gallery_section_message))
                ElsaInlineMessage(message = stringResource(R.string.gallery_message_info), type = ElsaMessageType.Info)
                ElsaInlineMessage(message = stringResource(R.string.gallery_message_success), type = ElsaMessageType.Success)
                ElsaInlineMessage(
                    message = stringResource(R.string.gallery_message_warning),
                    type = ElsaMessageType.Warning,
                    title = stringResource(R.string.gallery_message_warning_title),
                )
                ElsaInlineMessage(message = stringResource(R.string.gallery_message_error), type = ElsaMessageType.Error)
                ElsaButton(
                    text = stringResource(R.string.gallery_toast),
                    style = ElsaButtonStyle.Secondary,
                    onClick = { scope.launch { toast.show(savedText, ElsaMessageType.Success) } },
                )
                ElsaButton(
                    text = stringResource(R.string.gallery_toast_action),
                    style = ElsaButtonStyle.Secondary,
                    onClick = {
                        scope.launch {
                            val undo = toast.show(deletedText, ElsaMessageType.Info, actionLabel = undoText)
                            if (undo) toast.show(restoredText, ElsaMessageType.Success)
                        }
                    },
                )
                ElsaButton(
                    text = stringResource(R.string.gallery_dialog),
                    style = ElsaButtonStyle.Outline,
                    onClick = {
                        alert = ElsaAlert(
                            title = dialogTitle,
                            message = dialogMessage,
                            confirmText = deleteText,
                            dismissText = cancelText,
                            isDestructive = true,
                            onConfirm = { scope.launch { toast.show(deletedText, ElsaMessageType.Error) } },
                        )
                    },
                )

                Section(stringResource(R.string.gallery_section_loading_card))
                ElsaButton(
                    text = stringResource(R.string.gallery_loading_overlay),
                    style = ElsaButtonStyle.Outline,
                    onClick = {
                        scope.launch {
                            isLoading = true
                            delay(2_000)
                            isLoading = false
                        }
                    },
                )
                // 카드는 그룹 배경(backgroundGrouped) 위에 놓는 컴포넌트라 예시도 그 위에 둔다
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ElsaTheme.colors.backgroundGrouped, RoundedCornerShape(ElsaRadius.xl))
                        .padding(ElsaSpacing.md),
                ) {
                    ElsaCard(modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(R.string.gallery_card_title), style = ElsaTheme.typography.subtitle)
                        Text(
                            text = stringResource(R.string.gallery_card_description),
                            style = ElsaTheme.typography.bodySmall,
                            color = ElsaTheme.colors.onSurfaceVariant,
                        )
                    }
                }

                Section(stringResource(R.string.gallery_section_list_sheet))
                Column(modifier = Modifier.fillMaxWidth().background(ElsaTheme.colors.surface, RoundedCornerShape(ElsaRadius.xl))) {
                    ElsaListItem(
                        title = stringResource(R.string.gallery_list_title),
                        subtitle = stringResource(R.string.gallery_list_subtitle),
                        leading = { ElsaAvatar(name = stringResource(R.string.gallery_list_title), size = 48.dp) },
                        trailing = {
                            Text(
                                text = stringResource(R.string.gallery_list_time),
                                style = ElsaTheme.typography.caption,
                                color = ElsaTheme.colors.onSurfaceTertiary,
                            )
                        },
                        onClick = { scope.launch { toast.show(listTapText, ElsaMessageType.Info) } },
                    )
                    ElsaSettingsDivider()
                    ElsaListItem(
                        title = stringResource(R.string.gallery_list_notice),
                        leading = {
                            Icon(
                                painter = painterResource(R.drawable.elsa_ic_notification),
                                contentDescription = null,
                                modifier = Modifier.size(ElsaSize.icon),
                                tint = ElsaTheme.colors.onSurfaceVariant,
                            )
                        },
                        onClick = { scope.launch { toast.show(listTapText, ElsaMessageType.Info) } },
                    )
                }
                ElsaButton(
                    text = stringResource(R.string.gallery_sheet),
                    style = ElsaButtonStyle.Secondary,
                    onClick = { showSheet = true },
                )
                ElsaButton(
                    text = stringResource(R.string.gallery_action_sheet),
                    style = ElsaButtonStyle.Secondary,
                    onClick = { showActions = true },
                )

                Section(stringResource(R.string.gallery_section_chips))
                ElsaSearchBar(value = galleryQuery, onValueChange = { galleryQuery = it }, onSearch = {})
                ElsaChipGroup {
                    listOf(R.string.gallery_chip_all, R.string.gallery_chip_popular, R.string.gallery_chip_new).forEachIndexed { i, label ->
                        ElsaChip(label = stringResource(label), selected = chip == i, onClick = { chip = i })
                    }
                }
                ElsaSegmentedTabs(
                    options = listOf(stringResource(R.string.gallery_segment_list), stringResource(R.string.gallery_segment_grid)),
                    selectedIndex = segment,
                    onSelect = { segment = it },
                )
                ElsaSkeletonRow()

                Section(stringResource(R.string.gallery_section_format))
                FormatRow(stringResource(R.string.gallery_format_date), ElsaFormat.dateLong(now, locale))
                FormatRow(stringResource(R.string.gallery_format_time), ElsaFormat.time(15, 5, locale, is24Hour = false))
                FormatRow(stringResource(R.string.gallery_format_number), ElsaFormat.number(1234567.89, locale))
                FormatRow(stringResource(R.string.gallery_format_percent), ElsaFormat.percent(0.256, locale))
            }
        }

        if (showSheet) {
            ElsaBottomSheet(onDismiss = { showSheet = false }, title = stringResource(R.string.gallery_sheet_title)) {
                Text(
                    text = stringResource(R.string.gallery_sheet_body),
                    modifier = Modifier.padding(horizontal = ElsaSpacing.xl),
                    style = ElsaTheme.typography.body,
                    color = ElsaTheme.colors.onSurfaceVariant,
                )
            }
        }
        if (showActions) {
            ElsaActionSheet(
                title = stringResource(R.string.attach_title),
                actions = listOf(
                    ElsaAction(stringResource(R.string.attach_photo), R.drawable.elsa_ic_image) { scope.launch { toast.show(photoText, ElsaMessageType.Info) } },
                    ElsaAction(stringResource(R.string.attach_camera), R.drawable.elsa_ic_camera) { scope.launch { toast.show(cameraText, ElsaMessageType.Info) } },
                    ElsaAction(stringResource(R.string.attach_file), R.drawable.elsa_ic_file) { scope.launch { toast.show(fileText, ElsaMessageType.Info) } },
                    ElsaAction(deleteText, R.drawable.elsa_ic_delete, destructive = true) { scope.launch { toast.show(deletedText, ElsaMessageType.Error) } },
                ),
                onDismiss = { showActions = false },
            )
        }
        ElsaAlertDialog(alert = alert, onDismiss = { alert = null })
        ElsaLoadingOverlay(isLoading = isLoading, message = stringResource(R.string.common_processing))
    }
}

@Composable
private fun FormatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = ElsaTheme.typography.body, color = ElsaTheme.colors.onSurfaceVariant)
        Text(text = value, style = ElsaTheme.typography.bodyStrong, color = ElsaTheme.colors.onSurface)
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = ElsaSpacing.sm),
        style = ElsaTheme.typography.title,
        color = ElsaTheme.colors.onBackground,
    )
}
