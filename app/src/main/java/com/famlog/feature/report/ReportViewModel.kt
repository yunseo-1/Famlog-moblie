package com.famlog.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.ReportRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.ReportReason
import com.famlog.core.model.ReportTarget
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

/** 신고 시트 (SCR-82) */
data class ReportSheetState(
    val target: ReportTarget,
    val reason: ReportReason? = null,
    val detail: String = "",
    val saving: Boolean = false,
    /** [신고하기]를 눌렀는데 빈 칸이 있다 → "기타"의 내용 안내를 보인다 */
    val showErrors: Boolean = false,
) {
    val detailRequired: Boolean get() = reason == ReportReason.OTHER
    val detailMissing: Boolean get() = detailRequired && detail.isBlank()
    val canSubmit: Boolean get() = reason != null && !detailMissing && !saving
}

/**
 * 신고 (MOD-01, SCR-82). 채팅방 메시지, 사진·동영상 전체 화면, AI 후보 카드에서 같은 시트를 연다.
 * 접수되면 "신고가 접수됐어요"만 알린다 (처리 결과 알림은 없다).
 */
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _sheet = MutableStateFlow<ReportSheetState?>(null)
    val sheet: StateFlow<ReportSheetState?> = _sheet.asStateFlow()

    private val _events = Channel<UiText>(Channel.BUFFERED)

    /** 화면에 띄울 안내 (접수·실패) */
    val events: Flow<UiText> = _events.receiveAsFlow()

    fun open(target: ReportTarget) {
        _sheet.value = ReportSheetState(target)
    }

    fun close() {
        if (_sheet.value?.saving == true) return
        _sheet.value = null
    }

    fun onReasonChange(reason: ReportReason) = _sheet.update { it?.takeIf { s -> reason in s.target.reasons }?.copy(reason = reason) ?: it }

    fun onDetailChange(value: String) = _sheet.update { it?.copy(detail = value.take(MAX_DETAIL)) }

    fun submit() {
        val current = _sheet.value ?: return
        if (current.saving) return
        val reason = current.reason
        if (reason == null || current.detailMissing) {
            _sheet.value = current.copy(showErrors = true)
            return
        }
        _sheet.value = current.copy(saving = true)
        viewModelScope.launch {
            val result = reportRepository.report(current.target, reason, current.detail)
            if (result is ApiResult.Success) {
                _sheet.value = null
                _events.send(UiText.Res(R.string.report_done))
            } else {
                _sheet.update { it?.copy(saving = false) }
                _events.send(result.toUiText())
            }
        }
    }

    companion object {
        const val MAX_DETAIL = 500
    }
}
