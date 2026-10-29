package com.example.tunerstudioandroid

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.*
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.tunerstudioandroid.dashboard.*
import com.example.tunerstudioandroid.dashboard.vehicle.*
import com.example.tunerstudioandroid.trigger.TriggerScopeView
import com.example.tunerstudioandroid.vss.GpsVssBridge
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModelProvider
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import org.clonetuner.comm.*
import org.clonetuner.ini.IniModel
import org.clonetuner.model.*
import java.io.File
import java.util.Locale
import kotlin.math.*

class MainActivity : AppCompatActivity() {
    private lateinit var model: StudioModel
    private val session get() = model.session
    private lateinit var phone: PhoneSensors
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var tab = "Painel"
    private var gauge: GaugeView? = null
    private var dashView: PanelSurface? = null
    private var fullDashView: PanelSurface? = null
    private var fullScreenDialog: android.app.Dialog? = null
    private var fullScreenStatus: TextView? = null
    private var triggerDialog: android.app.Dialog? = null
    private var triggerScope: TriggerScopeView? = null
    private var triggerStatus: TextView? = null
    private var triggerSummary: TextView? = null
    private var triggerInspector: TextView? = null
    private var triggerStart: Button? = null
    private var triggerStop: Button? = null
    private var triggerExport: Button? = null
    private var triggerSelector: Spinner? = null
    private var vssStatusText: TextView? = null
    private var speedText: TextView? = null
    private var sensorText: TextView? = null
    private val readouts = linkedMapOf<String, TextView>()
    private var pendingPort: UsbSerialPort? = null
    private var pendingBluetooth = false
    private var lastError: String? = null
    private var pendingDocument = 0
    private val documents = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let { handleDocument(pendingDocument, it) }
    }
    private var lastLog = 0L
    private var layout = "Verde"
    private val extrema = mutableMapOf<String, Pair<Double,Double>>()
    private val bg = Color.rgb(11,17,24)
    private val panel = Color.rgb(19,30,40)
    private val muted = Color.rgb(151,168,185)
    private val teal = Color.rgb(36,197,176)
    private val permissionAction get() = "$packageName.USB_PERMISSION"
    private val usb get() = getSystemService(UsbManager::class.java)
    private val refresh = object : Runnable {
        override fun run() { updateReadings(); handler.postDelayed(this, 50) }
    }
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != permissionAction) return
            val port = pendingPort ?: return
            pendingPort = null
            if (usb.hasPermission(port.driver.device)) connectUsb(port)
            else toast("Permissão USB recusada")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[StudioModel::class.java]
        phone = PhoneSensors(this).apply { onGpsFix = { session.gpsFix = it } }
        tab = savedInstanceState?.getString("tab") ?: "Painel"
        pendingDocument = savedInstanceState?.getInt("document") ?: 0
        layout = getPreferences(MODE_PRIVATE).getString("layout", "Verde")!!.let { when(it) { "Rally"->"Verde";"Escort"->"Azul";"Mondeo"->"Âmbar";else->it } }
        root = column().apply { setBackgroundColor(bg) }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        ContextCompat.registerReceiver(this, usbReceiver, IntentFilter(permissionAction), ContextCompat.RECEIVER_NOT_EXPORTED)
        bindSession()
        render()
        if(savedInstanceState?.getBoolean("fullscreen") == true) handler.post { showFullDashboard() }
        if(savedInstanceState?.getBoolean("triggerLogger") == true) handler.post { showTriggerLogger() }
    }
    private fun bindSession() {
        session.listener = {
            if (!isFinishing && !isDestroyed) {
                detail.text = connectionDetail()
                if (session.error != null && session.error != lastError) { lastError = session.error; toast(session.error!!) }
                updateReadings()
            }
        }
    }
    override fun onStart() { super.onStart(); phone.start(); handler.post(refresh) }
    override fun onStop() {
        handler.removeCallbacks(refresh); phone.stop()
        // End acquisition in the foreground; no background location permission is needed.
        if (!isChangingConfigurations) {
            session.background()
        }
        super.onStop()
    }
    override fun onDestroy() { triggerDialog?.setOnDismissListener(null); triggerDialog?.dismiss(); fullScreenDialog?.dismiss(); unregisterReceiver(usbReceiver); session.listener = null; super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("triggerLogger",triggerDialog?.isShowing==true); outState.putBoolean("fullscreen",fullScreenDialog?.isShowing == true); outState.putString("tab",tab); outState.putInt("document",pendingDocument); super.onSaveInstanceState(outState) }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).roundToInt()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun text(value: String, size: Float = 15f, color: Int = Color.WHITE) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(dp(4), dp(6), dp(4), dp(6))
    }
    private fun title(value: String) { content.addView(text(value,25f).apply { typeface = Typeface.DEFAULT_BOLD }) }
    private fun note(value: String) { content.addView(text(value,14f,muted)) }
    private fun shape(color: Int, stroke: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(14).toFloat(); if (stroke) setStroke(dp(1), Color.rgb(43,61,76))
    }
    private fun card(parent: LinearLayout = content) = column().apply {
        background = shape(panel); setPadding(dp(12),dp(10),dp(12),dp(10))
        parent.addView(this, LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,dp(6),0,dp(6)) })
    }
    private fun button(label: String, parent: LinearLayout = content, action: () -> Unit): Button {
        return Button(this).apply {
            text = label; isAllCaps = false; textSize = 14f; setTextColor(teal)
            setBackgroundColor(panel); minHeight = dp(48)
            setOnClickListener { if (!session.busy) action() else toast("Aguarde a operação atual") }
            parent.addView(this, LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,dp(3),0,dp(3)) })
        }
    }
    private fun toast(message: String) { Toast.makeText(this,message,Toast.LENGTH_LONG).show() }
    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancelar",null)
            .setPositiveButton("Continuar") { _,_ -> if (!session.busy) action() }.show()
    }
    private fun connectionDetail(): String = when {
        session.busy -> "Operação em andamento…"
        session.transport.isConnected -> session.transport.description()
        else -> "Speeduino · ${session.ini.signature}"
    }
    private fun render() {
        root.removeAllViews(); readouts.clear(); gauge = null; dashView = null; vssStatusText = null; speedText = null; sensorText = null
        val heading = row().apply { setPadding(dp(16),dp(8),dp(16),dp(2)) }
        heading.addView(text("TUNER / SPEEDUINO",18f).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0,-2,1f))
        status = text("OFFLINE",12f,teal); heading.addView(status)
        root.addView(heading)
        detail = text(connectionDetail(),12f,muted).apply { setPadding(dp(20),0,dp(20),dp(8)); maxLines=2 }
        root.addView(detail)
        val navigation = row()
        listOf("Painel","Ajustes","Mapas","Logs","Conexão").forEach { name ->
            Button(this).apply {
                text=name; isAllCaps=false; textSize=13f; setTextColor(if(tab==name) teal else muted)
                setBackgroundColor(bg); minWidth=dp(78)
                setOnClickListener { tab=name; render() }; navigation.addView(this)
            }
        }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(navigation) })
        content = column().apply { setPadding(dp(16),dp(8),dp(16),dp(24)) }
        root.addView(ScrollView(this).apply { isFillViewport=true; addView(content) },LinearLayout.LayoutParams(-1,0,1f))
        when(tab) {
            "Painel" -> dashboard()
            "Ajustes" -> settings()
            "Mapas" -> maps()
            "Logs" -> logging()
            "Conexão" -> connections()
        }
        updateReadings()
    }
    private fun newDashView(entry: DashboardStore.Entry): PanelSurface {
        val history=session.dashHistories.getOrPut(entry.id) { DashHistory() }
        entry.vehicle?.let { model ->
            val telemetry=VehicleTelemetry(session::channel) { name ->
                if((name=="vss" || name=="vehicleSpeed") && session.gpsVssEnabled) "km/h" else session.ini.channel(name)?.units ?: ""
            }
            return VehiclePanelView(this,model,telemetry,history)
        }
        val signals = DashTelemetry(session::channel,
            { name -> if(session.ini.constant(name)!=null) session.ecu.get(name) else null },
            { session.gpsFix }, { SystemClock.elapsedRealtime() }, { session.fresh }, { session.recording }, { session.connectionProgress })
        return DashView(this,entry.definition,signals,{ value,fallback -> session.ecu.num(value,fallback) },::dashboardAction,::toast,history)
    }
    private fun dashboard() {
        val store=session.dashboards
        val options=store.entries.map { it.label }+"Painel nativo"
        val ids=store.entries.map { it.id }+"native"
        val selected=ids.indexOf(store.selectedId).coerceAtLeast(0)
        val top=row()
        top.addView(text("Painel",22f),LinearLayout.LayoutParams(0,-2,1f))
        val selector=Spinner(this).apply {
            contentDescription="Selecionar painel"
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,options)
            setSelection(selected)
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) {
                    if(store.selectedId!=ids[position]) { store.selectedId=ids[position];render() }
                }
                override fun onNothingSelected(parent:AdapterView<*>?) { }
            }
        }
        top.addView(selector,LinearLayout.LayoutParams(0,-2,2f));content.addView(top)
        if(store.selectedId=="native") { nativeDashboard();return }
        val entry=store.entries.getOrNull(selected) ?: return
        dashView=newDashView(entry)
        content.addView(dashView?.view,LinearLayout.LayoutParams(-1,-2))
        button("Tela cheia") { showFullDashboard() }
        val phoneCard=card()
        val speedLine=row()
        speedText=text("— km/h",26f,teal)
        speedLine.addView(speedText,LinearLayout.LayoutParams(0,-2,1f))
        sensorText=text(phone.gpsStatus,12f,muted)
        speedLine.addView(sensorText,LinearLayout.LayoutParams(0,-2,1f));phoneCard.addView(speedLine)
        vssStatusText=text(vssStatus(),12f,muted);phoneCard.addView(vssStatusText)
        button("VSS Mode e GPS",phoneCard) { editVss {} }
        button("Autorizar GPS e sensores",phoneCard) { locationPermission() }
        button("Importar painel .dash") { openDocument(IMPORT_DASH,"*/*") }
        button("Detalhes do painel") {
            val definition=entry.definition
            val info=if(entry.vehicle!=null) "${entry.label}\n\n${entry.vehicle.description}\n\nDesenho vetorial original do TunerSpeeduino, com fontes DejaVu incorporadas. A velocidade acompanha a fonte VSS selecionada, incluindo GPS. Sensores sem leitura permanecem indisponíveis." else "${entry.label}\n${definition.components().size} componentes · ${definition.resources().size} recursos embutidos\nFirmware do desenho: ${definition.firmwareSignature()}\n\n"+
                (dashView?.diagnostics?.joinToString("\n")?.ifBlank { "Todos os pintores e recursos do arquivo foram reconhecidos." } ?: "")+
                "\n\nToque no ícone de log para gravar. Segure um instrumento para inspecionar. Use dois dedos para ampliar; toque duas vezes para restaurar a escala."
            AlertDialog.Builder(this).setTitle(if(entry.vehicle==null) "Arquivo .dash" else "Painel personalizado").setMessage(info).setPositiveButton("Fechar",null).show()
        }
        button("Mínimos / máximos do painel") {
            AlertDialog.Builder(this).setTitle("Sessão do painel").setMessage(dashView?.extrema()?.joinToString("\n").orEmpty().ifBlank { "Sem dados disponíveis" })
                .setPositiveButton("Fechar",null).setNeutralButton("Zerar") { _,_ -> dashView?.resetHistory();fullDashView?.resetHistory() }.show()
        }
    }
    private fun dashboardAction(command:String) {
        if(session.busy) { toast("Aguarde a operação atual");return }
        fun toggle() {
            toggleRecording()
            if(fullScreenDialog?.isShowing==true)fullDashView?.refresh() else dashView?.refresh()
            fullScreenStatus?.text="${status.text} · ${vssStatus()}"
            toast(if(session.recording) "Gravação iniciada" else "Gravação parada")
        }
        when(command) {
            "toggleDatalogging" -> toggle()
            "toggleDatalogging?" -> confirm("Registro de dados",if(session.recording) "Parar a gravação?" else "Iniciar gravação CSV?") { toggle() }
            else -> toast("Ação do painel não disponível: $command")
        }
    }
    private fun showFullDashboard() {
        if(fullScreenDialog?.isShowing==true)return
        val entry=session.dashboards.entries.firstOrNull { it.id==session.dashboards.selectedId } ?: return
        val dialog=android.app.Dialog(this,com.google.android.material.R.style.Theme_MaterialComponents_NoActionBar)
        val frame=column().apply { setBackgroundColor(Color.BLACK) }
        val controls=row()
        fullScreenStatus=text("${status.text} · ${entry.label}",11f,muted).apply { maxLines=1 }
        controls.addView(fullScreenStatus,LinearLayout.LayoutParams(0,dp(32),1f))
        val view=newDashView(entry);fullDashView=view
        val close=Button(this).apply {
            text="×";contentDescription="Sair da tela cheia";textSize=24f;setTextColor(Color.WHITE);background=shape(0x880B1118.toInt())
            setOnClickListener { dialog.dismiss() }
        }
        controls.addView(close,LinearLayout.LayoutParams(dp(48),dp(36)))
        frame.addView(controls)
        frame.addView(view.view,LinearLayout.LayoutParams(-1,0,1f))
        dialog.setContentView(frame)
        dialog.setOnDismissListener { fullDashView=null;fullScreenDialog=null;fullScreenStatus=null }
        dialog.show();fullScreenDialog=dialog
        dialog.window?.let { window ->
            window.setLayout(-1,-1);window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window,false)
            WindowCompat.getInsetsController(window,frame).apply {
                systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    private fun vssStatus():String {
        if(!session.gpsVssEnabled)return "VSS: ECU · ${session.ecu.display("vssMode")}"
        val description=when(session.gpsVssStatus) {
            GpsVssBridge.Status.ACTIVE -> "enviando à ECU a 5 Hz"
            GpsVssBridge.Status.NO_FIX -> "ECU em espera por GPS válido"
            GpsVssBridge.Status.UNSUPPORTED -> "ECU precisa da extensão GPS VSS"
            GpsVssBridge.Status.CONFIGURATION_REQUIRED -> "grave VSS Mode = CAN/Serial/Analog na ECU"
            GpsVssBridge.Status.NEGOTIATING -> "verificando suporte da ECU"
            else -> "aguardando conexão com a ECU"
        }
        val reported=if(session.fresh)session.ecu.channels()["vss"]?.let { " · retorno ECU: ${fmt(it)} km/h" } ?: "" else ""
        return "VSS: GPS do celular · $description$reported"
    }
    private fun editVss(after:()->Unit) {
        val constant=session.ini.constant("vssMode") ?: run { toast("O INI atual não define VSS Mode");return }
        val native=session.ecu.visibleOptions(constant)
        val selected=if(session.gpsVssEnabled)native.size else session.ecu.visibleOptionIndex(constant,session.ecu.get("vssMode"))
        AlertDialog.Builder(this).setTitle("VSS Mode").setSingleChoiceItems((native+"GPS do celular → ECU").toTypedArray(),selected) { dialog,index ->
            if(!session.busy) {
                if(index==native.size) {
                    session.selectGpsVss(true)
                    toast("GPS selecionado. A ECU precisa da extensão GPS VSS e do modo CAN/Serial/Analog gravado.")
                } else {
                    session.selectGpsVss(false)
                    session.ecu.set("vssMode",session.ecu.actualOptionIndex(constant,index).toDouble())
                }
                after()
            }
            dialog.dismiss()
        }.setNegativeButton("Cancelar",null).show()
    }
    private fun nativeDashboard() {
        val top = row()
        top.addView(text("Instrumentos",25f),LinearLayout.LayoutParams(0,-2,1f))
        val styles = Spinner(this)
        styles.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("Verde","Azul","Âmbar"))
        styles.setSelection(listOf("Verde","Azul","Âmbar").indexOf(layout).coerceAtLeast(0))
        styles.onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?,v:View?,position:Int,id:Long) {
                layout = listOf("Verde","Azul","Âmbar")[position]
                getPreferences(MODE_PRIVATE).edit().putString("layout",layout).apply()
                gauge?.accent = when(layout) { "Azul" -> Color.rgb(112,178,255); "Âmbar" -> Color.rgb(255,186,91); else -> teal }
            }
            override fun onNothingSelected(p:AdapterView<*>?) { }
        }
        top.addView(styles); content.addView(top)
        val instrumentCard = card()
        gauge = GaugeView(this)
        instrumentCard.addView(gauge,LinearLayout.LayoutParams(-1,dp(245)))
        val speedRow=row()
        val speedBox=column()
        speedBox.addView(text("VELOCIDADE DO CELULAR",11f,muted))
        speedText=text("— km/h",36f,teal); speedBox.addView(speedText)
        speedRow.addView(speedBox,LinearLayout.LayoutParams(0,-2,1f))
        sensorText=text("Aguardando GPS",12f,muted); sensorText!!.gravity=Gravity.END
        speedRow.addView(sensorText,LinearLayout.LayoutParams(0,-2,1f)); instrumentCard.addView(speedRow)
        val cells=listOf("map" to "MAP · kPa","tps" to "TPS · %","afr" to "AFR","coolant" to "Motor · °C","iat" to "Admissão · °C","batteryVoltage" to "Bateria · V","advance" to "Avanço · °","pulseWidth" to "Injeção · ms")
        cells.chunked(2).forEach { pair ->
            val line=row()
            pair.forEach { (key,label) ->
                val box=column().apply { background=shape(panel); setPadding(dp(12),dp(10),dp(12),dp(10)) }
                box.addView(text(label,12f,muted)); val number=text("—",26f); box.addView(number)
                readouts[key]=number
                line.addView(box,LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) })
            }
            content.addView(line)
        }
        button("Mínimos / máximos da sessão") {
            AlertDialog.Builder(this).setTitle("Mínimos / máximos")
                .setMessage(if(extrema.isEmpty()) "Sem dados da ECU" else extrema.entries.joinToString("\n") { "${it.key}: ${fmt(it.value.first)} / ${fmt(it.value.second)}" })
                .setPositiveButton("Fechar",null).setNeutralButton("Zerar") { _,_ -> extrema.clear() }.show()
        }
        button("Autorizar GPS e sensores") { locationPermission() }
        note("GPS é a referência de velocidade. A estimativa inercial usa até 2 s entre leituras GPS válidas; fixe o celular no veículo. Sem sinal, a velocidade fica indisponível após 6 s.")
        button("Todos os canais ao vivo") { showChannels() }
    }
    private fun fmt(value: Double) = String.format(Locale.getDefault(),"%.1f",value)
    private fun updateReadings() {
        if (!::status.isInitialized) return
        status.text=when {
            session.busy -> "AGUARDE"
            session.transport.isSimulator -> "DEMONSTRAÇÃO"
            session.fresh -> "CONECTADO"
            session.transport.isConnected -> "SEM TELEMETRIA"
            else -> "OFFLINE"
        }
        detail.text=connectionDetail() + if(session.ecu.isDirty) " · alterações pendentes" else ""
        if(fullScreenDialog?.isShowing==true)fullDashView?.refresh() else dashView?.refresh()
        fullScreenStatus?.text="${status.text} · ${vssStatus()}"
        vssStatusText?.text=vssStatus()
        gauge?.value=session.channel("rpm")
        val now=SystemClock.elapsedRealtime()
        val speed=phone.speed.reading(now)
        speedText?.text=speed?.let { "${fmt(it.kmh)} km/h" } ?: "— km/h"
        sensorText?.text="${speed?.source ?: phone.gpsStatus}\n${phone.acceleration?.let { "${fmt(it)} m/s²" } ?: if(phone.hasAccelerometer) "Aguardando acelerômetro" else "Aceleração indisponível"}"
        readouts.forEach { (key,view) ->
            val value=when(key) {
                "coolant" -> session.channel("coolantRaw")?.minus(40)
                "iat" -> session.channel("iatRaw")?.minus(40)
                else -> session.channel(key)
            }
            view.text=value?.let(::fmt) ?: "—"
            value?.let { v -> val old=extrema[key]; extrema[key]=if(old==null) v to v else min(old.first,v) to max(old.second,v) }
        }
        updateTriggerLogger()
        if (session.recording && now-lastLog>=200) {
            lastLog=now
            val gps=phone.speed.gpsKmh(now)
            val values=Telemetry.logValues((now-session.logStart)/1000.0, session::channel, gps, phone.acceleration, speed?.kmh)
            if(!session.log.append(values)) { session.recording=false; toast("Limite de 60.000 amostras atingido. Exporte ou limpe o log.") }
        }
        if(tab=="Logs") readouts["logCount"]?.text="${session.log.size} amostras · ${if(session.recording) "GRAVANDO" else "parado"}"
    }
    private fun showChannels() {
        val snapshot=session.ini.channels.values.joinToString("\n") { c -> "${c.name}: ${session.channel(c.name)?.let(::fmt) ?: "—"} ${c.units}" }
        AlertDialog.Builder(this).setTitle("Canais · leitura atual").setMessage(snapshot).setPositiveButton("Fechar",null).show()
    }

    private fun settings() {
        title("Ajustes da ECU")
        button("VSS Mode e GPS") { editVss {} }
        note("Edições ficam na cópia local. Use Gravar na ECU para transmitir, conferir a leitura e persistir as páginas alteradas.")
        button("Gravar na ECU") {
            if(!session.transport.isConnected) toast("Conecte uma ECU ou inicie a demonstração")
            else if(session.gpsVssEnabled && session.gpsVssStatus==GpsVssBridge.Status.UNSUPPORTED) toast("Instale a extensão GPS VSS na ECU ou escolha uma fonte VSS nativa antes de gravar.")
            else confirm("Gravar ${session.ecu.dirtyPages().size} página(s)?", "As alterações locais serão transmitidas, verificadas e gravadas na memória da ECU.") {
                session.runOperation({ session.transport.burnDirty(); session.saveSession() })
            }
        }
        button("Ler novamente da ECU") {
            confirm("Ler páginas da ECU", "Esta leitura substituirá os ajustes locais pelas páginas da ECU conectada.") {
                session.runOperation({ session.transport.downloadPages(); session.saveSession() }, { render() })
            }
        }
        val fileRow=card()
        button("Abrir ajuste MSQ",fileRow) { openDocument(IMPORT_MSQ,"*/*") }
        button("Salvar ajuste MSQ",fileRow) { createDocument(EXPORT_MSQ,"application/xml","speeduino.msq") }
        button("Calculadora de combustível requerido",fileRow) { requiredFuel() }
        val search=EditText(this).apply { hint="Buscar parâmetro ou diálogo"; setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(muted) }
        content.addView(search)
        val list=column(); content.addView(list)
        fun populate(query:String) {
            list.removeAllViews()
            val nested=session.ini.dialogs.values.flatMap { it.items }.filter { it.type==IniModel.Item.Type.PANEL }.map { it.constant }.toSet()
            val dialogs=session.ini.dialogs.values.filter {
                if(query.isBlank()) it.name !in nested && it.items.isNotEmpty()
                else it.title.contains(query,true) || it.name.contains(query,true)
            }
            dialogs.forEach { d -> button(d.title.ifBlank { d.name },list) { showIniDialog(d.name) } }
            if(query.isNotBlank()) session.ini.constants.values.filter { !it.isArray && it.name.contains(query,true) }.forEach { c ->
                button("${c.name} · ${session.ecu.display(c.name)} ${session.ecu.units(c)}",list) { edit(c,0) { populate(search.text.toString()) } }
            }
        }
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) { }
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { populate(s.toString()) }
            override fun afterTextChanged(s:Editable?) { }
        }); populate("")
    }
    private fun showIniDialog(id: String) {
        val body=column().apply { setPadding(dp(16),dp(8),dp(16),dp(16)) }
        val seen=mutableSetOf<String>()
        lateinit var populate: () -> Unit
        fun addDialog(name:String,depth:Int) {
            if(depth>8 || !seen.add(name)) return
            val dialog=session.ini.dialogs[name]
            if(dialog==null) {
                session.ini.tables[name]?.let { table -> button(table.title.ifBlank { table.id },body) { tableEditor(table) }; return }
                session.ini.curves[name]?.let { curve -> button(curve.title.ifBlank { curve.id },body) { curveEditor(curve) }; return }
                body.addView(text("Painel $name não disponível neste INI",13f,muted)); return
            }
            dialog.items.forEach { item ->
                if(item.vis.isNotBlank() && !session.ecu.test(item.vis)) return@forEach
                when(item.type) {
                    IniModel.Item.Type.PANEL -> addDialog(item.constant,depth+1)
                    IniModel.Item.Type.FIELD, IniModel.Item.Type.SLIDER -> {
                        val c=session.ini.constant(item.constant)
                        if(c!=null) {
                            fun label() = "${item.label.ifBlank { c.name }}: ${if(c.name=="vssMode" && session.gpsVssEnabled) "GPS do celular → ECU" else session.ecu.display(c.name)} ${session.ecu.units(c)}"
                            button(label(),body) {
                                if(item.enable.isBlank() || session.ecu.test(item.enable)) edit(c,0) { populate() }
                                else toast("Campo desabilitado pelas condições do INI")
                            }
                        }
                    }
                    IniModel.Item.Type.COMMAND -> button(item.label.ifBlank { item.command },body) {
                        when {
                            item.command=="reqFuelCalc" -> requiredFuel()
                            item.command.startsWith("url:") -> runCatching {
                                startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(item.command.removePrefix("url:"))))
                            }.onFailure { toast("Não foi possível abrir a ajuda: ${it.message}") }
                            else -> confirm("Executar comando",item.label.ifBlank { item.command }) { session.runOperation({ session.transport.sendCommand(item.command) }) }
                        }
                    }
                    IniModel.Item.Type.LABEL, IniModel.Item.Type.TEXT -> body.addView(text(item.label,14f,muted))
                    IniModel.Item.Type.DISPLAY_ONLY -> body.addView(text("${item.label}: ${session.ecu.display(item.constant)}",14f))
                    else -> body.addView(text("${item.label.ifBlank { item.type.name }} · ${item.constant}",13f,muted))
                }
            }
        }
        populate = { body.removeAllViews(); seen.clear(); addDialog(id,0) }
        populate()
        AlertDialog.Builder(this).setTitle(session.ini.dialogs[id]?.title ?: id)
            .setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Fechar",null).show()
    }
    private fun edit(c: IniModel.Constant, index: Int, after: () -> Unit) {
        if(session.busy) { toast("Aguarde a operação atual"); return }
        if(c.name=="vssMode") { editVss(after);return }
        if(c.isBits && c.options.isNotEmpty()) {
            val options=session.ecu.visibleOptions(c)
            AlertDialog.Builder(this).setTitle(c.name).setSingleChoiceItems(options.toTypedArray(),session.ecu.visibleOptionIndex(c,session.ecu.get(c,index))) { dialog,selected ->
                if(!session.busy) { session.ecu.set(c.name,session.ecu.actualOptionIndex(c,selected).toDouble()); after() }
                dialog.dismiss()
            }.setNegativeButton("Cancelar",null).show(); return
        }
        val input=EditText(this).apply {
            inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(session.ecu.get(c,index).toString()); selectAll()
        }
        val low=session.ecu.num(c.loRaw,Double.NaN); val high=session.ecu.num(c.hiRaw,Double.NaN)
        val dialog=AlertDialog.Builder(this).setTitle("${c.name}${if(c.isArray) " [$index]" else ""}")
            .setMessage("${session.ecu.units(c)} · faixa $low a $high").setView(input)
            .setNegativeButton("Cancelar",null).setPositiveButton("Aplicar",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value=input.text.toString().replace(',','.').toDoubleOrNull()
                if(value==null || !value.isFinite() || (high>low && value !in low..high)) input.error="Informe um valor dentro da faixa"
                else if(session.busy) input.error="Aguarde a operação atual"
                else { session.ecu.set(c.name,index,value); after(); dialog.dismiss() }
            }
        }; dialog.show()
    }
    private fun maps() {
        title("Mapas e curvas")
        note("Toque em uma célula para editar. Os eixos preservam os endereços e as unidades definidos no INI.")
        val tables=session.ini.tables.values.distinctBy { it.zBins }
        tables.forEach { table -> button(table.title.ifBlank { table.id }) { tableEditor(table) } }
        session.ini.curves.values.forEach { curve -> button(curve.title.ifBlank { curve.id }) { curveEditor(curve) } }
    }
    private fun fittedMap(title:String,help:String,build:(LinearLayout)->Unit) {
        val dialog=android.app.Dialog(this,com.google.android.material.R.style.Theme_MaterialComponents_NoActionBar)
        val frame=column().apply { setBackgroundColor(bg); setPadding(dp(4),0,dp(4),0) }
        val heading=row()
        heading.addView(text(title,18f),LinearLayout.LayoutParams(0,-2,1f))
        button("Fechar",heading) { dialog.dismiss() }.layoutParams=LinearLayout.LayoutParams(dp(80),dp(48))
        frame.addView(heading)
        frame.addView(text(help,11f,muted))
        val grid=column()
        frame.addView(grid,LinearLayout.LayoutParams(-1,0,1f))
        build(grid)
        dialog.setContentView(frame)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left+dp(4),bars.top,bars.right+dp(4),bars.bottom); insets
        }
        dialog.show()
        dialog.window?.setLayout(-1,-1)
    }
    private fun mapCell(line:LinearLayout,label:String,color:Int,description:String,backgroundColor:Int=panel,action:(()->Unit)?=null) {
        val view=text(label,12f,color).apply {
            gravity=Gravity.CENTER; setPadding(0,0,0,0); maxLines=1
            contentDescription=description
            setBackgroundColor(backgroundColor)
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this,3,14,1,android.util.TypedValue.COMPLEX_UNIT_SP)
            if(action!=null) { isFocusable=true; setOnClickListener { action() } }
        }
        line.addView(view,LinearLayout.LayoutParams(0,-1,1f).apply { setMargins(1,1,1,1) })
    }
    private fun tableEditor(table: IniModel.Table) {
        val c=session.ini.constant(table.zBins) ?: return
        fittedMap(table.title.ifBlank { table.id },"${table.yLabel} ↑ / ${table.xLabel} → · toque na célula ou no eixo para editar") { grid ->
            fun populate() {
                grid.removeAllViews()
                val xs=session.ecu.array(table.xBins); val ys=session.ecu.array(table.yBins)
                fun axisLabel(name:String,value:Double) = session.ini.constant(name)?.let { session.ecu.format(it,value) } ?: fmt(value)
                val cols=(0 until c.cols()).sortedBy { xs.getOrElse(it) { it.toDouble() } }
                val rows=(0 until c.rows()).sortedByDescending { ys.getOrElse(it) { it.toDouble() } }
                val lo=session.ecu.num(c.loRaw,0.0); val hi=session.ecu.num(c.hiRaw,255.0)
                rows.forEach { y ->
                    val line=row()
                    val yValue=ys.getOrElse(y) { y.toDouble() }
                    mapCell(line,axisLabel(table.yBins,yValue),teal,"${table.yBins} [$y]") {
                        session.ini.constant(table.yBins)?.let { edit(it,y) { populate() } }
                    }
                    cols.forEach { x ->
                        val index=y*c.cols()+x
                        val value=session.ecu.get(c,index)
                        val hue=(210-190*((value-lo)/(hi-lo).coerceAtLeast(1.0)).coerceIn(0.0,1.0)).toFloat()
                        mapCell(line,session.ecu.format(c,value),Color.WHITE,
                            "${c.name}, ${table.xLabel} ${xs.getOrElse(x){x.toDouble()}}, ${table.yLabel} $yValue",
                            Color.HSVToColor(floatArrayOf(hue,0.50f,0.38f))) {
                            edit(c,index) { populate() }
                        }
                    }
                    grid.addView(line,LinearLayout.LayoutParams(-1,0,1f))
                }
                val axis=row()
                mapCell(axis,"Y / X",muted,"Eixos ${table.yLabel} / ${table.xLabel}")
                cols.forEach { x ->
                    mapCell(axis,axisLabel(table.xBins,xs.getOrElse(x){x.toDouble()}),teal,"${table.xBins} [$x]") {
                        session.ini.constant(table.xBins)?.let { edit(it,x) { populate() } }
                    }
                }
                grid.addView(axis,LinearLayout.LayoutParams(-1,0,1f))
            }
            populate()
        }
    }
    private fun curveEditor(curve: IniModel.Curve) {
        val x=session.ini.constant(curve.xBins) ?: return
        val y=session.ini.constant(curve.yBins) ?: return
        fittedMap(curve.title.ifBlank { curve.id },"${curve.xBins} ↑ / ${curve.yBins} · toque em um valor para editar") { grid ->
            fun populate() {
                grid.removeAllViews()
                val indices=(0 until min(x.length(),y.length())).sortedByDescending { session.ecu.get(x,it) }
                indices.forEach { index ->
                    val line=row()
                    listOf(x,y).forEach { c ->
                        mapCell(line,session.ecu.format(c,session.ecu.get(c,index)),Color.WHITE,"${c.name} [$index]") {
                            edit(c,index) { populate() }
                        }
                    }
                    grid.addView(line,LinearLayout.LayoutParams(-1,0,1f))
                }
                val labels=row()
                mapCell(labels,curve.columnLabels.getOrElse(0){curve.xBins},teal,"Eixo ${curve.xBins}")
                mapCell(labels,curve.columnLabels.getOrElse(1){curve.yBins},teal,"Eixo ${curve.yBins}")
                grid.addView(labels,LinearLayout.LayoutParams(-1,0,1f))
            }
            populate()
        }
    }
    private fun requiredFuel() {
        val box=column().apply { setPadding(dp(20),dp(8),dp(20),dp(8)) }
        fun field(label:String,default:String):EditText {
            box.addView(text(label,13f,muted))
            return EditText(this).apply {
                contentDescription=label
                setText(default)
                inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                box.addView(this)
            }
        }
        fun unitSelector(label:String,options:List<String>) = Spinner(this).apply {
            contentDescription=label
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,options)
            box.addView(this)
        }
        val displacement=field("Cilindrada do motor","2000")
        val displacementUnit=unitSelector("Unidade da cilindrada",listOf("cc","L"))
        val cylinders=field("Cilindros","4")
        val injectorFlow=field("Vazão do injetor","250")
        val injectorFlowUnit=unitSelector("Unidade da vazão do injetor",listOf("cc/min","lb/h"))
        val afr=field("AFR estequiométrico","14.7")
        val output=text("",20f,teal); box.addView(output)
        button("Calcular",box) {
            runCatching {
                val fields=listOf(displacement,cylinders,injectorFlow,afr)
                val values=fields.map { it.text.toString().replace(',','.').toDouble() }
                require(values[1] == values[1].roundToInt().toDouble()) { "Cilindros deve ser inteiro" }
                val displacementCc=if(displacementUnit.selectedItemPosition==1) values[0]*1000.0 else values[0]
                ReqFuelCalculator.calculate(displacementCc,true,values[1].toInt(),values[2],injectorFlowUnit.selectedItemPosition==0,values[3])
            }.onSuccess { output.text="${fmt(it)} ms" }.onFailure { output.text=it.message }
        }
        AlertDialog.Builder(this).setTitle("Combustível requerido").setView(ScrollView(this).apply { addView(box) }).setPositiveButton("Fechar",null).show()
    }

    private fun toggleRecording() {
        if(!session.recording && session.log.size==0) session.logStart=SystemClock.elapsedRealtime()
        session.recording=!session.recording
    }

    private fun logging() {
        title("Registro de dados")
        button("Tooth / Composite Logger") { showTriggerLogger() }
        val count=text("${session.log.size} amostras",22f,teal); readouts["logCount"]=count; content.addView(count)
        note("Aquisição a 5 Hz enquanto o aplicativo está visível. CSV com RPM, MAP, TPS, temperatura em °C, AFR, alvo AFR, Gego, GPS e aceleração. Campos indisponíveis ficam vazios.")
        button(if(session.recording) "Parar gravação" else "Iniciar gravação") {
            toggleRecording(); render()
        }
        button("Exportar CSV") { session.recording=false; createDocument(EXPORT_CSV,"text/csv","speeduino-log.csv") }
        button("Limpar registro") { confirm("Limpar registro", "Descartar ${session.log.size} amostras da sessão?") { session.recording=false; session.log.clear(); render() } }
        button("Analisar combustível de um CSV") { openDocument(ADVISOR,"*/*") }
        note("A análise usa o assessor do projeto original: motor aquecido, trechos estáveis e revisão por região. As sugestões não alteram o mapa automaticamente.")
        button("Histórico da comunicação") {
            AlertDialog.Builder(this).setTitle("Comunicação").setMessage(session.history.joinToString("\n").ifBlank{"Sem eventos"}).setPositiveButton("Fechar",null).show()
        }
    }
    private fun showTriggerLogger() {
        if(triggerDialog?.isShowing==true) return
        val dialog=android.app.Dialog(this,com.google.android.material.R.style.Theme_MaterialComponents_NoActionBar)
        val frame=column().apply { setBackgroundColor(bg);setPadding(dp(6),0,dp(6),0) }
        val heading=row()
        heading.addView(text("TOOTH / COMPOSITE LOGGER",16f),LinearLayout.LayoutParams(0,dp(40),1f))
        val close=Button(this).apply { text="Fechar";isAllCaps=false;setTextColor(teal);setOnClickListener { dialog.dismiss() } }
        heading.addView(close,LinearLayout.LayoutParams(dp(76),dp(40)));frame.addView(heading)
        val config=row()
        val selector=Spinner(this).apply {
            contentDescription="Modo do logger"
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Rotação + fase","Intervalos dos dentes"))
            setSelection(if(session.selectedTriggerMode==TriggerCapture.Mode.COMPOSITE)0 else 1)
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) {
                    if(!session.transport.triggerLoggerActive()) {
                        session.selectedTriggerMode=if(position==0)TriggerCapture.Mode.COMPOSITE else TriggerCapture.Mode.TOOTH
                        triggerScope?.fit()
                        updateTriggerLogger()
                    }
                }
                override fun onNothingSelected(parent:AdapterView<*>?) { }
            }
        }
        triggerSelector=selector
        config.addView(selector,LinearLayout.LayoutParams(0,dp(42),1f))
        triggerStatus=text("",11f,teal).apply { maxLines=2 }
        config.addView(triggerStatus,LinearLayout.LayoutParams(0,-2,1f));frame.addView(config)
        val body=row()
        val graph=column()
        triggerSummary=text("",11f,muted).apply { maxLines=2 };graph.addView(triggerSummary)
        triggerInspector=text("Toque no gráfico para inspecionar um evento",11f,muted).apply { maxLines=2 }
        val scope=TriggerScopeView(this).apply { onInspect={ triggerInspector?.text=it } }
        triggerScope=scope
        graph.addView(scope,LinearLayout.LayoutParams(-1,0,1f));graph.addView(triggerInspector)
        body.addView(graph,LinearLayout.LayoutParams(0,-1,1f))
        frame.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        val controls=column()
        val landscape=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if(landscape) body.addView(controls,LinearLayout.LayoutParams(dp(148),-1)) else frame.addView(controls)
        fun control(label:String,parent:LinearLayout,action:()->Unit):Button = button(label,parent,action).apply {
            minHeight=dp(40)
            layoutParams=if(parent.orientation==LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(0,dp(44),1f).apply { setMargins(dp(2),0,dp(2),0) }
                else LinearLayout.LayoutParams(-1,dp(44))
        }
        val actions=if(landscape)controls else row().also { controls.addView(it) }
        triggerStart=control("Iniciar captura",actions) { session.startTriggerLogger() }
        triggerStop=control("Congelar / parar",actions) { session.stopTriggerLogger() }
        triggerExport=control("Exportar CSV",actions) {
            session.transport.triggerCapture()?.let { capture ->
                session.triggerExport=capture.toCsv()
                createDocument(EXPORT_TRIGGER,"text/csv","speeduino-${capture.mode().name.lowercase(Locale.ROOT)}.csv")
            }
        }
        val zoom=row();controls.addView(zoom)
        control("Zoom −",zoom) { scope.zoomOut() }; control("Zoom +",zoom) { scope.zoomIn() }
        control("Ajustar",if(landscape)controls else zoom) { scope.fit() }
        frame.addView(text("Pulsos digitais reconhecidos pela ECU; não mede tensão. Pinça amplia; arraste para navegar. Intervalos longos podem ser a falha de dentes prevista na roda.",10f,muted).apply { maxLines=2 })
        dialog.setContentView(frame)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left+dp(6),bars.top,bars.right+dp(6),bars.bottom);insets
        }
        dialog.setOnDismissListener {
            triggerDialog=null;triggerScope=null;triggerStatus=null;triggerSummary=null;triggerInspector=null
            triggerStart=null;triggerStop=null;triggerExport=null;triggerSelector=null
            session.stopTriggerLogger()
        }
        dialog.show();dialog.window?.setLayout(-1,-1)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        triggerDialog=dialog
        updateTriggerLogger()
    }
    private fun updateTriggerLogger() {
        val scope=triggerScope ?: return
        val transport=session.transport
        val capture=transport.triggerCapture()?.takeIf { it.mode()==session.selectedTriggerMode }
        val active=transport.triggerLoggerActive()
        if(scope.capture !== capture) {
            scope.capture=capture
            triggerInspector?.text="Toque no gráfico para inspecionar um evento"
        }
        val source=if(capture?.simulated()==true || transport.isSimulator) "DEMONSTRAÇÃO" else "ECU"
        triggerStatus?.text=transport.triggerLoggerError() ?: when {
            active && capture==null -> "$source · aguardando 127 eventos"
            active -> "$source · capturando · ${transport.triggerLoggerBlocks()} blocos"
            capture!=null -> "$source · CONGELADO"
            !transport.isConnected -> "Conecte uma ECU ou inicie a demonstração"
            !transport.supportsTriggerLogger(session.selectedTriggerMode) -> "INI sem logger compatível"
            else -> "$source · pronto para capturar"
        }
        triggerStatus?.setTextColor(if(transport.triggerLoggerError()!=null) Color.rgb(255,100,100) else teal)
        triggerSummary?.text=when {
            capture==null -> "Composite: rotação, fase e sincronismo. Tooth: intervalo entre dentes em µs."
            capture.samples().isEmpty() -> "Bloco sem eventos válidos; verifique sensor, ligação e configuração de entrada."
            capture.mode()==TriggerCapture.Mode.TOOTH -> "${capture.samples().size} dentes · mediana ${capture.medianIntervalUs()} µs · intervalos ≥1,8× mediana: ${capture.longIntervals()}"
            else -> "${capture.samples().size} eventos · rotação: ${capture.crankEdges()} transições · fase: ${capture.camEdges()} · perdas de sinc.: ${capture.syncLosses()}"
        }
        triggerStart?.isEnabled=!session.busy && !active && transport.isConnected && transport.supportsTriggerLogger(session.selectedTriggerMode)
        triggerStop?.isEnabled=!session.busy && active
        triggerExport?.isEnabled=!session.busy && capture!=null && capture.samples().isNotEmpty()
        triggerSelector?.isEnabled=!session.busy && !active
    }

    private fun connections() {
        title("Conectar ao Speeduino")
        note("${session.ini.signature}\n${session.ini.constants.size} parâmetros · ${session.ini.channels.size} canais")
        button("Bluetooth serial (SPP)") { bluetoothPermission() }
        button("USB / RS232 via OTG") { chooseUsb() }
        button("Desconectar") { session.disconnect() }
        val sim=card()
        sim.addView(text("Modo de demonstração",18f))
        sim.addView(text("Telemetria simulada para explorar os instrumentos e editores sem uma ECU.",14f,muted))
        button("Iniciar demonstração",sim) { session.connect(SimLink(session.ini)) }
        button("Carregar definição INI") {
            confirm("Trocar definição INI", "A conexão será encerrada e o ajuste local será reiniciado. Salve um MSQ antes de trocar a definição.") { openDocument(IMPORT_INI,"*/*") }
        }
        note("Bluetooth: pareie o adaptador SPP nas configurações do Android antes de conectar. A velocidade serial do módulo deve coincidir com a ECU.\n\nUSB: use OTG e adaptador USB–serial compatível. RS232 exige um conversor com níveis elétricos RS232. O padrão é 115200 baud, 8N1.")
        button("Autorizar localização precisa") { locationPermission() }
        button("Configurações de localização") { startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
    }
    private fun locationPermission() {
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) { phone.stop(); phone.start(); return }
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),GPS_PERMISSION)
    }
    private fun bluetoothPermission() {
        if(Build.VERSION.SDK_INT>=31 && ContextCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) {
            pendingBluetooth=true; requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT),BT_PERMISSION)
        } else chooseBluetooth()
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        when(requestCode) {
            GPS_PERMISSION -> { phone.stop(); phone.start(); if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) toast("Velocidade GPS requer localização precisa. Você pode autorizar nas configurações do aplicativo.") }
            BT_PERMISSION -> { if(pendingBluetooth && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) chooseBluetooth() else toast("Permissão Bluetooth recusada"); pendingBluetooth=false }
        }
    }
    @SuppressLint("MissingPermission")
    private fun chooseBluetooth() {
        val adapter=getSystemService(BluetoothManager::class.java)?.adapter
        if(adapter==null) { toast("Este aparelho não possui Bluetooth"); return }
        if(!adapter.isEnabled) { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)); return }
        val devices=adapter.bondedDevices.sortedBy { it.name ?: it.address }
        if(devices.isEmpty()) { toast("Pareie primeiro seu adaptador serial nas configurações Bluetooth"); startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)); return }
        AlertDialog.Builder(this).setTitle("Adaptadores pareados · SPP").setItems(devices.map { "${it.name ?: "Bluetooth"}\n${it.address}" }.toTypedArray()) { _,which ->
            session.connect(StreamLink(session.ini,BluetoothSerial(devices[which])))
        }.setNegativeButton("Cancelar",null).show()
    }
    private fun chooseUsb() {
        val ports=UsbSerialProber.getDefaultProber().findAllDrivers(usb).flatMap { it.ports }
        if(ports.isEmpty()) { toast("Nenhum adaptador USB–serial encontrado. Conecte-o via OTG."); return }
        AlertDialog.Builder(this).setTitle("Portas USB disponíveis").setItems(ports.map {
            "${it.driver.javaClass.simpleName} · ${it.driver.device.deviceName} · porta ${it.portNumber+1}"
        }.toTypedArray()) { _,which ->
            val port=ports[which]
            if(usb.hasPermission(port.driver.device)) connectUsb(port)
            else {
                pendingPort=port
                usb.requestPermission(port.driver.device,PendingIntent.getBroadcast(this,0,Intent(permissionAction).setPackage(packageName),PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
        }.setNegativeButton("Cancelar",null).show()
    }
    private fun connectUsb(port:UsbSerialPort) {
        val rates=listOf(115200,57600,38400,19200,9600)
        AlertDialog.Builder(this).setTitle("Velocidade serial · 8N1").setItems(rates.map { "$it baud" }.toTypedArray()) { _,which ->
            session.connect(StreamLink(session.ini,UsbSerial(usb,port,rates[which])))
        }.setNegativeButton("Cancelar",null).show()
    }

    private fun openDocument(code:Int,mime:String) {
        pendingDocument = code
        documents.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type=mime; addCategory(Intent.CATEGORY_OPENABLE) })
    }
    private fun createDocument(code:Int,mime:String,name:String) {
        pendingDocument = code
        documents.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type=mime; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_TITLE,name) })
    }
    private fun handleDocument(requestCode: Int, uri: Uri) {
        if(session.busy) { toast("Aguarde a operação atual e tente novamente"); return }
        when(requestCode) {
            EXPORT_TRIGGER -> {
                val csv=session.triggerExport ?: return
                session.runOperation({ contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { it.write(csv) } }, { toast("Captura do logger exportada") })
            }
            EXPORT_CSV -> {
                val csv=CsvLog.HEADER+session.log.body()
                session.runOperation({ contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { it.write(csv) } }, { toast("CSV exportado"); render() })
            }
            EXPORT_MSQ -> session.runOperation({
                val file=File(cacheDir,"export.msq"); MsqIO.save(file.toPath(),session.ecu)
                contentResolver.openOutputStream(uri,"wt")!!.use { output -> file.inputStream().use { it.copyTo(output) } }
            }, { toast("Ajuste MSQ salvo") })
            IMPORT_MSQ -> session.runOperation({
                val file=readDocument(uri,"import.msq",8*1024*1024)
                // Parse into a separate ECU first, so malformed files cannot partially replace a tune.
                val candidate=Ecu(session.ini)
                for(page in 1..session.ini.nPages) candidate.replacePage(page,session.ecu.page(page).clone())
                val pc = session.ini.pcScalars.values + session.ini.pcBits.values
                pc.forEach { candidate.set(it.name,session.ecu.get(it.name)) }
                MsqIO.load(file.toPath(),candidate)
                for(page in 1..session.ini.nPages) { session.ecu.replacePage(page,candidate.page(page)); session.ecu.markDirty(page-1) }
                pc.forEach { session.ecu.set(it.name,candidate.get(it.name)) }
                session.saveSession()
            }, { toast("MSQ carregado localmente; gravação na ECU pendente"); render() })
            IMPORT_DASH -> {
                var added:DashboardStore.Entry?=null
                val name=contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor -> if(cursor.moveToFirst())cursor.getString(0) else "Painel.dash" } ?: "Painel.dash"
                session.runOperation({ added=session.dashboards.import(readDocument(uri,"import.dash",16*1024*1024),name) }, { toast("Painel ${added!!.label} importado");tab="Painel";render() })
            }
            IMPORT_INI -> {
                val old=session
                old.runOperation({
                    val file=readDocument(uri,"definition.ini",8*1024*1024)
                    val parsed=IniModel.load(file.toPath(),null)
                    require(parsed.signature.isNotBlank() && parsed.channels.isNotEmpty() && parsed.constants.isNotEmpty()) { "INI sem assinatura, parâmetros ou canais" }
                    old.transport.disconnect()
                    file.copyTo(File(filesDir,"speeduino.ini"),overwrite=true)
                    File(filesDir,"session.msq").delete()
                }, {
                    old.close(false); model.session=StudioSession(applicationContext); extrema.clear(); bindSession(); render(); toast("Definição INI carregada")
                })
            }
            ADVISOR -> {
                var report:FuelTrimAdvisor.Report?=null
                session.runOperation({
                    val file=readDocument(uri,"analysis.csv",20*1024*1024)
                    report=file.bufferedReader().use { FuelTrimAdvisor.analyze(it,FuelTrimAdvisor.Settings(14.7,70.0,20,5.0)) }
                }, {
                    val result=report!!
                    val body="${result.accepted()} amostras aceitas · ${result.rejected()} rejeitadas\n\n"+result.cells().joinToString("\n") { "${it.rpm()} rpm / ${it.map()} kPa: ${fmt(it.suggestedPercent())}% · n=${it.samples()}${if(it.ready()) "" else " · revisar dispersão/amostras"}" }
                    AlertDialog.Builder(this).setTitle("Análise de combustível").setMessage(body.ifBlank { "Sem regiões estáveis" }).setPositiveButton("Fechar",null).show()
                })
            }
        }
    }
    private fun readDocument(uri:Uri,name:String,limit:Int):File {
        val file=File(cacheDir,name)
        contentResolver.openInputStream(uri)!!.use { input ->
            file.outputStream().use { output ->
                val buffer=ByteArray(8192); var total=0
                while(true) { val count=input.read(buffer); if(count<0) break; total+=count; require(total<=limit) { "Arquivo excede ${limit/(1024*1024)} MiB" }; output.write(buffer,0,count) }
            }
        }; return file
    }
    companion object {
        private const val GPS_PERMISSION=20
        private const val BT_PERMISSION=21
        private const val EXPORT_MSQ=100
        private const val IMPORT_MSQ=101
        private const val EXPORT_CSV=102
        private const val IMPORT_INI=103
        private const val ADVISOR=104
        private const val IMPORT_DASH=105
        private const val EXPORT_TRIGGER=106
    }
}
