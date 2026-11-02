package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AttendanceConstants
import com.example.data.model.ReportFormatType
import com.example.data.model.ReportTemplateConfig
import com.example.data.model.WaktuApel
import com.example.ui.theme.CavalryGold
import com.example.ui.theme.CavalryGreenDark
import com.example.ui.theme.CavalryGreenPrimary
import java.io.File

@Composable
fun ReportExportScreen(
    reportText: String,
    date: String,
    waktuApel: WaktuApel,
    reportConfig: ReportTemplateConfig,
    hasReportPhoto: Boolean,
    reportPhotoFile: File?,
    onFormatTypeSelected: (ReportFormatType) -> Unit,
    onOpenCustomReportDialog: () -> Unit,
    onGenerateWordFile: () -> File,
    onGetShareWordIntent: (File) -> Intent,
    onGetOpenWordIntent: (File) -> Intent,
    onAttachPhoto: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    onGetShareReportIntent: (String) -> Intent,
    malamSiagaCompanionUnit: String = "TON I",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val formatScrollState = rememberScrollState()
    var generatedWordFile by remember { mutableStateOf<File?>(null) }

    // Android Modern Zero-Permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onAttachPhoto(uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp)
            .testTag("report_export_screen")
    ) {
        // --- 0. KHUSUS APEL MALAM SIAGA: LAPORAN TERPADU (1 LAPORAN) ---
        if (waktuApel == WaktuApel.MALAM_SIAGA) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_malam_siaga_report_info"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                border = BorderStroke(1.dp, Color(0xFFFBBF24))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFB45309)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "LAPORAN TERPADU APEL MALAM SIAGA",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                        Text(
                            text = "Laporan Apel Malam Siaga digabung menjadi 1 laporan terpadu. Khusus REMAJA dikelompokkan sendiri tanpa tercampur peleton lain. Jumlah Kuat Nyata, Hadir, dan Kurang TIDAK dijadikan satu (Jumlah Remaja sendiri, Jumlah Peleton Siaga sendiri).",
                            fontSize = 11.sp,
                            color = Color(0xFF78350F),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // --- 1. DOKUMENTASI FOTO APEL CARD ---
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_photo_documentation"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE11D48)), // Rose / Photo Red
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "DOKUMENTASI FOTO APEL",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = if (hasReportPhoto) "Foto siap dilampirkan ke Word & WA" else "Belum ada foto dokumentasi",
                                fontSize = 11.sp,
                                color = if (hasReportPhoto) Color(0xFF16A34A) else Color(0xFF64748B),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (hasReportPhoto) {
                        IconButton(
                            onClick = onRemovePhoto,
                            modifier = Modifier.size(32.dp).testTag("btn_remove_photo")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Hapus Foto",
                                tint = Color(0xFFDC2626)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (hasReportPhoto && reportPhotoFile != null && reportPhotoFile.exists()) {
                    // Photo Preview
                    val bitmap = remember(reportPhotoFile.lastModified()) {
                        try {
                            BitmapFactory.decodeFile(reportPhotoFile.absolutePath)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (bitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Foto Dokumentasi Apel",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("btn_change_photo"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ganti Foto", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                try {
                                    val shareIntent = onGetShareReportIntent(reportText)
                                    context.startActivity(Intent.createChooser(shareIntent, "Kirim Foto & Laporan Apel"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(40.dp)
                                .testTag("btn_share_photo_wa"),
                            colors = ButtonDefaults.buttonColors(containerColor = CavalryGreenPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan Foto & Teks WA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Empty Photo State: Attach Button
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_attach_photo"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Lampirkan Foto Apel ${waktuApel.label}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Foto otomatis disematkan ke dalam Dokumen Word (.doc) dan dikirim bersama laporan WA.",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 2. Word Export Card (With rapat text without lines) ---
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_word_export"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2B579A)), // Word Blue
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "W",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "EKSPOR KE MICROSOFT WORD (.DOC)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Format Portrait A4 • TTD Pejabat Piket",
                                fontSize = 11.sp,
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.SemiBold
                            )
                            if (hasReportPhoto) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFEFF6FF))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "+ FOTO TERLAMPIR",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        try {
                            val file = onGenerateWordFile()
                            generatedWordFile = file
                            Toast.makeText(
                                context,
                                "Dokumen Word siap: ${file.name}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Gagal membuat dokumen Word: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_export_word"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2B579A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasReportPhoto) "Buat Dokumen Word + Foto (.doc)" else "Buat Dokumen Word (.doc)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // If file is generated, show Open & Share buttons
                generatedWordFile?.let { file ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF0FDF4))
                            .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = file.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "(${file.length() / 1024} KB)",
                                    fontSize = 10.sp,
                                    color = Color(0xFF15803D)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val openIntent = onGetOpenWordIntent(file)
                                            context.startActivity(openIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                "Tidak ada aplikasi pembuka Word terinstal.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("btn_open_word_file"),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Buka File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        try {
                                            val shareIntent = onGetShareWordIntent(file)
                                            val chooser = Intent.createChooser(shareIntent, "Bagikan Dokumen Word Apel")
                                            context.startActivity(chooser)
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                "Gagal membagikan: ${e.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("btn_share_word_file"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF16A34A),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Kirim File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 3. Format Laporan Custom & Mode Selection ---
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "FORMAT LAPORAN TEKS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CavalryGreenDark
                        )
                        Text(
                            text = "Pilih susunan format atau kustomisasi sesuai kebutuhan",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    TextButton(
                        onClick = onOpenCustomReportDialog,
                        modifier = Modifier.testTag("btn_edit_format_laporan")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = CavalryGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Edit Format",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CavalryGold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Format Type Selection Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(formatScrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReportFormatType.values().forEach { format ->
                        val isSelected = reportConfig.formatType == format
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFormatTypeSelected(format) },
                            label = { Text(format.title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CavalryGreenPrimary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("chip_format_${format.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons for WhatsApp / Text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Laporan Absen Apel", reportText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(
                                context,
                                "Format teks laporan berhasil disalin!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("btn_copy_report"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CavalryGreenPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Salin Teks WA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            try {
                                val shareIntent = onGetShareReportIntent(reportText)
                                val chooser = Intent.createChooser(shareIntent, "Bagikan Laporan Apel")
                                context.startActivity(chooser)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("btn_share_report"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CavalryGold,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasReportPhoto) "Bagikan Foto+WA" else "Bagikan WA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 5. Pratinjau Laporan Final (Hanya Menampilkan Data Jadi / Non-Editable) ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("final_report_preview_card"),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F766E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "PRATINJAU LAPORAN FINAL",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Format: ${reportConfig.formatType.title} • Data Resmi Siap Kirim",
                                fontSize = 10.sp,
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFDCFCE7))
                            .border(0.5.dp, Color(0xFF86EFAC), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "DATA SUDAH JADI",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF15803D)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Wadah Pratinjau Teks Laporan Bersih (Read-Only)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 460.dp)
                        .testTag("readonly_report_preview"),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    val previewScrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(previewScrollState)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = reportText,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pratinjau data jadi hasil absen resmi KIKAV 2 (Read-Only). Gunakan tombol Salin Teks WA / Bagikan di atas untuk mengirim laporan.",
                        fontSize = 10.5.sp,
                        color = Color(0xFF475569),
                        fontWeight = FontWeight.Medium,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
