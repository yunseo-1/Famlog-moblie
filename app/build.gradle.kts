import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // app/google-services.json → Firebase 설정 리소스 (FCM, S11)
    alias(libs.plugins.google.services)
}

/** 소셜 로그인 키: local.properties → 환경 변수 → 빈 값 순서. 비어 있으면 해당 로그인 버튼이 "설정 필요"를 띄운다 */
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun secret(name: String): String = localProperties.getProperty(name) ?: System.getenv(name) ?: ""
val kakaoNativeAppKey = secret("FAMLOG_KAKAO_NATIVE_APP_KEY")
val googleWebClientId = secret("FAMLOG_GOOGLE_WEB_CLIENT_ID")

/**
 * 릴리스(S22) 값. local.properties 또는 CI 환경 변수에서 읽는다.
 * - 주소 두 개와 소셜 키는 릴리스 빌드에 꼭 있어야 한다. 비어 있으면 preReleaseBuild에서 빌드를 멈춘다
 * - 업로드 키가 없으면 서명하지 않은 AAB/APK가 나온다 (로컬 R8 확인용)
 */
val releaseApiBaseUrl = secret("FAMLOG_RELEASE_API_BASE_URL")
val releaseWebBaseUrl = secret("FAMLOG_RELEASE_WEB_BASE_URL")
val uploadStoreFile = secret("FAMLOG_UPLOAD_STORE_FILE")

android {
    namespace = "com.famlog"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.famlog"
        minSdk = 26
        targetSdk = 37
        // CI(release.yml)가 실행 번호로 넣는다. 로컬 빌드는 1
        versionCode = secret("FAMLOG_VERSION_CODE").toIntOrNull() ?: 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeAppKey\"")
        // Google 로그인 ID 토큰의 aud. 백엔드 GOOGLE_CLIENT_ID(웹 클라이언트)와 같은 값
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        // 카카오계정 로그인 후 돌아오는 스킴 kakao{앱 키}://oauth (키가 없으면 쓰이지 않는 값)
        manifestPlaceholders["kakaoScheme"] = "kakao" + kakaoNativeAppKey.ifBlank { "unset" }
    }

    signingConfigs {
        if (uploadStoreFile.isNotBlank()) {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = secret("FAMLOG_UPLOAD_STORE_PASSWORD")
                keyAlias = secret("FAMLOG_UPLOAD_KEY_ALIAS")
                keyPassword = secret("FAMLOG_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // 에뮬레이터에서 로컬 서버(bootRun) 접속. 평문 HTTP는 src/debug network security config에서만 허용
            // 실기기(USB + adb reverse tcp:8080 tcp:8080)는 local.properties FAMLOG_API_BASE_URL=http://localhost:8080/api/v1/
            val apiBaseUrl = secret("FAMLOG_API_BASE_URL").ifBlank { "http://10.0.2.2:8080/api/v1/" }
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
            // 웹 페이지(계정 삭제 안내 등). 로컬은 API 서버 루트가 웹 페이지도 제공한다
            val webBaseUrl = secret("FAMLOG_WEB_BASE_URL").ifBlank { apiBaseUrl.substringBefore("api/v1/") }
            buildConfigField("String", "WEB_BASE_URL", "\"$webBaseUrl\"")
        }
        release {
            // 운영: https://api.famlog.kr/api/v1/ , 웹: https://famlog.kr/ (사양서 17, 백엔드 S22)
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
            buildConfigField("String", "WEB_BASE_URL", "\"$releaseWebBaseUrl\"")
            signingConfig = signingConfigs.findByName("upload")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

/** 릴리스 빌드를 시작할 때만 검사한다. 디버그 빌드·CI 단위 테스트에는 영향 없음 */
val releaseProblems = buildList {
    if (!releaseApiBaseUrl.matches(Regex("https://.+/api/v1/"))) add("FAMLOG_RELEASE_API_BASE_URL (https://…/api/v1/)")
    if (!releaseWebBaseUrl.matches(Regex("https://.+/"))) add("FAMLOG_RELEASE_WEB_BASE_URL (https://…/)")
    if (kakaoNativeAppKey.isBlank()) add("FAMLOG_KAKAO_NATIVE_APP_KEY")
    if (googleWebClientId.isBlank()) add("FAMLOG_GOOGLE_WEB_CLIENT_ID")
}
tasks.named { it == "preReleaseBuild" }.configureEach {
    // 스크립트 속성을 직접 잡으면 configuration cache에 저장할 수 없어 지역 값으로 복사한다
    val problems = releaseProblems.toList()
    doFirst {
        check(problems.isEmpty()) { "릴리스 빌드에 필요한 값이 없거나 형식이 틀렸습니다: ${problems.joinToString()}" }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    debugImplementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kakao.sdk.user)
    implementation(libs.kakao.map)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    debugImplementation(libs.androidx.exifinterface)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
