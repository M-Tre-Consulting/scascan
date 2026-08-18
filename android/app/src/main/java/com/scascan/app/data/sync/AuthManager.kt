package com.scascan.app.data.sync

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.scascan.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google sign-in via Credential Manager, exchanged for a Firebase Auth session — replaces the
 * old Drive-scoped OAuth authorization flow. Firestore reads FirebaseAuth.currentUser directly,
 * so nothing downstream needs to carry an access token around the way DriveSyncManager did.
 */
@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val auth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    /**
     * [activityContext] must be an Activity context (e.g. a Fragment's requireActivity()) —
     * Credential Manager needs it to host the account-picker UI. Not retained beyond this call.
     */
    suspend fun signIn(activityContext: Context): Result<FirebaseUser> = runCatching {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(appContext.getString(R.string.default_web_client_id))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val credential = CredentialManager.create(activityContext)
            .getCredential(activityContext, request)
            .credential

        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type: ${credential.type}"
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken

        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(firebaseCredential).await().user
            ?: error("Firebase sign-in returned no user")
    }

    suspend fun signOut() {
        auth.signOut()
        runCatching {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        }
    }
}
