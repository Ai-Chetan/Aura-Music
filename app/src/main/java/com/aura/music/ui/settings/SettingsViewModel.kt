package com.aura.music.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.network.GateSnapshot
import com.aura.music.data.network.NetworkGate
import com.aura.music.data.prefs.DataUsagePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: DataUsagePreferences,
    gate: NetworkGate
) : ViewModel() {

    val gateState: StateFlow<GateSnapshot> = gate.state

    val useMobileData: StateFlow<Boolean> = prefs.useMobileData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setUseMobileData(use: Boolean) {
        viewModelScope.launch { prefs.setUseMobileData(use) }
    }
}
