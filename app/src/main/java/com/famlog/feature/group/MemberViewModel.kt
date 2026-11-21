package com.famlog.feature.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.GroupRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.GroupRole
import com.famlog.core.model.Member
import com.famlog.core.model.ReportTarget
import com.famlog.core.model.ReportTargetType
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

enum class MemberAction { MAKE_ADMIN, MAKE_MEMBER, TRANSFER_OWNER, REMOVE, REPORT }

/**
 * 내가 [target]에게 할 수 있는 것 (사양서 2.2, GRP-05). 서버와 같은 기준:
 * 권한 변경·내보내기는 관리자 이상이고 그룹장은 대상이 될 수 없다, 그룹장 위임은 그룹장만, 신고는 누구나. 나 자신에게는 없다.
 */
fun memberActions(myUserId: Long?, myRole: GroupRole, target: Member): List<MemberAction> {
    if (target.userId == myUserId) return emptyList()
    val manage = myRole != GroupRole.MEMBER && target.role != GroupRole.OWNER
    return buildList {
        if (manage) add(if (target.role == GroupRole.ADMIN) MemberAction.MAKE_MEMBER else MemberAction.MAKE_ADMIN)
        if (myRole == GroupRole.OWNER) add(MemberAction.TRANSFER_OWNER)
        if (manage) add(MemberAction.REMOVE)
        add(MemberAction.REPORT)
    }
}

/** 서랍에서 고른 구성원. [confirm]은 확인을 받는 중인 동작(그룹장 넘기기·내보내기) */
data class MemberSheetState(
    val member: Member,
    val actions: List<MemberAction>,
    val confirm: MemberAction? = null,
    val busy: Boolean = false,
)

sealed interface MemberEvent {
    data class Message(val text: UiText) : MemberEvent

    /** 구성원 신고는 신고 시트(SCR-82)로 */
    data class Report(val target: ReportTarget) : MemberEvent
}

/** 구성원 관리 (GRP-05): 채팅방 서랍의 구성원 줄을 누르면 연다 */
@HiltViewModel
class MemberViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
) : ViewModel() {

    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    private val _sheet = MutableStateFlow<MemberSheetState?>(null)
    val sheet: StateFlow<MemberSheetState?> = _sheet.asStateFlow()

    private val _events = Channel<MemberEvent>(Channel.BUFFERED)
    val events: Flow<MemberEvent> = _events.receiveAsFlow()

    /** 할 수 있는 것이 없으면(나 자신) 열지 않는다 */
    fun open(member: Member, myUserId: Long?, myRole: GroupRole) {
        val actions = memberActions(myUserId, myRole, member)
        if (actions.isEmpty()) return
        _sheet.value = MemberSheetState(member, actions)
    }

    fun close() {
        if (_sheet.value?.busy == true) return
        _sheet.value = null
    }

    fun cancelConfirm() = _sheet.update { it?.copy(confirm = null) }

    fun perform(action: MemberAction) {
        val current = _sheet.value ?: return
        if (current.busy || action !in current.actions) return
        when (action) {
            MemberAction.REPORT -> {
                _sheet.value = null
                viewModelScope.launch {
                    _events.send(MemberEvent.Report(ReportTarget(ReportTargetType.USER, current.member.userId, groupId)))
                }
            }
            MemberAction.TRANSFER_OWNER, MemberAction.REMOVE -> _sheet.value = current.copy(confirm = action)
            MemberAction.MAKE_ADMIN, MemberAction.MAKE_MEMBER -> run(current, action)
        }
    }

    /** 확인 대화상자의 [넘기기]·[내보내기] */
    fun confirm() {
        val current = _sheet.value ?: return
        val action = current.confirm ?: return
        run(current, action)
    }

    private fun run(current: MemberSheetState, action: MemberAction) {
        val member = current.member
        _sheet.value = current.copy(busy = true)
        viewModelScope.launch {
            val result = when (action) {
                MemberAction.MAKE_ADMIN -> groupRepository.changeRole(groupId, member.userId, GroupRole.ADMIN)
                MemberAction.MAKE_MEMBER -> groupRepository.changeRole(groupId, member.userId, GroupRole.MEMBER)
                MemberAction.TRANSFER_OWNER -> groupRepository.changeRole(groupId, member.userId, GroupRole.OWNER)
                MemberAction.REMOVE -> groupRepository.removeMember(groupId, member.userId)
                MemberAction.REPORT -> return@launch
            }
            if (result is ApiResult.Success) {
                _sheet.value = null
                _events.send(MemberEvent.Message(UiText.Res(action.doneRes(), listOf(member.displayName))))
            } else {
                _sheet.update { it?.copy(busy = false, confirm = null) }
                _events.send(MemberEvent.Message(result.toUiText()))
            }
        }
    }

    private fun MemberAction.doneRes(): Int = when (this) {
        MemberAction.MAKE_ADMIN -> R.string.member_done_admin
        MemberAction.MAKE_MEMBER -> R.string.member_done_member
        MemberAction.TRANSFER_OWNER -> R.string.member_done_owner
        MemberAction.REMOVE, MemberAction.REPORT -> R.string.member_done_removed
    }
}
