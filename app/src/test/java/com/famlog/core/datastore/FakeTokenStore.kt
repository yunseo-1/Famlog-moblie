package com.famlog.core.datastore

import com.famlog.core.model.AuthTokens

/** 메모리 토큰 저장소. 저장 횟수를 세어 재발급 횟수 검증에 쓴다 */
class FakeTokenStore(initial: AuthTokens? = null) : TokenStore {
    @Volatile var tokens: AuthTokens? = initial
    @Volatile var saveCount = 0

    override suspend fun get(): AuthTokens? = tokens

    override suspend fun save(tokens: AuthTokens) {
        this.tokens = tokens
        saveCount++
    }

    override suspend fun clear() {
        tokens = null
    }
}
