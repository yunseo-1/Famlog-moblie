package com.famlog.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famlog.core.data.StartDestination
import com.famlog.core.data.StartResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 앱 시작 분기 (AUTH-01). 서버에 닿지 못하면 [unavailable]로 다시 시도를 보인다 */
@HiltViewModel
class StartViewModel @Inject constructor(private val startResolver: StartResolver) : ViewModel() {

    private val _unavailable = MutableStateFlow(false)
    val unavailable: StateFlow<Boolean> = _unavailable.asStateFlow()

    private val _destinations = Channel<StartDestination>(Channel.BUFFERED)
    val destinations: Flow<StartDestination> = _destinations.receiveAsFlow()

    init {
        resolve()
    }

    fun resolve() {
        _unavailable.value = false
        viewModelScope.launch {
            when (val destination = startResolver.resolve()) {
                StartDestination.Unavailable -> _unavailable.value = true
                else -> _destinations.send(destination)
            }
        }
    }
}
