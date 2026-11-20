package com.famlog.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.ConsentRepository
import com.famlog.core.data.GroupSettingsRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 채팅방에 들어올 때 AI 분석 안내 (SET-01). 그룹 AI가 켜져 있는데 내가 현재 버전 안내를 확인하지 않았으면(서버 aiNoticePending)
 * 시트를 한 번 띄운다: [확인] → 안내 확인 기록, [내 메시지 분석 제외] → 이 그룹에서 제외.
 * 버튼 없이 닫으면 앱을 켜 둔 동안 이 그룹에서는 다시 띄우지 않는다. 확인하지 않아도 분석은 된다(고지 확인 방식).
 */
@HiltViewModel
class AiNoticePromptViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: GroupSettingsRepository,
    private val consentRepository: ConsentRepository,
) : ViewModel() {

    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    private val _prompt = MutableStateFlow<AiNoticeSheetState?>(null)
    val prompt: StateFlow<AiNoticeSheetState?> = _prompt.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { check() }
    }

    private suspend fun check() {
        if (settingsRepository.noticeSkipped(groupId)) return
        val settings = (settingsRepository.settings(groupId) as? ApiResult.Success)?.value ?: return
        if (!settings.noticePending) return
        val notice = (settingsRepository.aiNotice() as? ApiResult.Success)?.value ?: return
        _prompt.value = AiNoticeSheetState(notice, AiNoticeMode.CONFIRM)
    }

    /** [확인]: 보여 준 버전으로 기록한다. 그사이 안내가 바뀌었으면(400 docVersion) 새 안내를 다시 보여 준다 */
    fun confirm() {
        val sheet = _prompt.value ?: return
        if (sheet.saving) return
        _prompt.value = sheet.copy(saving = true)
        viewModelScope.launch {
            val result = consentRepository.confirmAiNotice(sheet.notice.version)
            when {
                result is ApiResult.Success -> _prompt.value = null
                result.isOutdatedNotice(FIELD_DOC_VERSION) -> {
                    val notice = (settingsRepository.aiNotice() as? ApiResult.Success)?.value
                    _prompt.value = notice?.let { AiNoticeSheetState(it, AiNoticeMode.CONFIRM) }
                    _events.send(SettingsEvent.Message(UiText.Res(R.string.ai_notice_changed)))
                }
                else -> {
                    _prompt.update { it?.copy(saving = false) }
                    _events.send(SettingsEvent.Message(result.toUiText()))
                }
            }
        }
    }

    /** [내 메시지 분석 제외]: 이 그룹에서만. 그룹 설정에서 다시 켤 수 있다 */
    fun optOut() {
        val sheet = _prompt.value ?: return
        if (sheet.saving) return
        _prompt.value = sheet.copy(saving = true)
        viewModelScope.launch {
            val result = settingsRepository.setAiOptOut(groupId, optOut = true)
            if (result is ApiResult.Success) {
                _prompt.value = null
                _events.send(SettingsEvent.Message(UiText.Res(R.string.ai_notice_opted_out)))
            } else {
                _prompt.update { it?.copy(saving = false) }
                _events.send(SettingsEvent.Message(result.toUiText()))
            }
        }
    }

    fun dismiss() {
        if (_prompt.value?.saving == true) return
        settingsRepository.skipNotice(groupId)
        _prompt.value = null
    }

    private companion object {
        const val FIELD_DOC_VERSION = "docVersion"
    }
}
