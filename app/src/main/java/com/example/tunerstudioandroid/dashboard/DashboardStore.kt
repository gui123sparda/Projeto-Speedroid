package com.example.tunerstudioandroid.dashboard

import android.content.Context
import org.clonetuner.dashboard.DashboardDefinition
import org.clonetuner.dashboard.DashboardReader
import java.io.File
import java.security.MessageDigest
import com.example.tunerstudioandroid.dashboard.vehicle.VehicleModel
import java.util.concurrent.CopyOnWriteArrayList

class DashboardStore(context: Context) {
    data class Entry(val id: String, val label: String, val definition: DashboardDefinition, val vehicle: VehicleModel? = null)
    private val app=context.applicationContext
    private val folder=File(app.filesDir,"dashboards").apply { mkdirs() }
    val entries=CopyOnWriteArrayList<Entry>()
    var selectedId: String
        get()=app.getSharedPreferences("dashboards",Context.MODE_PRIVATE).getString("selected", "Speeduino_Sweep.dash")!!
        set(value) { app.getSharedPreferences("dashboards",Context.MODE_PRIVATE).edit().putString("selected",value).apply() }
    init {
        for(name in listOf("Speeduino_Sweep.dash","Speeduino_LED.dash","Speeduino_LED_Amber.dash")) {
            val file=File(folder,name)
            if(!file.exists()) app.assets.open("dashboards/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
            entries.add(Entry(name,name.removePrefix("Speeduino_").removeSuffix(".dash").replace('_',' '),DashboardReader.read(file.toPath())))
        }
        VehicleModel.entries.forEach { model ->
            entries.add(Entry(model.id,model.label,DashboardDefinition(model.label,"",emptyMap(),emptyList(),emptyMap(),emptyList()),model))
        }
        folder.listFiles { file -> file.name.startsWith("import-") && file.extension=="dash" }?.sortedBy { it.name }?.forEach { file ->
            runCatching {
                val label=file.name.replaceFirst(Regex("^import-[a-f0-9]{16}-"), "")
                val parsed=DashboardReader.read(file.toPath())
                val definition=DashboardDefinition(label,parsed.firmwareSignature(),parsed.clusterProperties(),parsed.components(),parsed.resources(),parsed.diagnostics())
                entries.add(Entry(file.name,label.removeSuffix(".dash"),definition))
            }
        }
    }
    fun import(file: File, displayName: String): Entry {
        val definition=DashboardReader.read(file.toPath())
        require(definition.components().isNotEmpty()) { "O arquivo .dash não contém instrumentos" }
        val digest=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).take(8).joinToString("") { "%02x".format(it) }
        val safe=displayName.substringAfterLast('/').replace(Regex("[^\\p{L}\\p{N} ._-]"),"_").take(90).ifBlank { "Painel.dash" }
        val id="import-$digest-$safe".let { if(it.endsWith(".dash")) it else "$it.dash" }
        file.copyTo(File(folder,id),overwrite=true)
        val entry=Entry(id,safe.removeSuffix(".dash"),DashboardDefinition(safe,definition.firmwareSignature(),definition.clusterProperties(),definition.components(),definition.resources(),definition.diagnostics()))
        entries.removeAll { it.id==id }; entries.add(entry); selectedId=id
        return entry
    }
}
