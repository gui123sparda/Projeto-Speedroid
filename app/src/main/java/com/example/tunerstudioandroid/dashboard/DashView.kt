package com.example.tunerstudioandroid.dashboard

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import org.clonetuner.dashboard.DashboardDefinition
import org.clonetuner.dashboard.DashboardDefinition.Component
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.*

/** Canvas renderer for the actual .dash document, with embedded assets and virtual accessibility nodes. */
class DashView(
    context: Context,
    val definition: DashboardDefinition,
    private val telemetry: DashTelemetry,
    private val expression: (String, Double) -> Double,
    private val action: (String) -> Unit,
    private val inspect: (String) -> Unit = {},
    private val history: DashHistory = DashHistory()
) : View(context), PanelSurface {
    override val view: View get()=this
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rectangle=RectF()
    private val hsv=FloatArray(3)
    private val path=Path()
    private val images=hashMapOf<String,Bitmap>()
    private val fonts=hashMapOf<String,Typeface>()
    private val states=definition.components().map { State(it) }
    private val frames=states.map { Frame(it,0f,0f) }
    override val diagnostics=linkedSetOf<String>().apply { addAll(definition.diagnostics()) }
    private var viewport=DashGeometry.viewport(1.0,1.0)
    private var zoom=1f
    private var panX=0f
    private var panY=0f
    private var lastFrame=0L
    private var moving=false
    private val accessibility=DashAccessibility()
    private val scaleGesture=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector:ScaleGestureDetector):Boolean {
            val next=(zoom*detector.scaleFactor).coerceIn(1f,4f)
            val factor=next/zoom
            panX=detector.focusX-(detector.focusX-panX)*factor
            panY=detector.focusY-(detector.focusY-panY)*factor
            zoom=next; constrainPan();invalidate();return true
        }
    })
    private val gestures=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e:MotionEvent)=true
        override fun onSingleTapConfirmed(e:MotionEvent):Boolean { performClick(); return activateAt(e.x,e.y,false) }
        override fun onLongPress(e:MotionEvent) { activateAt(e.x,e.y,true) }
        override fun onDoubleTap(e:MotionEvent):Boolean {
            if(zoom>1) { zoom=1f;panX=0f;panY=0f }
            else { zoom=2f;panX=-e.x;panY=-e.y;constrainPan() }
            invalidate();accessibility.invalidateRoot();return true
        }
        override fun onScroll(e1:MotionEvent?,e2:MotionEvent,distanceX:Float,distanceY:Float):Boolean {
            if(zoom<=1)return false
            panX-=distanceX;panY-=distanceY;constrainPan();invalidate();return true
        }
    })
    private class State(val c:Component) {
        var displayed=Double.NaN
        var peak=Double.NaN
        var peakAt=0L
        var reading=DashTelemetry.Reading(null,"")
    }
    init {
        contentDescription="Painel .dash ${definition.name()}"
        isFocusable=true; isClickable=true
        ViewCompat.setAccessibilityDelegate(this,accessibility)
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        val directory=File(context.cacheDir,"dash-fonts").apply { mkdirs() }
        definition.resources().forEach { (id,resource) ->
            runCatching {
                val bytes=resource.bytes()
                if(resource.type().equals("ttf",true)) {
                    val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    val file=File(directory,"$hash.ttf")
                    if(!file.exists()) file.writeBytes(bytes)
                    fonts[id]=Typeface.createFromFile(file)
                } else {
                    val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: error("Imagem inválida")
                    images[id]=bitmap
                }
            }.onFailure { diagnostics.add("Recurso $id: ${it.message}") }
        }
        states.forEach { state ->
            for(key in listOf("BackgroundImageFileName","OnImageFileName","OffImageFileName","NeedleImageFileName")) {
                val id=state.c.text(key)
                if(id.isNotEmpty() && id !in images) diagnostics.add("${state.c.id()}: imagem ausente $id")
            }
        }
    }
    override fun refresh() { postInvalidateOnAnimation() }
    override fun resetHistory() { history.clear(); states.forEach { it.peak=Double.NaN;it.peakAt=0 };invalidate() }
    override fun extrema():List<String> = states.filter { !it.c.painter().contains("Indicator") }.mapNotNull { state -> history.range(state.c.id())?.let { range -> "${state.c.text("Title").ifBlank { state.c.text("OutputChannel") }}: ${format(range.minimum,2)} / ${format(range.maximum,2)} ${state.reading.units}" } }.distinct()
    private fun constrainPan() { panX=panX.coerceIn(width*(1-zoom),0f);panY=panY.coerceIn(height*(1-zoom),0f) }
    override fun onMeasure(widthSpec:Int,heightSpec:Int) {
        val width=MeasureSpec.getSize(widthSpec)
        val desired=(width*620.0/1200).roundToInt()
        setMeasuredDimension(width,resolveSize(desired,heightSpec))
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        viewport=DashGeometry.viewport(w.toDouble(),h.toDouble());constrainPan();accessibility.invalidateRoot()
    }
    override fun onDraw(canvas:Canvas) {
        val now=SystemClock.elapsedRealtime()
        val dt=if(lastFrame==0L) 0.1 else ((now-lastFrame)/1000.0).coerceIn(0.0,0.1)
        lastFrame=now;moving=false
        canvas.drawColor(Color.BLACK)
        canvas.save();canvas.translate(panX,panY);canvas.scale(zoom,zoom)
        canvas.clipRect(viewport.x.toFloat(),viewport.y.toFloat(),(viewport.x+viewport.width).toFloat(),(viewport.y+viewport.height).toFloat())
        paint.style=Paint.Style.FILL;paint.color=DashGeometry.argb(definition.clusterProperties()["clusterBackgroundColor"] ?: "",Color.BLACK)
        canvas.drawRect(viewport.x.toFloat(),viewport.y.toFloat(),(viewport.x+viewport.width).toFloat(),(viewport.y+viewport.height).toFloat(),paint)
        clusterBackground(canvas)
        for(index in states.indices) {
            val state=states[index]
            val c=state.c
            val box=DashGeometry.bounds(c,viewport)
            if(box.x+box.width<=viewport.x || box.y+box.height<=viewport.y || box.x>=viewport.x+viewport.width || box.y>=viewport.y+viewport.height)continue
            state.reading=telemetry.read(c)
            val value=state.reading.value
            if(value!=null) {
                history.accept(c.id(),value,now,c.number("HistoryDelay",15000.0).toLong())
                state.peak=history.range(c.id())!!.peak
                val smoothing=c.number("NeedleSmoothing",0.0).coerceIn(0.0,100.0)
                if(!state.displayed.isFinite() || smoothing<=0) state.displayed=value
                else {
                    state.displayed += (value-state.displayed)*(1-exp(-dt/(0.055*smoothing.coerceAtLeast(1.0))))
                    if(abs(value-state.displayed)>0.01)moving=true else state.displayed=value
                }
            } else state.displayed=Double.NaN
            // Render in design pixels, scaling both coordinates and stroke widths together.
            val scale=viewport.width/1200.0
            val w=(box.width/scale).toFloat();val h=(box.height/scale).toFloat()
            canvas.save();canvas.translate(box.x.toFloat(),box.y.toFloat());canvas.scale(scale.toFloat(),scale.toFloat());canvas.clipRect(0f,0f,w,h)
            paint.isAntiAlias=c.flag("AntialiasingOn",true)
            val frame=frames[index]
            frame.w=w;frame.h=h;frame.resolveScale()
            when(c.painter()) {
                "Basic Readout" -> readout(canvas,frame)
                "Analog Gauge", "Circle Analog Gauge" -> dial(canvas,frame)
                "Asymetric Sweep Gauge" -> sweep(canvas,frame)
                "Horizontal Bar Gauge","Horizontal Line Gauge","Vertical Bar Gauge","Vertical Dashed Bar Gauge" -> bar(canvas,frame)
                "Basic Rectangle Indicator","Bulb Indicator" -> indicator(canvas,frame)
                else -> { text(canvas,frame,"${c.painter()} · indisponível",w/2,h/2,min(16f,h*.4f),w,Color.GRAY) }
            }
            canvas.restore()
        }
        canvas.restore()
        if(moving && isShown && windowVisibility==VISIBLE)postInvalidateOnAnimation()
    }
    private fun clusterBackground(canvas:Canvas) {
        val id=definition.clusterProperties()["clusterBackgroundImageFileName"] ?: return
        val image=images[id] ?: return
        val b=viewport
        when(definition.clusterProperties()["clusterBackgroundImageStyle"]) {
            "Tile" -> {
                val shader=BitmapShader(image,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT)
                paint.shader=shader;paint.color=Color.WHITE
                canvas.drawRect(b.x.toFloat(),b.y.toFloat(),(b.x+b.width).toFloat(),(b.y+b.height).toFloat(),paint);paint.shader=null
            }
            "Stretch", "Scale" -> { rectangle.set(b.x.toFloat(),b.y.toFloat(),(b.x+b.width).toFloat(),(b.y+b.height).toFloat());paint.color=Color.WHITE;canvas.drawBitmap(image,null,rectangle,paint) }
            else -> { paint.color=Color.WHITE;canvas.drawBitmap(image,(b.x+(b.width-image.width)/2).toFloat(),(b.y+(b.height-image.height)/2).toFloat(),paint) }
        }
    }
    private inner class Frame(val state:State,var w:Float,var h:Float) {
        val c get()=state.c
        val reading get()=state.reading
        fun n(key:String,default:Double):Double {
            val raw=c.text(key)
            if(raw.isEmpty())return default
            val result=raw.toDoubleOrNull() ?: expression(raw,default)
            return result.takeIf { it.isFinite() } ?: default
        }
        var minimum=0.0
        var maximum=100.0
        private var cachedFont:Typeface?=null
        fun resolveScale() { minimum=n("MinVP",n("Min",0.0));maximum=n("MaxVP",n("Max",100.0)) }
        val fraction get()=DashGeometry.fraction(state.displayed,minimum,maximum,c.flag("PegLimits",true))
        val digits get()=n("ValueDigitsVP",n("ValueDigits",0.0)).toInt().coerceIn(0,6)
        fun color(key:String,default:Int)=DashGeometry.argb(c.text(key),default)
        fun valueColor(value:Double?,normal:Int):Int {
            if(value==null)return normal
            if(value<n("LowCriticalVP",n("LowCritical",-Double.MAX_VALUE)) || value>n("HighCriticalVP",n("HighCritical",Double.MAX_VALUE)))return color("CriticalColor",Color.RED)
            if(value<n("LowWarningVP",n("LowWarning",-Double.MAX_VALUE)) || value>n("HighWarningVP",n("HighWarning",Double.MAX_VALUE)))return color("WarnColor",Color.YELLOW)
            return normal
        }
        fun valueText()=reading.value?.let { format(it,digits) } ?: "--"
        fun typeface():Typeface {
            cachedFont?.let { return it }
            val base=fonts[c.text("FontFamily")] ?: if(c.text("FontFamily").isBlank()) Typeface.DEFAULT_BOLD else Typeface.create(c.text("FontFamily"),Typeface.NORMAL)
            return (if(c.flag("ItalicFont",false)) Typeface.create(base,if(base.isBold) Typeface.BOLD_ITALIC else Typeface.ITALIC) else base).also { cachedFont=it }
        }
    }
    private fun format(value:Double,digits:Int)=String.format(Locale.ROOT,"%.${digits.coerceIn(0,6)}f",value)
    private fun text(canvas:Canvas,f:Frame,value:String,x:Float,y:Float,size:Float,maxWidth:Float,color:Int) {
        if(value.isEmpty())return
        paint.style=Paint.Style.FILL;paint.color=color;paint.typeface=f.typeface();paint.textAlign=Paint.Align.CENTER
        paint.textSize=max(1f,size)
        val measured=paint.measureText(value)
        if(measured>maxWidth && maxWidth>0)paint.textSize=max(1f,paint.textSize*maxWidth/measured)
        val baseline=y-(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2
        canvas.drawText(value,x,baseline,paint)
    }
    private fun rect(canvas:Canvas,x:Float,y:Float,w:Float,h:Float,color:Int) {
        if(w<=0 || h<=0)return
        paint.style=Paint.Style.FILL;paint.color=color;canvas.drawRect(x,y,x+w,y+h,paint)
    }
    private fun line(canvas:Canvas,x:Float,y:Float,ex:Float,ey:Float,width:Float,color:Int) {
        paint.style=Paint.Style.STROKE;paint.strokeWidth=width.coerceAtLeast(.5f);paint.strokeCap=Paint.Cap.BUTT;paint.color=color;canvas.drawLine(x,y,ex,ey,paint);paint.style=Paint.Style.FILL
    }
    private fun bitmap(canvas:Canvas,id:String,w:Float,h:Float):Boolean {
        val image=images[id] ?: return false
        rectangle.set(0f,0f,w,h);paint.color=Color.WHITE;paint.style=Paint.Style.FILL
        canvas.drawBitmap(image,null,rectangle,paint);return true
    }
    private fun border(canvas:Canvas,f:Frame) {
        val width=f.n("BorderWidth",0.0).toFloat()
        if(width<=0)return
        rectangle.set(width/2,width/2,f.w-width/2,f.h-width/2)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=width;paint.color=f.color("TrimColor",Color.GRAY)
        canvas.drawRect(rectangle,paint);paint.style=Paint.Style.FILL
    }
    private fun readout(canvas:Canvas,f:Frame) {
        rectangle.set(0f,0f,f.w,f.h);paint.style=Paint.Style.FILL
        paint.color=f.valueColor(f.reading.value,f.color("BackColor",Color.TRANSPARENT))
        val radius=min(15f,f.h*.14f);canvas.drawRoundRect(rectangle,radius,radius,paint)
        bitmap(canvas,f.c.text("BackgroundImageFileName"),f.w,f.h)
        val ink=f.color("FontColor",Color.WHITE)
        text(canvas,f,f.valueText(),f.w/2,f.h*.38f,f.h*.86f+f.n("FontSizeAdjustment",0.0).toFloat(),f.w*.96f,if(f.reading.value==null)Color.GRAY else ink)
        val label=f.c.text("Title")+(if(f.reading.units.isBlank()) "" else " (${f.reading.units})")
        text(canvas,f,label,f.w/2,f.h*.84f,f.h*.24f,f.w*.96f,ink);border(canvas,f)
    }
    private fun indicator(canvas:Canvas,f:Frame) {
        val state=if(f.reading.value!=null && f.reading.value!=0.0) "On" else "Off"
        val background=f.color(state+"BackgroundColor",Color.TRANSPARENT)
        if(f.c.painter()=="Bulb Indicator") {
            paint.color=background;paint.style=Paint.Style.FILL;rectangle.set(1f,1f,f.w-1,f.h-1);canvas.drawOval(rectangle,paint)
        } else rect(canvas,0f,0f,f.w,f.h,background)
        val hasImage=bitmap(canvas,f.c.text(state+"ImageFileName"),f.w,f.h)
        if(!hasImage) {
            val label=if(f.c.text("OutputChannel").isBlank()) f.c.text(state+"Text") else if(f.reading.value==null) "--" else f.c.text(state+"Text")
            text(canvas,f,label,f.w/2,f.h/2,f.h*.73f,f.w*.96f,f.color(state+"TextColor",Color.WHITE))
        } else if(f.reading.value==null)rect(canvas,0f,0f,f.w,f.h,0x88000000.toInt())
        border(canvas,f)
    }
    private fun bar(canvas:Canvas,f:Frame) {
        rect(canvas,0f,0f,f.w,f.h,f.color("BackColor",Color.TRANSPARENT));bitmap(canvas,f.c.text("BackgroundImageFileName"),f.w,f.h)
        val pad=max(1f,f.n("BorderWidth",1.0).toFloat())
        val reverse=f.c.flag("CounterClockwise",false)
        val fraction=f.fraction.coerceIn(0.0,1.0).toFloat()
        val normal=f.color("FontColor",Color.WHITE)
        val color=f.valueColor(f.reading.value,normal)
        if(f.reading.value!=null) {
            when(f.c.painter()) {
                "Horizontal Line Gauge" -> {
                    val x=pad+(f.w-2*pad)*(if(reverse)1-fraction else fraction)
                    rect(canvas,x.coerceIn(pad,max(pad,f.w-pad-3)),pad,3f,max(0f,f.h-2*pad),color)
                }
                "Vertical Bar Gauge","Vertical Dashed Bar Gauge" -> {
                    val length=(f.h-2*pad)*fraction
                    rect(canvas,pad,if(reverse)pad else f.h-pad-length,max(0f,f.w-2*pad),length,color)
                    if(f.c.painter().contains("Dashed")) for(y in pad.toInt() until f.h.toInt() step 8)line(canvas,pad,y.toFloat(),f.w-pad,y.toFloat(),2f,Color.BLACK)
                }
                else -> {
                    val length=(f.w-2*pad)*fraction
                    rect(canvas,if(reverse)f.w-pad-length else pad,pad,length,max(0f,f.h-2*pad),color)
                }
            }
            if(f.c.flag("ShowHistory",false) && f.state.peak.isFinite() && f.c.painter().startsWith("Horizontal")) {
                val peak=DashGeometry.fraction(f.state.peak,f.minimum,f.maximum,true).toFloat()
                val x=pad+(f.w-2*pad)*(if(reverse)1-peak else peak)
                line(canvas,x,pad,x,f.h-pad,1.5f,normal)
            }
        }
        // Explicit endpoints remain visible, including ranges that decrease left to right.
        border(canvas,f)
    }
    private fun tickStep(range:Double):Double {
        if(range<=0 || !range.isFinite())return 1.0
        val raw=range/8;val power=10.0.pow(floor(log10(raw)));val x=raw/power
        return (if(x<=1)1 else if(x<=2)2 else if(x<=5)5 else 10)*power
    }
    private fun dial(canvas:Canvas,f:Frame) {
        val cx=f.w/2;val cy=f.h/2;val rx=max(1f,f.w/2-4);val ry=max(1f,f.h/2-4)
        val face=f.n("FaceAngle",360.0).toFloat()
        rectangle.set(cx-rx,cy-ry,cx+rx,cy+ry)
        paint.color=f.color("BackColor",Color.TRANSPARENT);paint.style=Paint.Style.FILL
        canvas.drawArc(rectangle,-face,face,true,paint)
        bitmap(canvas,f.c.text("BackgroundImageFileName"),f.w,f.h)
        val ink=f.color("FontColor",Color.WHITE)
        val sweep=f.n("SweepAngle",240.0)
        val direction=if(f.c.flag("CounterClockwise",false)) -1 else 1
        var low=f.n("SweepBeginDegree",f.n("StartAngle",210.0)-sweep)+sweep
        if(direction<0)low=180-low
        val step=f.n("MajorTicks",-1.0).takeIf { it>0 } ?: tickStep(abs(f.maximum-f.minimum))
        val divisions=ceil(abs(f.maximum-f.minimum)/step).toInt().coerceIn(1,100)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=max(1f,f.n("BorderWidth",1.0).toFloat());paint.color=f.color("TrimColor",ink)
        canvas.drawArc(rectangle,(-low).toFloat(),(sweep*direction).toFloat(),false,paint);paint.style=Paint.Style.FILL
        val minor=f.n("MinorTicks",-1.0).takeIf { it>0 } ?: step/5
        val count=ceil(abs(f.maximum-f.minimum)/minor).toInt().coerceIn(1,1000)
        for(i in 0..count) {
            val value=f.minimum+i*minor
            if(value>f.maximum+.00001)break
            val fraction=DashGeometry.fraction(value,f.minimum,f.maximum,true)
            val a=Math.toRadians(low-sweep*fraction*direction)
            val major=abs((value-f.minimum)/step-((value-f.minimum)/step).roundToInt())<.001
            val inner=if(major).84 else .92
            line(canvas,cx+(cos(a)*rx*inner).toFloat(),cy-(sin(a)*ry*inner).toFloat(),cx+(cos(a)*rx*.98).toFloat(),cy-(sin(a)*ry*.98).toFloat(),if(major)3f else 1.5f,ink)
            if(major) {
                val divisor=if(step>=1000)1000 else 1
                text(canvas,f,format(value/divisor,f.n("LabelDigits",0.0).toInt()),cx+(cos(a)*rx*.80).toFloat(),cy-(sin(a)*ry*.80).toFloat(),min(f.w*.045f,f.h*.10f)+f.n("FontSizeAdjustment",0.0).toFloat(),f.w*.14f,ink)
            }
        }
        val angle=Math.toRadians(low-sweep*f.fraction*direction)
        val needle=f.color("NeedleColor",Color.RED)
        val tx=cx+(cos(angle)*rx*.9).toFloat();val ty=cy-(sin(angle)*ry*.9).toFloat()
        val thickness=max(3f,f.w*.008f)
        path.reset();path.moveTo(cx-(sin(angle)*thickness).toFloat(),cy-(cos(angle)*thickness).toFloat())
        path.lineTo(tx,ty);path.lineTo(cx+(sin(angle)*thickness).toFloat(),cy+(cos(angle)*thickness).toFloat())
        path.lineTo(cx-(cos(angle)*f.w*.025).toFloat(),cy+(sin(angle)*f.h*.025).toFloat());path.close()
        paint.style=Paint.Style.FILL;paint.color=if(f.reading.value==null)Color.DKGRAY else needle;canvas.drawPath(path,paint)
        if(f.c.text("NeedleImageFileName").isNotBlank()) {
            images[f.c.text("NeedleImageFileName")]?.let { image ->
                canvas.save();canvas.rotate((-Math.toDegrees(angle)).toFloat(),cx,cy);rectangle.set(cx-f.w*.025f,cy-thickness,cx+rx*.9f,cy+thickness);paint.color=Color.WHITE;canvas.drawBitmap(image,null,rectangle,paint);canvas.restore()
            }
        }
        val titleY=if(face<=180)f.h*.40f else f.h*.66f
        text(canvas,f,f.c.text("Title"),cx,titleY,f.h*.075f,f.w*.65f,ink)
        if(f.c.flag("DisplayValueVisible",true))text(canvas,f,f.valueText()+f.reading.units,cx,titleY+(if(face<=180)-f.h*.115f else f.h*.115f),f.h*.10f,f.w*.5f,ink)
        text(canvas,f,if(step>=1000)"X1000" else f.reading.units,cx,if(face<=180)f.h*.18f else titleY+f.h*.21f,f.h*.065f,f.w*.5f,ink)
    }
    private fun control(q:Double):Pair<Double,Double> = when { q>3 -> q-3 to 0.0; q>2 -> 0.0 to 3-q; q>1 -> 2-q to 1.0; else -> 1.0 to q }
    private fun cubic(t:Double,a:Double,b:Double,c:Double,d:Double):Double { val u=1-t;return u*u*u*a+3*u*u*t*b+3*u*t*t*c+t*t*t*d }
    private fun sweep(canvas:Canvas,f:Frame) {
        val w=f.w.toDouble();val h=f.h.toDouble();val border=f.n("BorderWidth",2.0)/2
        val thickness=max(8.0,min(h-2*border,max(h*f.n("FaceAngle",180.0)/360,360-f.n("StartAngle",328.0))))
        val rise=h-thickness-2*border;val bottom=h-thickness-border
        val a=control(f.n("SweepBeginDegree",292.0)/90);val b=control(f.n("SweepAngle",281.0)/90)
        val x0=border;val x1=(w-2*border)*a.first;val x2=(w-2*border)*b.first;val x3=w-2*border
        val y0=bottom;val y1=rise*a.second;val y2=rise*b.second;val y3=border
        path.reset();path.moveTo(x0.toFloat(),y0.toFloat());path.cubicTo(x1.toFloat(),y1.toFloat(),x2.toFloat(),y2.toFloat(),x3.toFloat(),y3.toFloat())
        path.lineTo(x3.toFloat(),(y3+thickness).toFloat());path.cubicTo(x2.toFloat(),(y2+thickness).toFloat(),x1.toFloat(),(y1+thickness).toFloat(),x0.toFloat(),(y0+thickness).toFloat());path.close()
        paint.style=Paint.Style.FILL;paint.color=f.color("BackColor",Color.DKGRAY);canvas.drawPath(path,paint)
        canvas.save();canvas.clipPath(path)
        val segment=max(7.0,w/54)
        var x=0.0
        while(x<w) {
            val value=f.minimum+(f.maximum-f.minimum)*x/w
            val color=f.valueColor(value,f.color("NeedleColor",Color.WHITE))
            val active=f.reading.value!=null && x<=w*f.fraction.coerceIn(0.0,1.0)
            Color.colorToHSV(color,hsv);if(!active)hsv[2]*=.22f
            rect(canvas,x.toFloat(),0f,max(1.0,segment-2).toFloat(),f.h,Color.HSVToColor(Color.alpha(color),hsv));x+=segment
        }
        if(f.c.flag("ShowHistory",false) && f.reading.value!=null && f.state.peak.isFinite()) {
            val peakX=(DashGeometry.fraction(f.state.peak,f.minimum,f.maximum,true)*w).toFloat()
            line(canvas,peakX,0f,peakX,f.h,1.5f,f.color("FontColor",Color.WHITE))
        }
        canvas.restore()
        paint.style=Paint.Style.STROKE;paint.strokeWidth=max(1.0,border*2).toFloat();paint.color=f.color("TrimColor",Color.GRAY);canvas.drawPath(path,paint);paint.style=Paint.Style.FILL
        val ink=f.color("FontColor",Color.WHITE)
        val step=f.n("MajorTicks",-1.0).takeIf { it>0 } ?: tickStep(abs(f.maximum-f.minimum))
        val count=ceil(abs(f.maximum-f.minimum)/step).toInt().coerceIn(1,100)
        for(i in 1 until count) {
            val value=f.minimum+i*step
            val target=DashGeometry.fraction(value,f.minimum,f.maximum,true)*w
            var lo=0.0;var hi=1.0
            repeat(24) { val mid=(lo+hi)/2;if(cubic(mid,x0,x1,x2,x3)<target)lo=mid else hi=mid }
            val t=(lo+hi)/2;val px=cubic(t,x0,x1,x2,x3).toFloat();val py=cubic(t,y0,y1,y2,y3).toFloat()
            text(canvas,f,format(if(step>=1000)value/1000 else value,0),px,py+thickness.toFloat()*.55f,thickness.toFloat()*.38f,f.w*.08f,ink)
        }
    }
    private fun hit(x:Float,y:Float):Int {
        val dx=(x-panX)/zoom;val dy=(y-panY)/zoom
        return states.indices.reversed().firstOrNull { DashGeometry.bounds(states[it].c,viewport).contains(dx.toDouble(),dy.toDouble()) } ?: -1
    }
    private fun activateAt(x:Float,y:Float,long:Boolean):Boolean {
        val index=hit(x,y);if(index<0)return false
        return activate(index,long)
    }
    private fun activate(index:Int,long:Boolean):Boolean {
        val state=states[index]
        val command=state.c.text(if(long)"LongClickAction" else "ShortClickAction")
        if(command.isNotBlank()) { action(command);return true }
        if(long) { inspect(description(index));return true }
        return false
    }
    private fun description(index:Int):String {
        val state=states[index];val reading=telemetry.read(state.c)
        return "${state.c.text("Title").ifBlank { state.c.text("OutputChannel").ifBlank { state.c.text("OffText") } }}: ${reading.value?.let { format(it,2) } ?: "indisponível"} ${reading.units}"
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN)parent?.requestDisallowInterceptTouchEvent(zoom>1)
        if(event.actionMasked==MotionEvent.ACTION_POINTER_DOWN)parent?.requestDisallowInterceptTouchEvent(true)
        scaleGesture.onTouchEvent(event);gestures.onTouchEvent(event)
        if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL) { parent?.requestDisallowInterceptTouchEvent(false); accessibility.invalidateRoot() }
        return true
    }
    override fun onFocusChanged(gainFocus:Boolean,direction:Int,previouslyFocusedRect:Rect?) {
        super.onFocusChanged(gainFocus,direction,previouslyFocusedRect)
        accessibility.onFocusChanged(gainFocus,direction,previouslyFocusedRect)
    }
    override fun performClick():Boolean { super.performClick();return true }
    override fun dispatchHoverEvent(event:MotionEvent)=accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    override fun dispatchKeyEvent(event:KeyEvent)=accessibility.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    private inner class DashAccessibility:ExploreByTouchHelper(this@DashView) {
        override fun getVirtualViewAt(x:Float,y:Float):Int=hit(x,y).let { if(it<0)INVALID_ID else it }
        override fun getVisibleVirtualViews(ids:MutableList<Int>) {
            states.indices.filter { val b=DashGeometry.bounds(states[it].c,viewport);b.x+b.width>viewport.x && b.y+b.height>viewport.y && b.x<viewport.x+viewport.width && b.y<viewport.y+viewport.height }.forEach { ids.add(it) }
        }
        override fun onPopulateNodeForVirtualView(id:Int,node:AccessibilityNodeInfoCompat) {
            node.contentDescription=description(id)
            val b=DashGeometry.bounds(states[id].c,viewport)
            node.setBoundsInParent(Rect((b.x*zoom+panX).toInt(),(b.y*zoom+panY).toInt(),((b.x+b.width)*zoom+panX).toInt(),((b.y+b.height)*zoom+panY).toInt()))
            node.className=if(states[id].c.text("ShortClickAction").isNotBlank())"android.widget.Button" else "android.widget.TextView"
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
            node.isClickable=true;node.isLongClickable=true
        }
        override fun onPerformActionForVirtualView(id:Int,command:Int,args:Bundle?):Boolean=when(command) {
            AccessibilityNodeInfoCompat.ACTION_CLICK -> activate(id,false)
            AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> activate(id,true)
            else -> false
        }
    }
}
