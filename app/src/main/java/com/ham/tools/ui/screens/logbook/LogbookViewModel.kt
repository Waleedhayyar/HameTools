package com.ham.tools.ui.screens.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ham.tools.data.model.Mode
import com.ham.tools.data.model.PropagationMode
import com.ham.tools.data.model.QslStatus
import com.ham.tools.data.model.QsoLog
import com.ham.tools.data.repository.QsoLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for the Add/Edit QSO form
 * 
 * Organized into basic (required) and extended (optional) sections
 */
data class QsoFormState(
    // ===== 基本信息 (必填) =====
    val callsign: String = "",
    val frequency: String = "",
    val mode: Mode = Mode.SSB,
    val rstSent: String = "59",
    val rstRcvd: String = "59",
    
    // ===== 对方信息 (选填) =====
    val opName: String = "",
    val qth: String = "",
    val gridLocator: String = "",
    val qslInfo: String = "",
    
    // ===== 我方信息 (选填) =====
    val txPower: String = "",
    val rig: String = "",
    
    // ===== 传播与确认 =====
    val propagation: PropagationMode = PropagationMode.UNKNOWN,
    val qslStatus: QslStatus = QslStatus.NOT_SENT,
    
    // ===== 备注 =====
    val remarks: String = "",
    
    // ===== 表单状态 =====
    val isValid: Boolean = false,
    val callsignError: String? = null,
    val frequencyError: String? = null,
    val showAdvanced: Boolean = false
)

/**
 * ViewModel for the Logbook screen
 * 
 * Manages the list of QSO logs and the form state for adding new entries
 */
@HiltViewModel
class LogbookViewModel @Inject constructor(
    private val repository: QsoLogRepository
) : ViewModel() {
    
    /**
     * All QSO logs from the database (reactive)
     */
    val logs: StateFlow<List<QsoLog>> = repository.getAllLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * Current form state for adding/editing QSO
     */
    private val _formState = MutableStateFlow(QsoFormState())
    val formState: StateFlow<QsoFormState> = _formState.asStateFlow()
    
    /**
     * Whether the bottom sheet is currently shown
     */
    private val _showBottomSheet = MutableStateFlow(false)
    val showBottomSheet: StateFlow<Boolean> = _showBottomSheet.asStateFlow()
    
    /**
     * Show the add QSO bottom sheet
     */
    fun showAddSheet() {
        _formState.value = QsoFormState()
        _showBottomSheet.value = true
    }
    
    /**
     * Hide the bottom sheet
     */
    fun hideSheet() {
        _showBottomSheet.value = false
    }
    
    /**
     * Toggle advanced options visibility
     */
    fun toggleAdvanced() {
        _formState.value = _formState.value.copy(
            showAdvanced = !_formState.value.showAdvanced
        )
    }
    
    // ===== 基本信息更新 =====
    
    fun updateCallsign(value: String) {
        val uppercaseValue = value.uppercase()
        _formState.value = _formState.value.copy(
            callsign = uppercaseValue,
            callsignError = if (uppercaseValue.isBlank()) "请输入呼号" else null
        )
        validateForm()
    }
    
    fun updateFrequency(value: String) {
        _formState.value = _formState.value.copy(
            frequency = value,
            frequencyError = if (value.isBlank()) "请输入频率" else null
        )
        validateForm()
    }
    
    fun updateMode(mode: Mode) {
        _formState.value = _formState.value.copy(mode = mode)
    }
    
    fun updateRstSent(value: String) {
        _formState.value = _formState.value.copy(rstSent = value)
    }
    
    fun updateRstRcvd(value: String) {
        _formState.value = _formState.value.copy(rstRcvd = value)
    }
    
    // ===== 对方信息更新 =====
    
    fun updateOpName(value: String) {
        _formState.value = _formState.value.copy(opName = value)
    }
    
    fun updateQth(value: String) {
        _formState.value = _formState.value.copy(qth = value)
    }
    
    fun updateGridLocator(value: String) {
        _formState.value = _formState.value.copy(gridLocator = value.uppercase())
    }
    
    fun updateQslInfo(value: String) {
        _formState.value = _formState.value.copy(qslInfo = value)
    }
    
    // ===== 我方信息更新 =====
    
    fun updateTxPower(value: String) {
        _formState.value = _formState.value.copy(txPower = value)
    }
    
    fun updateRig(value: String) {
        _formState.value = _formState.value.copy(rig = value)
    }
    
    // ===== 传播与确认更新 =====
    
    fun updatePropagation(mode: PropagationMode) {
        _formState.value = _formState.value.copy(propagation = mode)
    }
    
    fun updateQslStatus(status: QslStatus) {
        _formState.value = _formState.value.copy(qslStatus = status)
    }
    
    // ===== 备注更新 =====
    
    fun updateRemarks(value: String) {
        _formState.value = _formState.value.copy(remarks = value)
    }
    
    /**
     * Validate the form and update isValid state
     */
    private fun validateForm() {
        val state = _formState.value
        _formState.value = state.copy(
            isValid = state.callsign.isNotBlank() && state.frequency.isNotBlank()
        )
    }
    
    /**
     * Save the current form as a new QSO log
     */
    fun saveQso() {
        val state = _formState.value
        if (!state.isValid) return
        
        viewModelScope.launch {
            val qsoLog = QsoLog(
                callsign = state.callsign.trim().uppercase(),
                frequency = state.frequency.trim(),
                mode = state.mode,
                rstSent = state.rstSent.ifBlank { "59" },
                rstRcvd = state.rstRcvd.ifBlank { "59" },
                timestamp = System.currentTimeMillis(),
                opName = state.opName.takeIf { it.isNotBlank() },
                qth = state.qth.takeIf { it.isNotBlank() },
                gridLocator = state.gridLocator.takeIf { it.isNotBlank() },
                qslInfo = state.qslInfo.takeIf { it.isNotBlank() },
                txPower = state.txPower.takeIf { it.isNotBlank() },
                rig = state.rig.takeIf { it.isNotBlank() },
                propagation = state.propagation,
                qslStatus = state.qslStatus,
                remarks = state.remarks.takeIf { it.isNotBlank() }
            )
            repository.insertLog(qsoLog)
            hideSheet()
        }
    }
    
    /**
     * Delete a QSO log
     */
    fun deleteQso(qsoLog: QsoLog) {
        viewModelScope.launch {
            repository.deleteLog(qsoLog)
        }
    }
}
