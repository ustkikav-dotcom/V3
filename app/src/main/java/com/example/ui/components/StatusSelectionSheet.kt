package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AttendanceConstants
import com.example.data.model.CustomStatusItem
import com.example.data.model.Person
import com.example.ui.theme.CavalryGold
import com.example.ui.theme.CavalryHadirCyan
import com.example.ui.theme.CavalryKurangRed

@Composable
fun StatusSelectionDialog(
    groupKey: String,
    person: Person,
    customStatuses: List<CustomStatusItem>,
    onStatusSelected: (newStatus: String, isKeteranganKhusus: Boolean) -> Unit,
    onManageStatusesClicked: () -> Unit,
    onDismiss: () -> Unit
) {
    val unitDisplayName = AttendanceConstants.DISPLAY_NAMES[groupKey] ?: groupKey
    var isKhusus by remember(person) { mutableStateOf(person.isKeteranganKhusus) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .testTag("status_selection_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Unit and Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PILIH KETERANGAN APEL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).testTag("btn_close_status_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Person Name Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEFF6FF))
                        .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(8.dp))
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = person.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1D4ED8)
                            )
                            if (isKhusus) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFFEF3C7))
                                        .border(0.5.dp, Color(0xFFD97706), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "⭐ KHUSUS",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF92400E)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "$unitDisplayName • Status Saat Ini: ${person.status}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (person.status == "HADIR") Color(0xFF047857) else CavalryKurangRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // FITUR KETERANGAN KHUSUS (KEBAL RESET APEL)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isKhusus = !isKhusus }
                        .testTag("card_toggle_keterangan_khusus"),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isKhusus) Color(0xFFFEF9C3) else Color(0xFFF8FAFC)
                    ),
                    border = BorderStroke(1.dp, if (isKhusus) CavalryGold else Color(0xFFCBD5E1))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isKhusus) Color(0xFFD97706) else Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "⭐ Keterangan Khusus (Kebal Reset)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isKhusus) Color(0xFF92400E) else Color(0xFF1E293B)
                                )
                                Text(
                                    text = if (isKhusus)
                                        "Aktif: Keterangan TIDAK akan ikut tereset ke Hadir saat apel disetel ulang"
                                    else
                                        "Nonaktif: Keterangan biasa, ikut kembali Hadir saat reset apel",
                                    fontSize = 9.sp,
                                    color = if (isKhusus) Color(0xFFB45309) else Color(0xFF64748B),
                                    lineHeight = 12.sp
                                )
                            }
                        }
                        Switch(
                            checked = isKhusus,
                            onCheckedChange = { isKhusus = it },
                            modifier = Modifier.testTag("switch_keterangan_khusus"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFD97706)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Primary HADIR Button
                Button(
                    onClick = { onStatusSelected("HADIR", isKhusus) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_status_hadir"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CavalryHadirCyan,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HADIR (LENGKAP)",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pilih Status Keterangan:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 4-Column Grid of Custom & Default Statuses
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(customStatuses) { statusItem ->
                        val isCurrent = person.status.equals(statusItem.code, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isCurrent) Color(0xFFFEE2E2) else Color(0xFFF1F5F9)
                                )
                                .border(
                                    width = if (isCurrent) 1.5.dp else 1.dp,
                                    color = if (isCurrent) CavalryKurangRed else Color(0xFFCBD5E1),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    onStatusSelected(statusItem.code, isKhusus)
                                }
                                .padding(vertical = 8.dp, horizontal = 2.dp)
                                .testTag("btn_status_${statusItem.code}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = statusItem.code,
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                                color = if (isCurrent) CavalryKurangRed else Color(0xFF1E293B),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Cancel Button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("btn_cancel_status"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Batal Kembali",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
