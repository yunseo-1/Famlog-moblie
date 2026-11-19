package com.famlog.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.famlog.core.data.LocationSource
import com.famlog.core.model.Coordinates
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 플랫폼 LocationManager로 현재 위치를 한 번 받는다 (Play 서비스 위치 라이브러리 없이).
 * Android 11 이상은 getCurrentLocation, 그 아래는 마지막으로 알려진 위치를 쓴다.
 */
@Singleton
class AndroidLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationSource {

    private val manager: LocationManager? = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission") // 아래 hasPermission()으로 확인한다
    override suspend fun current(): Coordinates? {
        val manager = manager ?: return null
        if (!hasPermission()) return null
        val providers = providers(manager)
        if (providers.isEmpty()) return null
        val fresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            withTimeoutOrNull(TIMEOUT_MS) { currentLocation(manager, providers.first()) }
        } else {
            null
        }
        val location = fresh ?: providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
        return location?.let { Coordinates(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun currentLocation(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            manager.getCurrentLocation(provider, signal, context.mainExecutor) { location -> continuation.resume(location) }
        }

    /** 대략적 위치면 충분하다: 통합(fused) → 네트워크 → GPS 순으로 켜져 있는 것 */
    private fun providers(manager: LocationManager): List<String> {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        return candidates.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds
    abstract fun bindLocationSource(impl: AndroidLocationSource): LocationSource
}
