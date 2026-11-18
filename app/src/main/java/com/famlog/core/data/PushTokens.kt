package com.famlog.core.data

import com.famlog.core.common.ApplicationScope
import com.famlog.core.datastore.TokenStore
import com.famlog.core.model.ApiResult
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.AppVersion
import com.famlog.core.network.DeviceApi
import com.famlog.core.network.DeviceTokenRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** FCM 등록 토큰을 받는 곳. Play 서비스가 없거나 실패하면 null */
interface PushTokenSource {
    suspend fun token(): String?
}

/**
 * 푸시 토큰을 서버에 올린다 (NOTI-01). 앱 시작·로그인 직후·onNewToken 때 부른다.
 * 같은 토큰을 다시 보내도 서버는 앱 버전만 바꾸므로 여러 번 불러도 된다.
 * 로그아웃하면 서버가 토큰을 지우므로 앱은 DELETE를 부르지 않는다.
 */
@Singleton
class PushTokenRepository @Inject constructor(
    private val api: DeviceApi,
    private val caller: ApiCaller,
    private val tokenStore: TokenStore,
    private val source: PushTokenSource,
    @AppVersion private val appVersion: String,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /** 로그인하지 않았거나 토큰을 받지 못하면 null (다음 시작 때 다시 올린다) */
    suspend fun register(token: String? = null): ApiResult<Unit>? {
        if (tokenStore.get() == null) return null
        val value = token ?: source.token() ?: return null
        return caller.call { api.putToken(DeviceTokenRequest(value, appVersion)) }
    }

    /** 화면·서비스를 붙잡지 않고 앱 범위에서 올린다 */
    fun registerInBackground(token: String? = null) {
        scope.launch { register(token) }
    }
}
