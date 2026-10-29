package com.example.tunerstudioandroid

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.clonetuner.comm.*
import org.clonetuner.ini.IniModel
import org.clonetuner.model.Ecu
import org.clonetuner.model.TriggerCapture
import org.clonetuner.model.MsqIO
import java.io.File
import com.example.tunerstudioandroid.dashboard.DashboardStore
import com.example.tunerstudioandroid.dashboard.DashHistory
import com.example.tunerstudioandroid.vss.*
import java.util.concurrent.Executors

/** Retained across Activity recreation. Serial work never runs on the UI thread. */
class StudioSession(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    val ini: IniModel
    val ecu: Ecu
    val transport: Transport
    val log = CsvLog()
    val dashHistories=hashMapOf<String,DashHistory>()
    val dashboards by lazy { DashboardStore(app) }
    @Volatile var gpsFix: GpsFix? = null
    @Volatile private var gpsBridge = GpsVssBridge()
    @Volatile var gpsVssEnabled = app.getSharedPreferences("vss", Context.MODE_PRIVATE).getBoolean("gps", false)
        private set
    var connectionProgress: Double? = null
        private set
    val gpsVssStatus get() = gpsBridge.status
    fun selectGpsVss(enabled: Boolean) {
        gpsVssEnabled = enabled
        app.getSharedPreferences("vss", Context.MODE_PRIVATE).edit().putBoolean("gps", enabled).apply()
        if (enabled && ini.constant("vssMode") != null && ecu.get("vssMode") != 1.0) ecu.set("vssMode", 1.0)
    }
    val history = ArrayDeque<String>()
    var recording = false
    var logStart = 0L
    var busy = false
        private set
    private var pendingTasks = 0
    var listener: (() -> Unit)? = null
    @Volatile private var lastTelemetry = 0L
    @Volatile private var candidate: Link? = null
    var error: String? = null
        private set

    init {
        val file = File(app.filesDir, "speeduino.ini")
        if (!file.exists()) app.assets.open("speeduino.ini").use { input -> file.outputStream().use { input.copyTo(it) } }
        ini = IniModel.load(file.toPath(), null)
        ecu = Ecu(ini)
        transport = Transport(ecu)
        transport.logger { entry -> main.post { message(entry); listener?.invoke() } }
        val saved = File(app.filesDir, "session.msq")
        if (saved.exists()) runCatching {
            MsqIO.load(saved.toPath(), ecu)
            val flags = File(app.filesDir, "session.flags")
            if (flags.exists()) {
                val lines = flags.readLines()
                if (lines.firstOrNull() == checksum(saved)) {
                    ecu.markClean()
                    lines.drop(1).mapNotNull { it.toIntOrNull() }.filter { it in 0 until ini.nPages }.forEach { ecu.markDirty(it) }
                }
            }
        }.onFailure { message("Sessão anterior: ${it.message}") }
    }
    fun message(text: String) {
        if (history.size >= 120) history.removeFirst()
        history.addLast(text)
    }
    val fresh get() = transport.isConnected && lastTelemetry != 0L && SystemClock.elapsedRealtime() - lastTelemetry < 2000
    fun channel(name: String): Double? {
        if ((name == "vss" || name == "vehicleSpeed") && gpsVssEnabled)
            return gpsFix?.takeIf { it.valid(SystemClock.elapsedRealtime()) }?.kmh
        if (!fresh || !ini.channels.containsKey(name)) return null
        val c = ini.channels[name]!!
        if (!c.isExpr && !ecu.channels().containsKey(name)) return null
        return ecu.num(name, Double.NaN).takeIf { it.isFinite() }
    }
    fun runOperation(action: () -> Unit, success: (() -> Unit)? = null) {
        if (busy) return
        enqueue(action, success)
    }
    private fun enqueue(action: () -> Unit, success: (() -> Unit)? = null) {
        pendingTasks++; busy = true; error = null; listener?.invoke()
        worker.execute {
            val result = runCatching(action)
            main.post {
                pendingTasks--; busy = pendingTasks > 0
                result.onSuccess { success?.invoke() }.onFailure { error = it.message ?: it.javaClass.simpleName; message(error!!) }
                listener?.invoke()
            }
        }
    }
    fun connect(link: Link) {
        if (busy) return
        candidate = link
        connectionProgress = 0.0
        val bridge = GpsVssBridge()
        gpsBridge = bridge
        if (link is SimLink) link.loadPages(ecu.allPages())
        val measured = object : Link by link {
            override fun supportsTriggerLogger(mode: TriggerCapture.Mode) = link.supportsTriggerLogger(mode)
            override fun startTriggerLogger(mode: TriggerCapture.Mode) = link.startTriggerLogger(mode)
            override fun stopTriggerLogger(mode: TriggerCapture.Mode) = link.stopTriggerLogger(mode)
            override fun readTriggerLogger(mode: TriggerCapture.Mode) = link.readTriggerLogger(mode)
            override fun disconnect() { bridge.close(link,SystemClock.elapsedRealtime()); link.disconnect() }
            override fun readChannels(): Map<String, Double> {
                return try {
                    bridge.tick(link,gpsVssEnabled,gpsFix,SystemClock.elapsedRealtime())
                    link.readChannels().also { lastTelemetry = SystemClock.elapsedRealtime() } }
                catch (e: RuntimeException) { lastTelemetry = 0; throw e }
            }
        }
        runOperation({ transport.connect(measured) }, { candidate = null; connectionProgress = 100.0 })
    }
    var selectedTriggerMode = TriggerCapture.Mode.COMPOSITE
    var triggerExport: String? = null
    fun startTriggerLogger() { runOperation({ transport.startTriggerLogger(selectedTriggerMode) }) }
    fun stopTriggerLogger() { enqueue({ transport.stopTriggerLogger() }) }
    fun disconnect() { recording = false; lastTelemetry = 0; runOperation({ transport.disconnect() }) }
    fun saveSession() {
        val file = File(app.filesDir, "session.msq")
        val temporary = File(app.filesDir, "session.tmp")
        MsqIO.save(temporary.toPath(), ecu)
        check(temporary.renameTo(file)) { "Falha ao salvar sessão local" }
        val flags = File(app.filesDir, "session.flags.tmp")
        flags.writeText(checksum(file) + "\n" + ecu.dirtyPages().sorted().joinToString("\n"))
        check(flags.renameTo(File(app.filesDir, "session.flags"))) { "Falha ao salvar estado da sessão" }
    }
    private fun checksum(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun background() {
        recording = false
        gpsFix = null
        candidate?.disconnect()
        enqueue({ transport.disconnect(); saveSession() })
    }
    fun close(save: Boolean = true) {
        listener = null
        candidate?.disconnect()
        worker.execute { transport.disconnect(); if (save) runCatching { saveSession() }; worker.shutdown() }
    }
}
