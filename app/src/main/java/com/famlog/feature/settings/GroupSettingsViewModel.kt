package com.famlog.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.AppConfigRepository
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.GroupSettingsRepository
import com.famlog.core.datastore.SessionStore
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.errorCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupSettingsUiState(
    val groupName: String = "",
    val myRole: GroupRole = GroupRole.MEMBER,
    val myUserId: Long? = null,
    val aiEnabled: Boolean = false,
    /** 이 그룹에서 내 메시지를 AI 분석에서 뺐다 */
    val aiOptOut: Boolean = false,
    /** 관리자에게만 구성원별 분석 제외가 보인다 (볼 수만 있다, 사양서 8.8) */
    val members: List<Member> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val savingAi: Boolean = false,
    val savingOptOut: Boolean = false,
    val notice: AiNoticeSheetState? = null,
    /** 서버가 AI 분석 전체를 잠시 멈췄다 (기능 스위치). 설정은 그대로 저장된다 */
    val aiPaused: Boolean = false,
    /** 나를 포함한 구성원 수 (나가기 안내를 고른다) */
    val memberCount: Int = 0,
    val leave: LeaveDialog? = null,
    val delete: DeleteDialog? = null,
) {
    val isAdmin: Boolean get() = myRole != GroupRole.MEMBER
    val isOwner: Boolean get() = myRole == GroupRole.OWNER
}

/** 그룹 나가기 확인. [ownerMustTransfer]면 나갈 수 없다는 안내만 (다른 가족이 있는 그룹장) */
data class LeaveDialog(val ownerMustTransfer: Boolean = false, val alone: Boolean = false, val busy: Boolean = false)

/** 그룹 삭제 확인 (그룹장). 그룹 이름을 그대로 입력해야 [삭제]가 켜진다 */
data class DeleteDialog(val name: String = "", val busy: Boolean = false)

sealed interface SettingsEvent {
    data class Message(val text: UiText) : SettingsEvent

    /** 그룹에서 나갔거나 그룹을 지웠다 → 시작 분기(다른 그룹 또는 그룹 시작) */
    data object Left : SettingsEvent
}

/** 그룹 설정 (SCR-12 서랍 → 그룹 설정): AI 분석 켜기·끄기(관리자), 내 메시지 분석 제외(모두), 안내 다시 보기 (SET-01) */
@HiltViewModel
class GroupSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: GroupSettingsRepository,
    private val groupRepository: GroupRepository,
    private val sessionStore: SessionStore,
    appConfigRepository: AppConfigRepository,
) : ViewModel() {

    val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    private val local = MutableStateFlow(GroupSettingsUiState())

    val uiState: StateFlow<GroupSettingsUiState> = combine(local, groupRepository.details, appConfigRepository.features) { local, details, features ->
        val state = local.copy(aiPaused = !features.aiAnalysis)
        val detail = details[groupId] ?: return@combine state
        state.copy(
            groupName = detail.name,
            myRole = detail.myRole,
            memberCount = detail.members.size,
            members = if (detail.myRole != GroupRole.MEMBER) detail.members else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        local.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            local.update { it.copy(myUserId = sessionStore.userId()) }
            // 구성원별 분석 제외는 그룹 상세에 실려 온다 (관리자에게만 값)
            val detail = async { groupRepository.detail(groupId) }
            val settings = async { settingsRepository.settings(groupId) }
            val optOut = async { settingsRepository.aiOptOut(groupId) }
            detail.await()
            val s = settings.await()
            val o = optOut.await()
            local.update {
                it.copy(
                    loading = false,
                    loadFailed = s !is ApiResult.Success || o !is ApiResult.Success,
                    aiEnabled = (s as? ApiResult.Success)?.value?.enabled ?: it.aiEnabled,
                    aiOptOut = (o as? ApiResult.Success)?.value ?: it.aiOptOut,
                )
            }
        }
    }

    /** 켜기는 안내를 먼저 보여 주고 [confirmNotice]에서 켠다. 끄기는 바로 */
    fun setAiEnabled(enabled: Boolean) {
        val state = uiState.value
        if (!state.isAdmin || state.savingAi || enabled == state.aiEnabled) return
        if (enabled) {
            openNotice(AiNoticeMode.ENABLE)
            return
        }
        local.update { it.copy(savingAi = true) }
        viewModelScope.launch {
            val result = settingsRepository.setAiEnabled(groupId, enabled = false, noticeVersion = null)
            local.update { it.copy(savingAi = false, aiEnabled = (result as? ApiResult.Success)?.value?.enabled ?: it.aiEnabled) }
            message(if (result is ApiResult.Success) UiText.Res(R.string.group_settings_ai_disabled) else result.toUiText())
        }
    }

    fun setAiOptOut(optOut: Boolean) {
        if (local.value.savingOptOut || optOut == local.value.aiOptOut) return
        local.update { it.copy(savingOptOut = true) }
        viewModelScope.launch {
            val result = settingsRepository.setAiOptOut(groupId, optOut)
            local.update { it.copy(savingOptOut = false, aiOptOut = (result as? ApiResult.Success)?.value ?: it.aiOptOut) }
            when {
                result !is ApiResult.Success -> message(result.toUiText())
                // 관리자는 아래 구성원별 분석 제외에 내 줄도 있다
                uiState.value.isAdmin -> groupRepository.detail(groupId)
            }
        }
    }

    fun showNotice() = openNotice(AiNoticeMode.VIEW)

    fun closeNotice() {
        if (local.value.notice?.saving == true) return
        local.update { it.copy(notice = null) }
    }

    /** [동의하고 켜기]: 보여 준 안내 버전으로 켠다. 그사이 안내가 바뀌었으면(400 aiNoticeVersion) 새 안내를 다시 보여 준다 */
    fun confirmNotice() {
        val sheet = local.value.notice ?: return
        if (sheet.mode != AiNoticeMode.ENABLE || sheet.saving) return
        local.update { it.copy(notice = sheet.copy(saving = true), savingAi = true) }
        viewModelScope.launch {
            val result = settingsRepository.setAiEnabled(groupId, enabled = true, noticeVersion = sheet.notice.version)
            when {
                result is ApiResult.Success -> {
                    local.update { it.copy(notice = null, savingAi = false, aiEnabled = result.value.enabled) }
                    message(UiText.Res(R.string.group_settings_ai_enabled))
                }
                result.isOutdatedNotice(FIELD_NOTICE_VERSION) -> {
                    local.update { it.copy(notice = null, savingAi = false) }
                    message(UiText.Res(R.string.ai_notice_changed))
                    openNotice(AiNoticeMode.ENABLE)
                }
                else -> {
                    local.update { it.copy(notice = sheet, savingAi = false) }
                    message(result.toUiText())
                }
            }
        }
    }

    // ---- 그룹 나가기·삭제 (GRP-05) ----

    fun openLeave() {
        val state = uiState.value
        val others = state.memberCount > 1
        local.update { it.copy(leave = LeaveDialog(ownerMustTransfer = state.isOwner && others, alone = !others)) }
    }

    fun closeLeave() {
        if (local.value.leave?.busy == true) return
        local.update { it.copy(leave = null) }
    }

    fun confirmLeave() {
        val dialog = local.value.leave ?: return
        if (dialog.busy || dialog.ownerMustTransfer) return
        local.update { it.copy(leave = dialog.copy(busy = true)) }
        viewModelScope.launch {
            val result = groupRepository.leave(groupId)
            when {
                result is ApiResult.Success -> {
                    local.update { it.copy(leave = null) }
                    _events.send(SettingsEvent.Left)
                }
                // 그사이 다른 가족이 들어왔으면 서버가 막는다
                result.errorCode == ErrorCodes.OWNER_TRANSFER_REQUIRED ->
                    local.update { it.copy(leave = LeaveDialog(ownerMustTransfer = true)) }
                else -> {
                    local.update { it.copy(leave = dialog) }
                    message(result.toUiText())
                }
            }
        }
    }

    fun openDelete() {
        if (!uiState.value.isOwner) return
        local.update { it.copy(delete = DeleteDialog()) }
    }

    fun closeDelete() {
        if (local.value.delete?.busy == true) return
        local.update { it.copy(delete = null) }
    }

    fun onDeleteNameChange(value: String) = local.update { it.copy(delete = it.delete?.copy(name = value.take(MAX_GROUP_NAME))) }

    /** 그룹 이름이 그대로 맞아야 지운다 */
    fun confirmDelete() {
        val dialog = local.value.delete ?: return
        if (dialog.busy || !canDelete(dialog)) return
        local.update { it.copy(delete = dialog.copy(busy = true)) }
        viewModelScope.launch {
            val result = groupRepository.deleteGroup(groupId)
            if (result is ApiResult.Success) {
                local.update { it.copy(delete = null) }
                _events.send(SettingsEvent.Left)
            } else {
                local.update { it.copy(delete = dialog) }
                message(result.toUiText())
            }
        }
    }

    fun canDelete(dialog: DeleteDialog): Boolean = dialog.name.trim() == uiState.value.groupName && uiState.value.groupName.isNotEmpty()

    private fun openNotice(mode: AiNoticeMode) {
        viewModelScope.launch {
            when (val result = settingsRepository.aiNotice()) {
                is ApiResult.Success -> local.update { it.copy(notice = AiNoticeSheetState(result.value, mode)) }
                else -> message(result.toUiText())
            }
        }
    }

    private suspend fun message(text: UiText) = _events.send(SettingsEvent.Message(text))

    private companion object {
        const val FIELD_NOTICE_VERSION = "aiNoticeVersion"
        const val MAX_GROUP_NAME = 30
    }
}

/** 보여 준 안내 버전이 서버 현재 버전과 다르다 (400, 그 항목의 fieldErrors) */
internal fun ApiResult<*>.isOutdatedNotice(field: String): Boolean =
    this is ApiResult.Failure && field in error?.fields.orEmpty()
