package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.AttendanceConstants
import com.example.data.model.AuthSession
import com.example.data.model.CustomStatusItem
import com.example.data.model.Person
import com.example.data.model.ReportFormatType
import com.example.data.model.ReportTemplateConfig
import com.example.data.model.UserRole
import com.example.data.model.WaktuApel
import com.example.util.DateUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AttendanceRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("absen_apel_prefs_v2", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_CUSTOM_STATUSES = "custom_status_definitions_v4"
        private const val PREF_REPORT_CONFIG = "custom_report_template_config_v1"
        private const val PREF_USER_SESSION = "app_user_session_v1"
        private const val PREF_ADMIN_USERNAME = "custom_admin_username_v1"
        private const val PREF_ADMIN_PASSWORD = "custom_admin_password_v1"
    }

    // --- Crash-Safe Atomic Internal Storage Persistence ---
    private fun writeAtomically(targetFile: File, content: String) {
        try {
            val parent = targetFile.parentFile ?: context.filesDir
            if (!parent.exists()) parent.mkdirs()
            val tempFile = File(parent, "${targetFile.name}.tmp")
            FileOutputStream(tempFile).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.flush()
            }
            if (tempFile.exists()) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tempFile.renameTo(targetFile)
            }
        } catch (e: Exception) {
            try {
                targetFile.writeText(content, Charsets.UTF_8)
            } catch (_: Exception) {}
        }
    }

    // --- Authentication & Custom Admin Credentials ---
    fun getAdminUsername(): String {
        return prefs.getString(PREF_ADMIN_USERNAME, "ADMIN") ?: "ADMIN"
    }

    fun getAdminPassword(): String {
        return prefs.getString(PREF_ADMIN_PASSWORD, "ADMIN32") ?: "ADMIN32"
    }

    fun setAdminCredentials(username: String, password: String): Boolean {
        val trimmedUser = username.trim()
        val trimmedPass = password.trim()
        if (trimmedUser.isBlank() || trimmedPass.isBlank()) return false
        prefs.edit()
            .putString(PREF_ADMIN_USERNAME, trimmedUser)
            .putString(PREF_ADMIN_PASSWORD, trimmedPass)
            .apply()
        return true
    }

    fun authenticate(usernameInput: String, passwordInput: String): AuthSession? {
        val userTrim = usernameInput.trim()
        val passTrim = passwordInput.trim()

        val expectedUser = getAdminUsername()
        val expectedPass = getAdminPassword()

        return if (userTrim.equals(expectedUser, ignoreCase = true) && passTrim == expectedPass) {
            AuthSession(
                username = expectedUser,
                role = UserRole.ADMIN,
                displayName = "ADMIN KIKAV 2",
                isLoggedIn = true
            )
        } else {
            null
        }
    }

    fun getCurrentSession(): AuthSession? {
        val json = prefs.getString(PREF_USER_SESSION, null) ?: return null
        return try {
            val obj = JSONObject(json)
            AuthSession(
                username = obj.optString("username", getAdminUsername()),
                role = UserRole.ADMIN,
                displayName = obj.optString("displayName", "ADMIN KIKAV 2"),
                isLoggedIn = obj.optBoolean("isLoggedIn", true)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveSession(session: AuthSession?) {
        if (session == null) {
            prefs.edit().remove(PREF_USER_SESSION).apply()
        } else {
            val obj = JSONObject()
            obj.put("username", session.username)
            obj.put("role", UserRole.ADMIN.name)
            obj.put("displayName", session.displayName)
            obj.put("isLoggedIn", session.isLoggedIn)
            prefs.edit().putString(PREF_USER_SESSION, obj.toString()).apply()
        }
    }

    fun logout() {
        saveSession(null)
    }

    fun getTodayDateWib(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("GMT+7")
        return sdf.format(Date())
    }

    fun getCurrentTimeWib(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("GMT+7")
        return "${sdf.format(Date())} WIB"
    }

    // --- Custom Status Management with Safe Dual-Layer Persistence ---
    fun loadCustomStatuses(): List<CustomStatusItem> {
        val backupFile = File(context.filesDir, "backup_custom_statuses.json")
        var jsonStr = prefs.getString(PREF_CUSTOM_STATUSES, null)
        var resultList: List<CustomStatusItem>? = null

        if (jsonStr != null) {
            try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<CustomStatusItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        CustomStatusItem(
                            code = obj.getString("code"),
                            description = obj.optString("description", ""),
                            isDefault = obj.optBoolean("isDefault", false)
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    resultList = list
                }
            } catch (_: Exception) {
                resultList = null // SharedPreferences corrupt!
            }
        }

        // Jika SharedPreferences kosong atau rusak, pulihkan otomatis dari backup internal storage
        if (resultList == null && backupFile.exists()) {
            try {
                val backupText = backupFile.readText(Charsets.UTF_8)
                val arr = JSONArray(backupText)
                val list = mutableListOf<CustomStatusItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        CustomStatusItem(
                            code = obj.getString("code"),
                            description = obj.optString("description", ""),
                            isDefault = obj.optBoolean("isDefault", false)
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    resultList = list
                    // Self-healing: pulihkan kembali ke SharedPreferences
                    prefs.edit().putString(PREF_CUSTOM_STATUSES, backupText).commit()
                }
            } catch (_: Exception) {
                resultList = null
            }
        }

        val finalStatuses = resultList ?: getDefaultStatusItems()
        if (resultList == null) {
            saveCustomStatuses(finalStatuses)
        }
        return finalStatuses
    }

    private fun getDefaultStatusItems(): List<CustomStatusItem> {
        return AttendanceConstants.DEFAULT_STATUS_LIST.map { code ->
            CustomStatusItem(
                code = code,
                description = "",
                isDefault = true
            )
        }
    }

    fun saveCustomStatuses(statuses: List<CustomStatusItem>) {
        val arr = JSONArray()
        for (item in statuses) {
            val obj = JSONObject()
            obj.put("code", item.code)
            obj.put("description", item.description)
            obj.put("isDefault", item.isDefault)
            arr.put(obj)
        }
        val jsonString = arr.toString()
        prefs.edit().putString(PREF_CUSTOM_STATUSES, jsonString).commit()

        // Cadangan internal mandiri atomik (Safety Dual-Layer Persistence)
        val backupFile = File(context.filesDir, "backup_custom_statuses.json")
        writeAtomically(backupFile, jsonString)
    }

    fun renameOrUpdateStatus(
        oldCode: String,
        newCode: String,
        newDescription: String,
        currentDate: String,
        currentWaktuApel: WaktuApel,
        currentAttendance: Map<String, List<Person>>
    ): Pair<List<CustomStatusItem>, Map<String, List<Person>>> {
        val currentStatuses = loadCustomStatuses().toMutableList()
        val index = currentStatuses.indexOfFirst { it.code.equals(oldCode, ignoreCase = true) }
        val formattedCode = newCode.uppercase().trim()
        val formattedDesc = newDescription.trim()

        val updatedItem = CustomStatusItem(
            code = formattedCode,
            description = formattedDesc,
            isDefault = AttendanceConstants.DEFAULT_STATUS_LIST.contains(formattedCode)
        )

        if (index >= 0) {
            currentStatuses[index] = updatedItem
        } else {
            currentStatuses.add(updatedItem)
        }
        saveCustomStatuses(currentStatuses)

        val updatedAttendance = if (!oldCode.equals(newCode, ignoreCase = true)) {
            val updatedMap = mutableMapOf<String, List<Person>>()
            for ((group, persons) in currentAttendance) {
                updatedMap[group] = persons.map { p ->
                    if (p.status.equals(oldCode, ignoreCase = true)) {
                        p.copy(status = formattedCode)
                    } else p
                }
            }
            saveAttendance(currentDate, currentWaktuApel, updatedMap)
            updatedMap
        } else {
            currentAttendance
        }

        return Pair(currentStatuses, updatedAttendance)
    }

    fun addCustomStatus(code: String, description: String): List<CustomStatusItem> {
        val current = loadCustomStatuses().toMutableList()
        val formattedCode = code.uppercase().trim()
        val existingIndex = current.indexOfFirst { it.code.equals(formattedCode, ignoreCase = true) }
        val formattedDesc = description.trim()
        val item = CustomStatusItem(
            code = formattedCode,
            description = formattedDesc,
            isDefault = AttendanceConstants.DEFAULT_STATUS_LIST.contains(formattedCode)
        )
        if (existingIndex >= 0) {
            current[existingIndex] = item
        } else {
            current.add(item)
        }
        saveCustomStatuses(current)
        return current
    }

    fun deleteCustomStatus(code: String): List<CustomStatusItem> {
        val current = loadCustomStatuses().toMutableList()
        current.removeAll { it.code.equals(code, ignoreCase = true) }
        saveCustomStatuses(current)
        return current
    }

    fun resetStatusesToDefault(): List<CustomStatusItem> {
        val defaults = getDefaultStatusItems()
        saveCustomStatuses(defaults)
        return defaults
    }

    // --- Dynamic Greeting for All Report Formats ---
    fun getSalamPembukaForWaktu(waktuApel: WaktuApel, customSalam: String = ""): String {
        val trimmed = customSalam.trim()
        if (trimmed.isNotBlank()) {
            if (trimmed.equals("SELAMAT PAGI KOMANDAN, IZIN MELAPORKAN HASIL APEL PAGI PENGECEKAN PERSONEL", ignoreCase = true) ||
                trimmed.equals("SELAMAT PAGI KOMANDAN,IZIN MELAPORKAN HASIL APEL PAGI PENGECEKAN PERSONEL", ignoreCase = true)) {
                return when (waktuApel) {
                    WaktuApel.PAGI -> "SELAMAT PAGI KOMANDAN, IZIN MELAPORKAN HASIL APEL PAGI PENGECEKAN PERSONEL"
                    WaktuApel.SIANG -> "SELAMAT SIANG KOMANDAN, IZIN MELAPORKAN HASIL APEL SIANG PENGECEKAN PERSONEL"
                    WaktuApel.SORE -> "SELAMAT SORE KOMANDAN, IZIN MELAPORKAN HASIL APEL SORE PENGECEKAN PERSONEL"
                    WaktuApel.MALAM_SIAGA -> "SELAMAT MALAM KOMANDAN, IZIN MELAPORKAN HASIL APEL MALAM SIAGA PENGECEKAN PERSONEL"
                }
            }
            return trimmed
        }
        return when (waktuApel) {
            WaktuApel.PAGI -> "SELAMAT PAGI KOMANDAN, IZIN MELAPORKAN HASIL APEL PAGI PENGECEKAN PERSONEL"
            WaktuApel.SIANG -> "SELAMAT SIANG KOMANDAN, IZIN MELAPORKAN HASIL APEL SIANG PENGECEKAN PERSONEL"
            WaktuApel.SORE -> "SELAMAT SORE KOMANDAN, IZIN MELAPORKAN HASIL APEL SORE PENGECEKAN PERSONEL"
            WaktuApel.MALAM_SIAGA -> "SELAMAT MALAM KOMANDAN, IZIN MELAPORKAN HASIL APEL MALAM SIAGA PENGECEKAN PERSONEL"
        }
    }

    // --- Report Template Configuration Persistence with Safe Dual-Layer Persistence ---
    private fun parseReportConfigJson(jsonStr: String): ReportTemplateConfig {
        val obj = JSONObject(jsonStr)
        val formatStr = obj.optString("formatType", ReportFormatType.STANDAR_KOMANDO.name)
        val format = try {
            ReportFormatType.valueOf(formatStr)
        } catch (e: Exception) {
            ReportFormatType.STANDAR_KOMANDO
        }
        val ketPa = obj.optString("ketPaJaga", "PA JAGA").trim().ifBlank { "PA JAGA" }
        val pa = obj.optString("namaPaJaga", obj.optString("namaPerwiraPiket", "")).trim()
        val ba = obj.optString("namaBaJaga", obj.optString("namaBintaraPiket", "")).trim()
        val ta = obj.optString("namaTaJaga", "").trim()

        return ReportTemplateConfig(
            formatType = format,
            headerKomando = obj.optString("headerKomando", AttendanceConstants.SATUAN_KOMANDO),
            headerSatuan = obj.optString("headerSatuan", AttendanceConstants.SATUAN_UNIT),
            reportTitle = obj.optString("reportTitle", "LAPORAN ABSEN APEL"),
            reportSubtitle = obj.optString("reportSubtitle", "PENGECEKAN PERSONEL KIKAV 2/JRTR"),
            salamPembuka = obj.optString("salamPembuka", ""),
            salamPenutup = obj.optString("salamPenutup", "Demikian dilaporkan, tertanda Piket Siaga KIKAV 2/JRTR."),
            wordHeaderFontSize = obj.optInt("wordHeaderFontSize", 16),
            wordTableFontSize = obj.optDouble("wordTableFontSize", 6.5).toFloat(),
            wordPersonnelFontSize = obj.optDouble("wordPersonnelFontSize", 7.0).toFloat(),
            labelKuatNyata = obj.optString("labelKuatNyata", "Kuat Nyata"),
            labelHadir = obj.optString("labelHadir", "Hadir"),
            labelKurang = obj.optString("labelKurang", "Kurang"),
            customTemplateText = obj.optString("customTemplateText", ""),
            ketPaJaga = ketPa,
            namaPaJaga = if (pa.contains("ARYA PRATAMA", ignoreCase = true)) "" else pa,
            namaBaJaga = if (ba.contains("WAHYU HIDAYAT", ignoreCase = true)) "" else ba,
            namaTaJaga = ta,
            namaPerwiraPiket = pa,
            jabatanPerwiraPiket = ketPa,
            namaBintaraPiket = ba,
            jabatanBintaraPiket = "BA JAGA",
            lokasiTandaTangan = obj.optString("lokasiTandaTangan", "").let { if (it.equals("Ambarawa", ignoreCase = true)) "" else it.trim() }
        )
    }

    fun loadReportConfig(): ReportTemplateConfig {
        val backupFile = File(context.filesDir, "backup_report_config.json")
        var jsonStr = prefs.getString(PREF_REPORT_CONFIG, null)
        var config: ReportTemplateConfig? = null

        if (jsonStr != null) {
            try {
                config = parseReportConfigJson(jsonStr)
            } catch (_: Exception) {
                config = null // SharedPreferences corrupt!
            }
        }

        if (config == null && backupFile.exists()) {
            try {
                val backupText = backupFile.readText(Charsets.UTF_8)
                config = parseReportConfigJson(backupText)
                if (config != null) {
                    prefs.edit().putString(PREF_REPORT_CONFIG, backupText).commit()
                }
            } catch (_: Exception) {
                config = null
            }
        }

        val finalConfig = config ?: ReportTemplateConfig()
        if (config == null) {
            saveReportConfig(finalConfig)
        }
        return finalConfig
    }

    fun saveReportConfig(config: ReportTemplateConfig) {
        val obj = JSONObject()
        obj.put("formatType", config.formatType.name)
        obj.put("headerKomando", config.headerKomando)
        obj.put("headerSatuan", config.headerSatuan)
        obj.put("reportTitle", config.reportTitle)
        obj.put("reportSubtitle", config.reportSubtitle)
        obj.put("salamPembuka", config.salamPembuka)
        obj.put("salamPenutup", config.salamPenutup)
        obj.put("wordHeaderFontSize", config.wordHeaderFontSize)
        obj.put("wordTableFontSize", config.wordTableFontSize.toDouble())
        obj.put("wordPersonnelFontSize", config.wordPersonnelFontSize.toDouble())
        obj.put("labelKuatNyata", config.labelKuatNyata)
        obj.put("labelHadir", config.labelHadir)
        obj.put("labelKurang", config.labelKurang)
        obj.put("customTemplateText", config.customTemplateText)
        obj.put("ketPaJaga", config.ketPaJaga)
        obj.put("namaPaJaga", config.namaPaJaga)
        obj.put("namaBaJaga", config.namaBaJaga)
        obj.put("namaTaJaga", config.namaTaJaga)
        obj.put("namaPerwiraPiket", config.namaPaJaga)
        obj.put("jabatanPerwiraPiket", config.ketPaJaga)
        obj.put("namaBintaraPiket", config.namaBaJaga)
        obj.put("jabatanBintaraPiket", "BA JAGA")
        obj.put("lokasiTandaTangan", config.lokasiTandaTangan)

        val jsonString = obj.toString()
        prefs.edit().putString(PREF_REPORT_CONFIG, jsonString).commit()

        // Cadangan internal mandiri atomik (Safety Dual-Layer Persistence)
        val backupFile = File(context.filesDir, "backup_report_config.json")
        writeAtomically(backupFile, jsonString)
    }

    // --- Platoon and Remaja Personnel Roster Persistence with Safe Dual-Layer Storage ---
    fun getPlatoonRoster(): Map<String, List<String>> {
        val backupFile = File(context.filesDir, "backup_platoon_roster.json")
        var jsonStr = prefs.getString("custom_platoon_roster_v2", null)
        var resultMap: Map<String, List<String>>? = null

        if (jsonStr != null) {
            try {
                val obj = JSONObject(jsonStr)
                val map = linkedMapOf<String, List<String>>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val arr = obj.getJSONArray(key)
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        list.add(arr.getString(i))
                    }
                    map[key] = list
                }
                if (map.isNotEmpty()) {
                    resultMap = map
                }
            } catch (_: Exception) {
                resultMap = null // Corrupt!
            }
        }

        if (resultMap == null && backupFile.exists()) {
            try {
                val backupText = backupFile.readText(Charsets.UTF_8)
                val obj = JSONObject(backupText)
                val map = linkedMapOf<String, List<String>>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val arr = obj.getJSONArray(key)
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        list.add(arr.getString(i))
                    }
                    map[key] = list
                }
                if (map.isNotEmpty()) {
                    resultMap = map
                    prefs.edit().putString("custom_platoon_roster_v2", backupText).commit()
                }
            } catch (_: Exception) {
                resultMap = null
            }
        }

        val finalMap = resultMap ?: AttendanceConstants.INITIAL_DATA
        if (resultMap == null) {
            savePlatoonRoster(finalMap)
        }
        return finalMap
    }

    fun getRemajaRoster(): List<String> {
        val backupFile = File(context.filesDir, "backup_remaja_roster.json")
        var jsonStr = prefs.getString("custom_remaja_roster_v2", null)
        var resultList: List<String>? = null

        if (jsonStr != null) {
            try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                if (list.isNotEmpty()) {
                    resultList = list
                }
            } catch (_: Exception) {
                resultList = null
            }
        }

        if (resultList == null && backupFile.exists()) {
            try {
                val backupText = backupFile.readText(Charsets.UTF_8)
                val arr = JSONArray(backupText)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                if (list.isNotEmpty()) {
                    resultList = list
                    prefs.edit().putString("custom_remaja_roster_v2", backupText).commit()
                }
            } catch (_: Exception) {
                resultList = null
            }
        }

        val finalList = resultList ?: AttendanceConstants.REMAJA_DATA
        if (resultList == null) {
            saveRemajaRoster(finalList)
        }
        return finalList
    }

    fun savePlatoonRoster(roster: Map<String, List<String>>) {
        val obj = JSONObject()
        for ((k, v) in roster) {
            val arr = JSONArray(v)
            obj.put(k, arr)
        }
        val jsonString = obj.toString()
        prefs.edit().putString("custom_platoon_roster_v2", jsonString).commit()

        // Simpan cadangan aman atomik ke internal storage
        val backupFile = File(context.filesDir, "backup_platoon_roster.json")
        writeAtomically(backupFile, jsonString)
    }

    fun saveRemajaRoster(remaja: List<String>) {
        val arr = JSONArray(remaja)
        val jsonString = arr.toString()
        prefs.edit().putString("custom_remaja_roster_v2", jsonString).commit()

        // Simpan cadangan aman atomik ke internal storage
        val backupFile = File(context.filesDir, "backup_remaja_roster.json")
        writeAtomically(backupFile, jsonString)
    }

    fun addMemberToRoster(groupKey: String, name: String, isRemaja: Boolean): Boolean {
        val trimmed = name.uppercase().trim()
        if (trimmed.isBlank()) return false

        val currentPlatoons = getPlatoonRoster().toMutableMap()
        val list = (currentPlatoons[groupKey] ?: emptyList()).toMutableList()
        if (!list.contains(trimmed)) {
            list.add(trimmed)
            currentPlatoons[groupKey] = list
            savePlatoonRoster(currentPlatoons)
        }

        if (isRemaja) {
            val remajaList = getRemajaRoster().toMutableList()
            if (!remajaList.contains(trimmed)) {
                remajaList.add(trimmed)
                saveRemajaRoster(remajaList)
            }
        }
        return true
    }

    fun removeMemberFromRoster(groupKey: String, name: String): Boolean {
        val trimmed = name.uppercase().trim()
        val currentPlatoons = getPlatoonRoster().toMutableMap()
        val list = (currentPlatoons[groupKey] ?: emptyList()).toMutableList()
        val removed = list.removeAll { it.equals(trimmed, ignoreCase = true) }
        if (removed) {
            currentPlatoons[groupKey] = list
            savePlatoonRoster(currentPlatoons)
        }

        val remajaList = getRemajaRoster().toMutableList()
        val removedRemaja = remajaList.removeAll { it.equals(trimmed, ignoreCase = true) }
        if (removedRemaja) {
            saveRemajaRoster(remajaList)
        }

        return removed || removedRemaja
    }

    // --- App Custom Identity & Logo with Safe Dual-Layer Persistence ---
    fun getCustomAppName(): String {
        var name = prefs.getString("custom_app_display_name_v1", null)
        if (name.isNullOrBlank()) {
            val backup = File(context.filesDir, "backup_app_name.txt")
            if (backup.exists()) {
                try {
                    name = backup.readText(Charsets.UTF_8).trim()
                } catch (_: Exception) {}
            }
        }
        return if (!name.isNullOrBlank()) name else "ABSEN KIKAV 2"
    }

    fun setCustomAppName(name: String) {
        val trimmed = name.trim()
        prefs.edit().putString("custom_app_display_name_v1", trimmed).apply()
        try {
            val backup = File(context.filesDir, "backup_app_name.txt")
            backup.writeText(trimmed, Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun getCustomAppLogoUri(): String? {
        var uriStr = prefs.getString("custom_app_logo_uri_v1", null)
        if (uriStr.isNullOrBlank()) {
            val backup = File(context.filesDir, "backup_app_logo.txt")
            if (backup.exists()) {
                try {
                    uriStr = backup.readText(Charsets.UTF_8).trim().ifBlank { null }
                } catch (_: Exception) {}
            }
        }
        return uriStr
    }

    fun setCustomAppLogoUri(uriStr: String?) {
        if (uriStr == null) {
            prefs.edit().remove("custom_app_logo_uri_v1").apply()
            try {
                val backup = File(context.filesDir, "backup_app_logo.txt")
                if (backup.exists()) backup.delete()
            } catch (_: Exception) {}
        } else {
            prefs.edit().putString("custom_app_logo_uri_v1", uriStr).apply()
            try {
                val backup = File(context.filesDir, "backup_app_logo.txt")
                backup.writeText(uriStr, Charsets.UTF_8)
            } catch (_: Exception) {}
        }
    }

    // --- Photo Documentation for Reports ---
    fun getApelPhotoFile(date: String, waktuApel: WaktuApel): File {
        val photosDir = File(context.filesDir, "apel_photos")
        if (!photosDir.exists()) photosDir.mkdirs()
        val safeWaktu = waktuApel.name
        return File(photosDir, "photo_${date}_${safeWaktu}.jpg")
    }

    fun hasApelPhoto(date: String, waktuApel: WaktuApel): Boolean {
        val file = getApelPhotoFile(date, waktuApel)
        return file.exists() && file.length() > 0
    }

    fun saveApelPhoto(date: String, waktuApel: WaktuApel, inputStream: java.io.InputStream): File {
        val file = getApelPhotoFile(date, waktuApel)
        FileOutputStream(file).use { out ->
            inputStream.copyTo(out)
        }
        return file
    }

    fun deleteApelPhoto(date: String, waktuApel: WaktuApel): Boolean {
        val file = getApelPhotoFile(date, waktuApel)
        return if (file.exists()) file.delete() else true
    }

    fun createShareReportWithPhotoIntent(reportText: String, photoFile: File?): Intent {
        return if (photoFile != null && photoFile.exists()) {
            val uri = getFileUri(photoFile)
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, reportText)
                putExtra(Intent.EXTRA_SUBJECT, "Laporan Absen Apel KIKAV 2")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, reportText)
                putExtra(Intent.EXTRA_SUBJECT, "Laporan Absen Apel KIKAV 2")
            }
        }
    }

    // --- Hapus Semua Data Kehadiran (Wipe All Attendance Data) ---
    fun clearAllAttendanceData(): Int {
        var clearedCount = 0
        val editor = prefs.edit()
        val allKeys = prefs.all.keys.toList()
        for (key in allKeys) {
            if (key.contains("_PAGI") ||
                key.contains("_SIANG") ||
                key.contains("_SORE") ||
                key.contains("_MALAM_SIAGA") ||
                key.contains("_chosen_unit")
            ) {
                editor.remove(key)
                clearedCount++
            }
        }
        editor.apply()

        // Hapus file cadangan lokal absensi
        try {
            val backupFiles = context.filesDir.listFiles { _, name ->
                name.startsWith("backup_") && (
                    name.contains("_PAGI") ||
                    name.contains("_SIANG") ||
                    name.contains("_SORE") ||
                    name.contains("_MALAM_SIAGA")
                )
            }
            backupFiles?.forEach { it.delete() }
        } catch (_: Exception) {}

        // Hapus seluruh foto dokumentasi apel
        try {
            val photosDir = File(context.filesDir, "apel_photos")
            if (photosDir.exists()) {
                photosDir.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Exception) {}

        // Hapus cache dokumen laporan
        try {
            val docsDir = File(context.cacheDir, "docs")
            if (docsDir.exists()) {
                docsDir.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Exception) {}

        return clearedCount
    }

    // --- Attendance Data Persistence & Apel Malam Siaga Support ---
    fun getMalamSiagaChosenUnit(date: String): String {
        return prefs.getString("${date}_MALAM_SIAGA_chosen_unit", "TON I") ?: "TON I"
    }

    fun setMalamSiagaChosenUnit(date: String, unitKey: String) {
        prefs.edit().putString("${date}_MALAM_SIAGA_chosen_unit", unitKey).apply()
    }

    fun getInitialAttendanceForSlot(waktuApel: WaktuApel, chosenUnit: String = "TON I"): Map<String, List<Person>> {
        val data = linkedMapOf<String, List<Person>>()
        val currentPlatoons = getPlatoonRoster()
        val currentRemaja = getRemajaRoster()

        if (waktuApel == WaktuApel.MALAM_SIAGA) {
            var counter = 1
            // 1. Data REMAJA (Personel Tetap)
            val remajaList = currentRemaja.map { name ->
                Person(id = counter++, name = name, status = "HADIR")
            }
            data["REMAJA"] = remajaList

            // 2. Data Peleton Pilihan Siaga (KOKI, SIMA, TON I, TON II, TON III)
            val companionKey = if (AttendanceConstants.SIAGA_OPTIONS.contains(chosenUnit)) chosenUnit else "TON I"
            val companionNames = currentPlatoons[companionKey]
                ?: currentPlatoons["TON I"]
                ?: AttendanceConstants.INITIAL_DATA["TON I"]!!
            val companionList = companionNames.map { name ->
                Person(id = counter++, name = name, status = "HADIR")
            }
            data[companionKey] = companionList
        } else {
            var counter = 1
            for ((group, names) in currentPlatoons) {
                val list = mutableListOf<Person>()
                for (name in names) {
                    list.add(Person(id = counter++, name = name, status = "HADIR"))
                }
                data[group] = list
            }
        }
        return data
    }

    fun getInitialAttendance(): Map<String, List<Person>> = getInitialAttendanceForSlot(WaktuApel.PAGI)

    private fun storageKey(date: String, waktuApel: WaktuApel): String {
        return "${date}_${waktuApel.name}"
    }

    private fun parsePersonArray(arr: JSONArray): List<Person> {
        val persons = mutableListOf<Person>()
        for (i in 0 until arr.length()) {
            val pObj = arr.getJSONObject(i)
            persons.add(
                Person(
                    id = pObj.optInt("id", i + 1),
                    name = pObj.optString("name", "PERSONEL"),
                    status = pObj.optString("status", "HADIR"),
                    isKeteranganKhusus = pObj.optBoolean("isKeteranganKhusus", false)
                )
            )
        }
        return persons
    }

    private fun parseAttendanceFromJsonString(
        jsonStr: String,
        waktuApel: WaktuApel,
        chosenUnit: String,
        date: String? = null
    ): Map<String, List<Person>> {
        val result = linkedMapOf<String, List<Person>>()
        val jsonObject = JSONObject(jsonStr)

        val rosterPlatoons = getPlatoonRoster()
        val rosterRemaja = getRemajaRoster()

        if (waktuApel == WaktuApel.MALAM_SIAGA) {
            // 1. Load REMAJA
            if (jsonObject.has("REMAJA")) {
                result["REMAJA"] = parsePersonArray(jsonObject.getJSONArray("REMAJA"))
            } else {
                result["REMAJA"] = rosterRemaja.mapIndexed { idx, name ->
                    Person(id = idx + 1, name = name, status = "HADIR")
                }
            }

            // 2. Load chosen companion unit
            var companionUnitKey = chosenUnit
            if (!jsonObject.has(companionUnitKey)) {
                val detected = AttendanceConstants.SIAGA_OPTIONS.firstOrNull { jsonObject.has(it) }
                if (detected != null) {
                    companionUnitKey = detected
                    if (date != null) {
                        setMalamSiagaChosenUnit(date, companionUnitKey)
                    }
                }
            }

            if (jsonObject.has(companionUnitKey)) {
                result[companionUnitKey] = parsePersonArray(jsonObject.getJSONArray(companionUnitKey))
            } else {
                val defaultList = rosterPlatoons[companionUnitKey] ?: AttendanceConstants.INITIAL_DATA[companionUnitKey] ?: emptyList()
                result[companionUnitKey] = defaultList.mapIndexed { idx, name ->
                    Person(id = 100 + idx + 1, name = name, status = "HADIR")
                }
            }
        } else {
            val allKeys = linkedSetOf<String>()
            allKeys.addAll(rosterPlatoons.keys)
            val jsonKeys = jsonObject.keys()
            while (jsonKeys.hasNext()) {
                val k = jsonKeys.next()
                if (k != "REMAJA") allKeys.add(k)
            }
            for (groupKey in allKeys) {
                if (jsonObject.has(groupKey)) {
                    result[groupKey] = parsePersonArray(jsonObject.getJSONArray(groupKey))
                } else {
                    val defaultList = rosterPlatoons[groupKey] ?: AttendanceConstants.INITIAL_DATA[groupKey] ?: emptyList()
                    result[groupKey] = defaultList.mapIndexed { idx, name ->
                        Person(id = idx + 1, name = name, status = "HADIR")
                    }
                }
            }
        }
        return result
    }

    fun harmonizeAttendanceWithRoster(
        currentData: Map<String, List<Person>>,
        waktuApel: WaktuApel,
        chosenUnit: String = "TON I"
    ): Map<String, List<Person>> {
        val currentPlatoons = getPlatoonRoster()
        val currentRemaja = getRemajaRoster()
        val harmonized = linkedMapOf<String, List<Person>>()
        var idCounter = 1

        if (waktuApel == WaktuApel.MALAM_SIAGA) {
            // 1. REMAJA
            val existingRemaja = currentData["REMAJA"] ?: emptyList()
            val statusByNormRemaja = existingRemaja.associate {
                AttendanceConstants.normalizeMilitaryName(it.name) to Pair(it.status, it.isKeteranganKhusus)
            }
            val newRemajaList = currentRemaja.map { name ->
                val norm = AttendanceConstants.normalizeMilitaryName(name)
                val (st, isKhusus) = statusByNormRemaja[norm] ?: Pair("HADIR", false)
                Person(id = idCounter++, name = name, status = st, isKeteranganKhusus = isKhusus)
            }
            harmonized["REMAJA"] = newRemajaList

            // 2. Companion unit (KOKI, SIMA, TON I, TON II, TON III)
            val companionKey = if (AttendanceConstants.SIAGA_OPTIONS.contains(chosenUnit)) chosenUnit else "TON I"
            val existingCompanion = currentData[companionKey] ?: emptyList()
            val statusByNormCompanion = existingCompanion.associate {
                AttendanceConstants.normalizeMilitaryName(it.name) to Pair(it.status, it.isKeteranganKhusus)
            }
            val companionNames = currentPlatoons[companionKey]
                ?: AttendanceConstants.INITIAL_DATA[companionKey]
                ?: emptyList()
            val newCompanionList = companionNames.map { name ->
                val norm = AttendanceConstants.normalizeMilitaryName(name)
                val (st, isKhusus) = statusByNormCompanion[norm] ?: Pair("HADIR", false)
                Person(id = idCounter++, name = name, status = st, isKeteranganKhusus = isKhusus)
            }
            harmonized[companionKey] = newCompanionList
        } else {
            // Reguler: KOKI, SIMA, TON I, TON II, TON III, etc.
            val activeKeys = currentPlatoons.keys.ifEmpty { AttendanceConstants.INITIAL_DATA.keys }
            for (groupKey in activeKeys) {
                val existingGroup = currentData[groupKey] ?: emptyList()
                val statusByNorm = existingGroup.associate {
                    AttendanceConstants.normalizeMilitaryName(it.name) to Pair(it.status, it.isKeteranganKhusus)
                }
                val names = currentPlatoons[groupKey]
                    ?: AttendanceConstants.INITIAL_DATA[groupKey]
                    ?: emptyList()
                val updatedList = names.map { name ->
                    val norm = AttendanceConstants.normalizeMilitaryName(name)
                    val (st, isKhusus) = statusByNorm[norm] ?: Pair("HADIR", false)
                    Person(id = idCounter++, name = name, status = st, isKeteranganKhusus = isKhusus)
                }
                harmonized[groupKey] = updatedList
            }
        }
        return harmonized
    }

    fun loadAttendance(date: String, waktuApel: WaktuApel): Map<String, List<Person>> {
        val key = storageKey(date, waktuApel)
        val chosenUnit = if (waktuApel == WaktuApel.MALAM_SIAGA) getMalamSiagaChosenUnit(date) else ""
        val backupFile = File(context.filesDir, "backup_${key}.json")
        var jsonStr = prefs.getString(key, null)
        var rawData: Map<String, List<Person>>? = null

        // 1. Coba baca dari SharedPreferences
        if (jsonStr != null) {
            try {
                rawData = parseAttendanceFromJsonString(jsonStr, waktuApel, chosenUnit, date)
            } catch (_: Exception) {
                rawData = null // Data di prefs corrupt!
            }
        }

        // 2. Jika prefs kosong atau corrupt, pulihkan otomatis dari backup internal storage
        if (rawData == null && backupFile.exists()) {
            try {
                val backupText = backupFile.readText(Charsets.UTF_8)
                rawData = parseAttendanceFromJsonString(backupText, waktuApel, chosenUnit, date)
                if (rawData.isNotEmpty()) {
                    // Pulihkan kembali ke SharedPreferences
                    prefs.edit().putString(key, backupText).commit()
                }
            } catch (_: Exception) {
                rawData = null
            }
        }

        // 3. Fallback ke initial data jika backup juga belum ada
        val finalRaw = rawData ?: getInitialAttendanceForSlot(waktuApel, chosenUnit)
        val harmonized = harmonizeAttendanceWithRoster(finalRaw, waktuApel, chosenUnit)

        // Self-heal storage jika data sebelumnya rusak/kosong
        if (rawData == null) {
            saveAttendance(date, waktuApel, harmonized)
        }

        return harmonized
    }

    /**
     * Memastikan data absensi di slot tertentu diselaraskan dengan roster terbaru
     * (menambahkan personel baru sebagai HADIR, membuang yang dihapus, dan mempertahankan status yang ada).
     */
    fun harmonizeSlotAttendance(date: String, waktuApel: WaktuApel): Map<String, List<Person>> {
        val current = loadAttendance(date, waktuApel)
        val chosenUnit = if (waktuApel == WaktuApel.MALAM_SIAGA) getMalamSiagaChosenUnit(date) else ""
        val harmonized = harmonizeAttendanceWithRoster(current, waktuApel, chosenUnit)
        saveAttendance(date, waktuApel, harmonized)
        return harmonized
    }

    /**
     * SATU-SATUNYA LOGIKA SINKRONISASI UTAMA:
     * Hanya dieksekusi apabila user secara eksplisit menekan tombol SINKRONKAN.
     * Menyalin seluruh status/keterangan personel hasil edit pada APEL PAGI
     * ke dalam APEL SIANG, APEL SORE, dan APEL MALAM SIAGA pada tanggal yang sama.
     */
    fun syncPagiToAllSlots(
        date: String,
        currentPagiData: Map<String, List<Person>>? = null
    ): Int {
        val sourcePagi = currentPagiData ?: loadAttendance(date, WaktuApel.PAGI)
        saveAttendance(date, WaktuApel.PAGI, sourcePagi)

        val statusByName = mutableMapOf<String, Pair<String, Boolean>>()
        val statusByNormName = mutableMapOf<String, Pair<String, Boolean>>()
        for ((_, persons) in sourcePagi) {
            for (p in persons) {
                statusByName[p.name] = Pair(p.status, p.isKeteranganKhusus)
                val norm = AttendanceConstants.normalizeMilitaryName(p.name)
                statusByNormName[norm] = Pair(p.status, p.isKeteranganKhusus)
            }
        }

        val nonHadirCount = sourcePagi.values.flatten().count { it.status != "HADIR" }

        // 1. Sinkronkan ke APEL SIANG dan APEL SORE
        for (targetSlot in listOf(WaktuApel.SIANG, WaktuApel.SORE)) {
            val targetAttendance = loadAttendance(date, targetSlot)
            val updatedMap = linkedMapOf<String, List<Person>>()
            for ((groupKey, persons) in targetAttendance) {
                val updatedPersons = persons.map { person ->
                    val norm = AttendanceConstants.normalizeMilitaryName(person.name)
                    val sourceData = statusByNormName[norm] ?: statusByName[person.name]
                    if (sourceData != null) {
                        person.copy(status = sourceData.first, isKeteranganKhusus = sourceData.second)
                    } else {
                        person
                    }
                }
                updatedMap[groupKey] = updatedPersons
            }
            saveAttendance(date, targetSlot, updatedMap)
        }

        // 2. Sinkronkan ke APEL MALAM SIAGA (REMAJA + Peleton Siaga yang dipilih)
        val malamAttendance = loadAttendance(date, WaktuApel.MALAM_SIAGA)
        val updatedMalamMap = linkedMapOf<String, List<Person>>()
        for ((groupKey, persons) in malamAttendance) {
            val updatedPersons = persons.map { person ->
                val norm = AttendanceConstants.normalizeMilitaryName(person.name)
                val sourceData = statusByNormName[norm] ?: statusByName[person.name]
                if (sourceData != null) {
                    person.copy(status = sourceData.first, isKeteranganKhusus = sourceData.second)
                } else {
                    person
                }
            }
            updatedMalamMap[groupKey] = updatedPersons
        }
        saveAttendance(date, WaktuApel.MALAM_SIAGA, updatedMalamMap)

        return nonHadirCount
    }

    /**
     * SINKRONISASI KETERANGAN SATU PERSONEL KE SEMUA SESI APEL PADA TANGGAL YANG SAMA:
     * Memastikan ketika keterangan seorang prajurit diubah (misal: SAKIT/DL/CUTI),
     * status prajurit tersebut otomatis tersinkron ke Apel Pagi, Siang, Sore & Malam Siaga.
     */
    fun syncPersonStatusToAllSlots(
        date: String,
        personName: String,
        newStatus: String,
        isKhusus: Boolean? = null
    ) {
        val normTarget = AttendanceConstants.normalizeMilitaryName(personName)
        for (slot in listOf(WaktuApel.PAGI, WaktuApel.SIANG, WaktuApel.SORE, WaktuApel.MALAM_SIAGA)) {
            val key = storageKey(date, slot)
            val jsonStr = prefs.getString(key, null)
            val chosenUnit = if (slot == WaktuApel.MALAM_SIAGA) getMalamSiagaChosenUnit(date) else ""
            val attendance = if (jsonStr != null) {
                try {
                    parseAttendanceFromJsonString(jsonStr, slot, chosenUnit, date)
                } catch (e: Exception) {
                    getInitialAttendanceForSlot(slot, chosenUnit)
                }
            } else {
                getInitialAttendanceForSlot(slot, chosenUnit)
            }

            var changed = false
            val updatedMap = linkedMapOf<String, List<Person>>()
            for ((group, persons) in attendance) {
                val updatedList = persons.map { p ->
                    val normP = AttendanceConstants.normalizeMilitaryName(p.name)
                    if (normP == normTarget || AttendanceConstants.isSameSoldier(p.name, personName)) {
                        val targetKhusus = isKhusus ?: p.isKeteranganKhusus
                        if (p.status != newStatus || p.isKeteranganKhusus != targetKhusus) {
                            changed = true
                            p.copy(status = newStatus, isKeteranganKhusus = targetKhusus)
                        } else p
                    } else p
                }
                updatedMap[group] = updatedList
            }

            if (changed) {
                saveAttendance(date, slot, updatedMap)
            }
        }
    }

    /**
     * SINKRONISASI KETERANGAN:
     * Menyalin semua status/keterangan personel hasil edit pada APEL PAGI
     * ke dalam data APEL SIANG dan APEL SORE pada tanggal yang sama.
     */
    fun syncPagiToSiangDanSore(
        date: String,
        pagiData: Map<String, List<Person>>? = null
    ): Int {
        val sourcePagi = pagiData ?: loadAttendance(date, WaktuApel.PAGI)
        saveAttendance(date, WaktuApel.PAGI, sourcePagi)

        val statusByName = mutableMapOf<String, Pair<String, Boolean>>()
        val statusByNormName = mutableMapOf<String, Pair<String, Boolean>>()
        for ((_, persons) in sourcePagi) {
            for (p in persons) {
                statusByName[p.name] = Pair(p.status, p.isKeteranganKhusus)
                val norm = AttendanceConstants.normalizeMilitaryName(p.name)
                statusByNormName[norm] = Pair(p.status, p.isKeteranganKhusus)
            }
        }

        val nonHadirCount = sourcePagi.values.flatten().count { it.status != "HADIR" }

        for (targetSlot in listOf(WaktuApel.SIANG, WaktuApel.SORE)) {
            val targetKey = storageKey(date, targetSlot)
            val jsonStr = prefs.getString(targetKey, null)
            val targetAttendance = if (jsonStr != null) {
                try {
                    parseAttendanceFromJsonString(jsonStr, targetSlot, "", date)
                } catch (e: Exception) {
                    getInitialAttendanceForSlot(targetSlot)
                }
            } else {
                getInitialAttendanceForSlot(targetSlot)
            }

            val updatedMap = linkedMapOf<String, List<Person>>()
            for ((groupKey, persons) in targetAttendance) {
                val updatedPersons = persons.map { person ->
                    val norm = AttendanceConstants.normalizeMilitaryName(person.name)
                    val sourceData = statusByNormName[norm] ?: statusByName[person.name]
                    if (sourceData != null) {
                        person.copy(status = sourceData.first, isKeteranganKhusus = sourceData.second)
                    } else {
                        person
                    }
                }
                updatedMap[groupKey] = updatedPersons
            }
            saveAttendance(date, targetSlot, updatedMap)
        }

        return nonHadirCount
    }

    /**
     * Sinkronkan slot aktif (SIANG, SORE, atau MALAM SIAGA)
     * menggunakan data keterangan yang ada di APEL PAGI.
     */
    fun syncSlotFromPagi(
        date: String,
        targetSlot: WaktuApel,
        currentSlotAttendance: Map<String, List<Person>>? = null
    ): Map<String, List<Person>>? {
        val pagiKey = storageKey(date, WaktuApel.PAGI)
        val pagiJson = prefs.getString(pagiKey, null)
        val sourcePagi = if (pagiJson != null) {
            try {
                parseAttendanceFromJsonString(pagiJson, WaktuApel.PAGI, "", date)
            } catch (e: Exception) {
                loadAttendance(date, WaktuApel.PAGI)
            }
        } else {
            loadAttendance(date, WaktuApel.PAGI)
        }

        val statusByName = mutableMapOf<String, String>()
        val statusByNormName = mutableMapOf<String, String>()
        for ((_, persons) in sourcePagi) {
            for (p in persons) {
                statusByName[p.name] = p.status
                val norm = AttendanceConstants.normalizeMilitaryName(p.name)
                statusByNormName[norm] = p.status
            }
        }

        val targetAttendance = currentSlotAttendance ?: loadAttendance(date, targetSlot)
        val updatedMap = linkedMapOf<String, List<Person>>()

        for ((groupKey, persons) in targetAttendance) {
            val updatedPersons = persons.map { person ->
                val norm = AttendanceConstants.normalizeMilitaryName(person.name)
                val sourceStatus = statusByNormName[norm] ?: statusByName[person.name]
                if (sourceStatus != null) {
                    person.copy(status = sourceStatus)
                } else {
                    person
                }
            }
            updatedMap[groupKey] = updatedPersons
        }

        saveAttendance(date, targetSlot, updatedMap)
        return updatedMap
    }

    fun switchMalamSiagaUnit(
        date: String,
        newUnitKey: String,
        currentAttendance: Map<String, List<Person>>
    ): Map<String, List<Person>> {
        setMalamSiagaChosenUnit(date, newUnitKey)
        val updatedMap = linkedMapOf<String, List<Person>>()

        // Keep REMAJA list and statuses as is
        updatedMap["REMAJA"] = currentAttendance["REMAJA"] ?: AttendanceConstants.REMAJA_DATA.mapIndexed { idx, name ->
            Person(id = idx + 1, name = name, status = "HADIR")
        }

        // Add the chosen companion unit (e.g. TON I, TON II, etc.) and sync status from Apel Pagi
        val pagiAttendance = loadAttendance(date, WaktuApel.PAGI)
        val pagiStatusByName = mutableMapOf<String, String>()
        for ((_, pList) in pagiAttendance) {
            for (p in pList) {
                pagiStatusByName[p.name] = p.status
            }
        }

        val defaultList = AttendanceConstants.INITIAL_DATA[newUnitKey] ?: emptyList()
        updatedMap[newUnitKey] = defaultList.mapIndexed { idx, name ->
            val statusFromPagi = pagiStatusByName[name] ?: "HADIR"
            Person(id = 100 + idx + 1, name = name, status = statusFromPagi)
        }

        saveAttendance(date, WaktuApel.MALAM_SIAGA, updatedMap)
        return updatedMap
    }

    fun saveAttendance(date: String, waktuApel: WaktuApel, data: Map<String, List<Person>>) {
        val key = storageKey(date, waktuApel)
        val jsonObject = JSONObject()

        for ((groupKey, persons) in data) {
            val arr = JSONArray()
            for (person in persons) {
                val pObj = JSONObject()
                pObj.put("id", person.id)
                pObj.put("name", person.name)
                pObj.put("status", person.status)
                pObj.put("isKeteranganKhusus", person.isKeteranganKhusus)
                arr.put(pObj)
            }
            jsonObject.put(groupKey, arr)
        }

        val jsonString = jsonObject.toString()
        val timestamp = getCurrentTimeWib()
        prefs.edit()
            .putString(key, jsonString)
            .putString("${key}_saved_at", timestamp)
            .apply()

        // Cadangan internal mandiri (Safety Dual-Layer Persistence)
        val backupFile = File(context.filesDir, "backup_${key}.json")
        try {
            writeAtomically(backupFile, jsonString)
        } catch (_: Exception) {}
    }

    fun getLastSavedTimestamp(date: String, waktuApel: WaktuApel): String? {
        val key = storageKey(date, waktuApel)
        return prefs.getString("${key}_saved_at", null)
    }

    fun getExportJson(data: Map<String, List<Person>>): String {
        val jsonObject = JSONObject()
        for ((groupKey, persons) in data) {
            val arr = JSONArray()
            for (person in persons) {
                val pObj = JSONObject()
                pObj.put("id", person.id)
                pObj.put("name", person.name)
                pObj.put("status", person.status)
                pObj.put("isKeteranganKhusus", person.isKeteranganKhusus)
                arr.put(pObj)
            }
            jsonObject.put(groupKey, arr)
        }
        return jsonObject.toString()
    }

    // --- Formatted Reports ---
    fun generateFormattedReport(
        date: String,
        waktuApel: WaktuApel,
        data: Map<String, List<Person>>,
        customStatuses: List<CustomStatusItem>,
        config: ReportTemplateConfig
    ): String {
        return when (config.formatType) {
            ReportFormatType.STANDAR_KOMANDO -> generateStandarKomandoReport(date, waktuApel, data, customStatuses, config)
            ReportFormatType.RINGKAS_WA -> generateRingkasWaReport(date, waktuApel, data, customStatuses, config)
            ReportFormatType.FORMAL_DINAS -> generateFormalDinasReport(date, waktuApel, data, customStatuses, config)
            ReportFormatType.CUSTOM -> generateCustomReport(date, waktuApel, data, customStatuses, config)
        }
    }

    private fun generateStandarKomandoReport(
        date: String,
        waktuApel: WaktuApel,
        data: Map<String, List<Person>>,
        customStatuses: List<CustomStatusItem>,
        config: ReportTemplateConfig
    ): String {
        val statusDescMap = customStatuses.associate { it.code to it.description }
        val formattedDate = DateUtils.formatDateHariBulanTahun(date)
        val dynamicSalam = getSalamPembukaForWaktu(waktuApel, config.salamPembuka)

        val reportTitleLine = if (waktuApel == WaktuApel.MALAM_SIAGA) {
            "*LAPORAN ABSEN APEL MALAM SIAGA*"
        } else {
            "*LAPORAN ABSEN APEL ${waktuApel.label}*"
        }

        val sb = StringBuilder()
        sb.append("*${config.headerKomando}*\n")
        sb.append("*${config.headerSatuan}*\n\n")
        sb.append("$dynamicSalam\n\n")
        sb.append("$reportTitleLine\n")
        sb.append("*PENGECEKAN PERSONEL KIKAV 2/JRTR*\n")
        sb.append("Hari/Tanggal: $formattedDate\n")
        sb.append("Waktu Apel: ${waktuApel.label}\n\n")

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("*A. REKAPITULASI PERSONEL:*\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")

        var totalAll = 0
        var totalHadir = 0
        var totalKurang = 0
        var rowNum = 1

        for ((groupKey, persons) in data) {
            val groupTotal = persons.size
            val hadirCount = persons.count { it.status == "HADIR" }
            val kurangCount = groupTotal - hadirCount
            val displayName = if (waktuApel == WaktuApel.MALAM_SIAGA) {
                if (groupKey == "REMAJA") "REMAJA"
                else {
                    val unit = AttendanceConstants.DISPLAY_NAMES[groupKey] ?: groupKey
                    "PELETON SIAGA ($unit)"
                }
            } else {
                AttendanceConstants.DISPLAY_NAMES[groupKey] ?: groupKey
            }

            totalAll += groupTotal
            totalHadir += hadirCount
            totalKurang += kurangCount

            val nonHadirMap = persons
                .filter { it.status != "HADIR" }
                .groupBy { it.status }
                .map { "${it.key}: ${it.value.size}" }
                .joinToString(", ")

            val detailSuffix = if (nonHadirMap.isNotEmpty()) " ($nonHadirMap)" else ""
            sb.append("${rowNum++}. *$displayName*: Jml $groupTotal | K $kurangCount | H $hadirCount$detailSuffix\n")
        }

        sb.append("────────────────────────────\n")
        if (waktuApel == WaktuApel.MALAM_SIAGA) {
            val remajaPersons = data["REMAJA"] ?: emptyList()
            val companionEntries = data.filterKeys { it != "REMAJA" }
            val hadirRemaja = remajaPersons.count { it.status == "HADIR" }
            val kurangRemaja = remajaPersons.size - hadirRemaja

            sb.append("*JUMLAH REMAJA:*\n")
            sb.append("• Kuat Nyata : ${remajaPersons.size} Orang\n")
            sb.append("• Hadir      : $hadirRemaja Orang\n")
            sb.append("• Kurang     : $kurangRemaja Orang\n\n")

            for ((compKey, compList) in companionEntries) {
                val compName = AttendanceConstants.DISPLAY_NAMES[compKey] ?: compKey
                val hadirComp = compList.count { it.status == "HADIR" }
                val kurangComp = compList.size - hadirComp
                sb.append("*JUMLAH PELETON SIAGA ($compName):*\n")
                sb.append("• Kuat Nyata : ${compList.size} Orang\n")
                sb.append("• Hadir      : $hadirComp Orang\n")
                sb.append("• Kurang     : $kurangComp Orang\n\n")
            }
        } else {
            sb.append("*JUMLAH KESELURUHAN:*\n")
            sb.append("• Kuat Nyata : $totalAll Orang\n")
            sb.append("• Hadir      : $totalHadir Orang\n")
            sb.append("• Kurang     : $totalKurang Orang\n\n")
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("*B. RINCIAN PERSONEL KURANG (KETERANGAN):*\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")

        if (waktuApel == WaktuApel.MALAM_SIAGA) {
            val remajaPersons = data["REMAJA"] ?: emptyList()
            val companionEntries = data.filterKeys { it != "REMAJA" }
            val remajaKurang = remajaPersons.filter { it.status != "HADIR" }
            val anyCompanionKurang = companionEntries.values.any { list -> list.any { it.status != "HADIR" } }

            if (remajaKurang.isEmpty() && !anyCompanionKurang) {
                sb.append("NIHIL (SEMUA PERSONEL HADIR LENGKAP)\n")
            } else {
                sb.append("*1. REMAJA:*\n")
                if (remajaKurang.isEmpty()) {
                    sb.append("  • NIHIL (Semua Remaja Hadir Lengkap)\n")
                } else {
                    val groupedRemaja = remajaKurang.groupBy { it.status }
                    for ((status, list) in groupedRemaja) {
                        val desc = statusDescMap[status] ?: status
                        val descSuffix = if (desc.isNotBlank() && desc != status) " ($desc)" else ""
                        sb.append("  *• $status$descSuffix: ${list.size} Orang*\n")
                        list.forEachIndexed { idx, p ->
                            sb.append("    ${idx + 1}. ${p.name}\n")
                        }
                    }
                }

                var companionIndex = 2
                for ((compKey, compList) in companionEntries) {
                    val compName = AttendanceConstants.DISPLAY_NAMES[compKey] ?: compKey
                    val compKurang = compList.filter { it.status != "HADIR" }
                    sb.append("\n*$companionIndex. PELETON SIAGA ($compName):*\n")
                    companionIndex++
                    if (compKurang.isEmpty()) {
                        sb.append("  • NIHIL (Semua Personel Peleton Hadir Lengkap)\n")
                    } else {
                        val groupedComp = compKurang.groupBy { it.status }
                        for ((status, list) in groupedComp) {
                            val desc = statusDescMap[status] ?: status
                            val descSuffix = if (desc.isNotBlank() && desc != status) " ($desc)" else ""
                            sb.append("  *• $status$descSuffix: ${list.size} Orang*\n")
                            list.forEachIndexed { idx, p ->
                                sb.append("    ${idx + 1}. ${p.name}\n")
                            }
                        }
                    }
                }
            }
        } else {
            val allKurang = mutableListOf<Triple<String, String, String>>()
            for ((groupKey, persons) in data) {
                val unitName = AttendanceConstants.DISPLAY_NAMES[groupKey] ?: groupKey
                for (p in persons) {
                    if (p.status != "HADIR") {
                        allKurang.add(Triple(p.status, unitName, p.name))
                    }
                }
            }

            if (allKurang.isEmpty()) {
                sb.append("NIHIL (SEMUA PERSONEL HADIR LENGKAP)\n")
            } else {
                val groupedByStatus = allKurang.groupBy { it.first }
                for ((status, list) in groupedByStatus) {
                    val desc = statusDescMap[status] ?: status
                    sb.append("\n*• $status ($desc): ${list.size} Orang*\n")
                    list.forEachIndexed { idx, item ->
                        sb.append("  ${idx + 1}. ${item.third} (${item.second})\n")
                    }
                }
            }
        }

        sb.append("\n${config.salamPenutup}\n")

        // Pejabat Jaga: PA JAGA, BA JAGA, TA JAGA
        val paLabel = config.ketPaJaga.ifBlank { "PA JAGA" }
        val paJaga = config.namaPaJaga.ifBlank { config.namaPerwiraPiket }
        val baJaga = config.namaBaJaga.ifBlank { config.namaBintaraPiket }
        val taJaga = config.namaTaJaga
        if (paJaga.isNotBlank() || baJaga.isNotBlank() || taJaga.isNotBlank() || (paLabel != "PA JAGA" && paLabel.isNotBlank())) {
            sb.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("*PEJABAT JAGA:*\n")
            if (paJaga.isNotBlank()) {
                sb.append("• $paLabel : *$paJaga*\n")
            } else if (paLabel != "PA JAGA" && paLabel.isNotBlank()) {
                sb.append("• PA JAGA : *$paLabel*\n")
            }
            if (baJaga.isNotBlank()) {
                sb.append("• BA JAGA : *$baJaga*\n")
            }
            if (taJaga.isNotBlank()) {
                sb.append("• TA JAGA : *$taJaga*\n")
            }
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        }

        return sb.toString().trimEnd()
    }

    private fun generateRingkasWaReport(
        date: String,
        waktuApel: WaktuApel,
        data: Map<String, List<Person>>,
        customStatuses: List<CustomStatusItem>,
        config: ReportTemplateConfig
    ): String {
        var totalAll = 0
        var totalHadir = 0
        for ((_, persons) in data) {
            totalAll += persons.size
            totalHadir += persons.count { it.status == "HADIR" }
        }
        val totalKurang = totalAll - totalHadir
        val formattedDate = DateUtils.formatDateHariBulanTahun(date)
        val dynamicSalam = getSalamPembukaForWaktu(waktuApel, config.salamPembuka)

        val sb = StringBuilder()
        sb.append("*${config.headerSatuan}*\n")
        sb.append("$dynamicSalam\n\n")

        if (waktuApel == WaktuApel.MALAM_SIAGA) {
            sb.append("📋 *APEL MALAM SIAGA KIKAV 2/JRTR* ($formattedDate)\n\n")

            val remajaPersons = data["REMAJA"] ?: emptyList()
            val companionEntries = data.filterKeys { it != "REMAJA" }
            val hadirRemaja = remajaPersons.count { it.status == "HADIR" }
            val kurangRemaja = remajaPersons.size - hadirRemaja

            sb.append("📊 *JUMLAH REMAJA:*\n")
            sb.append("• Kuat Nyata: *${remajaPersons.size}* | Hadir: *$hadirRemaja* | Kurang: *$kurangRemaja*\n\n")

            for ((compKey, compList) in companionEntries) {
                val compName = AttendanceConstants.DISPLAY_NAMES[compKey] ?: compKey
                val hadirComp = compList.count { it.status == "HADIR" }
                val kurangComp = compList.size - hadirComp
                sb.append("📊 *JUMLAH PELETON SIAGA ($compName):*\n")
                sb.append("• Kuat Nyata: *${compList.size}* | Hadir: *$hadirComp* | Kurang: *$kurangComp*\n\n")
            }

            val remajaKurang = remajaPersons.filter { it.status != "HADIR" }
            val anyCompanionKurang = companionEntries.values.any { list -> list.any { it.status != "HADIR" } }

            if (remajaKurang.isEmpty() && !anyCompanionKurang) {
                sb.append("✅ *LENGKAP: NIHIL KURANG*")
            } else {
                sb.append("⚠️ *RINCIAN PERSONEL KURANG:*\n")
                sb.append("*— REMAJA —*\n")
                if (remajaKurang.isEmpty()) {
                    sb.append("Nihil (Lengkap Hadir)\n")
                } else {
                    remajaKurang.forEachIndexed { i, p ->
                        sb.append("${i + 1}. ${p.name} (${p.status})\n")
                    }
                }

                for ((compKey, compList) in companionEntries) {
                    val compName = AttendanceConstants.DISPLAY_NAMES[compKey] ?: compKey
                    val compKurang = compList.filter { it.status != "HADIR" }
                    sb.append("\n*— PELETON SIAGA ($compName) —*\n")
                    if (compKurang.isEmpty()) {
                        sb.append("Nihil (Lengkap Hadir)\n")
                    } else {
                        compKurang.forEachIndexed { i, p ->
                            sb.append("${i + 1}. ${p.name} (${p.status})\n")
                        }
                    }
                }
            }
        } else {
            val nonHadirList = mutableListOf<String>()
            for ((groupKey, persons) in data) {
                val unitShort = AttendanceConstants.DISPLAY_NAMES[groupKey] ?: groupKey
                for (p in persons) {
                    if (p.status != "HADIR") {
                        nonHadirList.add("${p.name} ($unitShort - ${p.status})")
                    }
                }
            }

            sb.append("📋 *APEL ${waktuApel.label} KIKAV 2/JRTR* ($formattedDate)\n")
            sb.append("📊 Kuat: *$totalAll* | Hadir: *$totalHadir* | Kurang: *$totalKurang*\n\n")
            if (nonHadirList.isEmpty()) {
                sb.append("✅ *LENGKAP: NIHIL KURANG*")
            } else {
                sb.append("⚠️ *DAFTAR PERSONEL KURANG (${nonHadirList.size} Org):*\n")
                nonHadirList.forEachIndexed { i, item ->
                    sb.append("${i + 1}. $item\n")
                }
            }
        }
        sb.append("\n${config.salamPenutup}")

        val paLabel = config.ketPaJaga.ifBlank { "PA JAGA" }
        val paJaga = config.namaPaJaga.ifBlank { config.namaPerwiraPiket }
        val baJaga = config.namaBaJaga.ifBlank { config.namaBintaraPiket }
        val taJaga = config.namaTaJaga
        if (paJaga.isNotBlank() || baJaga.isNotBlank() || taJaga.isNotBlank() || (paLabel != "PA JAGA" && paLabel.isNotBlank())) {
            sb.append("\n\n*PEJABAT JAGA:*")
            if (paJaga.isNotBlank()) {
                sb.append("\n• $paLabel : $paJaga")
            } else if (paLabel != "PA JAGA" && paLabel.isNotBlank()) {
                sb.append("\n• PA JAGA : $paLabel")
            }
            if (baJaga.isNotBlank()) sb.append("\n• BA JAGA : $baJaga")
            if (taJaga.isNotBlank()) sb.append("\n• TA JAGA : $taJaga")
        }
        return sb.toString().trimEnd()
    }

    private fun generateFormalDinasReport(
        date: String,
        waktuApel: WaktuApel,
        data: Map<String, List<Person>>,
        customStatuses: List<CustomStatusItem>,
        config: ReportTemplateConfig
    ): String {
        val formattedDate = DateUtils.formatDateHariBulanTahun(date)
        val dynamicSalam = getSalamPembukaForWaktu(waktuApel, config.salamPembuka)

        val perihalLine = if (waktuApel == WaktuApel.MALAM_SIAGA) {
            "Perihal : Pengecekan Apel Malam Siaga"
        } else {
            "Perihal : Pengecekan Apel ${waktuApel.label}"
        }

        val sb = StringBuilder()
        sb.append("*${config.headerKomando}*\n")
        sb.append("*${config.headerSatuan}*\n\n")
        sb.append("$dynamicSalam\n\n")
        sb.append("$perihalLine\n")
        sb.append("Hari/Tgl: $formattedDate\n\n")

        if (waktuApel == WaktuApel.MALAM_SIAGA) {
            val remajaPersons = data["REMAJA"] ?: emptyList()
            val companionEntries = data.filterKeys { it != "REMAJA" }
            val hadirRemaja = remajaPersons.count { it.status == "HADIR" }
            val kurangRemaja = remajaPersons.size - hadirRemaja

            sb.append("1. Keadaan Personel REMAJA:\n")
            sb.append("   a. Kuat Nyata : ${remajaPersons.size} orang\n")
            sb.append("   b. Hadir      : $hadirRemaja orang\n")
            sb.append("   c. Kurang     : $kurangRemaja orang\n\n")

            var nextNum = 2
            for ((compKey, compList) in companionEntries) {
                val compName = AttendanceConstants.DISPLAY_NAMES[compKey] ?: compKey
                val hadirComp = compList.count { it.status == "HADIR" }
                val kurangComp = compList.size - hadirComp
                sb.append("$nextNum. Keadaan Personel Peleton Siaga ($compName):\n")
                sb.append("   a. Kuat Nyata : ${compList.size} orang\n")
                sb.append("   b. Hadir      : $hadirComp orang\n")
                sb.append("   c. Kurang     : $kurangComp orang\n\n")
                nextNum++
            }

            sb.append("$nextNum. Personel Kurang:\n")
            val remajaKurang = remajaPersons.filter { it.status != "HADIR" }
            val anyCompanionKurang = companionEntries.values.any { list -> list.any { it.status != "HADIR" } }

            if (remajaKurang.isEmpty() && !anyCompanionKurang) {
                sb.append("   Nihil.\n")
            } else {
                sb.append("   a. REMAJA:\n")
                if (remajaKurang.isEmpty()) {
                    sb.append("      Nihil.\n")
                } else {
                    remajaKurang.forEachIndexed { i, p ->
                        sb.append("      ${i + 1}. ${p.name} - Ket: ${p.status}\n")
                    }
                }

                var subLetter = 'b'
                for ((compKey, compList) in companionEntries) {
                    val compName = AttendanceConstants.DISPLAY_NAMES[compKey] ?: compKey
                    val compKurang = compList.filter { it.status != "HADIR" }
                    sb.append("   $subLetter. Peleton Siaga ($compName):\n")
                    subLetter++
                    if (compKurang.isEmpty()) {
                        sb.append("      Nihil.\n")
                    } else {
                        compKurang.forEachIndexed { i, p ->
                            sb.append("      ${i + 1}. ${p.name} - Ket: ${p.status}\n")
                        }
                    }
                }
            }
        } else {
            var totalAll = 0
            var totalHadir = 0
            for ((_, persons) in data) {
                totalAll += persons.size
                totalHadir += persons.count { it.status == "HADIR" }
            }
            val totalKurang = totalAll - totalHadir

            sb.append("1. Keadaan Umum Personel:\n")
            sb.append("   a. Kuat Nyata : $totalAll orang\n")
            sb.append("   b. Hadir      : $totalHadir orang\n")
            sb.append("   c. Kurang     : $totalKurang orang\n\n")
            sb.append("2. Rincian per Peleton:\n")
            for ((groupKey, persons) in data) {
                val unitName = AttendanceConstants.DISPLAY_FULL_NAMES[groupKey] ?: groupKey
                val hadir = persons.count { it.status == "HADIR" }
                val kurang = persons.size - hadir
                sb.append("   • $unitName: ${persons.size} org (Hadir: $hadir, Kurang: $kurang)\n")
            }

            sb.append("\n3. Personel Kurang:\n")
            val kurangList = mutableListOf<String>()
            for ((groupKey, persons) in data) {
                val unitShort = AttendanceConstants.DISPLAY_NAMES[groupKey] ?: groupKey
                for (p in persons) {
                    if (p.status != "HADIR") {
                        kurangList.add("${p.name} ($unitShort) - Ket: ${p.status}")
                    }
                }
            }

            if (kurangList.isEmpty()) {
                sb.append("   Nihil.\n")
            } else {
                kurangList.forEachIndexed { i, k ->
                    sb.append("   ${i + 1}. $k\n")
                }
            }
        }

        sb.append("\n${config.salamPenutup}\n\n")

        val formalPaLabel = config.ketPaJaga.ifBlank { "PA JAGA" }
        val paJaga = config.namaPaJaga.ifBlank { config.namaPerwiraPiket }.trim()
        val baJaga = config.namaBaJaga.ifBlank { config.namaBintaraPiket }.trim()
        val taJaga = config.namaTaJaga.trim()
        if (paJaga.isNotBlank() || baJaga.isNotBlank() || taJaga.isNotBlank() || (formalPaLabel != "PA JAGA" && formalPaLabel.isNotBlank())) {
            sb.append("Pejabat Jaga:\n")
            if (paJaga.isNotBlank()) {
                sb.append("1. $formalPaLabel : $paJaga\n")
            } else if (formalPaLabel != "PA JAGA" && formalPaLabel.isNotBlank()) {
                sb.append("1. PA JAGA : $formalPaLabel\n")
            }
            if (baJaga.isNotBlank()) sb.append("2. BA JAGA : $baJaga\n")
            if (taJaga.isNotBlank()) sb.append("3. TA JAGA : $taJaga\n")
        }
        return sb.toString().trimEnd()
    }

    private fun generateCustomReport(
        date: String,
        waktuApel: WaktuApel,
        data: Map<String, List<Person>>,
        customStatuses: List<CustomStatusItem>,
        config: ReportTemplateConfig
    ): String {
        var totalAll = 0
        var totalHadir = 0
        val rekapLines = StringBuilder()
        val allKurang = mutableListOf<Triple<String, String, String>>()
        val statusDescMap = customStatuses.associate { it.code to it.description }

        var rowNum = 1
        for ((groupKey, persons) in data) {
            val groupTotal = persons.size
            val hadirCount = persons.count { it.status == "HADIR" }
            val kurangCount = groupTotal - hadirCount
            val displayName = AttendanceConstants.DISPLAY_NAMES[groupKey] ?: groupKey

            totalAll += groupTotal
            totalHadir += hadirCount

            val nonHadirMap = persons
                .filter { it.status != "HADIR" }
                .groupBy { it.status }
                .map { "${it.key}: ${it.value.size}" }
                .joinToString(", ")

            val detailSuffix = if (nonHadirMap.isNotEmpty()) " ($nonHadirMap)" else ""
            rekapLines.append("${rowNum++}. *$displayName*: Jml $groupTotal | K $kurangCount | H $hadirCount$detailSuffix\n")

            for (p in persons) {
                if (p.status != "HADIR") {
                    allKurang.add(Triple(p.status, displayName, p.name))
                }
            }
        }
        val totalKurang = totalAll - totalHadir

        val rincianKurangSb = StringBuilder()
        if (allKurang.isEmpty()) {
            rincianKurangSb.append("NIHIL (SEMUA HADIR LENGKAP)\n")
        } else {
            val groupedByStatus = allKurang.groupBy { it.first }
            for ((status, list) in groupedByStatus) {
                val desc = statusDescMap[status] ?: status
                rincianKurangSb.append("\n*• $status ($desc): ${list.size} Org*\n")
                list.forEachIndexed { idx, item ->
                    rincianKurangSb.append("  ${idx + 1}. ${item.third} (${item.second})\n")
                }
            }
        }

        val formattedDate = DateUtils.formatDateHariBulanTahun(date)
        val dmyDate = DateUtils.formatDateDmy(date)

        val template = if (config.customTemplateText.isNotBlank()) {
            config.customTemplateText
        } else {
            """
*{KODAM}*
*{SATUAN}*

*LAPORAN ABSEN APEL {WAKTU}*
Hari/Tanggal: {TANGGAL}
Waktu Apel: {WAKTU}

{PEMBUKA}

*A. REKAPITULASI:*
{REKAP}
*TOTAL: Kuat {KUAT_NYATA} | Hadir {HADIR} | Kurang {KURANG}*

*B. RINCIAN KURANG:*
{RINCIAN_KURANG}

{PENUTUP}
            """.trimIndent()
        }

        val dynamicSalam = getSalamPembukaForWaktu(waktuApel, config.salamPembuka)
        val paJaga = config.namaPaJaga.ifBlank { config.namaPerwiraPiket }
        val baJaga = config.namaBaJaga.ifBlank { config.namaBintaraPiket }
        val taJaga = config.namaTaJaga

        var result = template
            .replace("{KODAM}", config.headerKomando)
            .replace("{SATUAN}", config.headerSatuan)
            .replace("{WAKTU}", waktuApel.label)
            .replace("{TANGGAL}", formattedDate)
            .replace("{PEMBUKA}", dynamicSalam)
            .replace("{REKAP}", rekapLines.toString().trimEnd())
            .replace("{KUAT_NYATA}", totalAll.toString())
            .replace("{HADIR}", totalHadir.toString())
            .replace("{KURANG}", totalKurang.toString())
            .replace("{RINCIAN_KURANG}", rincianKurangSb.toString().trimEnd())
            .replace("{PENUTUP}", config.salamPenutup)
            .replace("{PA_JAGA}", paJaga.ifBlank { "(....................)" })
            .replace("{KET_PA_JAGA}", config.ketPaJaga.ifBlank { "PA JAGA" })
            .replace("{BA_JAGA}", baJaga.ifBlank { "(....................)" })
            .replace("{TA_JAGA}", taJaga.ifBlank { "(....................)" })
            .replace("{JABATAN_PAWAS}", config.ketPaJaga.ifBlank { "PA JAGA" })
            .replace("{NAMA_PAWAS}", paJaga.ifBlank { "(....................)" })
            .replace("{JABATAN_BA_PIKET}", "BA JAGA")
            .replace("{NAMA_BA_PIKET}", baJaga.ifBlank { "(....................)" })
            .replace("{LOKASI}", config.lokasiTandaTangan)

        return result.trimEnd()
    }

    // --- Export To Word (.doc) Feature ---
    fun exportToWordFile(
        date: String,
        waktuApel: WaktuApel,
        data: Map<String, List<Person>>,
        customStatuses: List<CustomStatusItem>,
        config: ReportTemplateConfig = loadReportConfig()
    ): File {
        val docsDir = File(context.cacheDir, "docs")
        if (!docsDir.exists()) {
            docsDir.mkdirs()
        }
        val dmyDate = DateUtils.formatDateDmy(date).replace("/", "-")
        val fileName = if (waktuApel == WaktuApel.MALAM_SIAGA) {
            "Absen_Apel_Malam_Siaga_${dmyDate}.doc"
        } else {
            val safeWaktuName = waktuApel.label.replace(" ", "_")
            "Absen_Apel_${safeWaktuName}_${dmyDate}.doc"
        }
        val wordFile = File(docsDir, fileName)

        val htmlContent = generateWordHtml(date, waktuApel, data, customStatuses, config)
        FileOutputStream(wordFile).use { fos ->
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // UTF-8 BOM
            fos.write(htmlContent.toByteArray(Charsets.UTF_8))
        }

        return wordFile
    }

    fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun createShareWordIntent(file: File): Intent {
        val uri = getFileUri(file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/msword"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createOpenWordIntent(file: File): Intent {
        val uri = getFileUri(file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/msword")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // --- File Update: Export and Import Application Configuration (.json) ---
    data class ImportResult(
        val success: Boolean,
        val message: String,
        val statusesCount: Int = 0,
        val personnelCount: Int = 0
    )

    data class DataRepairReport(
        val success: Boolean,
        val filesChecked: Int,
        val filesRepaired: Int,
        val message: String,
        val repairedItems: List<String> = emptyList()
    ) {
        val repairedCount: Int get() = filesRepaired
    }

    data class RepairAndSyncReport(
        val success: Boolean,
        val message: String,
        val syncedCount: Int,
        val repairedCount: Int,
        val slotsSynced: Int = 4,
        val specialCarriedOver: Int = 0,
        val repairReport: DataRepairReport = DataRepairReport(success, 0, repairedCount, message)
    )

    fun exportAppConfigJson(
        currentDate: String,
        currentWaktuApel: WaktuApel,
        currentAttendance: Map<String, List<Person>>
    ): File {
        val docsDir = File(context.cacheDir, "docs")
        if (!docsDir.exists()) {
            docsDir.mkdirs()
        }
        val fileName = "Setingan_Absen_JRTR_${currentDate}.json"
        val configFile = File(docsDir, fileName)

        val rootObj = JSONObject()
        rootObj.put("app", "Absen Apel JRTR")
        rootObj.put("version", 2)
        rootObj.put("exportDate", currentDate)
        rootObj.put("exportTime", getCurrentTimeWib())
        rootObj.put("komando", AttendanceConstants.SATUAN_KOMANDO)
        rootObj.put("satuan", AttendanceConstants.SATUAN_UNIT)
        rootObj.put("customAppName", getCustomAppName())
        val logoUri = getCustomAppLogoUri()
        if (!logoUri.isNullOrBlank()) {
            rootObj.put("customAppLogoUri", logoUri)
        }

        // 1. Custom Statuses
        val statusesArr = JSONArray()
        for (item in loadCustomStatuses()) {
            val sObj = JSONObject()
            sObj.put("code", item.code)
            sObj.put("description", item.description)
            sObj.put("isDefault", item.isDefault)
            statusesArr.put(sObj)
        }
        rootObj.put("customStatuses", statusesArr)

        // 2. Report Template Config
        val reportConfig = loadReportConfig()
        val reportObj = JSONObject()
        reportObj.put("formatType", reportConfig.formatType.name)
        reportObj.put("headerKomando", reportConfig.headerKomando)
        reportObj.put("headerSatuan", reportConfig.headerSatuan)
        reportObj.put("salamPembuka", reportConfig.salamPembuka)
        reportObj.put("salamPenutup", reportConfig.salamPenutup)
        reportObj.put("customTemplateText", reportConfig.customTemplateText)
        reportObj.put("ketPaJaga", reportConfig.ketPaJaga)
        reportObj.put("namaPaJaga", reportConfig.namaPaJaga)
        reportObj.put("namaBaJaga", reportConfig.namaBaJaga)
        reportObj.put("namaTaJaga", reportConfig.namaTaJaga)
        reportObj.put("namaPerwiraPiket", reportConfig.namaPaJaga)
        reportObj.put("jabatanPerwiraPiket", reportConfig.ketPaJaga)
        reportObj.put("namaBintaraPiket", reportConfig.namaBaJaga)
        reportObj.put("jabatanBintaraPiket", "BA JAGA")
        reportObj.put("lokasiTandaTangan", reportConfig.lokasiTandaTangan)
        rootObj.put("reportConfig", reportObj)

        // 3. Platoons Roster
        val platoonsObj = JSONObject()
        val platoons = getPlatoonRoster()
        for ((group, names) in platoons) {
            val namesArr = JSONArray()
            names.forEach { namesArr.put(it) }
            platoonsObj.put(group, namesArr)
        }
        rootObj.put("platoonRoster", platoonsObj)

        // 4. Remaja Roster
        val remajaArr = JSONArray()
        getRemajaRoster().forEach { remajaArr.put(it) }
        rootObj.put("remajaRoster", remajaArr)

        // 5. Current Attendance Data
        val attendanceObj = JSONObject()
        for ((groupKey, persons) in currentAttendance) {
            val arr = JSONArray()
            for (p in persons) {
                val pObj = JSONObject()
                pObj.put("id", p.id)
                pObj.put("name", p.name)
                pObj.put("status", p.status)
                pObj.put("isKeteranganKhusus", p.isKeteranganKhusus)
                arr.put(pObj)
            }
            attendanceObj.put(groupKey, arr)
        }
        rootObj.put("activeAttendance", attendanceObj)
        rootObj.put("activeDate", currentDate)
        rootObj.put("activeWaktuApel", currentWaktuApel.name)

        writeAtomically(configFile, rootObj.toString(2))
        return configFile
    }

    fun createShareConfigIntent(file: File): Intent {
        val uri = getFileUri(file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("Config JSON", uri)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
            putExtra(Intent.EXTRA_SUBJECT, "Update Setingan Absen KIKAV 2 (${file.name})")
            putExtra(
                Intent.EXTRA_TEXT,
                "File setingan Absen Apel KIKAV 2/JRTR (${file.name}). Buka file ini dengan aplikasi ABSEN KIKAV 2."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createShareConfigTextIntent(jsonText: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, jsonText)
            putExtra(Intent.EXTRA_SUBJECT, "Teks Setingan Absen KIKAV 2/JRTR")
        }
    }

    fun exportAppConfigJsonString(
        currentDate: String,
        currentWaktuApel: WaktuApel,
        currentAttendance: Map<String, List<Person>>
    ): String {
        val file = exportAppConfigJson(currentDate, currentWaktuApel, currentAttendance)
        return file.readText(Charsets.UTF_8)
    }

    private fun normalizePlatoonKey(rawKey: String): String {
        val upper = rawKey.trim().uppercase()
        return when {
            upper == "KOKI" || upper == "KOMPI" -> "KOKI"
            upper == "SIMA" || upper == "MARKAS" || upper == "STAF" -> "SIMA"
            upper == "TON I" || upper == "TON 1" || upper == "TON_1" || upper == "TON1" || upper == "TON_I" || upper == "PLETON 1" || upper == "PLETON I" -> "TON I"
            upper == "TON II" || upper == "TON 2" || upper == "TON_2" || upper == "TON2" || upper == "TON_II" || upper == "PLETON 2" || upper == "PLETON II" -> "TON II"
            upper == "TON III" || upper == "TON 3" || upper == "TON_3" || upper == "TON3" || upper == "TON_III" || upper == "PLETON 3" || upper == "PLETON III" -> "TON III"
            else -> upper
        }
    }

    /**
     * Menerima dan memproses teks konfigurasi JSON setingan aplikasi.
     * Sangat tangguh dan toleran terhadap teks WhatsApp, kutip ganda/smart quotes,
     * tanda pagar/komentar, format kode markdown, dan daftar personel teks bebas.
     */
    fun importAppConfigFromJson(rawJsonStr: String): ImportResult {
        val rawTrim = rawJsonStr.trim()
        if (rawTrim.isBlank()) {
            return ImportResult(
                success = false,
                message = "Teks setingan kosong. Silakan salin & tempel teks setingan JSON atau daftar personel yang dibagikan."
            )
        }

        return try {
            var sanitized = rawTrim
                .removePrefix("\uFEFF")
                .removePrefix("\u200B")
                .removePrefix("\u200C")
                .removePrefix("\u200D")
                .replace("\u00A0", " ")
                .trim()

            // Unescape HTML entities yang umum terjadi jika disalin dari web/chat
            sanitized = sanitized
                .replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")

            // Bersihkan label forward atau header timestamp WhatsApp
            sanitized = sanitized.replace(Regex("(?m)^(\\[.*?\\]|Pesan diteruskan|Forwarded message).*?$"), "")

            // Strip markdown code fences if present (```json ... ``` or ``` ...)
            if (sanitized.contains("```")) {
                sanitized = sanitized.substringAfter("```").substringBeforeLast("```").trim()
                if (sanitized.startsWith("json", ignoreCase = true)) {
                    sanitized = sanitized.substring(4).trim()
                }
            }

            // Tangani JSON yang dibungkus kutip terluar karena double-encoding
            if (sanitized.startsWith("\"") && sanitized.endsWith("\"") && sanitized.length > 2) {
                val unquoted = sanitized.substring(1, sanitized.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                if (unquoted.contains("{") || unquoted.contains("[")) {
                    sanitized = unquoted
                }
            }

            // Normalize curly/smart quotes & apostrophes
            sanitized = sanitized
                .replace('“', '"')
                .replace('”', '"')
                .replace('„', '"')
                .replace('`', '"')
                .replace('‘', '\'')
                .replace('’', '\'')

            // Hapus koma gantung sebelum } atau ] (kesalahan paling umum pada copy-paste JSON)
            sanitized = sanitized.replace(Regex(",\\s*([}\\]])"), "$1")

            val firstBrace = sanitized.indexOf('{')
            val lastBrace = sanitized.lastIndexOf('}')
            val firstBracket = sanitized.indexOf('[')
            val lastBracket = sanitized.lastIndexOf(']')

            var statusesImported = 0
            var personnelImported = 0

            // Skenario A: JSON Array murni (misal: daftar custom status langsung atau daftar personel)
            val isRootArray = firstBracket != -1 && (firstBrace == -1 || firstBracket < firstBrace) && lastBracket > firstBracket
            if (isRootArray) {
                val arrayStr = sanitized.substring(firstBracket, lastBracket + 1)
                val jsonArr = JSONArray(arrayStr)
                if (jsonArr.length() > 0) {
                    val firstElem = jsonArr.opt(0)
                    if (firstElem is JSONObject && firstElem.has("code")) {
                        // Array of custom statuses
                        val list = mutableListOf<CustomStatusItem>()
                        for (i in 0 until jsonArr.length()) {
                            val sObj = jsonArr.optJSONObject(i) ?: continue
                            val code = sObj.optString("code", "").trim().uppercase()
                            if (code.isBlank()) continue
                            val desc = sObj.optString("description", "")
                            val isDef = sObj.optBoolean("isDefault", AttendanceConstants.DEFAULT_STATUS_LIST.contains(code))
                            list.add(CustomStatusItem(code = code, description = desc, isDefault = isDef))
                        }
                        if (list.isNotEmpty()) {
                            saveCustomStatuses(list)
                            statusesImported = list.size
                        }
                    } else if (firstElem is String) {
                        // Array of soldier names (import as Remaja roster)
                        val names = mutableListOf<String>()
                        for (i in 0 until jsonArr.length()) {
                            val n = jsonArr.optString(i, "").trim().uppercase()
                            if (n.isNotBlank()) names.add(n)
                        }
                        if (names.isNotEmpty()) {
                            saveRemajaRoster(names)
                            personnelImported = names.size
                        }
                    }
                }

                return ImportResult(
                    success = true,
                    message = "Setingan Array Berhasil Diterapkan ($statusesImported keterangan, $personnelImported personel)!",
                    statusesCount = statusesImported,
                    personnelCount = personnelImported
                )
            }

            // Skenario B: Teks daftar personel atau format WhatsApp bebas (jika tidak ada kurung JSON)
            if (firstBrace == -1 && firstBracket == -1) {
                val lines = sanitized.lines().map { it.trim() }.filter { it.isNotBlank() }
                if (lines.isNotEmpty()) {
                    var currentGroup = "TON I"
                    val parsedRoster = mutableMapOf<String, MutableList<String>>()
                    val validGroups = listOf("KOKI", "SIMA", "TON I", "TON II", "TON III", "REMAJA")
                    validGroups.forEach { parsedRoster[it] = mutableListOf() }

                    for (rawLine in lines) {
                        val upperLine = rawLine.uppercase()
                        val matchedGroup = validGroups.find { g ->
                            upperLine.startsWith(g) || upperLine.contains("PLETON $g") || upperLine.contains("PELETON $g")
                        }
                        if (matchedGroup != null) {
                            currentGroup = matchedGroup
                            continue
                        }
                        val cleanName = rawLine.replace(Regex("^[0-9]+[.\\-)]\\s*"), "").trim().uppercase()
                        if (cleanName.length >= 2 && !cleanName.startsWith("//") && !cleanName.startsWith("#")) {
                            parsedRoster[currentGroup]?.add(cleanName)
                            personnelImported++
                        }
                    }

                    if (personnelImported > 0) {
                        val currentPlatoons = getPlatoonRoster().toMutableMap()
                        for ((group, names) in parsedRoster) {
                            if (names.isNotEmpty()) {
                                if (group == "REMAJA") {
                                    saveRemajaRoster(names)
                                } else {
                                    currentPlatoons[group] = names
                                }
                            }
                        }
                        savePlatoonRoster(currentPlatoons)
                        return ImportResult(
                            success = true,
                            message = "Daftar Personel Teks Berhasil Diterapkan ($personnelImported personel ke dalam peleton)!",
                            statusesCount = 0,
                            personnelCount = personnelImported
                        )
                    }
                }
            }

            // Skenario C: JSON Object utama { ... }
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                sanitized = sanitized.substring(firstBrace, lastBrace + 1)
            }

            val root = JSONObject(sanitized)

            // 1. Import Custom Statuses (customStatuses, statuses, keterangan)
            try {
                val arr = root.optJSONArray("customStatuses")
                    ?: root.optJSONArray("statuses")
                    ?: root.optJSONArray("keterangan")
                if (arr != null) {
                    val list = mutableListOf<CustomStatusItem>()
                    for (i in 0 until arr.length()) {
                        val sObj = arr.optJSONObject(i) ?: continue
                        val code = sObj.optString("code", "").trim().uppercase()
                        if (code.isBlank()) continue
                        val desc = sObj.optString("description", "")
                        val isDef = sObj.optBoolean("isDefault", AttendanceConstants.DEFAULT_STATUS_LIST.contains(code))
                        list.add(CustomStatusItem(code = code, description = desc, isDefault = isDef))
                    }
                    if (list.isNotEmpty()) {
                        saveCustomStatuses(list)
                        statusesImported = list.size
                    }
                }
            } catch (_: Exception) {}

            // 2. Import Report Config (reportConfig, config, template)
            try {
                val rObj = root.optJSONObject("reportConfig")
                    ?: root.optJSONObject("config")
                    ?: root.optJSONObject("template")
                if (rObj != null) {
                    val format = try {
                        ReportFormatType.valueOf(rObj.optString("formatType", ReportFormatType.STANDAR_KOMANDO.name))
                    } catch (e: Exception) {
                        ReportFormatType.STANDAR_KOMANDO
                    }
                    val ketPa = rObj.optString("ketPaJaga", "PA JAGA").trim().ifBlank { "PA JAGA" }
                    val pa = rObj.optString("namaPaJaga", rObj.optString("namaPerwiraPiket", "")).trim()
                    val ba = rObj.optString("namaBaJaga", rObj.optString("namaBintaraPiket", "")).trim()
                    val ta = rObj.optString("namaTaJaga", "").trim()

                    val cleanNamaPa = if (pa.contains("ARYA PRATAMA", ignoreCase = true)) "" else pa
                    val cleanNamaBa = if (ba.contains("WAHYU HIDAYAT", ignoreCase = true)) "" else ba

                    val config = ReportTemplateConfig(
                        formatType = format,
                        headerKomando = rObj.optString("headerKomando", AttendanceConstants.SATUAN_KOMANDO),
                        headerSatuan = rObj.optString("headerSatuan", AttendanceConstants.SATUAN_UNIT),
                        salamPembuka = rObj.optString("salamPembuka", ""),
                        salamPenutup = rObj.optString("salamPenutup", "Demikian dilaporkan, tertanda Piket Siaga KIKAV 2/JRTR."),
                        customTemplateText = rObj.optString("customTemplateText", ""),
                        ketPaJaga = ketPa,
                        namaPaJaga = cleanNamaPa,
                        namaBaJaga = cleanNamaBa,
                        namaTaJaga = ta,
                        namaPerwiraPiket = cleanNamaPa,
                        jabatanPerwiraPiket = ketPa,
                        namaBintaraPiket = cleanNamaBa,
                        jabatanBintaraPiket = "BA JAGA",
                        lokasiTandaTangan = rObj.optString("lokasiTandaTangan", "").let { if (it.equals("Ambarawa", ignoreCase = true)) "" else it.trim() }
                    )
                    saveReportConfig(config)
                }
            } catch (_: Exception) {}

            // 3. Import Platoon Roster (platoonRoster, roster, personel, peleton)
            val pObj = root.optJSONObject("platoonRoster")
                ?: root.optJSONObject("roster")
                ?: root.optJSONObject("personel")
                ?: root.optJSONObject("peleton")

            val rosterTargetObj = if (pObj != null) {
                pObj
            } else {
                val hasPlatoonKey = root.has("TON I") || root.has("TON II") || root.has("TON III") || root.has("KOKI") || root.has("SIMA")
                if (hasPlatoonKey) root else null
            }

            if (rosterTargetObj != null) {
                val currentPlatoons = getPlatoonRoster().toMutableMap()
                val rosterMap = linkedMapOf<String, List<String>>()
                val keys = rosterTargetObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "customStatuses" || key == "reportConfig" || key == "remajaRoster" || key == "activeAttendance" || key == "customAppName" || key == "customAppLogoUri") continue
                    val arr = rosterTargetObj.optJSONArray(key)
                    if (arr != null) {
                        val normKey = normalizePlatoonKey(key)
                        val names = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val n = arr.optString(i, "").trim().uppercase()
                            if (n.isNotBlank()) names.add(n)
                        }
                        if (names.isNotEmpty()) {
                            rosterMap[normKey] = names
                            personnelImported += names.size
                        }
                    }
                }
                if (rosterMap.isNotEmpty()) {
                    for ((k, v) in rosterMap) {
                        currentPlatoons[k] = v
                    }
                    savePlatoonRoster(currentPlatoons)
                }
            }

            // 4. Import Remaja Roster (remajaRoster, remaja)
            val rArr = root.optJSONArray("remajaRoster") ?: root.optJSONArray("remaja")
            if (rArr != null) {
                val remajaList = mutableListOf<String>()
                for (i in 0 until rArr.length()) {
                    val n = rArr.optString(i, "").trim().uppercase()
                    if (n.isNotBlank()) remajaList.add(n)
                }
                if (remajaList.isNotEmpty()) {
                    saveRemajaRoster(remajaList)
                }
            }

            // 5. Import Active Attendance if present
            if (root.has("activeAttendance") && root.has("activeDate") && root.has("activeWaktuApel")) {
                val attObj = root.optJSONObject("activeAttendance")
                val date = root.optString("activeDate", "")
                val waktuStr = root.optString("activeWaktuApel", "")
                if (attObj != null && date.isNotBlank() && waktuStr.isNotBlank()) {
                    val waktu = try { WaktuApel.valueOf(waktuStr) } catch (e: Exception) { WaktuApel.PAGI }
                    val attMap = linkedMapOf<String, List<Person>>()
                    val keys = attObj.keys()
                    while (keys.hasNext()) {
                        val gKey = keys.next()
                        val arr = attObj.optJSONArray(gKey)
                        if (arr != null) {
                            attMap[gKey] = parsePersonArray(arr)
                        }
                    }
                    if (attMap.isNotEmpty()) {
                        saveAttendance(date, waktu, attMap)
                    }
                }
            }

            // 6. Import App Name & Logo if present
            val customName = root.optString("customAppName", root.optString("appName", "")).trim()
            if (customName.isNotBlank()) {
                setCustomAppName(customName)
            }
            val customLogo = root.optString("customAppLogoUri", root.optString("logoUri", "")).trim()
            if (customLogo.isNotBlank()) {
                setCustomAppLogoUri(customLogo)
            }

            ImportResult(
                success = true,
                message = "Setingan Berhasil Diterapkan ($statusesImported keterangan, $personnelImported personel diperbarui)!",
                statusesCount = statusesImported,
                personnelCount = personnelImported
            )
        } catch (e: Exception) {
            ImportResult(
                success = false,
                message = "Gagal memproses setingan: ${e.message ?: "Format data tidak valid"}. Pastikan teks merupakan file JSON atau daftar personel resmi."
            )
        }
    }

    /**
     * Memeriksa dan memperbaiki seluruh file dan data penyimpanan yang korup/rusak.
     * Mengembalikan laporan pemulihan detail.
     */
    fun verifyAndRepairAllData(currentDate: String? = null): DataRepairReport {
        var checked = 0
        var repaired = 0
        val sb = StringBuilder()
        val repairedItemsList = mutableListOf<String>()

        // 1. Verifikasi Status Kustom
        checked++
        try {
            val statuses = loadCustomStatuses()
            if (statuses.isEmpty()) {
                val def = getDefaultStatusItems()
                saveCustomStatuses(def)
                repaired++
                repairedItemsList.add("Daftar Status")
                sb.append("• Keterangan status kosong, dipulihkan ke standar.\n")
            } else {
                saveCustomStatuses(statuses)
            }
        } catch (e: Exception) {
            val def = getDefaultStatusItems()
            saveCustomStatuses(def)
            repaired++
            repairedItemsList.add("Daftar Status")
            sb.append("• Keterangan status rusak, diperbaiki ke standar.\n")
        }

        // 2. Verifikasi Roster Peleton
        checked++
        try {
            val roster = getPlatoonRoster()
            var rosterFixed = false
            val cleanRoster = roster.toMutableMap()
            val requiredPlatoons = listOf("KOKI", "SIMA", "TON I", "TON II", "TON III")
            for (p in requiredPlatoons) {
                if (!cleanRoster.containsKey(p) || cleanRoster[p].isNullOrEmpty()) {
                    cleanRoster[p] = AttendanceConstants.INITIAL_DATA[p] ?: emptyList()
                    rosterFixed = true
                }
            }
            if (rosterFixed) {
                savePlatoonRoster(cleanRoster)
                repaired++
                repairedItemsList.add("Struktur Peleton")
                sb.append("• Susunan peleton diperbaiki (kelengkapan peleton dipulihkan).\n")
            } else {
                savePlatoonRoster(cleanRoster)
            }
        } catch (e: Exception) {
            savePlatoonRoster(AttendanceConstants.INITIAL_DATA)
            repaired++
            repairedItemsList.add("Struktur Peleton")
            sb.append("• Susunan peleton rusak, dipulihkan ke default resmi.\n")
        }

        // 3. Verifikasi Remaja
        checked++
        try {
            val remaja = getRemajaRoster()
            if (remaja.isEmpty()) {
                saveRemajaRoster(AttendanceConstants.REMAJA_DATA)
                repaired++
                repairedItemsList.add("Data Remaja")
                sb.append("• Data Remaja kosong, dipulihkan ke 43 personel resmi.\n")
            } else {
                saveRemajaRoster(remaja)
            }
        } catch (e: Exception) {
            saveRemajaRoster(AttendanceConstants.REMAJA_DATA)
            repaired++
            repairedItemsList.add("Data Remaja")
            sb.append("• Data Remaja rusak, dipulihkan.\n")
        }

        // 4. Verifikasi Template Laporan
        checked++
        try {
            val reportConfig = loadReportConfig()
            saveReportConfig(reportConfig)
        } catch (e: Exception) {
            saveReportConfig(ReportTemplateConfig())
            repaired++
            repairedItemsList.add("Template Laporan")
            sb.append("• Konfigurasi template laporan diperbaiki.\n")
        }

        // 5. Verifikasi Data Kehadiran Tanggal Aktif (jika ada)
        if (currentDate != null) {
            for (slot in WaktuApel.values()) {
                checked++
                try {
                    val att = loadAttendance(currentDate, slot)
                    saveAttendance(currentDate, slot, att)
                } catch (e: Exception) {
                    val def = getInitialAttendanceForSlot(slot)
                    saveAttendance(currentDate, slot, def)
                    repaired++
                    repairedItemsList.add("Sesi ${slot.label}")
                    sb.append("• Sesi ${slot.label} diperbaiki.\n")
                }
            }
        }

        val msg = if (repaired > 0) {
            "Pemeriksaan selesai: $repaired file/data diperbaiki dan disinkronkan kembali!\n$sb"
        } else {
            "Semua file dan data terverifikasi aman, utuh, dan tidak ada kerusakan ($checked komponen diperiksa)."
        }

        return DataRepairReport(
            success = true,
            filesChecked = checked,
            filesRepaired = repaired,
            message = msg.trim(),
            repairedItems = repairedItemsList
        )
    }

    /**
     * SINKRONISASI SEMUA DATA & PERBAIKI FILE RUSAK:
     * 1. Memeriksa dan memperbaiki file yang rusak/hilang secara otomatis.
     * 2. Menyinkronkan status/keterangan personel ke seluruh sesi apel (Pagi, Siang, Sore, Malam Siaga).
     * 3. Mempertahankan Keterangan Khusus prajurit secara konsisten.
     * 4. Melakukan flush atomik ke internal storage untuk keamanan total.
     */
    fun syncAllDataAndRepair(
        date: String,
        activeSlot: WaktuApel,
        currentAttendance: Map<String, List<Person>>? = null
    ): RepairAndSyncReport {
        // 1. Verifikasi dan perbaiki file rusak terlebih dahulu
        val repairReport = verifyAndRepairAllData(date)

        // 2. Simpan data slot aktif
        val sourceData = currentAttendance ?: loadAttendance(date, activeSlot)
        saveAttendance(date, activeSlot, sourceData)

        // 3. Tentukan peta status sumber (peta dari activeSlot, atau dari PAGI jika activeSlot Hadir semua)
        val statusByName = mutableMapOf<String, Pair<String, Boolean>>()
        val statusByNormName = mutableMapOf<String, Pair<String, Boolean>>()

        for ((_, persons) in sourceData) {
            for (p in persons) {
                statusByName[p.name] = Pair(p.status, p.isKeteranganKhusus)
                val norm = AttendanceConstants.normalizeMilitaryName(p.name)
                statusByNormName[norm] = Pair(p.status, p.isKeteranganKhusus)
            }
        }

        // Jika sesi aktif bukan PAGI dan semua HADIR, ambil juga keterangan khusus dari PAGI agar tidak hilang
        if (activeSlot != WaktuApel.PAGI) {
            val pagiData = loadAttendance(date, WaktuApel.PAGI)
            for ((_, persons) in pagiData) {
                for (p in persons) {
                    val norm = AttendanceConstants.normalizeMilitaryName(p.name)
                    if (p.isKeteranganKhusus || (p.status != "HADIR" && statusByNormName[norm]?.first == "HADIR")) {
                        statusByName[p.name] = Pair(p.status, p.isKeteranganKhusus)
                        statusByNormName[norm] = Pair(p.status, p.isKeteranganKhusus)
                    }
                }
            }
        }

        var totalSynced = 0
        val specialCount = statusByNormName.values.count { it.second }

        // 4. Sinkronkan ke seluruh 4 slot waktu apel
        for (targetSlot in WaktuApel.values()) {
            val targetAttendance = if (targetSlot == activeSlot) sourceData else loadAttendance(date, targetSlot)
            val updatedMap = linkedMapOf<String, List<Person>>()
            for ((groupKey, persons) in targetAttendance) {
                val updatedPersons = persons.map { person ->
                    val norm = AttendanceConstants.normalizeMilitaryName(person.name)
                    val sourceInfo = statusByNormName[norm] ?: statusByName[person.name]
                    if (sourceInfo != null) {
                        if (person.status != sourceInfo.first || person.isKeteranganKhusus != sourceInfo.second) {
                            totalSynced++
                        }
                        person.copy(status = sourceInfo.first, isKeteranganKhusus = sourceInfo.second)
                    } else {
                        person
                    }
                }
                updatedMap[groupKey] = updatedPersons
            }
            saveAttendance(date, targetSlot, updatedMap)
        }

        val repairNote = if (repairReport.filesRepaired > 0) {
            " (${repairReport.filesRepaired} file/data rusak diperbaiki)"
        } else {
            " (file penyimpanan aman)"
        }

        val msg = "✓ Seluruh sesi apel (Pagi, Siang, Sore & Malam Siaga) berhasil disinkronkan$repairNote!"

        return RepairAndSyncReport(
            success = true,
            message = msg,
            syncedCount = totalSynced,
            repairedCount = repairReport.filesRepaired,
            slotsSynced = 4,
            specialCarriedOver = specialCount,
            repairReport = repairReport
        )
    }

    private fun generateWordHtml(
        date: String,
        waktuApel: WaktuApel,
        data: Map<String, List<Person>>,
        customStatuses: List<CustomStatusItem>,
        config: ReportTemplateConfig = loadReportConfig()
    ): String {
        val statusCodes = customStatuses.map { it.code }
        val statusCountTotal = statusCodes.size

        // Build Summary Table Rows
        val tbodyRows = StringBuilder()
        var totalAll = 0
        var totalHadir = 0
        var totalKurang = 0
        val statusTotals = mutableMapOf<String, Int>()
        statusCodes.forEach { statusTotals[it] = 0 }

        var rowNum = 1
        for ((groupKey, persons) in data) {
            val groupTotal = persons.size
            val hadirCount = persons.count { it.status == "HADIR" }
            val kurangCount = groupTotal - hadirCount
            val displayName = if (waktuApel == WaktuApel.MALAM_SIAGA) {
                if (groupKey == "REMAJA") "REMAJA"
                else {
                    val unit = AttendanceConstants.DISPLAY_NAMES[groupKey] ?: groupKey
                    "PELETON SIAGA ($unit)"
                }
            } else {
                AttendanceConstants.DISPLAY_NAMES[groupKey] ?: groupKey
            }

            totalAll += groupTotal
            totalHadir += hadirCount
            totalKurang += kurangCount

            val counts = mutableMapOf<String, Int>()
            statusCodes.forEach { counts[it] = 0 }
            for (p in persons) {
                if (p.status != "HADIR") {
                    counts[p.status] = (counts[p.status] ?: 0) + 1
                    statusTotals[p.status] = (statusTotals[p.status] ?: 0) + 1
                }
            }

            tbodyRows.append("<tr>")
            tbodyRows.append("<td style='background-color:#f2f2f2;'>${rowNum++}</td>")
            tbodyRows.append("<td style='text-align:left;font-weight:bold;padding-left:3px;'>$displayName</td>")
            tbodyRows.append("<td style='background-color:#f2f2f2;'>$groupTotal</td>")
            tbodyRows.append("<td style='color:#dc2626;font-weight:bold;background-color:#fff1f2;'>${if (kurangCount > 0) kurangCount else "-"}</td>")
            tbodyRows.append("<td style='color:#0284c7;font-weight:bold;background-color:#f0f9ff;'>${if (hadirCount > 0) hadirCount else "-"}</td>")

            for (st in statusCodes) {
                val cnt = counts[st] ?: 0
                val cellVal = if (cnt > 0) cnt.toString() else ""
                val cellStyle = if (cnt > 0) "color:#dc2626;font-weight:bold;background-color:#fff1f2;" else ""
                tbodyRows.append("<td style='$cellStyle'>$cellVal</td>")
            }
            tbodyRows.append("</tr>\n")
        }

        // Summary Total Row
        val totalRow = StringBuilder()
        if (waktuApel == WaktuApel.MALAM_SIAGA) {
            val remajaPersons = data["REMAJA"] ?: emptyList()
            val hadirRemaja = remajaPersons.count { it.status == "HADIR" }
            val kurangRemaja = remajaPersons.size - hadirRemaja
            val remajaStatusTotals = mutableMapOf<String, Int>()
            for (p in remajaPersons) {
                if (p.status != "HADIR") {
                    remajaStatusTotals[p.status] = (remajaStatusTotals[p.status] ?: 0) + 1
                }
            }

            totalRow.append("<tr style='background-color:#e0f2fe;font-weight:bold;'>")
            totalRow.append("<td colspan='2' style='text-align:left;padding-left:3px;'>JUMLAH REMAJA</td>")
            totalRow.append("<td>${remajaPersons.size}</td>")
            totalRow.append("<td style='color:#b91c1c;background-color:#fecdd3;'>$kurangRemaja</td>")
            totalRow.append("<td style='color:#0369a1;background-color:#bae6fd;'>$hadirRemaja</td>")
            for (st in statusCodes) {
                val cnt = remajaStatusTotals[st] ?: 0
                val cellVal = if (cnt > 0) cnt.toString() else ""
                val cellStyle = if (cnt > 0) "color:#b91c1c;" else ""
                totalRow.append("<td style='$cellStyle'>$cellVal</td>")
            }
            totalRow.append("</tr>\n")

            val companionEntries = data.filterKeys { it != "REMAJA" }
            for ((compKey, compList) in companionEntries) {
                val compName = AttendanceConstants.DISPLAY_NAMES[compKey] ?: compKey
                val hadirComp = compList.count { it.status == "HADIR" }
                val kurangComp = compList.size - hadirComp
                val compStatusTotals = mutableMapOf<String, Int>()
                for (p in compList) {
                    if (p.status != "HADIR") {
                        compStatusTotals[p.status] = (compStatusTotals[p.status] ?: 0) + 1
                    }
                }

                totalRow.append("<tr style='background-color:#fef3c7;font-weight:bold;'>")
                totalRow.append("<td colspan='2' style='text-align:left;padding-left:3px;'>JUMLAH PELETON SIAGA ($compName)</td>")
                totalRow.append("<td>${compList.size}</td>")
                totalRow.append("<td style='color:#b91c1c;background-color:#fecdd3;'>$kurangComp</td>")
                totalRow.append("<td style='color:#0369a1;background-color:#bae6fd;'>$hadirComp</td>")
                for (st in statusCodes) {
                    val cnt = compStatusTotals[st] ?: 0
                    val cellVal = if (cnt > 0) cnt.toString() else ""
                    val cellStyle = if (cnt > 0) "color:#b91c1c;" else ""
                    totalRow.append("<td style='$cellStyle'>$cellVal</td>")
                }
                totalRow.append("</tr>\n")
            }
        } else {
            totalRow.append("<tr style='background-color:#d9d9d9;font-weight:bold;'>")
            totalRow.append("<td colspan='2' style='text-align:left;padding-left:3px;'>JUMLAH KESELURUHAN</td>")
            totalRow.append("<td>$totalAll</td>")
            totalRow.append("<td style='color:#b91c1c;background-color:#fecdd3;'>$totalKurang</td>")
            totalRow.append("<td style='color:#0369a1;background-color:#bae6fd;'>$totalHadir</td>")
            for (st in statusCodes) {
                val cnt = statusTotals[st] ?: 0
                val cellVal = if (cnt > 0) cnt.toString() else ""
                val cellStyle = if (cnt > 0) "color:#b91c1c;" else ""
                totalRow.append("<td style='$cellStyle'>$cellVal</td>")
            }
            totalRow.append("</tr>\n")
        }

        // Subheader th
        val thKeterangan = StringBuilder()
        for (st in statusCodes) {
            thKeterangan.append("<th>$st</th>")
        }

        // Detail Grid Columns for each Platoon (No lines between names, rapat)
        val detailColumns = StringBuilder()
        for ((groupKey, persons) in data) {
            val displayName = if (waktuApel == WaktuApel.MALAM_SIAGA) {
                if (groupKey == "REMAJA") "REMAJA"
                else {
                    val unit = AttendanceConstants.DISPLAY_NAMES[groupKey] ?: groupKey
                    "PELETON SIAGA ($unit)"
                }
            } else {
                if (groupKey == "PIMPINAN") "ESELON PIMPINAN" else groupKey
            }
            val nonHadirMap = mutableMapOf<String, MutableList<Person>>()
            val hadirList = mutableListOf<Person>()
            for (p in persons) {
                if (p.status == "HADIR") hadirList.add(p)
                else nonHadirMap.getOrPut(p.status) { mutableListOf() }.add(p)
            }

            detailColumns.append("<td style='vertical-align:top;padding:0;border:1px solid black;'>")
            detailColumns.append("<div class='col-header'>$displayName (${persons.size})</div>")

            // Non-Hadir groups
            for ((st, pList) in nonHadirMap) {
                detailColumns.append("<div class='status-group-header'>$st</div>")
                pList.forEachIndexed { idx, p ->
                    detailColumns.append("<p class='person-row' style='color:#b91c1c;font-weight:bold;margin:1.5px 0;padding:0 2px;line-height:8.5pt;font-size:6.5pt;border:none;'>${idx + 1}.${p.name}</p>")
                }
            }

            // Hadir group
            detailColumns.append("<div class='hadir-header'>HADIR (${hadirList.size})</div>")
            hadirList.forEachIndexed { idx, p ->
                detailColumns.append("<p class='person-row' style='color:#000000;margin:1.5px 0;padding:0 2px;line-height:8.5pt;font-size:6.5pt;border:none;'>${idx + 1}.${p.name}</p>")
            }
            detailColumns.append("</td>\n")
        }

        val photoFile = getApelPhotoFile(date, waktuApel)
        val photoHtml = if (photoFile.exists() && photoFile.length() > 0) {
            try {
                val bytes = photoFile.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                """
                <div style='margin-top:8px;text-align:center;'>
                    <div style='font-weight:bold;font-size:8pt;margin-bottom:3px;'>DOKUMENTASI FOTO APEL:</div>
                    <img src='data:image/jpeg;base64,$base64' style='max-width:320px;max-height:200px;border:1px solid #333;' />
                </div>
                """.trimIndent()
            } catch (e: Exception) { "" }
        } else ""

        val formattedDateDisplay = DateUtils.formatDateHariBulanTahun(date)
        val dynamicSalam = getSalamPembukaForWaktu(waktuApel, config.salamPembuka)
        val paLabel = config.ketPaJaga.ifBlank { "PA JAGA" }
        val paJagaName = if (config.namaPaJaga.isNotBlank()) config.namaPaJaga else if (config.ketPaJaga.isNotBlank() && config.ketPaJaga != "PA JAGA") "(${config.ketPaJaga})" else "(....................................)"
        val baJagaName = (config.namaBaJaga.ifBlank { config.namaBintaraPiket }).ifBlank { "(....................................)" }
        val taJagaName = config.namaTaJaga.ifBlank { "(....................................)" }

        val headerTitleWord = if (waktuApel == WaktuApel.MALAM_SIAGA) {
            "${config.reportTitle} MALAM SIAGA ${config.reportSubtitle}".trim()
        } else {
            "${config.reportTitle} ${waktuApel.label} ${config.reportSubtitle}".trim()
        }

        return """
        <html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'>
        <head>
          <meta charset='utf-8'>
          <title>Absen Apel KIKAV 2/JRTR</title>
          <!--[if gte mso 9]>
          <xml>
            <w:WordDocument>
              <w:View>Print</w:View>
              <w:Zoom>100</w:Zoom>
              <w:DoNotOptimizeForBrowser/>
            </w:WordDocument>
          </xml>
          <![endif]-->
          <style>
            @page WordSection1 {
                size: 595.3pt 841.9pt; /* A4 Portrait */
                mso-page-orientation: portrait;
                margin: 0.4in 0.35in 0.4in 0.35in;
                mso-header-margin: 0.2in;
                mso-footer-margin: 0.2in;
                mso-paper-source: 0;
            }
            div.WordSection1 { page: WordSection1; }
            body { font-family: Arial, sans-serif; font-size: 7pt; line-height: 1.05; margin: 0; padding: 2px; }
            table { border-collapse: collapse; width: 100%; margin-bottom: 6px; }
            #summaryTable { width: 100%; border-collapse: collapse; table-layout: auto; }
            #summaryTable th, #summaryTable td { border: 1px solid black; padding: 1.5px 1px; text-align: center; vertical-align: middle; font-size: 5.5pt; word-wrap: break-word; }
            #summaryTable th { font-size: 5.5pt; background-color: #d9d9d9; }
            .grid-personnel-table { width: 100%; table-layout: fixed; border-collapse: collapse; margin-top: 6px; }
            .grid-personnel-table td { vertical-align: top; padding: 0; border: 1px solid black; }
            .col-header { background-color: #dbe5f1; font-weight: bold; border-bottom: 1px solid black; padding: 2px; font-size: 6.5pt; text-align: center; }
            .status-group-header { color: #dc2626; font-weight: bold; text-align: left; padding: 1px 2px; border-bottom: 1px solid #ccc; background-color: #f2f2f2; font-size: 5.5pt; }
            .hadir-header { background-color: #00b0f0; color: black; font-weight: bold; text-align: left; padding: 1px 2px; border-bottom: 1px solid black; border-top: 1px solid black; font-size: 5.5pt; }
            .person-row {
                display: block;
                text-align: left;
                padding: 1px 2px !important;
                margin: 1.5px 0px !important;
                line-height: 8.5pt !important;
                mso-line-height-rule: exactly;
                font-size: 6.5pt;
                border: none !important;
                border-bottom: none !important;
            }
            .header-top { font-size: 13pt; font-weight: bold; margin-bottom: 4px; text-align: left; text-transform: uppercase; line-height: 1.15; }
            .header-title { font-size: 9.5pt; font-weight: bold; margin-bottom: 2px; text-align: center; text-transform: uppercase; }
            .header-date { font-size: 8.5pt; font-weight: bold; margin-bottom: 6px; text-align: center; }
            .ttd-table { width: 100%; margin-top: 14px; border: none !important; border-collapse: collapse; }
            .ttd-table td { border: none !important; text-align: center; vertical-align: top; font-size: 7.5pt; }
          </style>
        </head>
        <body>
        <div class="WordSection1">
          <div class="header-top">${config.headerKomando.replace("\n", "<br>")}<br>${config.headerSatuan.replace("\n", "<br>")}</div>
          <div style="font-size:8pt; font-weight:bold; margin-bottom: 4px; text-align: left;">${dynamicSalam.replace("\n", "<br>")}</div>
          <div class="header-title">$headerTitleWord</div>
          <div class="header-date">HARI / TANGGAL: ${formattedDateDisplay.uppercase()}</div>

          <!-- Summary Table -->
          <table id="summaryTable">
            <thead>
              <tr style="background-color: #a6a6a6;">
                <th rowspan="2" style="width:20px;">NO</th>
                <th rowspan="2" style="width:55px;">NAMA</th>
                <th rowspan="2" style="width:25px;">JML</th>
                <th colspan="${statusCountTotal + 2}">KETERANGAN</th>
              </tr>
              <tr style="background-color: #d9d9d9;">
                <th style="color:#dc2626;width:24px;">KRG</th>
                <th style="color:#0284c7;width:24px;">HDR</th>
                $thKeterangan
              </tr>
            </thead>
            <tbody>
              $tbodyRows
              $totalRow
            </tbody>
          </table>

          <!-- Personnel Grid per Platoon (Portrait, No lines between names, rapat) -->
          <table class="grid-personnel-table">
            <tr>
              $detailColumns
            </tr>
          </table>

          <!-- Photo Documentation if attached -->
          $photoHtml

          <!-- Signature Block Table (Pejabat Jaga: BA JAGA, TA JAGA, PA JAGA) -->
          <table class="ttd-table">
            <tr>
              <td style="width:33%;">
                <b>BA JAGA</b><br><br><br><br>
                <span style="text-decoration:underline;font-weight:bold;">${baJagaName.replace("\n", "<br>")}</span>
              </td>
              <td style="width:33%;">
                <b>TA JAGA</b><br><br><br><br>
                <span style="text-decoration:underline;font-weight:bold;">${taJagaName.replace("\n", "<br>")}</span>
              </td>
              <td style="width:34%;">
                <b>Mengetahui,</b><br>
                <b>${paLabel.replace("\n", "<br>")}</b><br><br><br>
                <span style="text-decoration:underline;font-weight:bold;">${paJagaName.replace("\n", "<br>")}</span>
              </td>
            </tr>
          </table>
        </div>
        </body>
        </html>
        """.trimIndent()
    }
}
