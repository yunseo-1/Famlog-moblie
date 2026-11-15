package com.famlog.debug

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.famlog.core.designsystem.theme.FamDimens
import com.famlog.core.designsystem.theme.FamLogTheme
import com.famlog.core.designsystem.theme.FamTheme

/**
 * S6 기술 검증 스파이크 (디버그 빌드 전용, 릴리스에 포함되지 않음).
 * Photo Picker로 고른 사진에서 EXIF 촬영 시각·GPS가 읽히는지, ACCESS_MEDIA_LOCATION 권한에 따라 달라지는지 확인한다.
 * 결과는 docs/handoff/HANDOFF.md 6장에 기록한다. 좌표는 화면에만 보이고 로그에 남기지 않는다 (사양서 9.8).
 */
class ExifSpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FamLogTheme { ExifSpikeScreen() } }
    }
}

@Composable
private fun ExifSpikeScreen() {
    val context = LocalContext.current
    var report by remember { mutableStateOf("사진을 골라 주세요.") }
    var hasMediaLocation by remember { mutableStateOf(context.hasMediaLocationPermission()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if (uris.isNotEmpty()) report = uris.joinToString("\n\n") { context.describeExif(it) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasMediaLocation = it
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(FamDimens.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(FamDimens.md),
    ) {
        Text("EXIF 검증 (S6)", style = FamTheme.type.titleLarge, color = FamTheme.colors.textPrimary)
        Text(
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Photo Picker 사용 가능: ${ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)}\n" +
                "ACCESS_MEDIA_LOCATION: ${if (hasMediaLocation) "허용" else "없음"}",
            style = FamTheme.type.bodyMedium,
            color = FamTheme.colors.textSecondary,
        )
        Button(onClick = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) { Text("Photo Picker로 사진 고르기 (최대 5장)") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasMediaLocation) {
            OutlinedButton(onClick = { permission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) }) {
                Text("ACCESS_MEDIA_LOCATION 요청")
            }
        }
        Text(report, style = FamTheme.type.bodyMedium, color = FamTheme.colors.textPrimary)
    }
}

private fun android.content.Context.hasMediaLocationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/** 받은 URI 그대로 읽은 값과, setRequireOriginal로 원본을 요청해 읽은 값을 나란히 보여준다 */
private fun android.content.Context.describeExif(uri: Uri): String {
    val plain = readExif(uri)
    val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            readExif(MediaStore.setRequireOriginal(uri))
        } catch (e: Exception) {
            "실패 (${e.javaClass.simpleName})"
        }
    } else {
        "API 29 미만이라 해당 없음"
    }
    return "URI: ${uri.authority}${uri.path?.substringBeforeLast('/')}/…\n" +
        "· 그대로 읽기 → $plain\n" +
        "· setRequireOriginal → $original"
}

private fun android.content.Context.readExif(uri: Uri): String = try {
    contentResolver.openInputStream(uri)?.use { input ->
        val exif = ExifInterface(input)
        val takenAt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        val gps = exif.latLong?.let { (lat, lng) -> "%.4f, %.4f".format(lat, lng) }
        "촬영 시각: ${takenAt ?: "없음"} / GPS: ${gps ?: "없음"}"
    } ?: "열 수 없음"
} catch (e: Exception) {
    "실패 (${e.javaClass.simpleName})"
}
