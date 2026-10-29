package com.example.tunerstudioandroid.trigger

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import org.clonetuner.model.TriggerCapture
import java.util.Locale
import kotlin.math.*

/** Digital levels reported by the ECU, not analog sensor voltages. */
class TriggerScopeView(context: Context) : View(context) {
    var onInspect: (String) -> Unit = {}
    var capture: TriggerCapture? = null
        set(value) {
            if(field === value) return
            if(field?.mode()!=value?.mode()) fit()
            field=value; selected=-1; invalidate()
        }
    private val density=resources.displayMetrics.density
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val plot=RectF()
    private var zoom=1.0
    private var offset=0.0
    private var selected=-1
    private var downX=0f
    private var downY=0f
    private var previousX=0f
    private var dragging=false
    private val teal=Color.rgb(36,217,183)
    private val amber=Color.rgb(255,190,65)
    private val blue=Color.rgb(97,173,255)
    private val scaler=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector:ScaleGestureDetector):Boolean {
            changeZoom(detector.scaleFactor.toDouble(),((detector.focusX-plot.left)/plot.width()).toDouble().coerceIn(0.0,1.0));return true
        }
    })
    init { contentDescription="Osciloscópio de rotação e fase"; isFocusable=true; setBackgroundColor(Color.rgb(8,15,22)) }
    fun fit() { zoom=1.0; offset=0.0; selected=-1; invalidate() }
    fun zoomIn() { changeZoom(2.0,0.5) }
    fun zoomOut() { changeZoom(0.5,0.5) }
    private fun changeZoom(factor:Double,focus:Double) {
        val anchor=offset+focus/zoom
        zoom=(zoom*factor).coerceIn(1.0,32.0)
        offset=(anchor-focus/zoom).coerceIn(0.0,1.0-1.0/zoom)
        invalidate()
    }
    private fun number(value:Double)=String.format(Locale.getDefault(),"%.2f",value)
    private fun label(canvas:Canvas,text:String,x:Float,y:Float,color:Int=Color.LTGRAY,size:Float=11f,align:Paint.Align=Paint.Align.LEFT) {
        paint.textAlign=align
        paint.color=color; paint.textSize=size*density;paint.style=Paint.Style.FILL
        canvas.drawText(text,x,y,paint)
    }
    private fun axisMaximum(c:TriggerCapture):Double = if(c.mode()==TriggerCapture.Mode.TOOTH) max(1,c.samples().size).toDouble() else max(1L,c.durationUs()).toDouble()
    private fun axisValue(c:TriggerCapture,index:Int):Double = if(c.mode()==TriggerCapture.Mode.TOOTH) index+0.5 else c.samples()[index].timeUs().toDouble()
    private fun screenX(value:Double,maximum:Double) = plot.left+((value/maximum-offset)*zoom*plot.width()).toFloat()
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        plot.set(66*density,16*density,width-12*density,height-34*density)
        if(plot.width()<=0 || plot.height()<=0) return
        paint.strokeWidth=density;paint.style=Paint.Style.STROKE;paint.color=Color.rgb(38,55,68)
        for(i in 0..8) { val x=plot.left+plot.width()*i/8;canvas.drawLine(x,plot.top,x,plot.bottom,paint) }
        for(i in 0..6) { val y=plot.top+plot.height()*i/6;canvas.drawLine(plot.left,y,plot.right,y,paint) }
        val c=capture
        if(c==null || c.samples().isEmpty()) {
            label(canvas,"Aguardando pulsos da ECU",plot.left+8*density,plot.centerY(),teal)
            return
        }
        val maximum=axisMaximum(c)
        if(c.mode()==TriggerCapture.Mode.TOOTH) {
            val peak=c.samples().maxOf { it.intervalUs() }.coerceAtLeast(1).toDouble()*1.1
            label(canvas,"µs",4*density,plot.top+10*density,teal)
            for(i in 0..4) label(canvas,(peak*i/4).roundToLong().toString(),4*density,plot.bottom-plot.height()*i/4,teal,9f)
            canvas.save();canvas.clipRect(plot)
            val median=c.medianIntervalUs()
            c.samples().forEachIndexed { i,s ->
                val left=screenX(i.toDouble(),maximum);val right=screenX(i+1.0,maximum)
                paint.color=if(s.intervalUs()>=median*1.8) amber else teal
                paint.style=Paint.Style.FILL
                canvas.drawRect(left+1,plot.bottom-(plot.height()*s.intervalUs()/peak).toFloat(),right-1,plot.bottom,paint)
            }
            canvas.restore()
        } else {
            val names=listOf("Rotação","Fase","Sinc.")
            val colors=listOf(teal,amber,blue)
            for(lane in 0..2) {
                val top=plot.top+plot.height()*lane/3
                val high=top+plot.height()/3*0.22f
                val low=top+plot.height()/3*0.78f
                label(canvas,names[lane],3*density,(high+low)/2,colors[lane],10f)
                label(canvas,"1",plot.left-10*density,high,colors[lane],8f)
                label(canvas,"0",plot.left-10*density,low,colors[lane],8f)
                fun y(index:Int):Float {
                    val sample=c.samples()[index]
                    return if(when(lane){0->sample.crank();1->sample.cam();else->sample.sync()}) high else low
                }
                val path=Path(); path.moveTo(screenX(axisValue(c,0),maximum),y(0))
                for(i in 1 until c.samples().size) {
                    val x=screenX(axisValue(c,i),maximum)
                    path.lineTo(x,y(i-1));path.lineTo(x,y(i))
                }
                paint.color=colors[lane];paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density
                canvas.save();canvas.clipRect(plot);canvas.drawPath(path,paint);canvas.restore()
            }
        }
        for(i in 0..4) {
            val value=(offset+i/4.0/zoom)*maximum
            val text=if(c.mode()==TriggerCapture.Mode.TOOTH) value.roundToInt().toString() else number(value/1000)
            label(canvas,text,plot.left+plot.width()*i/4,plot.bottom+16*density,Color.LTGRAY,9f,
                if(i==0) Paint.Align.LEFT else if(i==4) Paint.Align.RIGHT else Paint.Align.CENTER)
        }
        label(canvas,if(c.mode()==TriggerCapture.Mode.TOOTH) "Dentes (ordem)" else "Tempo (ms)",plot.left, height-3*density,Color.GRAY,9f)
        if(c.simulated()) label(canvas,"DEMONSTRAÇÃO",plot.left+plot.width()*0.45f,plot.top+12*density,amber,10f)
        if(selected in c.samples().indices) {
            val x=screenX(axisValue(c,selected),maximum)
            paint.color=Color.WHITE;paint.strokeWidth=density
            if(x in plot.left..plot.right) canvas.drawLine(x,plot.top,x,plot.bottom,paint)
        }
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        scaler.onTouchEvent(event)
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX=event.x;downY=event.y;previousX=event.x;dragging=false;parent?.requestDisallowInterceptTouchEvent(true);return true }
            MotionEvent.ACTION_POINTER_DOWN -> dragging=true
            MotionEvent.ACTION_MOVE -> {
                if(hypot(event.x-downX,event.y-downY)>8*density) dragging=true
                if(!scaler.isInProgress && dragging && plot.width()>0) {
                    offset=(offset-(event.x-previousX)/plot.width()/zoom).coerceIn(0.0,1.0-1.0/zoom);invalidate()
                }
                previousX=event.x;return true
            }
            MotionEvent.ACTION_UP -> { if(!dragging) inspect(event.x);parent?.requestDisallowInterceptTouchEvent(false);performClick();return true }
            MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false);return true }
        }
        return true
    }
    private fun inspect(x:Float) {
        val c=capture ?: return
        if(c.samples().isEmpty() || plot.width()<=0) return
        val value=(offset+((x-plot.left)/plot.width()).coerceIn(0f,1f)/zoom)*axisMaximum(c)
        selected=c.samples().indices.minBy { abs(axisValue(c,it)-value) }
        val s=c.samples()[selected]
        val levels=if(c.mode()==TriggerCapture.Mode.COMPOSITE) " · rotação ${if(s.crank())1 else 0} · fase ${if(s.cam())1 else 0} · sinc. ${if(s.sync())1 else 0}" else ""
        onInspect("Evento ${selected+1} · t=${number(s.timeUs()/1000.0)} ms · Δt=${s.intervalUs()} µs$levels")
        invalidate()
    }
    override fun performClick():Boolean { super.performClick();return true }
}
