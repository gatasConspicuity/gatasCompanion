package nl.rvt.gatas.companion.services

import co.touchlab.kermit.Logger
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Buffer

private val gdl90UdpLog = Logger.withTag("Gdl90UdpBridge")

class Gdl90UdpBridgeService(
    val destination: Gdl90Destination = Gdl90Destination(),
) : Gdl90DatagramSender {
    private var selectorManager: SelectorManager? = null
    private val mutex = Mutex()

    private var socket: BoundDatagramSocket? = null

    override suspend fun send(payload: ByteArray) = mutex.withLock {
        val activeSocket = ensureSocket()

        try {
            activeSocket.send(
                Datagram(
                    packet = Buffer().also { it.write(payload) },
                    address = InetSocketAddress(destination.host, destination.port),
                )
            )
        } catch (e: Exception) {
            socket?.close()
            socket = null
            gdl90UdpLog.e(e) { "GDL90 UDP bridge failed for ${destination.host}:${destination.port}" }
            throw e
        }
    }

    override suspend fun stop() = mutex.withLock {
        socket?.close()
        socket = null
        selectorManager?.close()
        selectorManager = null
    }

    private suspend fun ensureSocket(): BoundDatagramSocket {
        val current = socket
        if (current != null) {
            return current
        }

        val selector = selectorManager ?: SelectorManager(Dispatchers.IO).also {
            selectorManager = it
        }
        // Keep this socket deliberately unconnected. GDL90 forwarding is a one-way,
        // best-effort datagram stream and the EFB does not acknowledge individual
        // packets. On Darwin, a connected UDP socket can surface an ICMP "port
        // unreachable" response as ECONNREFUSED when the EFB temporarily has no
        // listener (for example while it is being brought to the foreground). An
        // unconnected socket retains the intended fire-and-forget semantics and lets
        // every datagram carry the explicit same-device loopback destination.
        val created = aSocket(selector).udp().bind()
        socket = created
        gdl90UdpLog.i {
            "GDL90 UDP sender opened for IPv4 loopback ${destination.host}:${destination.port}"
        }
        return created
    }
}
