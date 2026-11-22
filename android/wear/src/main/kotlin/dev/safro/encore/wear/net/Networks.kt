package dev.safro.encore.wear.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import okhttp3.Dns
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory

/**
 * The watch's networks. A watch near its phone is usually online through the phone over Bluetooth, which is
 * too slow for music. While something is streaming or downloading, [acquire] asks Wear OS for Wi-Fi or LTE
 * (the system turns the radio on), and the app's HTTP client sends its requests over that network.
 */
class Networks(context: Context) {
    /** Called (on a system thread) when the networks change. */
    @Volatile var onChange: () -> Unit = {}

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val holders = AtomicInteger()

    @Volatile var fast: Network? = null
        private set

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { fast = network; onChange() }
        override fun onLost(network: Network) { if (fast == network) fast = null; onChange() }
    }

    private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onChange()
        override fun onLost(network: Network) = onChange()
    }

    init {
        runCatching { cm.registerDefaultNetworkCallback(defaultCallback) }
    }

    fun kind(): NetKind {
        val n = fast ?: cm.activeNetwork ?: return NetKind.NONE
        val caps = cm.getNetworkCapabilities(n) ?: return NetKind.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetKind.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetKind.PHONE
            else -> NetKind.OTHER
        }
    }

    val online get() = kind() != NetKind.NONE

    /** Ask for Wi-Fi or LTE until [release] is called as often. */
    fun acquire() {
        if (holders.getAndIncrement() > 0) return
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        runCatching { cm.requestNetwork(req, callback) }
    }

    fun release() {
        if (holders.decrementAndGet() > 0) return
        holders.set(0)
        runCatching { cm.unregisterNetworkCallback(callback) }
        fast = null
        onChange()
    }

    /** Sockets on the fast network while there is one, else the default. */
    val socketFactory: SocketFactory = object : SocketFactory() {
        private fun f() = fast?.socketFactory ?: getDefault()
        override fun createSocket(): Socket = f().createSocket()
        override fun createSocket(host: String, port: Int): Socket = f().createSocket(host, port)
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            f().createSocket(host, port, localHost, localPort)
        override fun createSocket(host: InetAddress, port: Int): Socket = f().createSocket(host, port)
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
            f().createSocket(address, port, localAddress, localPort)
    }

    val dns: Dns = Dns { host -> fast?.getAllByName(host)?.toList() ?: Dns.SYSTEM.lookup(host) }
}
