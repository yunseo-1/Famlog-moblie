package com.famlog.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.NotificationSettingsRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.NotificationSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/** 그룹 하나의 알림. [muted]가 null이면 불러오지 못했다 */
data class GroupNotification(val groupId: Long, val name: String, val muted: Boolean?)

data class NotificationSettingsUiState(
    val settings: NotificationSettings = NotificationSettings(),
    val groups: List<GroupNotification> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
)

/**
 * 알림 설정 (SET-02, SCR-80): 미리보기 숨기기, 방해 금지 시간, 그룹별 알림 끄기.
 * 바꾸면 바로 화면에 보이고 서버에 저장한다. 실패하면 되돌리고 알린다
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val repository: NotificationSettingsRepository,
    private val groupRepository: GroupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationSettingsUiState())
    val state: StateFlow<NotificationSettingsUiState> = _state.asStateFlow()

    private val _events = Channel<UiText>(Channel.BUFFERED)
    val events: Flow<UiText> = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            val result = repository.settings()
            if (result !is ApiResult.Success) {
                _state.update { it.copy(loading = false, loadFailed = true) }
                return@launch
            }
            val groups = groupRepository.groups.value.ifEmpty {
                (groupRepository.refreshGroups() as? ApiResult.Success)?.value.orEmpty()
            }
            val muted = groups.map { group -> async { (repository.groupMuted(group.id) as? ApiResult.Success)?.value } }.awaitAll()
            _state.update {
                it.copy(
                    loading = false,
                    settings = result.value,
                    groups = groups.zip(muted) { group, m -> GroupNotification(group.id, group.name, m) },
                )
            }
        }
    }

    fun setPreviewHidden(hidden: Boolean) = save({ it.copy(previewHidden = hidden) }) { repository.setPreviewHidden(hidden) }

    fun setQuietHours(enabled: Boolean) = save({ it.copy(quietHoursEnabled = enabled) }) { repository.setQuietHours(enabled) }

    fun setQuietStart(start: LocalTime) = setQuietTimes(start, _state.value.settings.quietEnd)

    fun setQuietEnd(end: LocalTime) = setQuietTimes(_state.value.settings.quietStart, end)

    /** 이 그룹 알림 받기 */
    fun setGroupEnabled(groupId: Long, enabled: Boolean) {
        val before = _state.value.groups.firstOrNull { it.groupId == groupId } ?: return
        setGroupMuted(groupId, !enabled)
        viewModelScope.launch {
            val result = repository.setGroupMuted(groupId, !enabled)
            if (result is ApiResult.Success) {
                setGroupMuted(groupId, result.value)
            } else {
                setGroupMuted(groupId, before.muted)
                _events.send(result.toUiText())
            }
        }
    }

    private fun setQuietTimes(start: LocalTime, end: LocalTime) {
        if (start == end) {
            viewModelScope.launch { _events.send(UiText.Res(R.string.notif_quiet_same)) }
            return
        }
        save({ it.copy(quietStart = start, quietEnd = end) }) { repository.setQuietTimes(start, end) }
    }

    private fun save(change: (NotificationSettings) -> NotificationSettings, call: suspend () -> ApiResult<NotificationSettings>) {
        val before = _state.value.settings
        _state.update { it.copy(settings = change(it.settings)) }
        viewModelScope.launch {
            val result = call()
            if (result is ApiResult.Success) {
                _state.update { it.copy(settings = result.value) }
            } else {
                _state.update { it.copy(settings = before) }
                _events.send(result.toUiText())
            }
        }
    }

    private fun setGroupMuted(groupId: Long, muted: Boolean?) {
        _state.update { s -> s.copy(groups = s.groups.map { if (it.groupId == groupId) it.copy(muted = muted) else it }) }
    }
}
