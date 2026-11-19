package com.example.vault

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "GoogleSignInManager"

class GoogleSignInCancelledException : Exception("تم إلغاء تسجيل الدخول من المستخدم.")

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

object GoogleSignInManager {

    fun toSignedInAccountInfo(user: FirebaseUser?): SignedInAccountInfo? {
        val current = user ?: return null
        return SignedInAccountInfo(
            uid = current.uid,
            displayName = current.displayName?.takeIf { it.isNotBlank() } ?: "حساب Google",
            email = current.email.orEmpty(),
            photoUrl = current.photoUrl?.toString()
        )
    }

    suspend fun attemptSilentSignIn(
        context: Context,
        auth: FirebaseAuth = Firebase.auth
    ): Result<SignedInAccountInfo?> {
        val existing = auth.currentUser
        if (existing != null) {
            return Result.success(toSignedInAccountInfo(existing))
        }
        val clientId = context.getString(R.string.default_web_client_id).trim()
        if (clientId.isBlank()) {
            return Result.success(null)
        }
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(context)
            val result = credentialManager.getCredential(context, request)
            val user = exchangeCredentialForFirebaseUser(result.credential, auth)
            Result.success(toSignedInAccountInfo(user))
        } catch (e: NoCredentialException) {
            // Normal when no pre-authorized account exists yet on this device
            Result.success(null)
        } catch (e: GetCredentialCancellationException) {
            Result.success(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Silent Google Sign-In skipped/unavailable", e)
            Result.success(null)
        }
    }

    suspend fun signInInteractive(
        activityContext: Context,
        auth: FirebaseAuth = Firebase.auth
    ): Result<SignedInAccountInfo> {
        val clientId = activityContext.getString(R.string.default_web_client_id).trim()
        if (clientId.isBlank()) {
            return Result.failure(IllegalStateException("معرف عميل Google Sign-In غير مهيأ."))
        }
        return try {
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val credentialManager = CredentialManager.create(activityContext)
            val result = credentialManager.getCredential(activityContext, request)
            val firebaseUser = exchangeCredentialForFirebaseUser(result.credential, auth)
            val info = toSignedInAccountInfo(firebaseUser)
                ?: return Result.failure(IllegalStateException("تعذر إتمام تسجيل الدخول."))
            Result.success(info)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "User cancelled Google Sign-In", e)
            Result.failure(GoogleSignInCancelledException())
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager sign-in failed", e)
            Result.failure(
                IllegalStateException(
                    "تعذر تسجيل الدخول بحساب Google على هذا الجهاز. تأكد من توفر خدمات Google أو استخدم «مجلد الجهاز الدائم» الذي يعمل بدون أي حساب.\n(${e.localizedMessage ?: "Credential error"})"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Google sign-in failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(
        context: Context,
        auth: FirebaseAuth = Firebase.auth,
        beforeAuthSignOut: suspend () -> Unit = {}
    ) {
        withContext(NonCancellable) {
            runCatching { beforeAuthSignOut() }
            runCatching {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            }
            auth.signOut()
        }
    }

    private suspend fun exchangeCredentialForFirebaseUser(
        credential: androidx.credentials.Credential,
        auth: FirebaseAuth
    ): FirebaseUser {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdCredential.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            return authResult.user ?: throw IllegalStateException("لم يرجع Firebase بيانات الحساب.")
        }
        throw IllegalStateException("نوع بيانات الاعتماد غير مدعوم.")
    }
}
