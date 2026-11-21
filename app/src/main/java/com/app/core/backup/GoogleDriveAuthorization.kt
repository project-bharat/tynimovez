package com.app.core.backup

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.tasks.await

internal class DriveAuthorizationRequired : Exception("Reconnect Google Drive to continue.")

class GoogleDriveAuthorization(context: Context) {
    private val client = Identity.getAuthorizationClient(context.applicationContext)

    suspend fun selectAccount(activity: Activity, webClientId: String): String {
        require(webClientId.isNotBlank()) { "Google sign-in is not configured for this build." }
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activity).getCredential(activity, request)
        val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
        return credential.id.also { require(it.isNotBlank()) }
    }

    suspend fun authorize(email: String): AuthorizationResult = client.authorize(
        AuthorizationRequest.builder()
            .setAccount(Account(email, "com.google"))
            .setRequestedScopes(listOf(Scope(APP_DATA_SCOPE)))
            .build()
    ).await()

    fun complete(intent: Intent?) {
        val result = client.getAuthorizationResultFromIntent(intent)
        check(!result.accessToken.isNullOrBlank()) { "Google Drive access was not granted." }
    }

    internal suspend fun token(email: String): String {
        val result = authorize(email)
        if (result.hasResolution()) throw DriveAuthorizationRequired()
        return result.accessToken?.takeIf { it.isNotBlank() } ?: throw DriveAuthorizationRequired()
    }

    companion object {
        private const val APP_DATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
