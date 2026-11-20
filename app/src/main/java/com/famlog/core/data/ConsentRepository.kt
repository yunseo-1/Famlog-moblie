package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.Consent
import com.famlog.core.model.map
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.ConsentApi
import com.famlog.core.network.UpdateConsentRequest
import com.famlog.core.network.toModel
import javax.inject.Inject
import javax.inject.Singleton

/** 선택 동의. 서버 상태가 기준이라 기기에 저장하지 않고 필요할 때 받는다 */
@Singleton
class ConsentRepository @Inject constructor(
    private val api: ConsentApi,
    private val caller: ApiCaller,
) {
    /** 위치기반서비스 이용약관 + 개인위치정보 수집·이용 (PLC-04) */
    suspend fun location(): ApiResult<Consent> = caller.call { api.list() }.map { response ->
        response.items.firstOrNull { it.type == Consent.LOCATION }?.toModel()
            ?: Consent(Consent.LOCATION, agreed = false, docVersion = null, currentVersion = null, reconsentRequired = false)
    }

    /** [docVersion]은 사용자에게 보여 준 약관 버전. 서버 현재 버전과 다르면 400 */
    suspend fun agreeLocation(docVersion: String): ApiResult<Consent> =
        caller.call { api.update(Consent.LOCATION, UpdateConsentRequest(agreed = true, docVersion = docVersion)).toModel() }

    /** AI 분석 안내 확인 (SET-01). [docVersion]은 보여 준 안내 버전. 다르면 400(field docVersion) */
    suspend fun confirmAiNotice(docVersion: String): ApiResult<Consent> =
        caller.call { api.update(Consent.AI_ANALYSIS, UpdateConsentRequest(agreed = true, docVersion = docVersion)).toModel() }

    /** 철회하면 서버가 내가 올린 사진의 위치도 지운다 (설정 화면 S20에서 쓴다) */
    suspend fun withdrawLocation(): ApiResult<Consent> =
        caller.call { api.update(Consent.LOCATION, UpdateConsentRequest(agreed = false)).toModel() }
}
