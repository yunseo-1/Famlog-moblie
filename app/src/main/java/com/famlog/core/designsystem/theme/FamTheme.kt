// 기준: docs/design/design-tokens.json (Figma 변수, 2026-11-12)
// 폰트: res/font/noto_sans_kr_{regular,medium,bold}.ttf (Google Fonts, OFL 라이선스)
package com.famlog.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famlog.R

@Immutable
data class FamColors(
    val bgDefault: Color = Color(0xFFFFFFFF),
    val bgSubtle: Color = Color(0xFFF7F7F8),
    val surfaceMuted: Color = Color(0xFFEFEFF1),
    val surfacePlaceholder: Color = Color(0xFFE1E1E5),
    val borderDefault: Color = Color(0xFFE1E1E5),
    val borderStrong: Color = Color(0xFFC8C8CE),
    val textPrimary: Color = Color(0xFF1C1C21),
    val textSecondary: Color = Color(0xFF4A4A52),
    val textTertiary: Color = Color(0xFF8A8A93),
    val textOnBrand: Color = Color(0xFFFFFFFF),
    val brandPrimary: Color = Color(0xFFC94A26),
    val brandAccent: Color = Color(0xFFE8643C),
    val brandSubtle: Color = Color(0xFFFFF1EB),
    val bubbleMine: Color = Color(0xFFFFDCCB),
    val bubbleOther: Color = Color(0xFFEFEFF1),
    val categorySchedule: Color = Color(0xFF2F6FED),
    val categoryScheduleBg: Color = Color(0xFFEAF1FF),
    val categoryAnniversary: Color = Color(0xFFC2255C),
    val categoryAnniversaryBg: Color = Color(0xFFFCEBF3),
    val categoryPlace: Color = Color(0xFF1F9D6B),
    val categoryPlaceBg: Color = Color(0xFFE6F6EF),
    val categoryTodo: Color = Color(0xFF7A4FE0),
    val categoryTodoBg: Color = Color(0xFFF1ECFF),
    val statusDanger: Color = Color(0xFFD93A3A),
    /** 사진·동영상 전체 화면 보기 배경과 그 위 글자·아이콘 (Figma 미디자인, 사진이 잘 보이도록 검정) */
    val mediaViewerBackground: Color = Color(0xFF000000),
    val onMediaViewer: Color = Color(0xFFFFFFFF),
    /** 사진 위 겹침 (올리는 중·동영상 길이) */
    val mediaOverlay: Color = Color(0x66000000),
    /**
     * 구성원 캘린더 색 팔레트 12색 (GRP-04). 서버 famlog.group.calendar-palette와 순서·값이 같아야 한다.
     * 앞 4색은 design-tokens.json memberColorSamples, 나머지는 서버 설정의 임시값 (Figma 확정 전).
     */
    val memberPalette: List<Color> = listOf(
        Color(0xFF14A3B8), Color(0xFFE5568D), Color(0xFFE8A33C), Color(0xFF7BB33A),
        Color(0xFF4C8DF0), Color(0xFFA66BD3), Color(0xFFD9534F), Color(0xFF8D6E63),
        Color(0xFF607D8B), Color(0xFFF2C94C), Color(0xFF5C6BC0), Color(0xFFFF8A65),
    ),
    // 소셜 로그인 버튼: 각 사의 브랜드 가이드 색 (카카오 로그인 디자인 가이드, Google Sign-In 브랜딩 가이드)
    val kakaoContainer: Color = Color(0xFFFEE500),
    val kakaoLabel: Color = Color(0xD9000000),
    val googleContainer: Color = Color(0xFFFFFFFF),
    val googleBorder: Color = Color(0xFF747775),
    val googleLabel: Color = Color(0xFF1F1F1F),
)

/** 팔레트 색을 서버 형식 "#RRGGBB"로 바꾼다 */
fun Color.toHexRgb(): String = "#%06X".format(toArgb() and 0xFFFFFF)

/** 후보 카드·칩·캘린더·지도 핀에 공통으로 쓰는 유형 색 (패턴 B) */
enum class CandidateType { SCHEDULE, ANNIVERSARY, TODO, PLACE }

fun FamColors.categoryOf(type: CandidateType): Pair<Color, Color> = when (type) {
    CandidateType.SCHEDULE -> categorySchedule to categoryScheduleBg
    CandidateType.ANNIVERSARY -> categoryAnniversary to categoryAnniversaryBg
    CandidateType.TODO -> categoryTodo to categoryTodoBg
    CandidateType.PLACE -> categoryPlace to categoryPlaceBg
}

object FamDimens {
    val xs: Dp = 4.dp; val sm: Dp = 8.dp; val md: Dp = 12.dp; val lg: Dp = 16.dp
    val xl: Dp = 20.dp; val xxl: Dp = 24.dp; val xxxl: Dp = 32.dp
    val radiusSm: Dp = 8.dp; val radiusMd: Dp = 12.dp; val radiusLg: Dp = 16.dp
    val screenPadding: Dp = 16.dp; val appBarHeight: Dp = 56.dp; val bottomNavHeight: Dp = 64.dp
    val minTouchTarget: Dp = 48.dp; val fabSize: Dp = 56.dp
    val candidateCardWidth: Dp = 280.dp; val bubbleMaxTextWidth: Dp = 200.dp; val chatIndent: Dp = 40.dp
}

val NotoSansKr = FontFamily(
    Font(R.font.noto_sans_kr_regular, FontWeight.Normal),
    Font(R.font.noto_sans_kr_medium, FontWeight.Medium),
    Font(R.font.noto_sans_kr_bold, FontWeight.Bold),
)

@Immutable
data class FamType(
    val titleLarge: TextStyle = TextStyle(fontFamily = NotoSansKr, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
    val titleMedium: TextStyle = TextStyle(fontFamily = NotoSansKr, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp),
    val bodyLarge: TextStyle = TextStyle(fontFamily = NotoSansKr, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    val bodyMedium: TextStyle = TextStyle(fontFamily = NotoSansKr, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    val bodyMediumStrong: TextStyle = TextStyle(fontFamily = NotoSansKr, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    val label: TextStyle = TextStyle(fontFamily = NotoSansKr, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    val caption: TextStyle = TextStyle(fontFamily = NotoSansKr, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
)

val LocalFamColors = staticCompositionLocalOf { FamColors() }
val LocalFamType = staticCompositionLocalOf { FamType() }

object FamTheme {
    val colors: FamColors @Composable get() = LocalFamColors.current
    val type: FamType @Composable get() = LocalFamType.current
}

/** Material 3 컴포넌트(Scaffold, NavigationBar 등)가 기본 보라색 대신 FamLog 토큰을 쓰게 연결한다 */
private fun FamColors.toMaterialColorScheme() = lightColorScheme(
    primary = brandPrimary,
    onPrimary = textOnBrand,
    primaryContainer = brandSubtle,
    onPrimaryContainer = brandPrimary,
    secondaryContainer = brandSubtle,
    onSecondaryContainer = brandPrimary,
    background = bgDefault,
    onBackground = textPrimary,
    surface = bgDefault,
    onSurface = textPrimary,
    surfaceVariant = surfaceMuted,
    onSurfaceVariant = textSecondary,
    surfaceContainer = bgDefault,
    outline = borderStrong,
    outlineVariant = borderDefault,
    error = statusDanger,
)

private fun FamType.toMaterialTypography() = Typography(
    titleLarge = titleLarge,
    titleMedium = titleMedium,
    bodyLarge = bodyLarge,
    bodyMedium = bodyMedium,
    labelLarge = bodyMediumStrong,
    labelMedium = label,
    bodySmall = caption,
    labelSmall = caption,
)

@Composable
fun FamLogTheme(content: @Composable () -> Unit) {
    // 다크 모드는 아직 디자인 없음 (Figma Color 컬렉션에 Dark 모드 추가 예정)
    val colors = FamColors()
    val type = FamType()
    CompositionLocalProvider(
        LocalFamColors provides colors,
        LocalFamType provides type,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = type.toMaterialTypography(),
            content = content,
        )
    }
}
