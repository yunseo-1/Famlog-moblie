package com.famlog.feature.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.GroupRepository
import com.famlog.core.data.ScheduleRepository
import com.famlog.core.datastore.SessionStore
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.Member
import com.famlog.core.model.Reminder
import com.famlog.core.model.Repeat
import com.famlog.core.model.RepeatFreq
import com.famlog.core.model.Schedule
import com.famlog.core.model.ScheduleConflict
import com.famlog.core.model.ScheduleDraft
import com.famlog.core.model.errorCode
import com.famlog.feature.chat.SeoulZone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class ScheduleForm(
    val title: String = "",
    val allDay: Boolean = false,
    val startDate: LocalDate = LocalDate.MIN,
    val startTime: LocalTime = LocalTime.of(10, 0),
    val endDate: LocalDate = LocalDate.MIN,
    val endTime: LocalTime = LocalTime.of(11, 0),
    val participants: Set<Long> = emptySet(),
    val repeatFreq: RepeatFreq? = null,
    val repeatUntil: LocalDate? = null,
    val reminders: List<Reminder> = Reminder.defaults(allDay = false),
    val memo: String = "",
) {
    val startsAt: Instant get() = LocalDateTime.of(startDate, startTime).atZone(SeoulZone).toInstant()
    val endsAt: Instant get() = LocalDateTime.of(endDate, endTime).atZone(SeoulZone).toInstant()

    /** 겹침 확인 범위: 종일은 첫날 00:00 ~ 마지막 날 다음 날 00:00 */
    val rangeStart: Instant get() = if (allDay) startDate.atStartOfDay(SeoulZone).toInstant() else startsAt
    val rangeEnd: Instant get() = if (allDay) endDate.plusDays(1).atStartOfDay(SeoulZone).toInstant() else endsAt

    val endBeforeStart: Boolean get() = if (allDay) endDate.isBefore(startDate) else endsAt.isBefore(startsAt)
    val untilBeforeStart: Boolean get() = repeatUntil?.isBefore(startDate) == true
}

data class ScheduleEditUiState(
    val loading: Boolean = false,
    val isEdit: Boolean = false,
    val form: ScheduleForm = ScheduleForm(),
    val members: List<Member> = emptyList(),
    val conflicts: List<ScheduleConflict> = emptyList(),
    /** [저장]을 한 번 누른 뒤에만 빈 칸 오류를 보인다 */
    val showErrors: Boolean = false,
    val saving: Boolean = false,
) {
    val titleMissing: Boolean get() = form.title.isBlank()
    val participantsMissing: Boolean get() = form.participants.isEmpty()
    val canSave: Boolean
        get() = !loading && !saving && !titleMissing && !participantsMissing && !form.endBeforeStart && !form.untilBeforeStart

    fun nameOf(userId: Long): String? = members.firstOrNull { it.userId == userId }?.displayName
}

sealed interface ScheduleEditEvent {
    data class Message(val text: UiText) : ScheduleEditEvent
    data class Saved(val scheduleId: Long) : ScheduleEditEvent
    data object Closed : ScheduleEditEvent
}

/**
 * SCR-21 일정 등록·수정 (SCH-01·03·05). 등록은 [ScheduleEditRoute.date]의 날을 기본으로,
 * 수정은 시리즈 전체를 고친다(반복 일정의 "이번 회차만"은 v1.1).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ScheduleEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scheduleRepository: ScheduleRepository,
    private val groupRepository: GroupRepository,
    private val sessionStore: SessionStore,
    private val clock: Clock,
) : ViewModel() {

    private val scheduleId: Long? = savedStateHandle.get<Long>("scheduleId")?.takeIf { it > 0 }
    private val initialDate: LocalDate? = savedStateHandle.get<String>("date")?.let(LocalDate::parse)

    /** 수정 대상 (version·원래 반복 간격·횟수를 지킨다) */
    private var original: Schedule? = null
    private var groupId: Long? = null

    /** 사용자가 알림을 직접 고쳤으면 종일 전환 때 기본값으로 되돌리지 않는다 */
    private var remindersTouched = false

    private val local = MutableStateFlow(ScheduleEditUiState(loading = true, isEdit = scheduleId != null))

    val uiState: StateFlow<ScheduleEditUiState> = local
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    private val _events = Channel<ScheduleEditEvent>(Channel.BUFFERED)
    val events: Flow<ScheduleEditEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { if (scheduleId != null) loadExisting(scheduleId) else startNew() }
        // 시간·참여자가 바뀌면 잠시 뒤 겹치는 일정을 물어본다 (SCH-05, 막지는 않는다)
        viewModelScope.launch {
            local.map { state -> state.takeUnless { it.loading }?.form?.let { ConflictQuery(it.rangeStart, it.rangeEnd, it.participants, it.endBeforeStart) } }
                .distinctUntilChanged()
                .debounce(CONFLICT_DEBOUNCE_MS)
                .collect { query -> checkConflicts(query) }
        }
    }

    private data class ConflictQuery(val start: Instant, val end: Instant, val participants: Set<Long>, val invalid: Boolean)

    fun onTitleChange(value: String) = updateForm { it.copy(title = value.take(MAX_TITLE)) }

    fun onMemoChange(value: String) = updateForm { it.copy(memo = value.take(MAX_MEMO)) }

    fun onAllDayChange(allDay: Boolean) = updateForm { form ->
        val reminders = if (remindersTouched) {
            if (allDay) form.reminders.filter { it == Reminder.PREV_DAY_20 } else form.reminders
        } else {
            Reminder.defaults(allDay)
        }
        form.copy(allDay = allDay, reminders = reminders)
    }

    /** 시작을 옮기면 길이를 지킨 채 끝도 옮긴다 */
    fun onStartDateChange(date: LocalDate) = updateForm { form -> form.shiftStart(LocalDateTime.of(date, form.startTime)) }

    fun onStartTimeChange(time: LocalTime) = updateForm { form -> form.shiftStart(LocalDateTime.of(form.startDate, time)) }

    fun onEndDateChange(date: LocalDate) = updateForm { it.copy(endDate = date) }

    fun onEndTimeChange(time: LocalTime) = updateForm { it.copy(endTime = time) }

    fun toggleParticipant(userId: Long) = updateForm { form ->
        val next = if (userId in form.participants) form.participants - userId else form.participants + userId
        form.copy(participants = next)
    }

    fun onRepeatChange(freq: RepeatFreq?) = updateForm { it.copy(repeatFreq = freq, repeatUntil = if (freq == null) null else it.repeatUntil) }

    fun onRepeatUntilChange(date: LocalDate?) = updateForm { it.copy(repeatUntil = date) }

    /** 최대 3개, 종일 일정은 전날 오후 8시만 */
    fun toggleReminder(reminder: Reminder) {
        val form = local.value.form
        if (form.allDay && reminder != Reminder.PREV_DAY_20) return
        val next = if (reminder in form.reminders) {
            form.reminders - reminder
        } else {
            if (form.reminders.size >= Reminder.MAX) {
                viewModelScope.launch { _events.send(ScheduleEditEvent.Message(UiText.Res(R.string.schedule_reminder_max))) }
                return
            }
            (form.reminders + reminder).sortedBy { it.ordinal }
        }
        remindersTouched = true
        updateForm { it.copy(reminders = next) }
    }

    fun save() {
        val state = local.value
        if (!state.canSave) {
            local.update { it.copy(showErrors = true) }
            return
        }
        val groupId = groupId ?: return
        local.update { it.copy(saving = true) }
        viewModelScope.launch {
            val draft = state.form.toDraft()
            val current = original
            val result = if (current == null) scheduleRepository.create(groupId, draft) else scheduleRepository.update(current, draft)
            when {
                result is ApiResult.Success -> _events.send(ScheduleEditEvent.Saved(result.value.id))
                result.errorCode == ErrorCodes.VERSION_CONFLICT && current != null -> {
                    // 다른 가족이 먼저 고쳤다: 최신 내용을 다시 불러와 보여 준다
                    _events.send(ScheduleEditEvent.Message(UiText.Res(R.string.schedule_version_conflict)))
                    loadExisting(current.id)
                }
                else -> _events.send(ScheduleEditEvent.Message(result.toUiText()))
            }
            local.update { it.copy(saving = false) }
        }
    }

    private suspend fun startNew() {
        val groupId = groupRepository.currentGroupId.first() ?: return close()
        this.groupId = groupId
        val members = membersOf(groupId)
        val me = sessionStore.userId()
        val date = initialDate ?: LocalDate.now(clock)
        val now = LocalDateTime.now(clock)
        // 오늘이면 다음 정시, 다른 날이면 오전 10시. 1시간짜리
        val start = if (date == now.toLocalDate() && now.hour < 23) {
            now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        } else {
            LocalDateTime.of(date, LocalTime.of(10, 0))
        }
        val end = start.plusHours(1)
        local.update {
            it.copy(
                loading = false,
                members = members,
                form = ScheduleForm(
                    startDate = start.toLocalDate(),
                    startTime = start.toLocalTime(),
                    endDate = end.toLocalDate(),
                    endTime = end.toLocalTime(),
                    // 참여자 기본값은 등록하는 나 (서버 계약)
                    participants = setOfNotNull(me?.takeIf { id -> members.isEmpty() || members.any { m -> m.userId == id } }),
                ),
            )
        }
    }

    private suspend fun loadExisting(id: Long) {
        local.update { it.copy(loading = true) }
        when (val result = scheduleRepository.detail(id)) {
            is ApiResult.Success -> {
                val schedule = result.value
                original = schedule
                groupId = schedule.groupId
                remindersTouched = true
                local.update {
                    it.copy(loading = false, members = membersOf(schedule.groupId), form = schedule.toForm())
                }
            }
            else -> {
                _events.send(ScheduleEditEvent.Message(result.toUiText()))
                close()
            }
        }
    }

    private suspend fun close() {
        _events.send(ScheduleEditEvent.Closed)
    }

    private suspend fun membersOf(groupId: Long): List<Member> {
        groupRepository.details.value[groupId]?.let { return it.members }
        return (groupRepository.detail(groupId) as? ApiResult.Success)?.value?.members.orEmpty()
    }

    private suspend fun checkConflicts(query: ConflictQuery?) {
        val groupId = groupId
        if (query == null || groupId == null || query.invalid || query.participants.isEmpty()) {
            local.update { it.copy(conflicts = emptyList()) }
            return
        }
        val result = scheduleRepository.conflicts(groupId, query.start, query.end, query.participants.toList(), original?.id)
        // 겹침 안내는 참고용이라 실패하면 조용히 비운다
        local.update { it.copy(conflicts = (result as? ApiResult.Success)?.value.orEmpty()) }
    }

    private fun updateForm(transform: (ScheduleForm) -> ScheduleForm) = local.update { it.copy(form = transform(it.form)) }

    private fun ScheduleForm.shiftStart(newStart: LocalDateTime): ScheduleForm {
        val oldStart = LocalDateTime.of(startDate, startTime)
        val oldEnd = LocalDateTime.of(endDate, endTime)
        val duration = java.time.Duration.between(oldStart, oldEnd).takeUnless { it.isNegative } ?: java.time.Duration.ZERO
        val newEnd = newStart.plus(duration)
        return copy(
            startDate = newStart.toLocalDate(),
            startTime = newStart.toLocalTime(),
            endDate = newEnd.toLocalDate(),
            endTime = newEnd.toLocalTime(),
        )
    }

    /** 원래 반복의 간격·횟수는 종류가 같을 때만 지킨다. 종료일을 정하면 횟수는 버린다 */
    private fun ScheduleForm.toDraft(): ScheduleDraft {
        val base = original?.repeat
        val repeat = repeatFreq?.let { freq ->
            if (base != null && base.freq == freq) {
                // 종료일이 있으면 toRrule이 횟수보다 종료일을 쓴다
                base.copy(until = repeatUntil)
            } else {
                Repeat(freq, repeatUntil)
            }
        }
        return ScheduleDraft(
            title = title.trim(),
            allDay = allDay,
            startsAt = if (allDay) null else startsAt,
            endsAt = if (allDay) null else endsAt,
            startDate = if (allDay) startDate else null,
            endDate = if (allDay) endDate else null,
            participantIds = participants.toList(),
            rrule = repeat?.toRrule(),
            memo = memo.trim().ifEmpty { null },
            reminders = if (allDay) reminders.filter { it == Reminder.PREV_DAY_20 } else reminders,
        )
    }

    private fun Schedule.toForm(): ScheduleForm {
        val start = startsAt.atZone(SeoulZone)
        val end = endsAt.atZone(SeoulZone)
        val repeat = repeat
        return ScheduleForm(
            title = title,
            allDay = allDay,
            startDate = if (allDay) startDate else start.toLocalDate(),
            startTime = if (allDay) LocalTime.of(10, 0) else start.toLocalTime(),
            endDate = if (allDay) endDate else end.toLocalDate(),
            endTime = if (allDay) LocalTime.of(11, 0) else end.toLocalTime(),
            participants = participantIds.toSet(),
            repeatFreq = repeat?.freq,
            repeatUntil = repeat?.until,
            reminders = reminders,
            memo = memo.orEmpty(),
        )
    }

    private companion object {
        const val MAX_TITLE = 100
        const val MAX_MEMO = 1000
        const val CONFLICT_DEBOUNCE_MS = 400L
    }
}
