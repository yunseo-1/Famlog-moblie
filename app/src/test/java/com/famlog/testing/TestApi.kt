package com.famlog.testing

import com.famlog.core.network.ApiCaller
import com.famlog.core.network.ApiErrorParser
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** 앱과 같은 Json 설정 (NetworkModule) */
val testJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

inline fun <reified T> MockWebServer.api(): T = Retrofit.Builder()
    .baseUrl(url("/api/v1/"))
    .client(OkHttpClient())
    .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
    .build()
    .create(T::class.java)

fun testApiCaller() = ApiCaller(ApiErrorParser(testJson))

fun jsonResponse(body: String, code: Int = 200): MockResponse =
    MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

fun errorResponse(code: Int, errorCode: String, message: String = "서버 안내"): MockResponse =
    jsonResponse("""{"code":"$errorCode","message":"$message","traceId":"t-1"}""", code)
