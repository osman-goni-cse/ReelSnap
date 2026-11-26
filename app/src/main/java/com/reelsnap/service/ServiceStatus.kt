package com.reelsnap.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Live status published by the accessibility service and observed by the UI (same process). */
data class Status(
    val connected: Boolean = false,
    val running: Boolean = false,
    val appLabel: String? = null,
    val session: String? = null,
    val reels: Int = 0,
    val shots: Int = 0,
    val message: String? = null,
)

object ServiceStatus {
    private val _state = MutableStateFlow(Status())
    val state: StateFlow<Status> = _state.asStateFlow()
    fun update(f: (Status) -> Status) = _state.update(f)
}
