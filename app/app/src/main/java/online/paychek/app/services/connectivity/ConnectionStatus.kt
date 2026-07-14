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

    /** ✅ Connected → null; otherwise Disconnected / No Internet. */
    fun toConnectivityBanner(): ConnectionBanner? = when {
        !layer1NetworkConnected || !layer2InternetAvailable -> ConnectionBanner.NoInternet()
        !layer3ServerAlive -> ConnectionBanner.Disconnected()
        else -> null
    }
}
