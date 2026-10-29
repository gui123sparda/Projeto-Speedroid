package com.example.tunerstudioandroid.dashboard.vehicle

import android.content.Context
import android.graphics.*
import com.example.tunerstudioandroid.dashboard.vehicle.VectorPainter.Kind

/** Replays the original vector paths directly onto Android Canvas; no cluster bitmap is used. */
class VehicleSceneRenderer(context:Context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds=RectF()
    private val path=Path()
    private val matrix=Matrix()
    private val matrixValues=FloatArray(9)
    private val regular=Typeface.createFromAsset(context.assets,"fonts/DejaVuSans.ttf")
    private val monoBold=Typeface.createFromAsset(context.assets,"fonts/DejaVuSansMono-Bold.ttf")
    fun draw(canvas:Canvas,commands:List<VectorPainter.Command>) {
        for(command in commands) {
            val style=command.style
            val transform=style.transform
            matrixValues[0]=transform.a.toFloat();matrixValues[1]=transform.c.toFloat();matrixValues[2]=transform.tx.toFloat()
            matrixValues[3]=transform.b.toFloat();matrixValues[4]=transform.d.toFloat();matrixValues[5]=transform.ty.toFloat()
            matrixValues[6]=0f;matrixValues[7]=0f;matrixValues[8]=1f;matrix.setValues(matrixValues)
            canvas.save();canvas.concat(matrix)
            val stroke=when(command.kind) { Kind.STROKE_RECT,Kind.STROKE_ROUND_RECT,Kind.STROKE_OVAL,Kind.LINE,Kind.ARC,Kind.STROKE_POLYGON,Kind.STROKE_PATH->true;else->false }
            applyStyle(style,stroke)
            val p=command.points
            when(command.kind) {
                Kind.FILL_RECT,Kind.STROKE_RECT -> { rect(p);canvas.drawRect(bounds,paint) }
                Kind.FILL_ROUND_RECT,Kind.STROKE_ROUND_RECT -> { rect(p);canvas.drawRoundRect(bounds,(p[4]/2).toFloat(),(p[5]/2).toFloat(),paint) }
                Kind.FILL_OVAL,Kind.STROKE_OVAL -> { rect(p);canvas.drawOval(bounds,paint) }
                Kind.LINE -> canvas.drawLine(p[0].toFloat(),p[1].toFloat(),p[2].toFloat(),p[3].toFloat(),paint)
                Kind.ARC -> { rect(p);canvas.drawArc(bounds,(-p[4]).toFloat(),(-p[5]).toFloat(),false,paint) }
                Kind.FILL_POLYGON,Kind.STROKE_POLYGON -> {
                    path.rewind();path.moveTo(p[0].toFloat(),p[1].toFloat())
                    for(i in 2 until p.size step 2)path.lineTo(p[i].toFloat(),p[i+1].toFloat())
                    path.close();canvas.drawPath(path,paint)
                }
                Kind.FILL_PATH,Kind.STROKE_PATH -> {
                    path.rewind()
                    for(segment in command.path) {
                        val point=segment.points
                        when(segment.operation) {
                            'M'->path.moveTo(point[0].toFloat(),point[1].toFloat())
                            'L'->path.lineTo(point[0].toFloat(),point[1].toFloat())
                            'C'->path.cubicTo(point[0].toFloat(),point[1].toFloat(),point[2].toFloat(),point[3].toFloat(),point[4].toFloat(),point[5].toFloat())
                            'Q'->path.quadTo(point[0].toFloat(),point[1].toFloat(),point[2].toFloat(),point[3].toFloat())
                            'Z'->path.close()
                        }
                    };canvas.drawPath(path,paint)
                }
                Kind.TEXT -> {
                    paint.typeface=if(style.font.family.contains("Mono"))monoBold else regular
                    paint.textSize=style.font.size.toFloat()
                    paint.textAlign=when(style.alignment) { VectorPainter.TextAlignment.CENTER->Paint.Align.CENTER;VectorPainter.TextAlignment.RIGHT->Paint.Align.RIGHT;else->Paint.Align.LEFT }
                    val width=paint.measureText(command.text)
                    if(width>command.maxWidth)paint.textSize=(paint.textSize*command.maxWidth/width).toFloat()
                    val y=if(style.baseline==VectorPainter.VPos.CENTER)p[1]-(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2 else p[1]
                    canvas.drawText(command.text,p[0].toFloat(),y.toFloat(),paint)
                }
            }
            canvas.restore()
        }
        paint.shader=null
    }
    private fun rect(p:DoubleArray) { bounds.set(p[0].toFloat(),p[1].toFloat(),(p[0]+p[2]).toFloat(),(p[1]+p[3]).toFloat()) }
    private fun applyStyle(style:VectorPainter.Style,stroke:Boolean) {
        paint.shader=null;paint.style=if(stroke)Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth=style.width.toFloat();paint.strokeJoin=Paint.Join.ROUND
        paint.strokeCap=when(style.cap) { VectorPainter.StrokeLineCap.BUTT->Paint.Cap.BUTT;VectorPainter.StrokeLineCap.ROUND->Paint.Cap.ROUND;VectorPainter.StrokeLineCap.SQUARE->Paint.Cap.SQUARE }
        when(val color=if(stroke)style.stroke else style.fill) {
            is VectorPainter.Color -> { paint.color=color.argb;paint.alpha=(Color.alpha(color.argb)*style.alpha).toInt().coerceIn(0,255) }
            is VectorPainter.LinearGradient -> {
                paint.color=Color.WHITE;paint.alpha=(255*style.alpha).toInt().coerceIn(0,255)
                paint.shader=LinearGradient(color.x0.toFloat(),color.y0.toFloat(),color.x1.toFloat(),color.y1.toFloat(),color.stops.map { it.color.argb }.toIntArray(),color.stops.map { it.offset.toFloat() }.toFloatArray(),Shader.TileMode.CLAMP)
            }
        }
    }
}
