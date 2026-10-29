package com.omnisolve.overlay.data

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.omnisolve.overlay.R
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Reactive AuthStateListener flow emitting the active `FirebaseUser?`.
 */
fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

sealed interface GoogleSignInOutcome {
    data class Success(val user: FirebaseUser) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Error(val userMessage: String) : GoogleSignInOutcome
}

/**
 * Manages Google Sign-In via Jetpack Credential Manager + Firebase Authentication.
 * Uses `GetSignInWithGoogleOption` for explicit button sign-in and a separate single-option
 * `GetGoogleIdOption` for silent startup session restoration.
 */
object GoogleAuthManager {

    private const val TAG = "GoogleAuthManager"

    /**
     * Explicit interactive Google Sign-In bottom sheet triggered from a button tap.
     * `activityContext` must be an `Activity` context (e.g., `LocalContext.current` in Compose).
     */
    suspend fun signInInteractive(
        activityContext: Context,
        auth: FirebaseAuth = Firebase.auth
    ): GoogleSignInOutcome {
        val webClientId = resolveWebClientId(activityContext)
            ?: return GoogleSignInOutcome.Error("Google Sign-In client configuration not found.")

        val credentialManager = CredentialManager.create(activityContext)
        val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )
            exchangeGoogleCredentialWithFirebase(result.credential, auth)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
            GoogleSignInOutcome.Cancelled
        } catch (noCred: NoCredentialException) {
            Log.w(TAG, "No Google account available on device: ${noCred.message}")
            GoogleSignInOutcome.Error("Add a Google account in Android Settings to sync your cloud study history.")
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager error (${e.type}): ${e.message}")
            GoogleSignInOutcome.Error("Google Sign-In unavailable right now (${e.errorMessage ?: e.type}).")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected Google Sign-In failure", e)
            GoogleSignInOutcome.Error("Could not complete Google Sign-In: ${e.localizedMessage ?: "Try again"}")
        }
    }

    /**
     * Silent startup check for returning authorized users (single `GetGoogleIdOption` only).
     */
    suspend fun trySilentRestore(
        activityContext: Context,
        auth: FirebaseAuth = Firebase.auth
    ): FirebaseUser? {
        auth.currentUser?.let { return it }
        val webClientId = resolveWebClientId(activityContext) ?: return null
        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val silentOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(silentOption)
                .build()
            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )
            when (val outcome = exchangeGoogleCredentialWithFirebase(result.credential, auth)) {
                is GoogleSignInOutcome.Success -> outcome.user
                else -> null
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    suspend fun signOut(
        context: Context,
        auth: FirebaseAuth = Firebase.auth
    ) {
        auth.signOut()
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Credential state clear skipped: ${e.message}")
        }
    }

    private suspend fun exchangeGoogleCredentialWithFirebase(
        credential: androidx.credentials.Credential,
        auth: FirebaseAuth
    ): GoogleSignInOutcome {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCred).await()
            val user = authResult.user
                ?: return GoogleSignInOutcome.Error("Signed in, but no user profile returned.")
            return GoogleSignInOutcome.Success(user)
        }
        return GoogleSignInOutcome.Error("Unsupported credential type returned.")
    }

    private fun resolveWebClientId(context: Context): String? {
        return try {
            val value = context.getString(R.string.default_web_client_id).trim()
            value.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }
}
