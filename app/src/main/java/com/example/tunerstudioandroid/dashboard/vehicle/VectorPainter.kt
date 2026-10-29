package com.example.tunerstudioandroid.dashboard.vehicle

import java.util.ArrayDeque
import kotlin.math.*

data class VehiclePoint(val x:Double,val y:Double)

/** Platform-independent display list preserving the original JavaFX path coordinates and transforms. */
class VectorPainter {
    sealed interface PanelPaint
    data class Color(val argb:Int):PanelPaint {
        companion object {
            @JvmField val BLACK=Color(0xff000000.toInt())
            @JvmField val TRANSPARENT=Color(0)
            @JvmStatic fun web(value:String)=Color((0xff000000L or value.removePrefix("#").toLong(16)).toInt())
            @JvmStatic fun color(r:Double,g:Double,b:Double,a:Double)=Color(((a.coerceIn(0.0,1.0)*255).roundToInt() shl 24) or ((r*255).roundToInt() shl 16) or ((g*255).roundToInt() shl 8) or (b*255).roundToInt())
        }
    }
    data class Stop(val offset:Double,val color:Color)
    enum class CycleMethod { NO_CYCLE }
    class LinearGradient(val x0:Double,val y0:Double,val x1:Double,val y1:Double,val proportional:Boolean,val cycle:CycleMethod,vararg val stops:Stop):PanelPaint
    enum class FontWeight { NORMAL,BOLD }
    enum class VPos { BASELINE,CENTER }
    enum class TextAlignment { LEFT,CENTER,RIGHT }
    enum class StrokeLineCap { BUTT,ROUND,SQUARE }
    enum class ArcType { OPEN }
    data class Font(val family:String,val weight:FontWeight,val size:Double) {
        companion object { @JvmStatic fun font(family:String,weight:FontWeight,size:Double)=Font(family,weight,size) }
    }
    data class Transform(val a:Double=1.0,val b:Double=0.0,val c:Double=0.0,val d:Double=1.0,val tx:Double=0.0,val ty:Double=0.0) {
        fun point(x:Double,y:Double)=VehiclePoint(a*x+c*y+tx,b*x+d*y+ty)
        fun translate(x:Double,y:Double)=copy(tx=a*x+c*y+tx,ty=b*x+d*y+ty)
        fun scale(x:Double,y:Double)=copy(a=a*x,b=b*x,c=c*y,d=d*y)
        fun rotate(degrees:Double):Transform {
            val radians=Math.toRadians(degrees);val cos=cos(radians);val sin=sin(radians)
            return copy(a=a*cos+c*sin,b=b*cos+d*sin,c=c*cos-a*sin,d=d*cos-b*sin)
        }
    }
    data class Style(val fill:PanelPaint=Color.BLACK,val stroke:PanelPaint=Color.BLACK,val width:Double=1.0,val alpha:Double=1.0,val font:Font=Font("DejaVu Sans",FontWeight.NORMAL,12.0),val baseline:VPos=VPos.BASELINE,val alignment:TextAlignment=TextAlignment.LEFT,val cap:StrokeLineCap=StrokeLineCap.BUTT,val transform:Transform=Transform())
    enum class Kind { FILL_RECT,STROKE_RECT,FILL_ROUND_RECT,STROKE_ROUND_RECT,FILL_OVAL,STROKE_OVAL,LINE,ARC,FILL_POLYGON,STROKE_POLYGON,TEXT,FILL_PATH,STROKE_PATH }
    data class Segment(val operation:Char,val points:DoubleArray)
    data class Command(val kind:Kind,val points:DoubleArray,val style:Style,val text:String="",val path:List<Segment> = emptyList(),val maxWidth:Double=Double.POSITIVE_INFINITY)
    private var style=Style()
    private val stack=ArrayDeque<Style>()
    private val operations=ArrayList<Command>(700)
    private var path=ArrayList<Segment>()
    fun getCommands():List<Command> = operations.toList()
    fun clear() { operations.clear();stack.clear();path.clear();style=Style() }
    fun save() { stack.push(style) }
    fun restore() { style=stack.pop() }
    fun translate(x:Double,y:Double) { style=style.copy(transform=style.transform.translate(x,y)) }
    fun scale(x:Double,y:Double) { style=style.copy(transform=style.transform.scale(x,y)) }
    fun rotate(degrees:Double) { style=style.copy(transform=style.transform.rotate(degrees)) }
    fun setFill(value:PanelPaint) { style=style.copy(fill=value) }
    fun setStroke(value:PanelPaint) { style=style.copy(stroke=value) }
    fun setLineWidth(value:Double) { style=style.copy(width=value) }
    fun setGlobalAlpha(value:Double) { style=style.copy(alpha=value) }
    fun setFont(value:Font) { style=style.copy(font=value) }
    fun setTextBaseline(value:VPos) { style=style.copy(baseline=value) }
    fun setTextAlign(value:TextAlignment) { style=style.copy(alignment=value) }
    fun setLineCap(value:StrokeLineCap) { style=style.copy(cap=value) }
    private fun add(kind:Kind,vararg points:Double) { operations.add(Command(kind,points,style)) }
    fun fillRect(x:Double,y:Double,w:Double,h:Double)=add(Kind.FILL_RECT,x,y,w,h)
    fun strokeRect(x:Double,y:Double,w:Double,h:Double)=add(Kind.STROKE_RECT,x,y,w,h)
    fun fillRoundRect(x:Double,y:Double,w:Double,h:Double,aw:Double,ah:Double)=add(Kind.FILL_ROUND_RECT,x,y,w,h,aw,ah)
    fun strokeRoundRect(x:Double,y:Double,w:Double,h:Double,aw:Double,ah:Double)=add(Kind.STROKE_ROUND_RECT,x,y,w,h,aw,ah)
    fun fillOval(x:Double,y:Double,w:Double,h:Double)=add(Kind.FILL_OVAL,x,y,w,h)
    fun strokeOval(x:Double,y:Double,w:Double,h:Double)=add(Kind.STROKE_OVAL,x,y,w,h)
    fun strokeLine(x:Double,y:Double,x2:Double,y2:Double)=add(Kind.LINE,x,y,x2,y2)
    fun strokeArc(x:Double,y:Double,w:Double,h:Double,start:Double,length:Double,type:ArcType)=add(Kind.ARC,x,y,w,h,start,length)
    private fun polygon(kind:Kind,x:DoubleArray,y:DoubleArray,count:Int) { add(kind,*DoubleArray(count*2) { i -> if(i%2==0)x[i/2] else y[i/2] }) }
    fun fillPolygon(x:DoubleArray,y:DoubleArray,count:Int)=polygon(Kind.FILL_POLYGON,x,y,count)
    fun strokePolygon(x:DoubleArray,y:DoubleArray,count:Int)=polygon(Kind.STROKE_POLYGON,x,y,count)
    @JvmOverloads fun fillText(text:String,x:Double,y:Double,maxWidth:Double=Double.POSITIVE_INFINITY) { operations.add(Command(Kind.TEXT,doubleArrayOf(x,y),style,text,maxWidth=maxWidth)) }
    fun beginPath() { path=ArrayList() }
    fun moveTo(x:Double,y:Double) { path.add(Segment('M',doubleArrayOf(x,y))) }
    fun lineTo(x:Double,y:Double) { path.add(Segment('L',doubleArrayOf(x,y))) }
    fun bezierCurveTo(a:Double,b:Double,c:Double,d:Double,x:Double,y:Double) { path.add(Segment('C',doubleArrayOf(a,b,c,d,x,y))) }
    fun quadraticCurveTo(a:Double,b:Double,x:Double,y:Double) { path.add(Segment('Q',doubleArrayOf(a,b,x,y))) }
    fun closePath() { path.add(Segment('Z',doubleArrayOf())) }
    fun fill() { operations.add(Command(Kind.FILL_PATH,doubleArrayOf(),style,path=path.toList())) }
    fun stroke() { operations.add(Command(Kind.STROKE_PATH,doubleArrayOf(),style,path=path.toList())) }
}
