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

    // MOVED: Unlock state is now global so it can be destroyed when navigating away
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    // NEW: Global registry of censored category IDs populated by Home/VOD/Live ViewModels
    private val _censoredCategoryIds = MutableStateFlow<Set<String>>(emptySet())
    val censoredCategoryIds: StateFlow<Set<String>> = _censoredCategoryIds.asStateFlow()

    private val _requestUnlock = MutableStateFlow(0)
    val requestUnlock: StateFlow<Int> = _requestUnlock.asStateFlow()

    fun enterAdultMode() { _isAdultMode.value = true }
    fun exitAdultMode() { _isAdultMode.value = false }

    fun unlock() { _isUnlocked.value = true }

    // FIX: Destroys the adult session completely when navigating to Home, Movies, etc.
    fun lock() {
        _isAdultMode.value = false
        _isUnlocked.value = false
    }

    fun triggerUnlockPrompt() { _requestUnlock.value++ }

    fun updateCensoredCategories(ids: Set<String>) {
        _censoredCategoryIds.value = ids
    }

    fun isCategoryCensored(categoryId: String): Boolean {
        return _censoredCategoryIds.value.contains(categoryId)
    }
}