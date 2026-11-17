package com.famlog.navigation

import androidx.lifecycle.ViewModel
import com.famlog.core.network.SessionEvent
import com.famlog.core.network.SessionEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(sessionEvents: SessionEvents) : ViewModel() {
    val sessionEvents: Flow<SessionEvent> = sessionEvents.events
}
