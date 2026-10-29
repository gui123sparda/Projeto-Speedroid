package com.example.tunerstudioandroid.vss

import org.clonetuner.comm.Link

/** Called only inside Transport's serial lock, between complete telemetry transactions. */
class GpsVssBridge {
    enum class Status { IDLE, NEGOTIATING, ACTIVE, NO_FIX, UNSUPPORTED, CONFIGURATION_REQUIRED }
    @Volatile var status=Status.IDLE
        private set
    private var capability:Boolean?=null
    private var lastSent=Long.MIN_VALUE
    private var enabledOnController=false

    fun tick(link: Link, enabled: Boolean, fix: GpsFix?, nowMs: Long) {
        if(!enabled && !enabledOnController) { status=Status.IDLE;return }
        if(capability==null) {
            status=Status.NEGOTIATING
            capability=GpsVssProtocol.supported(link.gpsVssCapabilities())
        }
        if(capability!=true) { status=Status.UNSUPPORTED;return }
        if(lastSent!=Long.MIN_VALUE && nowMs-lastSent<200 && enabled==enabledOnController) return
        lastSent=nowMs
        try {
            link.sendGpsVss(GpsVssProtocol.packet(enabled,fix,nowMs))
            enabledOnController=enabled
            status=if(!enabled) Status.IDLE else if(fix?.valid(nowMs)==true) Status.ACTIVE else Status.NO_FIX
        } catch(e: IllegalStateException) {
            if(e.message?.contains("0x84")==true) status=Status.CONFIGURATION_REQUIRED else throw e
        }
    }
    fun close(link: Link, nowMs: Long) {
        if(enabledOnController && link.isConnected) runCatching { link.sendGpsVss(GpsVssProtocol.packet(false,null,nowMs)) }
        enabledOnController=false
        status=Status.IDLE
    }
}
