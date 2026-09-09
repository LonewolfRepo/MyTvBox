package com.itv.blockbuster.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdultSessionManager @Inject constructor() {
    private val _isAdultMode = MutableStateFlow(false)
    val isAdultMode: StateFlow<Boolean> = _isAdultMode.asStateFlow()

    fun enterAdultMode() { _isAdultMode.value = true }
    fun exitAdultMode() { _isAdultMode.value = false }
}