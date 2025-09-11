// Dans ton Activity/VM (pseudo-code)
suspend fun provideGoogleIdToken(activity: Activity): String? {
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestIdToken(activity.getString(R.string.default_web_client_id))
        .build()
    val client = GoogleSignIn.getClient(activity, gso)

    // 1) tenter silentSignIn
    runCatching { client.silentSignIn().await() }.getOrNull()?.idToken?.let { return it }

    // 2) sinon lance l'intent interactif et récupère account.idToken (via Activity Result API)
    // ... ton code UI ici ...
    return null
}

// Appel :
AccountDeletionService.deleteAccount(
    activity = this,
    googleIdTokenProvider = { provideGoogleIdToken(this) }
)
