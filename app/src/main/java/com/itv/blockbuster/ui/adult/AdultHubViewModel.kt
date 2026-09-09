package com.itv.blockbuster.ui.adult

import androidx.lifecycle.ViewModel
import com.itv.blockbuster.data.session.StalkerSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AdultHubViewModel @Inject constructor(
    private val sessionManager: StalkerSessionManager
) : ViewModel() {
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _passwordError = MutableStateFlow<String?>(null)
    val passwordError: StateFlow<String?> = _passwordError.asStateFlow()

    fun verifyPassword(enteredPassword: String) {
        val correctPassword = sessionManager.parentPassword.value
        val expected = if (correctPassword.isBlank()) "0000" else correctPassword
        if (enteredPassword == expected) {
            _isUnlocked.value = true
            _passwordError.value = null
        } else {
            _passwordError.value = "Incorrect password"
        }
    }
}