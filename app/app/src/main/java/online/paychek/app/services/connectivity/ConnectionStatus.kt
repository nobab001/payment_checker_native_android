package online.paychek.app.services.connectivity

data class ConnectionStatus(
    val layer1NetworkConnected: Boolean = false,
    val layer2InternetAvailable: Boolean = false,
    val layer3ServerAlive: Boolean = false,
    val isChecking: Boolean = false,
    val lastCheckedAtMs: Long = 0L
) {
    val hasInternet: Boolean
        get() = layer1NetworkConnected && layer2InternetAvailable

    val hasServer: Boolean
        get() = hasInternet && layer3ServerAlive

    fun toConnectivityBanner(): ConnectionBanner? = when {
        !layer1NetworkConnected -> ConnectionBanner.NoInternet("নেটওয়ার্ক সংযোগ নেই। ডেটা আপডেট হচ্ছে না।")
        !layer2InternetAvailable -> ConnectionBanner.NoInternet()
        !layer3ServerAlive -> ConnectionBanner.ServerUnavailable()
        else -> null
    }
}
