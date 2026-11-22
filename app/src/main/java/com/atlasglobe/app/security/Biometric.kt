package com.atlasglobe.app.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object Biometric {
    private const val AUTH = BIOMETRIC_STRONG or BIOMETRIC_WEAK

    fun available(c: Context) =
        BiometricManager.from(c).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun activity(c: Context): FragmentActivity? {
        var x: Context? = c
        while (x is ContextWrapper) { if (x is FragmentActivity) return x; x = x.baseContext }
        return null
    }

    fun prompt(c: Context, onSuccess: () -> Unit) {
        val act = activity(c) ?: return
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Atlas Globe").setSubtitle("Déverrouillez avec votre empreinte")
            .setNegativeButtonText("Utiliser le PIN").setAllowedAuthenticators(AUTH).build()
        BiometricPrompt(act, ContextCompat.getMainExecutor(c), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        }).authenticate(info)
    }
}
