package com.famlog.feature.place

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.famlog.BuildConfig
import com.famlog.R
import com.famlog.core.designsystem.theme.FamTheme
import com.famlog.core.model.Coordinates
import com.famlog.core.model.MapBounds
import com.famlog.core.model.Place
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLogger
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles

/** 지도 카메라를 ViewModel 이벤트로 움직이기 위한 손잡이 */
@Stable
class KakaoMapController {
    internal var map: KakaoMap? by mutableStateOf(null)

    fun moveTo(center: Coordinates) {
        map?.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(center.lat, center.lng), NEARBY_ZOOM))
    }

    private companion object {
        /** 반경 3km가 한 화면에 들어오는 정도 */
        const val NEARBY_ZOOM = 13
    }
}

/**
 * 카카오 지도 (PLC-04, Figma 04 Map). 화면 수명에 맞춰 resume·pause하고 나가면 finish한다.
 * 핀 = category/place 알약 + 장소명, 내 위치 = 파란 점 + 흰 테두리. 카메라 이동이 끝날 때마다 화면 영역을 알린다.
 * 앱 키가 없으면(CI) 지도 대신 안내 문구.
 */
@Composable
fun KakaoMapView(
    initialCenter: Coordinates,
    pins: List<Place>,
    myLocation: Coordinates?,
    controller: KakaoMapController,
    onViewportChanged: (MapBounds) -> Unit,
    onPinClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
        MapNotice(stringResource(R.string.place_map_unavailable), modifier)
        return
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = FamTheme.colors
    val currentOnViewport by rememberUpdatedState(onViewportChanged)
    val currentOnPinClick by rememberUpdatedState(onPinClick)
    var failed by remember { mutableStateOf(false) }

    val mapView = remember {
        disableSdkLogs()
        MapView(context).apply {
            start(
                object : MapLifeCycleCallback() {
                    override fun onMapDestroy() {
                        controller.map = null
                    }

                    // 인증 실패(카카오맵 사용 설정 꺼짐 등)·엔진 오류. 원인은 화면에만 알린다
                    override fun onMapError(error: Exception) {
                        failed = true
                    }
                },
                object : KakaoMapReadyCallback() {
                    override fun getPosition(): LatLng = LatLng.from(initialCenter.lat, initialCenter.lng)

                    override fun getZoomLevel(): Int = INITIAL_ZOOM

                    override fun onMapReady(map: KakaoMap) {
                        controller.map = map
                        map.setOnCameraMoveEndListener { moved, _, _ -> moved.visibleBounds(this@apply)?.let { currentOnViewport(it) } }
                        map.setOnLabelClickListener { _, _, label ->
                            (label.tag as? Long)?.let { currentOnPinClick(it) }
                            true
                        }
                        post { map.visibleBounds(this@apply)?.let { currentOnViewport(it) } }
                    }
                },
            )
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.finish()
        }
    }

    val map = controller.map
    // 비트맵은 이미 기기 밀도로 그렸으니 SDK가 다시 키우지 않게 setApplyDpScale(false)
    LaunchedEffect(map, pins, myLocation) {
        val layer = map?.labelManager?.layer ?: return@LaunchedEffect
        layer.removeAll()
        pins.forEach { place ->
            val pin = pinBitmap(context, place.name, colors.categoryPlace.toArgb(), colors.textOnBrand.toArgb())
            layer.addLabel(
                LabelOptions.from(LatLng.from(place.lat, place.lng))
                    .setStyles(LabelStyles.from(LabelStyle.from(pin).setAnchorPoint(0.5f, 1f).setApplyDpScale(false)))
                    .setClickable(true)
                    .setTag(place.id),
            )
        }
        myLocation?.let { me ->
            val dot = dotBitmap(context, colors.categorySchedule.toArgb(), colors.bgDefault.toArgb())
            layer.addLabel(LabelOptions.from(LatLng.from(me.lat, me.lng)).setStyles(LabelStyles.from(LabelStyle.from(dot).setApplyDpScale(false))))
        }
    }

    if (failed) {
        MapNotice(stringResource(R.string.place_map_error), modifier)
    } else {
        AndroidView(factory = { mapView }, modifier = modifier)
    }
}

@Composable
private fun MapNotice(text: String, modifier: Modifier) {
    Box(modifier.background(FamTheme.colors.surfaceMuted), contentAlignment = Alignment.Center) {
        Text(text, style = FamTheme.type.bodyMedium, color = FamTheme.colors.textSecondary)
    }
}

/**
 * SDK는 카메라 이동·좌표 변환(예: newCenterPoint(lat, lng))과 인증 요청 헤더를 logcat에 남긴다.
 * 내 주변에서는 내 위치가 찍히므로 끈다 (좌표는 로그에 남기지 않는다 — CLAUDE.md 3장, 사양서 8.6).
 * 스위치가 네이티브라 MapView보다 먼저 라이브러리를 올린다 (이미 올라와 있으면 아무 일 없음).
 */
private fun disableSdkLogs() {
    MapLogger.ENABLE = false
    runCatching {
        System.loadLibrary(SDK_NATIVE_LIBRARY)
        MapLogger.setLogEnable(false)
        MapLogger.setCameraLogEnable(false)
        MapLogger.setCommonLogEnable(false)
        MapLogger.setLabelLogEnable(false)
    }
}

/** 지금 화면의 남서·북동 꼭짓점. 아직 크기가 없으면 null */
private fun KakaoMap.visibleBounds(view: MapView): MapBounds? {
    if (view.width == 0 || view.height == 0) return null
    val sw = fromScreenPoint(0, view.height) ?: return null
    val ne = fromScreenPoint(view.width, 0) ?: return null
    return MapBounds(sw.latitude, sw.longitude, ne.latitude, ne.longitude)
}

/** Figma Pin: pill(bg category/place, radius full, padding 4/10/4/6) = place 아이콘 16(on-brand) + 장소명(Caption on-brand) */
private fun pinBitmap(context: Context, name: String, background: Int, foreground: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = foreground
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, context.resources.displayMetrics)
        typeface = Typeface.DEFAULT_BOLD
    }
    val label = if (name.length > MAX_PIN_NAME) name.take(MAX_PIN_NAME) + "…" else name
    val icon = 16 * density
    val padStart = 6 * density
    val padEnd = 10 * density
    val padV = 4 * density
    val gap = 4 * density
    val height = maxOf(icon, text.fontMetrics.let { it.descent - it.ascent }) + padV * 2
    val width = padStart + icon + gap + text.measureText(label) + padEnd
    val bitmap = createBitmap(width.toInt() + 1, height.toInt() + 1)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
    canvas.drawRoundRect(RectF(0f, 0f, width, height), height / 2, height / 2, fill)
    ContextCompat.getDrawable(context, R.drawable.ic_tab_place)?.mutate()?.let { drawable ->
        drawable.setTint(foreground)
        val top = ((height - icon) / 2).toInt()
        drawable.setBounds(padStart.toInt(), top, (padStart + icon).toInt(), (top + icon).toInt())
        drawable.draw(canvas)
    }
    val baseline = height / 2 - (text.fontMetrics.ascent + text.fontMetrics.descent) / 2
    canvas.drawText(label, padStart + icon + gap, baseline, text)
    return bitmap
}

/** 내 위치: 파란 점 14 + 흰 테두리 3 */
private fun dotBitmap(context: Context, fill: Int, border: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (14 + 3 * 2) * density
    val bitmap = createBitmap(size.toInt() + 1, size.toInt() + 1)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = border
    canvas.drawCircle(size / 2, size / 2, size / 2, paint)
    paint.color = fill
    canvas.drawCircle(size / 2, size / 2, 7 * density, paint)
    return bitmap
}

private const val INITIAL_ZOOM = 14
private const val SDK_NATIVE_LIBRARY = "K3fAndroid"
private const val MAX_PIN_NAME = 10
