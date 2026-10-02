package id.ninerouter.app.data

/**
 * Encrypted storage for the 9Router API key.
 *
 * The key grants access to paid/proxied LLM providers, so it must never live
 * in plain SharedPreferences/DataStore. Production code uses
 * [EncryptedCredentialStore] (AndroidX Security, AES256); unit tests use an
 * in-memory fake.
 */
interface CredentialStore {
    /** Returns the stored key, or null when none was saved. */
    fun getApiKey(): String?

    /** Persists [apiKey] in encrypted storage. */
    fun saveApiKey(apiKey: String)

    /** Irreversibly deletes the stored key. */
    fun clear()
}
