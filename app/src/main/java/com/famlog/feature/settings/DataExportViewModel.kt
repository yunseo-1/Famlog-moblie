package com.famlog.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.R
import com.famlog.core.data.DataExportRepository
import com.famlog.core.designsystem.UiText
import com.famlog.core.designsystem.toUiText
import com.famlog.core.model.ApiResult
import com.famlog.core.model.DataExport
import com.famlog.core.model.DataExportStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DataExportUiState(
    /** 가장 최근 요청. null이면 요청한 적 없음 */
    val export: DataExport? = null,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val requesting: Boolean = false,
    val downloading: Boolean = false,
) {
    /** 요청 없음·만료·실패면 [요청] 버튼 */
    val canRequest: Boolean
        get() = !loading && !loadFailed && (export == null || export.status == DataExportStatus.EXPIRED || export.status == DataExportStatus.FAILED)
}

sealed interface DataExportEvent {
    data class Message(val text: UiText) : DataExportEvent

    /** 10분짜리 링크. 화면이 시스템 다운로드(10 이상)나 브라우저(9 이하)로 받는다 */
    data class Download(val url: String, val fileName: String) : DataExportEvent {
        override fun toString(): String = "Download(fileName=$fileName)"
    }
}

/**
 * 내 데이터 내보내기 (ACC-02): 들어올 때 가장 최근 요청으로 상태를 되살린다.
 * 준비 중이면 실시간 알림을 기다리면서 [POLL_MS]마다 다시 묻는다 — 만들다 실패(FAILED)하면 알림이 오지 않는다
 */
@HiltViewModel
class DataExportViewModel @Inject constructor(
    private val repository: DataExportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DataExportUiState())
    val state: StateFlow<DataExportUiState> = _state.asStateFlow()

    private val _events = Channel<DataExportEvent>(Channel.BUFFERED)
    val events: Flow<DataExportEvent> = _events.receiveAsFlow()

    init {
        load()
        viewModelScope.launch {
            repository.ready.collect { id -> if (id == _state.value.export?.id) refresh() }
        }
    }

    fun load() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            val result = repository.latest()
            _state.update {
                if (result is ApiResult.Success) it.copy(loading = false, export = result.value) else it.copy(loading = false, loadFailed = true)
            }
        }
    }

    /** 화면이 보이는 동안(STARTED) 부른다. 준비 중인 동안만 주기적으로 다시 묻는다 */
    suspend fun watch() {
        refresh()
        _state.map { it.export?.status?.preparing == true }.distinctUntilChanged().collectLatest { preparing ->
            while (preparing) {
                delay(POLL_MS)
                refresh()
            }
        }
    }

    fun request() {
        if (_state.value.requesting || !_state.value.canRequest) return
        _state.update { it.copy(requesting = true) }
        viewModelScope.launch {
            val result = repository.request()
            if (result is ApiResult.Success) {
                _state.update { it.copy(requesting = false, export = result.value) }
            } else {
                // 하루 한 번 제한(429)이면 서버 문구 그대로
                _state.update { it.copy(requesting = false) }
                _events.send(DataExportEvent.Message(result.toUiText()))
            }
        }
    }

    /** 링크는 10분만 유효해서 누를 때마다 새로 받는다 */
    fun download() {
        val id = _state.value.export?.id ?: return
        if (_state.value.downloading) return
        _state.update { it.copy(downloading = true) }
        viewModelScope.launch {
            val result = repository.detail(id)
            _state.update { it.copy(downloading = false) }
            if (result !is ApiResult.Success) {
                _events.send(DataExportEvent.Message(result.toUiText()))
                return@launch
            }
            val export = result.value
            // 링크는 화면 상태에 두지 않는다
            _state.update { it.copy(export = export.copy(downloadUrl = null)) }
            val url = export.downloadUrl
            if (export.status == DataExportStatus.READY && url != null) {
                _events.send(DataExportEvent.Download(url, export.fileName ?: DEFAULT_FILE_NAME))
            } else {
                _events.send(DataExportEvent.Message(UiText.Res(R.string.export_not_ready)))
            }
        }
    }

    private suspend fun refresh() {
        val id = _state.value.export?.id ?: return
        val result = repository.detail(id)
        if (result is ApiResult.Success) _state.update { it.copy(export = result.value.copy(downloadUrl = null)) }
    }

    companion object {
        const val POLL_MS = 10_000L
        private const val DEFAULT_FILE_NAME = "famlog-export.zip"
    }
}
