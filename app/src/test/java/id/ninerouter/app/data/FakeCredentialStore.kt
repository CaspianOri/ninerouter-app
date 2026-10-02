package id.ninerouter.app.data

/** In-memory [CredentialStore] for unit tests. */
class FakeCredentialStore : CredentialStore {
    private var apiKey: String? = null

    override fun getApiKey(): String? = apiKey

    override fun saveApiKey(apiKey: String) {
        this.apiKey = apiKey
    }

    override fun clear() {
        apiKey = null
    }
}
