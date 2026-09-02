package com.neko.music.data.lan

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.neko.music.data.manager.PlaylistManager
import com.neko.music.data.manager.TokenManager
import com.neko.music.service.MusicPlayerManager
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class LanMusic(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val coverPath: String = "",
    val playable: Boolean = true
)

@Serializable
data class LanQueueSnapshot(
    val protocol: Int = 1,
    val type: String = "queue",
    val revision: Long,
    val currentIndex: Int,
    val currentMusicId: Int? = null,
    val isPlaying: Boolean = false,
    val playMode: String = "LIST_LOOP",
    val items: List<LanMusic>
)

@Serializable
data class LanAnnouncement(
    val protocol: Int = 1,
    val type: String = "announce",
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val port: Int,
    val accountTag: String,
    val queueRevision: Long = 0,
    val queueCount: Int = 0,
    val currentMusicId: Int? = null,
    val timestamp: Long = 0
)

@Serializable
private data class LanSubscribe(
    val protocol: Int = 1,
    val type: String = "subscribe",
    val accountTag: String
)

data class LanDevice(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val host: String,
    val port: Int,
    val accountTag: String,
    val queueRevision: Long,
    val queueCount: Int,
    val currentMusicId: Int?,
    val lastSeen: Long
)

/**
 * Android-side LAN discovery and queue subscription.
 *
 * It is deliberately owned by the playlist screen instead of being a permanent
 * process-wide network service. This keeps multicast reception and the TCP
 * listener off when the user is not using the feature.
 */
class LanDeviceManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "LanDeviceManager"
        private const val PREFS = "lan_device"
        private const val DEVICE_ID = "device_id"
        private const val GROUP = "239.255.77.77"
        private const val PORT = 39393
        private const val ANNOUNCE_INTERVAL_MS = 5_000L
        private const val DEVICE_TIMEOUT_MS = 15_000L

        @Volatile
        private var instance: LanDeviceManager? = null

        fun getInstance(context: Context): LanDeviceManager {
            return instance ?: synchronized(this) {
                instance ?: LanDeviceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playlistManager = PlaylistManager.getInstance(appContext)
    private val playerManager = MusicPlayerManager.getInstance(appContext)
    private val revision = AtomicLong(0)
    private val devicesById = linkedMapOf<String, LanDevice>()
    private val devicesLock = Any()
    private val serverSocketRef = AtomicReference<ServerSocket?>()
    private val multicastSocketRef = AtomicReference<MulticastSocket?>()
    private var runtimeJob: Job? = null
    private var runtimeScope: CoroutineScope? = null
    private var runningAccountTag: String? = null
    private var selectedConnectionJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var selectedDeviceIdValue: String? = null

    private val _devices = MutableStateFlow<List<LanDevice>>(emptyList())
    val devices: StateFlow<List<LanDevice>> = _devices.asStateFlow()

    private val _selectedDeviceId = MutableStateFlow<String?>(null)
    val selectedDeviceId: StateFlow<String?> = _selectedDeviceId.asStateFlow()

    private val _remoteQueue = MutableStateFlow<LanQueueSnapshot?>(null)
    val remoteQueue: StateFlow<LanQueueSnapshot?> = _remoteQueue.asStateFlow()

    fun start() {
        val currentAccountTag = accountTag()
        if (runtimeJob?.isActive == true && runningAccountTag == currentAccountTag) return
        if (runtimeJob?.isActive == true) {
            stop()
        }
        if (TokenManager(appContext).getUserId() < 0) {
            stop()
            return
        }

        val runScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        runtimeScope = runScope
        runningAccountTag = currentAccountTag
        runtimeJob = runScope.launch {
            var server: ServerSocket? = null
            try {
                val activeServer = ServerSocket(0)
                server = activeServer
                serverSocketRef.set(activeServer)
                acquireMulticastLock()
                launch { acceptLoop(activeServer) }
                launch { discoveryLoop(activeServer.localPort) }
                launch { queueChangeLoop() }
                launch { expireDevicesLoop() }
                awaitCancellation()
            } finally {
                closeQuietly(server)
                serverSocketRef.set(null)
                closeQuietly(multicastSocketRef.getAndSet(null))
                releaseMulticastLock()
                synchronized(devicesLock) { devicesById.clear() }
                _devices.value = emptyList()
                if (runtimeScope === runScope) {
                    runtimeScope = null
                    runningAccountTag = null
                }
                runScope.cancel()
            }
        }
    }

    fun stop() {
        selectedDeviceIdValue = null
        _selectedDeviceId.value = null
        selectedConnectionJob?.cancel()
        selectedConnectionJob = null
        _remoteQueue.value = null
        runtimeJob?.cancel()
        runtimeJob = null
        runtimeScope?.cancel()
        runtimeScope = null
        closeQuietly(serverSocketRef.getAndSet(null))
        closeQuietly(multicastSocketRef.getAndSet(null))
        releaseMulticastLock()
        synchronized(devicesLock) { devicesById.clear() }
        _devices.value = emptyList()
    }

    fun selectDevice(device: LanDevice?) {
        selectedDeviceIdValue = device?.deviceId
        _selectedDeviceId.value = device?.deviceId
        _remoteQueue.value = null
        selectedConnectionJob?.cancel()
        selectedConnectionJob = null
        if (device == null) return

        selectedConnectionJob = (runtimeScope ?: scope).launch {
            var backoff = 1_000L
            while (isActive && selectedDeviceIdValue == device.deviceId) {
                try {
                    subscribe(device)
                    backoff = 1_000L
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(TAG, "远程设备连接断开: ${device.deviceName}", e)
                    _remoteQueue.value = null
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    private suspend fun acceptLoop(server: ServerSocket) = coroutineScope {
        while (isActive && !server.isClosed) {
            try {
                val socket = withContext(Dispatchers.IO) { server.accept() }
                launch { servePeer(socket) }
            } catch (e: Exception) {
                if (!server.isClosed) {
                    Log.w(TAG, "接收局域网连接失败", e)
                }
            }
        }
    }

    private suspend fun servePeer(socket: Socket) {
        socket.use { peer ->
            try {
                peer.soTimeout = 1_000
                val reader = BufferedReader(InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8))
                val writerMutex = Mutex()
                val request = json.decodeFromString<LanSubscribe>(reader.readLine() ?: return)
                if (request.type != "subscribe" || request.accountTag != accountTag()) return

                writeSnapshot(writer, writerMutex)
                val changes = merge(
                    playlistManager.events,
                    playerManager.currentMusicId.drop(1).map { Unit },
                    playerManager.isPlaying.drop(1).map { Unit },
                    playerManager.playMode.drop(1).map { Unit }
                )
                coroutineScope {
                    val writerJob = launch {
                        changes.collect {
                            writeSnapshot(writer, writerMutex)
                        }
                    }
                    try {
                        while (isActive) {
                            try {
                                val line = reader.readLine() ?: break
                                if (line == "{\"type\":\"ping\"}") {
                                    writerMutex.withLock {
                                        writer.write("{\"type\":\"pong\"}\n")
                                        writer.flush()
                                    }
                                }
                            } catch (_: java.net.SocketTimeoutException) {
                                currentCoroutineContext().ensureActive()
                            }
                        }
                    } finally {
                        writerJob.cancelAndJoin()
                    }
                }
            } catch (e: Exception) {
                if (e !is java.net.SocketTimeoutException) {
                    Log.d(TAG, "局域网连接处理结束", e)
                }
            }
        }
    }

    private suspend fun writeSnapshot(writer: BufferedWriter, writerMutex: Mutex) {
        writerMutex.withLock {
            val snapshot = buildSnapshot()
            writer.write(json.encodeToString(snapshot))
            writer.newLine()
            writer.flush()
        }
    }

    private suspend fun subscribe(device: LanDevice) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(device.host, device.port), 1_500)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            writer.write(json.encodeToString(LanSubscribe(accountTag = accountTag())))
            writer.newLine()
            writer.flush()

            while (selectedDeviceIdValue == device.deviceId) {
                val line = reader.readLine() ?: error("连接已关闭")
                val element = json.parseToJsonElement(line)
                val snapshot = json.decodeFromJsonElement<LanQueueSnapshot>(element)
                if (snapshot.type == "queue") {
                    _remoteQueue.value = snapshot
                }
            }
        }
    }

    private suspend fun discoveryLoop(serverPort: Int) = coroutineScope {
        val group = InetAddress.getByName(GROUP)
        val socket = MulticastSocket(PORT).apply {
            reuseAddress = true
            joinGroup(group)
        }
        multicastSocketRef.set(socket)
        val receiveJob = launch {
            val buffer = ByteArray(16 * 1024)
            while (isActive && !socket.isClosed) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val text = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)
                    val announcement = json.decodeFromString<LanAnnouncement>(text)
                    if (announcement.protocol != 1 ||
                        announcement.type != "announce" ||
                        announcement.accountTag != accountTag() ||
                        announcement.deviceId == deviceId() ||
                        announcement.port <= 0
                    ) continue
                    val device = LanDevice(
                        deviceId = announcement.deviceId,
                        deviceName = announcement.deviceName,
                        platform = announcement.platform,
                        host = packet.address.hostAddress ?: continue,
                        port = announcement.port,
                        accountTag = announcement.accountTag,
                        queueRevision = announcement.queueRevision,
                        queueCount = announcement.queueCount,
                        currentMusicId = announcement.currentMusicId,
                        lastSeen = System.currentTimeMillis()
                    )
                    synchronized(devicesLock) { devicesById[device.deviceId] = device }
                    publishDevices()
                } catch (e: Exception) {
                    if (!socket.isClosed) Log.d(TAG, "局域网设备广播解析失败", e)
                }
            }
        }

        try {
            while (isActive && !socket.isClosed) {
                val announcement = buildAnnouncement(serverPort)
                val bytes = json.encodeToString(announcement).toByteArray(StandardCharsets.UTF_8)
                socket.send(DatagramPacket(bytes, bytes.size, group, PORT))
                delay(ANNOUNCE_INTERVAL_MS)
            }
        } finally {
            receiveJob.cancelAndJoin()
            try {
                socket.leaveGroup(group)
            } catch (_: Exception) {
            }
            closeQuietly(socket)
        }
    }

    private suspend fun queueChangeLoop() {
        merge(
            playlistManager.events,
            playerManager.currentMusicId.drop(1).map { Unit },
            playerManager.isPlaying.drop(1).map { Unit }
        ).collect {
            revision.incrementAndGet()
        }
    }

    private suspend fun expireDevicesLoop() {
        while (currentCoroutineContext().isActive) {
            delay(5_000L)
            val cutoff = System.currentTimeMillis() - DEVICE_TIMEOUT_MS
            var selectedExpired = false
            synchronized(devicesLock) {
                devicesById.entries.removeAll {
                    if (it.value.lastSeen < cutoff) {
                        selectedExpired = selectedExpired || it.key == selectedDeviceIdValue
                        true
                    } else {
                        false
                    }
                }
            }
            if (selectedExpired) {
                selectedDeviceIdValue = null
                _selectedDeviceId.value = null
                selectedConnectionJob?.cancel()
                selectedConnectionJob = null
                _remoteQueue.value = null
            }
            publishDevices()
        }
    }

    private suspend fun buildSnapshot(): LanQueueSnapshot {
        val items = playlistManager.getAllPlaylistList().map { music ->
            LanMusic(
                id = music.id,
                title = music.title,
                artist = music.artist,
                album = music.album,
                duration = music.duration,
                coverPath = music.coverFilePath ?: "",
                playable = music.id > 0
            )
        }
        val currentId = playerManager.currentMusicId.value
        return LanQueueSnapshot(
            revision = revision.get(),
            currentIndex = items.indexOfFirst { it.id == currentId },
            currentMusicId = currentId,
            isPlaying = playerManager.isPlaying.value,
            playMode = playerManager.playMode.value.name,
            items = items
        )
    }

    private suspend fun buildAnnouncement(serverPort: Int): LanAnnouncement {
        val currentId = playerManager.currentMusicId.value
        val queueCount = playlistManager.getPlaylistCount()
        return LanAnnouncement(
            deviceId = deviceId(),
            deviceName = deviceName(),
            platform = "android",
            port = serverPort,
            accountTag = accountTag(),
            queueRevision = revision.get(),
            queueCount = queueCount,
            currentMusicId = currentId,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun publishDevices() {
        _devices.value = synchronized(devicesLock) {
            devicesById.values.sortedBy { it.deviceName.lowercase() }
        }
    }

    private fun accountTag(): String {
        val userId = TokenManager(appContext).getUserId()
        return sha256("nekomusic-lan-v1|$userId")
    }

    private fun deviceId(): String {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(DEVICE_ID, it).apply()
        }
    }

    private fun deviceName(): String {
        val model = Build.MODEL?.trim().orEmpty()
        return if (model.isBlank()) "Android" else model
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("NekoMusicLan").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let {
            if (it.isHeld) it.release()
        }
        multicastLock = null
    }

    private fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (_: Exception) {
        }
    }

}
