package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AttendanceConstants
import com.example.data.model.AuthSession
import com.example.data.model.CustomStatusItem
import com.example.data.model.Person
import com.example.data.model.ReportFormatType
import com.example.data.model.ReportTemplateConfig
import com.example.data.model.UserRole
import com.example.data.model.WaktuApel
import com.example.data.repository.AttendanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

data class AttendanceUiState(
    val selectedDate: String = "",
    val selectedWaktuApel: WaktuApel = WaktuApel.PAGI,
    val attendanceData: Map<String, List<Person>> = emptyMap(),
    val customStatuses: List<CustomStatusItem> = emptyList(),
    val reportConfig: ReportTemplateConfig = ReportTemplateConfig(),
    val malamSiagaCompanionUnit: String = "TON I", // PIMPINAN, SIMA, TON I, TON II, TON III
    val searchQuery: String = "",
    val selectedPlatoonFilter: String? = null,
    val editingPerson: Pair<String, Person>? = null, // (groupKey, Person)
    val isManagingStatuses: Boolean = false,
    val editingStatusItem: CustomStatusItem? = null,
    val isEditingReportFormat: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isSyncDialogOpen: Boolean = false,
    val isManagePersonnelOpen: Boolean = false,
    val settingsInitialTab: Int = 0,
    val customAppName: String = "ABSEN KIKAV 2",
    val customAppLogoUri: String? = null,
    val hasApelPhoto: Boolean = false,
    val lastSavedTimestamp: String? = null,
    val hasUnsavedChanges: Boolean = false,
    val userNotification: String? = null,
    val isLoading: Boolean = false,
    // --- Login & Role State (Single Admin Login) ---
    val currentSession: AuthSession? = null,
    val isLoggedIn: Boolean = false,
    val loginError: String? = null,
    val adminUsername: String = "ADMIN"
)

private data class SlotLoadResult(
    val loaded: Map<String, List<Person>>,
    val savedAt: String?,
    val companionUnit: String,
    val hasPhoto: Boolean
)

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AttendanceRepository(application.applicationContext)

    private val initialDate = repository.getTodayDateWib()
    private val initialWaktu = WaktuApel.PAGI
    private val initialSession = repository.getCurrentSession()

    private val _uiState = MutableStateFlow(
        AttendanceUiState(
            selectedDate = initialDate,
            selectedWaktuApel = initialWaktu,
            attendanceData = repository.loadAttendance(initialDate, initialWaktu),
            customStatuses = repository.loadCustomStatuses(),
            reportConfig = repository.loadReportConfig(),
            malamSiagaCompanionUnit = repository.getMalamSiagaChosenUnit(initialDate),
            customAppName = repository.getCustomAppName(),
            customAppLogoUri = repository.getCustomAppLogoUri(),
            hasApelPhoto = repository.hasApelPhoto(initialDate, initialWaktu),
            lastSavedTimestamp = repository.getLastSavedTimestamp(initialDate, initialWaktu),
            currentSession = initialSession,
            isLoggedIn = initialSession?.isLoggedIn == true,
            adminUsername = repository.getAdminUsername()
        )
    )
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.verifyAndRepairAllData(initialDate)
        }
    }

    fun setDate(newDate: String) {
        if (_uiState.value.selectedDate == newDate) return
        val oldDate = _uiState.value.selectedDate
        val oldSlot = _uiState.value.selectedWaktuApel
        val currentData = _uiState.value.attendanceData
        val isDirty = _uiState.value.hasUnsavedChanges

        viewModelScope.launch {
            val (loaded, savedAt, companionUnit, hasPhoto) = withContext(Dispatchers.IO) {
                if (isDirty) {
                    repository.saveAttendance(oldDate, oldSlot, currentData)
                }
                val l = repository.loadAttendance(newDate, oldSlot)
                val s = repository.getLastSavedTimestamp(newDate, oldSlot)
                val c = repository.getMalamSiagaChosenUnit(newDate)
                val p = repository.hasApelPhoto(newDate, oldSlot)
                SlotLoadResult(l, s, c, p)
            }
            _uiState.update {
                it.copy(
                    selectedDate = newDate,
                    attendanceData = loaded,
                    malamSiagaCompanionUnit = companionUnit,
                    hasApelPhoto = hasPhoto,
                    lastSavedTimestamp = savedAt,
                    hasUnsavedChanges = false
                )
            }
        }
    }

    fun setWaktuApel(waktu: WaktuApel) {
        if (_uiState.value.selectedWaktuApel == waktu) return
        val oldDate = _uiState.value.selectedDate
        val oldSlot = _uiState.value.selectedWaktuApel
        val currentData = _uiState.value.attendanceData
        val isDirty = _uiState.value.hasUnsavedChanges

        viewModelScope.launch {
            val (loaded, savedAt, companionUnit, hasPhoto) = withContext(Dispatchers.IO) {
                if (isDirty) {
                    repository.saveAttendance(oldDate, oldSlot, currentData)
                }
                val l = repository.loadAttendance(oldDate, waktu)
                val s = repository.getLastSavedTimestamp(oldDate, waktu)
                val c = repository.getMalamSiagaChosenUnit(oldDate)
                val p = repository.hasApelPhoto(oldDate, waktu)
                SlotLoadResult(l, s, c, p)
            }
            _uiState.update {
                it.copy(
                    selectedWaktuApel = waktu,
                    attendanceData = loaded,
                    malamSiagaCompanionUnit = companionUnit,
                    hasApelPhoto = hasPhoto,
                    lastSavedTimestamp = savedAt,
                    hasUnsavedChanges = false,
                    selectedPlatoonFilter = null
                )
            }
        }
    }

    // --- Switch Malam Siaga Companion Platoon (KOKI, SIMA, TON I, TON II, TON III) ---
    fun setMalamSiagaCompanionUnit(unitKey: String) {
        if (_uiState.value.malamSiagaCompanionUnit == unitKey) return
        viewModelScope.launch {
            val updatedAttendance = repository.switchMalamSiagaUnit(
                date = _uiState.value.selectedDate,
                newUnitKey = unitKey,
                currentAttendance = _uiState.value.attendanceData
            )
            val displayName = AttendanceConstants.DISPLAY_NAMES[unitKey] ?: unitKey
            _uiState.update {
                it.copy(
                    malamSiagaCompanionUnit = unitKey,
                    attendanceData = updatedAttendance,
                    hasUnsavedChanges = false,
                    selectedPlatoonFilter = null,
                    userNotification = "Peleton Siaga diubah ke $displayName"
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setPlatoonFilter(filter: String?) {
        _uiState.update { it.copy(selectedPlatoonFilter = filter) }
    }

    fun openStatusEditor(groupKey: String, person: Person) {
        _uiState.update { it.copy(editingPerson = Pair(groupKey, person)) }
    }

    fun closeStatusEditor() {
        _uiState.update { it.copy(editingPerson = null) }
    }

    fun updateStatus(
        groupKey: String,
        personId: Int,
        newStatus: String,
        isKeteranganKhusus: Boolean? = null,
        personName: String? = null
    ) {
        val current = _uiState.value.attendanceData
        val targetName = personName ?: current[groupKey]?.find { it.id == personId }?.name
        val targetId = personId

        // Sinkronkan ke SEMUA grup/peleton di attendanceData jika nama atau ID cocok (misal: REMAJA dan TON I / SIMA)
        val updatedMap = current.mapValues { (_, list) ->
            list.map { p ->
                val matches = (p.id == targetId) || (targetName != null && AttendanceConstants.isSameSoldier(p.name, targetName))
                if (matches) {
                    val finalKhusus = isKeteranganKhusus ?: p.isKeteranganKhusus
                    p.copy(status = newStatus, isKeteranganKhusus = finalKhusus)
                } else {
                    p
                }
            }
        }.toMutableMap()

        val khususNote = if (isKeteranganKhusus == true) " [⭐ Keterangan Khusus]" else ""
        _uiState.update {
            it.copy(
                attendanceData = updatedMap,
                editingPerson = null,
                hasUnsavedChanges = false,
                userNotification = "Status ${targetName ?: "personel"} diubah ke $newStatus$khususNote"
            )
        }

        viewModelScope.launch {
            val timestamp = withContext(Dispatchers.IO) {
                repository.saveAttendance(
                    _uiState.value.selectedDate,
                    _uiState.value.selectedWaktuApel,
                    updatedMap
                )
                if (targetName != null && isKeteranganKhusus == true) {
                    repository.syncPersonStatusToAllSlots(
                        date = _uiState.value.selectedDate,
                        personName = targetName,
                        newStatus = newStatus,
                        isKhusus = isKeteranganKhusus
                    )
                }
                repository.getLastSavedTimestamp(_uiState.value.selectedDate, _uiState.value.selectedWaktuApel)
            }
            _uiState.update {
                it.copy(
                    lastSavedTimestamp = timestamp,
                    hasUnsavedChanges = false,
                    userNotification = "✓ Status ${targetName ?: "personel"} diubah ke $newStatus$khususNote (Tersimpan & Tersinkron)"
                )
            }
        }
    }

    /**
     * Mengatur apakah seorang personel memiliki status "Keterangan Khusus" (kebal reset apel).
     */
    fun togglePersonKeteranganKhusus(groupKey: String, personId: Int, isKhusus: Boolean) {
        val current = _uiState.value.attendanceData
        val targetPerson = current[groupKey]?.find { it.id == personId } ?: return
        val targetName = targetPerson.name

        val updatedMap = current.mapValues { (_, list) ->
            list.map { p ->
                val matches = (p.id == personId) || AttendanceConstants.isSameSoldier(p.name, targetName)
                if (matches) {
                    p.copy(isKeteranganKhusus = isKhusus)
                } else {
                    p
                }
            }
        }.toMutableMap()

        val note = if (isKhusus) "✓ $targetName dijadikan Keterangan Khusus (Kebal Reset Apel)" else "✓ Status khusus $targetName dinonaktifkan"
        _uiState.update {
            it.copy(
                attendanceData = updatedMap,
                hasUnsavedChanges = false,
                userNotification = note
            )
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.saveAttendance(
                    _uiState.value.selectedDate,
                    _uiState.value.selectedWaktuApel,
                    updatedMap
                )
                repository.syncPersonStatusToAllSlots(
                    date = _uiState.value.selectedDate,
                    personName = targetName,
                    newStatus = targetPerson.status,
                    isKhusus = isKhusus
                )
            }
        }
    }

    /**
     * Menghadirkan semua personel dalam satu peleton.
     * Jika [preserveSpecial] bernilai true, personel dengan Keterangan Khusus TIDAK akan ikut tereset.
     */
    fun markAllPresentInGroup(groupKey: String, preserveSpecial: Boolean = true) {
        val current = _uiState.value.attendanceData
        val groupList = current[groupKey] ?: return
        var preservedCount = 0
        val updatedGroup = groupList.map { p ->
            if (preserveSpecial && p.isKeteranganKhusus) {
                preservedCount++
                p
            } else {
                p.copy(status = "HADIR")
            }
        }
        val updatedMap = current.toMutableMap()
        updatedMap[groupKey] = updatedGroup

        val name = AttendanceConstants.DISPLAY_NAMES[groupKey] ?: groupKey
        val msg = if (preservedCount > 0) {
            "Personel $name diset HADIR ($preservedCount Keterangan Khusus dipertahankan)"
        } else {
            "Semua personel $name diset HADIR"
        }
        _uiState.update {
            it.copy(
                attendanceData = updatedMap,
                hasUnsavedChanges = false,
                userNotification = msg
            )
        }

        viewModelScope.launch {
            val timestamp = withContext(Dispatchers.IO) {
                repository.saveAttendance(
                    _uiState.value.selectedDate,
                    _uiState.value.selectedWaktuApel,
                    updatedMap
                )
                repository.getLastSavedTimestamp(_uiState.value.selectedDate, _uiState.value.selectedWaktuApel)
            }
            _uiState.update {
                it.copy(lastSavedTimestamp = timestamp, hasUnsavedChanges = false)
            }
        }
    }

    /**
     * Setel Ulang Kehadiran Satuan:
     * Menghadirkan seluruh prajurit, TETAPI mempertahankan personel yang berstatus "Keterangan Khusus".
     */
    fun markAllPresentEntireUnit(preserveSpecial: Boolean = true) {
        val current = _uiState.value.attendanceData
        var preservedCount = 0
        val updatedMap = mutableMapOf<String, List<Person>>()
        for ((groupKey, list) in current) {
            updatedMap[groupKey] = list.map { p ->
                if (preserveSpecial && p.isKeteranganKhusus) {
                    preservedCount++
                    p
                } else {
                    p.copy(status = "HADIR")
                }
            }
        }

        val countTotal = updatedMap.values.sumOf { it.size }
        val msg = if (preservedCount > 0) {
            "✓ Apel disetel ulang: Prajurit diset HADIR ($preservedCount Personel Keterangan Khusus tetap dipertahankan)!"
        } else {
            "Semua Personel ($countTotal Orang) diset HADIR Lengkap!"
        }
        _uiState.update {
            it.copy(
                attendanceData = updatedMap,
                hasUnsavedChanges = false,
                userNotification = msg
            )
        }

        viewModelScope.launch {
            val timestamp = withContext(Dispatchers.IO) {
                repository.saveAttendance(
                    _uiState.value.selectedDate,
                    _uiState.value.selectedWaktuApel,
                    updatedMap
                )
                repository.getLastSavedTimestamp(_uiState.value.selectedDate, _uiState.value.selectedWaktuApel)
            }
            _uiState.update {
                it.copy(lastSavedTimestamp = timestamp, hasUnsavedChanges = false)
            }
        }
    }

    // --- Explicit Save Data Feature ---
    fun saveDataExplicitly() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.saveAttendance(
                    _uiState.value.selectedDate,
                    _uiState.value.selectedWaktuApel,
                    _uiState.value.attendanceData
                )
            }
            val timestamp = withContext(Dispatchers.IO) {
                repository.getLastSavedTimestamp(
                    _uiState.value.selectedDate,
                    _uiState.value.selectedWaktuApel
                )
            }
            _uiState.update {
                it.copy(
                    lastSavedTimestamp = timestamp,
                    hasUnsavedChanges = false,
                    userNotification = "✓ Data Apel ${_uiState.value.selectedWaktuApel.label} Berhasil Disimpan!"
                )
            }
        }
    }

    // --- SINGLE SYNC BUTTON WORKFLOW ---
    fun openSyncDialog() {
        _uiState.update { it.copy(isSyncDialogOpen = true) }
    }

    fun closeSyncDialog() {
        _uiState.update { it.copy(isSyncDialogOpen = false) }
    }

    /**
     * EKSEKUSI SINKRONISASI TUNGGAL (HANYA APABILA DIKLIK USER):
     * Menyalin status/keterangan dari Apel Pagi ke Siang, Sore, dan Malam Siaga.
     * Jika tombol ini tidak diklik, data pada masing-masing sesi apel TIDAK AKAN tersinkron.
     */
    fun executeSingleSync() {
        viewModelScope.launch {
            val currentDate = _uiState.value.selectedDate
            val currentSlot = _uiState.value.selectedWaktuApel
            val pagiData = if (currentSlot == WaktuApel.PAGI) _uiState.value.attendanceData else null

            val syncedCount = withContext(Dispatchers.IO) {
                if (_uiState.value.hasUnsavedChanges && currentSlot != WaktuApel.PAGI) {
                    repository.saveAttendance(currentDate, currentSlot, _uiState.value.attendanceData)
                }
                repository.syncPagiToAllSlots(
                    date = currentDate,
                    currentPagiData = pagiData
                )
            }

            // Muat ulang data slot aktif saat ini agar UI langsung diperbarui
            val reloaded = withContext(Dispatchers.IO) {
                repository.loadAttendance(currentDate, currentSlot)
            }
            val timestamp = withContext(Dispatchers.IO) {
                repository.getLastSavedTimestamp(currentDate, currentSlot)
            }

            _uiState.update {
                it.copy(
                    attendanceData = reloaded,
                    lastSavedTimestamp = timestamp,
                    hasUnsavedChanges = false,
                    isSyncDialogOpen = false,
                    userNotification = "✓ Berhasil! Keterangan Apel Pagi diterapkan ke Apel Siang, Sore & Malam Siaga ($syncedCount status disinkronkan)."
                )
            }
        }
    }

    /**
     * SINKRONKAN SEMUA DATA DAN PERBAIKI FILE RUSAK
     * Memperbaiki dan merecovery data corrupt, menyelaraskan 4 sesi apel, serta memastikan atomic backup.
     */
    fun syncAllDataAndRepair() {
        viewModelScope.launch {
            val currentDate = _uiState.value.selectedDate
            val currentSlot = _uiState.value.selectedWaktuApel
            val currentData = _uiState.value.attendanceData

            val report = withContext(Dispatchers.IO) {
                repository.syncAllDataAndRepair(
                    date = currentDate,
                    activeSlot = currentSlot,
                    currentAttendance = currentData
                )
            }

            // Muat ulang data slot aktif setelah sinkronisasi & repair selesai
            val reloaded = withContext(Dispatchers.IO) {
                repository.loadAttendance(currentDate, currentSlot)
            }
            val timestamp = withContext(Dispatchers.IO) {
                repository.getLastSavedTimestamp(currentDate, currentSlot)
            }
            val refreshedStatuses = withContext(Dispatchers.IO) {
                repository.loadCustomStatuses()
            }
            val refreshedConfig = withContext(Dispatchers.IO) {
                repository.loadReportConfig()
            }

            val notificationMsg = buildString {
                append("✓ Sinkronisasi Selesai: ")
                append("${report.slotsSynced} sesi apel disinkronkan (${report.specialCarriedOver} keterangan khusus dipertahankan).")
                if (report.repairReport.repairedCount > 0) {
                    append(" Perbaikan: ${report.repairReport.repairedCount} file/komponen pulih.")
                }
            }

            _uiState.update {
                it.copy(
                    attendanceData = reloaded,
                    customStatuses = refreshedStatuses,
                    reportConfig = refreshedConfig,
                    lastSavedTimestamp = timestamp,
                    hasUnsavedChanges = false,
                    isSyncDialogOpen = false,
                    userNotification = notificationMsg
                )
            }
        }
    }

    /**
     * PEMERIKSAAN & PERBAIKAN FILE RUSAK MANDIRI
     * Memindai seluruh penyimpanan lokal dan memulihkan file yang corrupt tanpa mengubah input absensi yang valid.
     */
    fun repairCorruptedFiles() {
        viewModelScope.launch {
            val currentDate = _uiState.value.selectedDate
            val currentSlot = _uiState.value.selectedWaktuApel

            val repairReport = withContext(Dispatchers.IO) {
                repository.verifyAndRepairAllData(currentDate)
            }

            val reloaded = withContext(Dispatchers.IO) {
                repository.loadAttendance(currentDate, currentSlot)
            }
            val refreshedStatuses = withContext(Dispatchers.IO) {
                repository.loadCustomStatuses()
            }
            val refreshedConfig = withContext(Dispatchers.IO) {
                repository.loadReportConfig()
            }

            val summaryMsg = if (repairReport.repairedCount > 0) {
                "✓ Pemulihan Sukses: ${repairReport.repairedCount} file/komponen dipulihkan (${repairReport.repairedItems.joinToString(", ")})."
            } else {
                "✓ Pemeriksaan Selesai: Semua file sistem & data penyimpanan dalam kondisi aman dan sehat."
            }

            _uiState.update {
                it.copy(
                    attendanceData = reloaded,
                    customStatuses = refreshedStatuses,
                    reportConfig = refreshedConfig,
                    isSyncDialogOpen = false,
                    userNotification = summaryMsg
                )
            }
        }
    }

    /**
     * SINKRONISASI KETERANGAN APEL PAGI KE SIANG DAN SORE:
     * Menyalin semua keterangan/status hasil edit pada Apel Pagi ke Apel Siang & Apel Sore.
     */
    fun syncPagiToSiangDanSore() {
        viewModelScope.launch {
            val (syncedCount, timestamp) = withContext(Dispatchers.IO) {
                if (_uiState.value.selectedWaktuApel == WaktuApel.PAGI) {
                    repository.saveAttendance(
                        _uiState.value.selectedDate,
                        WaktuApel.PAGI,
                        _uiState.value.attendanceData
                    )
                }
                val count = repository.syncPagiToSiangDanSore(
                    date = _uiState.value.selectedDate,
                    pagiData = if (_uiState.value.selectedWaktuApel == WaktuApel.PAGI) _uiState.value.attendanceData else null
                )
                val ts = repository.getLastSavedTimestamp(
                    _uiState.value.selectedDate,
                    _uiState.value.selectedWaktuApel
                )
                Pair(count, ts)
            }
            _uiState.update {
                it.copy(
                    lastSavedTimestamp = timestamp,
                    hasUnsavedChanges = false,
                    userNotification = "✓ Keterangan Apel Pagi berhasil disinkronkan ke Apel Siang & Sore ($syncedCount status disesuaikan)!"
                )
            }
        }
    }

    /**
     * SINKRONISASI SLOT AKTIF (SIANG / SORE / MALAM SIAGA) DARI APEL PAGI:
     * Menerapkan status/keterangan yang sudah diedit di Apel Pagi ke slot yang sedang dibuka.
     */
    fun syncCurrentSlotFromPagi() {
        viewModelScope.launch {
            val targetSlot = _uiState.value.selectedWaktuApel
            val (updated, timestamp) = withContext(Dispatchers.IO) {
                val up = repository.syncSlotFromPagi(
                    date = _uiState.value.selectedDate,
                    targetSlot = targetSlot,
                    currentSlotAttendance = _uiState.value.attendanceData
                )
                val ts = if (up != null) {
                    repository.getLastSavedTimestamp(_uiState.value.selectedDate, targetSlot)
                } else null
                Pair(up, ts)
            }
            if (updated != null) {
                _uiState.update {
                    it.copy(
                        attendanceData = updated,
                        lastSavedTimestamp = timestamp,
                        hasUnsavedChanges = false,
                        userNotification = "✓ Keterangan Apel ${targetSlot.label} berhasil disinkronkan dari Apel Pagi!"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(userNotification = "Data Apel Pagi belum tersedia untuk disinkronkan")
                }
            }
        }
    }

    /**
     * Menyetel ulang formasi absensi ke kondisi awal, dengan opsi mempertahankan Keterangan Khusus.
     */
    fun resetDataForCurrentSlot(preserveSpecial: Boolean = true) {
        viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) {
                repository.getInitialAttendanceForSlot(
                    _uiState.value.selectedWaktuApel,
                    _uiState.value.malamSiagaCompanionUnit
                )
            }
            val currentData = _uiState.value.attendanceData
            val specialMap = mutableMapOf<String, Person>()
            if (preserveSpecial) {
                for ((_, list) in currentData) {
                    for (p in list) {
                        if (p.isKeteranganKhusus) {
                            specialMap[AttendanceConstants.normalizeMilitaryName(p.name)] = p
                        }
                    }
                }
            }

            val preserved = fresh.mapValues { (_, list) ->
                list.map { p ->
                    val norm = AttendanceConstants.normalizeMilitaryName(p.name)
                    val special = specialMap[norm]
                    if (special != null) {
                        p.copy(status = special.status, isKeteranganKhusus = true)
                    } else p
                }
            }

            val msg = if (specialMap.isNotEmpty()) {
                "Data apel disetel ulang (${specialMap.size} Keterangan Khusus dipertahankan)"
            } else {
                "Data direset ke formasi awal"
            }

            _uiState.update {
                it.copy(
                    attendanceData = preserved,
                    hasUnsavedChanges = false,
                    userNotification = msg
                )
            }
            withContext(Dispatchers.IO) {
                repository.saveAttendance(
                    _uiState.value.selectedDate,
                    _uiState.value.selectedWaktuApel,
                    preserved
                )
            }
        }
    }

    /**
     * HAPUS SEMUA DATA KEHADIRAN (RESET TOTAL):
     * Menghapus seluruh rekaman kehadiran tersimpan, foto dokumentasi, dan mereset formasi ke kondisi bersih.
     */
    fun clearAllData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.clearAllAttendanceData()
            }
            val currentDate = repository.getTodayDateWib()
            val currentSlot = WaktuApel.PAGI
            val fresh = withContext(Dispatchers.IO) {
                repository.getInitialAttendanceForSlot(currentSlot)
            }
            _uiState.update {
                it.copy(
                    selectedDate = currentDate,
                    selectedWaktuApel = currentSlot,
                    attendanceData = fresh,
                    hasApelPhoto = false,
                    lastSavedTimestamp = null,
                    hasUnsavedChanges = false,
                    userNotification = "✓ SEMUA DATA BERHASIL DIHAPUS. Aplikasi kembali bersih ke formasi awal!"
                )
            }
            onComplete()
        }
    }

    // --- Custom Status Actions ---
    fun openManageStatuses() {
        _uiState.update { it.copy(isManagingStatuses = true) }
    }

    fun closeManageStatuses() {
        _uiState.update { it.copy(isManagingStatuses = false, editingStatusItem = null) }
    }

    fun renameOrSaveStatus(oldCode: String, newCode: String, newDescription: String) {
        val (updatedStatuses, updatedAttendance) = repository.renameOrUpdateStatus(
            oldCode = oldCode,
            newCode = newCode,
            newDescription = newDescription,
            currentDate = _uiState.value.selectedDate,
            currentWaktuApel = _uiState.value.selectedWaktuApel,
            currentAttendance = _uiState.value.attendanceData
        )
        _uiState.update {
            it.copy(
                customStatuses = updatedStatuses,
                attendanceData = updatedAttendance,
                editingStatusItem = null,
                userNotification = "Keterangan '$newCode' berhasil disimpan"
            )
        }
    }

    fun addNewStatus(code: String, description: String) {
        val updated = repository.addCustomStatus(code, description)
        _uiState.update {
            it.copy(
                customStatuses = updated,
                editingStatusItem = null,
                userNotification = "Keterangan '$code' ditambahkan"
            )
        }
    }

    fun deleteStatus(code: String) {
        val updated = repository.deleteCustomStatus(code)
        _uiState.update {
            it.copy(
                customStatuses = updated,
                userNotification = "Keterangan '$code' dihapus"
            )
        }
    }

    fun resetStatusesToDefault() {
        val updated = repository.resetStatusesToDefault()
        _uiState.update {
            it.copy(
                customStatuses = updated,
                userNotification = "Daftar keterangan dikembalikan ke bawaan"
            )
        }
    }

    // --- Custom Report Template Actions ---
    fun setReportFormatType(type: ReportFormatType) {
        val updated = _uiState.value.reportConfig.copy(formatType = type)
        repository.saveReportConfig(updated)
        _uiState.update { it.copy(reportConfig = updated) }
    }

    fun openEditReportFormat() {
        _uiState.update { it.copy(isEditingReportFormat = true) }
    }

    fun closeEditReportFormat() {
        _uiState.update { it.copy(isEditingReportFormat = false) }
    }

    fun saveReportTemplateConfig(config: ReportTemplateConfig) {
        repository.saveReportConfig(config)
        _uiState.update {
            it.copy(
                reportConfig = config,
                isEditingReportFormat = false,
                userNotification = "Format template laporan berhasil diperbarui"
            )
        }
    }

    fun getFormattedReportText(): String {
        return repository.generateFormattedReport(
            date = _uiState.value.selectedDate,
            waktuApel = _uiState.value.selectedWaktuApel,
            data = _uiState.value.attendanceData,
            customStatuses = _uiState.value.customStatuses,
            config = _uiState.value.reportConfig
        )
    }

    fun getExportJson(): String {
        return repository.getExportJson(_uiState.value.attendanceData)
    }

    fun exportToWordFile(): File {
        return repository.exportToWordFile(
            date = _uiState.value.selectedDate,
            waktuApel = _uiState.value.selectedWaktuApel,
            data = _uiState.value.attendanceData,
            customStatuses = _uiState.value.customStatuses,
            config = _uiState.value.reportConfig
        )
    }

    fun getShareWordIntent(file: File): Intent {
        return repository.createShareWordIntent(file)
    }

    fun getOpenWordIntent(file: File): Intent {
        return repository.createOpenWordIntent(file)
    }

    fun openSettings(initialTab: Int = 0) {
        _uiState.update { it.copy(isSettingsOpen = true, settingsInitialTab = initialTab) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    // --- File Update: Export and Import Configuration JSON ---
    fun exportConfigJson(): File {
        return repository.exportAppConfigJson(
            currentDate = _uiState.value.selectedDate,
            currentWaktuApel = _uiState.value.selectedWaktuApel,
            currentAttendance = _uiState.value.attendanceData
        )
    }

    fun exportConfigJsonString(): String {
        return repository.exportAppConfigJsonString(
            currentDate = _uiState.value.selectedDate,
            currentWaktuApel = _uiState.value.selectedWaktuApel,
            currentAttendance = _uiState.value.attendanceData
        )
    }

    fun getShareConfigIntent(file: File): Intent {
        return repository.createShareConfigIntent(file)
    }

    fun getShareConfigTextIntent(jsonText: String): Intent {
        return repository.createShareConfigTextIntent(jsonText)
    }

    fun importConfigFromJson(jsonStr: String): AttendanceRepository.ImportResult {
        val result = repository.importAppConfigFromJson(jsonStr)
        if (result.success) {
            val curDate = _uiState.value.selectedDate
            // Harmonize all 4 slots for the active date so switching slots is 100% instant and synchronized
            for (slot in listOf(WaktuApel.PAGI, WaktuApel.SIANG, WaktuApel.SORE, WaktuApel.MALAM_SIAGA)) {
                repository.harmonizeSlotAttendance(curDate, slot)
            }
            val freshStatuses = repository.loadCustomStatuses()
            val freshReportConfig = repository.loadReportConfig()
            val freshAttendance = repository.loadAttendance(
                curDate,
                _uiState.value.selectedWaktuApel
            )
            val savedAt = repository.getLastSavedTimestamp(
                curDate,
                _uiState.value.selectedWaktuApel
            )
            val freshAppName = repository.getCustomAppName()
            val freshAppLogo = repository.getCustomAppLogoUri()
            _uiState.update {
                it.copy(
                    customStatuses = freshStatuses,
                    reportConfig = freshReportConfig,
                    attendanceData = freshAttendance,
                    customAppName = freshAppName,
                    customAppLogoUri = freshAppLogo,
                    lastSavedTimestamp = savedAt,
                    hasUnsavedChanges = false,
                    userNotification = result.message
                )
            }
        } else {
            _uiState.update {
                it.copy(userNotification = result.message)
            }
        }
        return result
    }

    // --- Photo Documentation Feature ---
    fun getApelPhotoFile(): File {
        return repository.getApelPhotoFile(_uiState.value.selectedDate, _uiState.value.selectedWaktuApel)
    }

    fun saveApelPhoto(inputStream: java.io.InputStream): Boolean {
        return try {
            val file = repository.saveApelPhoto(_uiState.value.selectedDate, _uiState.value.selectedWaktuApel, inputStream)
            val success = file.exists() && file.length() > 0
            if (success) {
                _uiState.update { it.copy(hasApelPhoto = true, userNotification = "Foto dokumentasi apel berhasil disimpan!") }
            }
            success
        } catch (e: Exception) {
            false
        }
    }

    fun deleteApelPhoto(): Boolean {
        val success = repository.deleteApelPhoto(_uiState.value.selectedDate, _uiState.value.selectedWaktuApel)
        if (success) {
            _uiState.update { it.copy(hasApelPhoto = false, userNotification = "Foto dokumentasi apel dihapus") }
        }
        return success
    }

    fun getShareReportWithPhotoIntent(reportText: String): Intent {
        val photoFile = if (_uiState.value.hasApelPhoto) getApelPhotoFile() else null
        return repository.createShareReportWithPhotoIntent(reportText, photoFile)
    }

    // --- Personnel Management Feature (Add / Delete with Auto Sync) ---
    fun openManagePersonnel() {
        _uiState.update { it.copy(isManagePersonnelOpen = true) }
    }

    fun closeManagePersonnel() {
        _uiState.update { it.copy(isManagePersonnelOpen = false) }
    }

    fun getAllPlatoonRoster(): Map<String, List<String>> {
        return repository.getPlatoonRoster()
    }

    fun getRemajaRoster(): List<String> {
        return repository.getRemajaRoster()
    }

    fun addMember(groupKey: String, name: String, isRemaja: Boolean) {
        val trimmed = name.trim().uppercase()
        if (trimmed.isBlank()) return

        // Sync into active attendanceData
        val currentAttendance = _uiState.value.attendanceData.toMutableMap()
        if (currentAttendance.containsKey(groupKey)) {
            val list = currentAttendance[groupKey]?.toMutableList() ?: mutableListOf()
            if (list.none { it.name.equals(trimmed, ignoreCase = true) }) {
                val nextId = (currentAttendance.values.flatten().maxOfOrNull { it.id } ?: 0) + 1
                list.add(Person(id = nextId, name = trimmed, status = "HADIR"))
                currentAttendance[groupKey] = list
            }
        }
        if (isRemaja && currentAttendance.containsKey("REMAJA")) {
            val remajaList = currentAttendance["REMAJA"]?.toMutableList() ?: mutableListOf()
            if (remajaList.none { it.name.equals(trimmed, ignoreCase = true) }) {
                val nextId = (currentAttendance.values.flatten().maxOfOrNull { it.id } ?: 0) + 1
                remajaList.add(Person(id = nextId, name = trimmed, status = "HADIR"))
                currentAttendance["REMAJA"] = remajaList
            }
        }

        _uiState.update {
            it.copy(
                attendanceData = currentAttendance,
                userNotification = "Anggota $trimmed berhasil ditambahkan ke $groupKey!"
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.addMemberToRoster(groupKey, trimmed, isRemaja)
            repository.saveAttendance(
                _uiState.value.selectedDate,
                _uiState.value.selectedWaktuApel,
                currentAttendance
            )
            for (slot in listOf(WaktuApel.PAGI, WaktuApel.SIANG, WaktuApel.SORE, WaktuApel.MALAM_SIAGA)) {
                if (slot != _uiState.value.selectedWaktuApel) {
                    repository.harmonizeSlotAttendance(_uiState.value.selectedDate, slot)
                }
            }
        }
    }

    fun removeMember(groupKey: String, name: String) {
        val trimmed = name.trim()

        // Sync out of active attendanceData
        val currentAttendance = _uiState.value.attendanceData.toMutableMap()
        if (currentAttendance.containsKey(groupKey)) {
            val list = currentAttendance[groupKey]?.filterNot { it.name.equals(trimmed, ignoreCase = true) } ?: emptyList()
            currentAttendance[groupKey] = list
        }
        if (currentAttendance.containsKey("REMAJA")) {
            val remajaList = currentAttendance["REMAJA"]?.filterNot { it.name.equals(trimmed, ignoreCase = true) } ?: emptyList()
            currentAttendance["REMAJA"] = remajaList
        }

        _uiState.update {
            it.copy(
                attendanceData = currentAttendance,
                userNotification = "Anggota $trimmed berhasil dihapus!"
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.removeMemberFromRoster(groupKey, trimmed)
            repository.saveAttendance(
                _uiState.value.selectedDate,
                _uiState.value.selectedWaktuApel,
                currentAttendance
            )
            for (slot in listOf(WaktuApel.PAGI, WaktuApel.SIANG, WaktuApel.SORE, WaktuApel.MALAM_SIAGA)) {
                if (slot != _uiState.value.selectedWaktuApel) {
                    repository.harmonizeSlotAttendance(_uiState.value.selectedDate, slot)
                }
            }
        }
    }

    // --- App Identity & Logo Customization ---
    fun updateAppIdentity(name: String, logoUri: String?) {
        repository.setCustomAppName(name)
        repository.setCustomAppLogoUri(logoUri)
        _uiState.update {
            it.copy(
                customAppName = name,
                customAppLogoUri = logoUri,
                userNotification = "Identitas aplikasi dan logo berhasil diperbarui!"
            )
        }
    }

    fun resetAppIdentity() {
        repository.setCustomAppName("ABSEN KIKAV 2")
        repository.setCustomAppLogoUri(null)
        _uiState.update {
            it.copy(
                customAppName = "ABSEN KIKAV 2",
                customAppLogoUri = null,
                userNotification = "Identitas dan logo direset ke default KIKAV 2!"
            )
        }
    }

    fun clearNotification() {
        _uiState.update { it.copy(userNotification = null, loginError = null) }
    }

    // --- Authentication & Session Management ---
    fun login(usernameInput: String, passwordInput: String): Boolean {
        val session = repository.authenticate(usernameInput, passwordInput)
        if (session != null) {
            repository.saveSession(session)
            _uiState.update {
                it.copy(
                    currentSession = session,
                    isLoggedIn = true,
                    loginError = null,
                    userNotification = "Selamat bertugas, ${session.displayName}!"
                )
            }
            return true
        } else {
            val err = "Username atau Password tidak valid! Silakan periksa kembali akun Anda."
            _uiState.update {
                it.copy(loginError = err)
            }
            return false
        }
    }

    fun changeAdminCredentials(newUsername: String, newPassword: String): Boolean {
        if (newUsername.isBlank() || newPassword.isBlank()) {
            _uiState.update { it.copy(userNotification = "Username dan Password tidak boleh kosong!") }
            return false
        }
        val success = repository.setAdminCredentials(newUsername.trim(), newPassword.trim())
        if (success) {
            _uiState.update {
                it.copy(
                    adminUsername = newUsername.trim(),
                    userNotification = "Username dan Password Admin berhasil diperbarui!"
                )
            }
        } else {
            _uiState.update { it.copy(userNotification = "Gagal memperbarui kredensial.") }
        }
        return success
    }

    fun autoSaveData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.saveAttendance(
                    _uiState.value.selectedDate,
                    _uiState.value.selectedWaktuApel,
                    _uiState.value.attendanceData
                )
            }
            val timestamp = withContext(Dispatchers.IO) {
                repository.getLastSavedTimestamp(
                    _uiState.value.selectedDate,
                    _uiState.value.selectedWaktuApel
                )
            }
            _uiState.update {
                it.copy(
                    lastSavedTimestamp = timestamp,
                    hasUnsavedChanges = false
                )
            }
        }
    }

    fun logout() {
        val date = _uiState.value.selectedDate
        val slot = _uiState.value.selectedWaktuApel
        val data = _uiState.value.attendanceData
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveAttendance(date, slot, data)
            repository.logout()
        }
        _uiState.update {
            it.copy(
                currentSession = null,
                isLoggedIn = false,
                loginError = null,
                hasUnsavedChanges = false,
                userNotification = "✓ Data otomatis tersimpan. Silakan login kembali dengan Username & Password."
            )
        }
    }
}
