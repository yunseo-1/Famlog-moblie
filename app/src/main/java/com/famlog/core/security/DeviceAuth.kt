package com.famlog.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 기기 인증 (SEC-01): 지문·얼굴, 안 되면 휴대폰 화면 잠금(PIN·패턴). 앱은 PIN을 따로 저장하지 않는다.
 * 잠금 해제용이라 암호 키를 묶지 않으므로 BIOMETRIC_WEAK로 충분하고, 이 조합은 모든 API에서 된다
 */
object DeviceAuth {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** 휴대폰에 화면 잠금이나 생체 인증이 설정돼 있는지 */
    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** 성공하면 true, 취소·실패하면 false. [activity]가 살아 있는 동안만 결과가 온다 */
    fun authenticate(activity: FragmentActivity, title: String, subtitle: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}
