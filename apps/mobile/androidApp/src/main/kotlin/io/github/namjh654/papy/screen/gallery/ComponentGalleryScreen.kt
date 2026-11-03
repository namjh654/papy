package io.github.namjh654.papy.screen.gallery

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
import io.github.namjh654.papy.R
import io.github.namjh654.papy.designsystem.component.PapyAction
import io.github.namjh654.papy.designsystem.component.PapyActionSheet
import io.github.namjh654.papy.designsystem.component.PapyAlert
import io.github.namjh654.papy.designsystem.component.PapyAlertDialog
import io.github.namjh654.papy.designsystem.component.PapyAvatar
import io.github.namjh654.papy.designsystem.component.PapyBottomSheet
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyButtonSize
import io.github.namjh654.papy.designsystem.component.PapyButtonStyle
import io.github.namjh654.papy.designsystem.component.PapyCard
import io.github.namjh654.papy.designsystem.component.PapyCheckbox
import io.github.namjh654.papy.designsystem.component.PapyChip
import io.github.namjh654.papy.designsystem.component.PapyChipGroup
import io.github.namjh654.papy.designsystem.component.PapyDate
import io.github.namjh654.papy.designsystem.component.PapyDatePickerField
import io.github.namjh654.papy.designsystem.component.PapyFormColumn
import io.github.namjh654.papy.designsystem.component.PapyImagePicker
import io.github.namjh654.papy.designsystem.component.PapyInlineMessage
import io.github.namjh654.papy.designsystem.component.PapyListItem
import io.github.namjh654.papy.designsystem.component.PapyLoadingOverlay
import io.github.namjh654.papy.designsystem.component.PapyMessageType
import io.github.namjh654.papy.designsystem.component.PapyRadioGroup
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapySearchBar
import io.github.namjh654.papy.designsystem.component.PapySegmentedTabs
import io.github.namjh654.papy.designsystem.component.PapySettingsDivider
import io.github.namjh654.papy.designsystem.component.PapySkeletonRow
import io.github.namjh654.papy.designsystem.component.PapySwitch
import io.github.namjh654.papy.designsystem.component.PapyTextField
import io.github.namjh654.papy.designsystem.component.PapyTime
import io.github.namjh654.papy.designsystem.component.PapyTimePickerField
import io.github.namjh654.papy.designsystem.component.rememberPapyToastState
import io.github.namjh654.papy.designsystem.format.PapyFormat
import io.github.namjh654.papy.designsystem.format.currentLocale
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapyRadius
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing
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
    val toast = rememberPapyToastState()

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
    var alert by remember { mutableStateOf<PapyAlert?>(null) }
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
        PapyScaffold(title = stringResource(R.string.gallery_title), onBack = onBack, toastState = toast) { padding ->
            PapyFormColumn(modifier = Modifier.padding(padding)) {
                Section(stringResource(R.string.gallery_section_button))
                PapyButton(text = "Primary", onClick = {})
                PapyButton(text = "Secondary", onClick = {}, style = PapyButtonStyle.Secondary)
                PapyButton(text = "Outline", onClick = {}, style = PapyButtonStyle.Outline)
                PapyButton(text = "Destructive", onClick = {}, style = PapyButtonStyle.Destructive)
                PapyButton(text = "Loading", onClick = {}, isLoading = true)
                PapyButton(text = "Disabled", onClick = {}, enabled = false)
                Row(horizontalArrangement = Arrangement.spacedBy(PapySpacing.sm)) {
                    PapyButton(text = "Small", onClick = {}, size = PapyButtonSize.Small, fullWidth = false)
                    PapyButton(
                        text = "Text",
                        onClick = {},
                        size = PapyButtonSize.Small,
                        style = PapyButtonStyle.Text,
                        fullWidth = false,
                    )
                }

                Section(stringResource(R.string.gallery_section_text_field))
                PapyTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.gallery_name), placeholder = stringResource(R.string.gallery_name_placeholder), maxLength = 20)
                PapyTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = stringResource(R.string.gallery_email),
                    placeholder = "name@example.com",
                    helperText = stringResource(R.string.gallery_email_helper),
                    errorText = emailError,
                    keyboardType = KeyboardType.Email,
                )
                PapyTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.field_password),
                    isSecure = true,
                )
                PapyTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = stringResource(R.string.gallery_memo),
                    singleLine = false,
                    minLines = 3,
                    maxLength = 200,
                    imeAction = ImeAction.Default,
                )
                PapyTextField(
                    value = stringResource(R.string.gallery_disabled_value),
                    onValueChange = {},
                    label = stringResource(R.string.gallery_disabled),
                    enabled = false,
                )

                Section(stringResource(R.string.gallery_section_selection))
                PapyCheckbox(
                    checked = agreed,
                    onCheckedChange = { agreed = it },
                    label = stringResource(R.string.gallery_agree),
                    description = stringResource(R.string.gallery_required),
                )
                PapySwitch(
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
                PapyRadioGroup(
                    label = stringResource(R.string.gallery_gender),
                    options = genderLabels.keys.toList(),
                    selected = gender,
                    onSelect = { gender = it },
                    optionLabel = { genderLabels.getValue(it) },
                )

                Section(stringResource(R.string.gallery_section_date_time))
                PapyDatePickerField(
                    label = stringResource(R.string.gallery_birth_date),
                    selectedDateMillis = birthDate,
                    onDateSelected = { birthDate = it },
                    maxDateMillis = PapyDate.todayUtcMillis(),
                )
                PapyTimePickerField(
                    label = stringResource(R.string.gallery_alarm_time),
                    selectedTime = alarmHour?.let { PapyTime(it, alarmMinute) },
                    onTimeSelected = {
                        alarmHour = it.hour
                        alarmMinute = it.minute
                    },
                )

                Section(stringResource(R.string.gallery_section_image))
                PapyImagePicker(
                    label = stringResource(R.string.gallery_photos, 3),
                    images = images,
                    onImagesChange = { images = it },
                    maxCount = 3,
                    helperText = stringResource(R.string.gallery_photos_helper),
                )

                Section(stringResource(R.string.gallery_section_message))
                PapyInlineMessage(message = stringResource(R.string.gallery_message_info), type = PapyMessageType.Info)
                PapyInlineMessage(message = stringResource(R.string.gallery_message_success), type = PapyMessageType.Success)
                PapyInlineMessage(
                    message = stringResource(R.string.gallery_message_warning),
                    type = PapyMessageType.Warning,
                    title = stringResource(R.string.gallery_message_warning_title),
                )
                PapyInlineMessage(message = stringResource(R.string.gallery_message_error), type = PapyMessageType.Error)
                PapyButton(
                    text = stringResource(R.string.gallery_toast),
                    style = PapyButtonStyle.Secondary,
                    onClick = { scope.launch { toast.show(savedText, PapyMessageType.Success) } },
                )
                PapyButton(
                    text = stringResource(R.string.gallery_toast_action),
                    style = PapyButtonStyle.Secondary,
                    onClick = {
                        scope.launch {
                            val undo = toast.show(deletedText, PapyMessageType.Info, actionLabel = undoText)
                            if (undo) toast.show(restoredText, PapyMessageType.Success)
                        }
                    },
                )
                PapyButton(
                    text = stringResource(R.string.gallery_dialog),
                    style = PapyButtonStyle.Outline,
                    onClick = {
                        alert = PapyAlert(
                            title = dialogTitle,
                            message = dialogMessage,
                            confirmText = deleteText,
                            dismissText = cancelText,
                            isDestructive = true,
                            onConfirm = { scope.launch { toast.show(deletedText, PapyMessageType.Error) } },
                        )
                    },
                )

                Section(stringResource(R.string.gallery_section_loading_card))
                PapyButton(
                    text = stringResource(R.string.gallery_loading_overlay),
                    style = PapyButtonStyle.Outline,
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
                        .background(PapyTheme.colors.backgroundGrouped, RoundedCornerShape(PapyRadius.xl))
                        .padding(PapySpacing.md),
                ) {
                    PapyCard(modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(R.string.gallery_card_title), style = PapyTheme.typography.subtitle)
                        Text(
                            text = stringResource(R.string.gallery_card_description),
                            style = PapyTheme.typography.bodySmall,
                            color = PapyTheme.colors.onSurfaceVariant,
                        )
                    }
                }

                Section(stringResource(R.string.gallery_section_list_sheet))
                Column(modifier = Modifier.fillMaxWidth().background(PapyTheme.colors.surface, RoundedCornerShape(PapyRadius.xl))) {
                    PapyListItem(
                        title = stringResource(R.string.gallery_list_title),
                        subtitle = stringResource(R.string.gallery_list_subtitle),
                        leading = { PapyAvatar(name = stringResource(R.string.gallery_list_title), size = 48.dp) },
                        trailing = {
                            Text(
                                text = stringResource(R.string.gallery_list_time),
                                style = PapyTheme.typography.caption,
                                color = PapyTheme.colors.onSurfaceTertiary,
                            )
                        },
                        onClick = { scope.launch { toast.show(listTapText, PapyMessageType.Info) } },
                    )
                    PapySettingsDivider()
                    PapyListItem(
                        title = stringResource(R.string.gallery_list_notice),
                        leading = {
                            Icon(
                                painter = painterResource(R.drawable.papy_ic_notification),
                                contentDescription = null,
                                modifier = Modifier.size(PapySize.icon),
                                tint = PapyTheme.colors.onSurfaceVariant,
                            )
                        },
                        onClick = { scope.launch { toast.show(listTapText, PapyMessageType.Info) } },
                    )
                }
                PapyButton(
                    text = stringResource(R.string.gallery_sheet),
                    style = PapyButtonStyle.Secondary,
                    onClick = { showSheet = true },
                )
                PapyButton(
                    text = stringResource(R.string.gallery_action_sheet),
                    style = PapyButtonStyle.Secondary,
                    onClick = { showActions = true },
                )

                Section(stringResource(R.string.gallery_section_chips))
                PapySearchBar(value = galleryQuery, onValueChange = { galleryQuery = it }, onSearch = {})
                PapyChipGroup {
                    listOf(R.string.gallery_chip_all, R.string.gallery_chip_popular, R.string.gallery_chip_new).forEachIndexed { i, label ->
                        PapyChip(label = stringResource(label), selected = chip == i, onClick = { chip = i })
                    }
                }
                PapySegmentedTabs(
                    options = listOf(stringResource(R.string.gallery_segment_list), stringResource(R.string.gallery_segment_grid)),
                    selectedIndex = segment,
                    onSelect = { segment = it },
                )
                PapySkeletonRow()

                Section(stringResource(R.string.gallery_section_format))
                FormatRow(stringResource(R.string.gallery_format_date), PapyFormat.dateLong(now, locale))
                FormatRow(stringResource(R.string.gallery_format_time), PapyFormat.time(15, 5, locale, is24Hour = false))
                FormatRow(stringResource(R.string.gallery_format_number), PapyFormat.number(1234567.89, locale))
                FormatRow(stringResource(R.string.gallery_format_percent), PapyFormat.percent(0.256, locale))
            }
        }

        if (showSheet) {
            PapyBottomSheet(onDismiss = { showSheet = false }, title = stringResource(R.string.gallery_sheet_title)) {
                Text(
                    text = stringResource(R.string.gallery_sheet_body),
                    modifier = Modifier.padding(horizontal = PapySpacing.xl),
                    style = PapyTheme.typography.body,
                    color = PapyTheme.colors.onSurfaceVariant,
                )
            }
        }
        if (showActions) {
            PapyActionSheet(
                title = stringResource(R.string.attach_title),
                actions = listOf(
                    PapyAction(stringResource(R.string.attach_photo), R.drawable.papy_ic_image) { scope.launch { toast.show(photoText, PapyMessageType.Info) } },
                    PapyAction(stringResource(R.string.attach_camera), R.drawable.papy_ic_camera) { scope.launch { toast.show(cameraText, PapyMessageType.Info) } },
                    PapyAction(stringResource(R.string.attach_file), R.drawable.papy_ic_file) { scope.launch { toast.show(fileText, PapyMessageType.Info) } },
                    PapyAction(deleteText, R.drawable.papy_ic_delete, destructive = true) { scope.launch { toast.show(deletedText, PapyMessageType.Error) } },
                ),
                onDismiss = { showActions = false },
            )
        }
        PapyAlertDialog(alert = alert, onDismiss = { alert = null })
        PapyLoadingOverlay(isLoading = isLoading, message = stringResource(R.string.common_processing))
    }
}

@Composable
private fun FormatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = PapyTheme.typography.body, color = PapyTheme.colors.onSurfaceVariant)
        Text(text = value, style = PapyTheme.typography.bodyStrong, color = PapyTheme.colors.onSurface)
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = PapySpacing.sm),
        style = PapyTheme.typography.title,
        color = PapyTheme.colors.onBackground,
    )
}
