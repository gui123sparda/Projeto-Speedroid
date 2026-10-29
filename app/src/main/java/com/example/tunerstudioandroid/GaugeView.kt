package com.example.tunerstudioandroid

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*
import java.util.Locale

class GaugeView(context: Context) : View(context) {
    var value: Double? = null
        set(v) { field = v; invalidate() }
    var accent = Color.rgb(36,197,176)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val light = Typeface.create("sans-serif-light",Typeface.NORMAL)
    init { contentDescription = "Rotação do motor"; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height * 0.53f
        val radius = min(width * 0.40f, height * 0.44f)
        oval.set(cx-radius,cy-radius,cx+radius,cy+radius)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = radius*.075f; paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(41,55,67); canvas.drawArc(oval,135f,270f,false,paint)
        paint.color = accent
        canvas.drawArc(oval,135f,((value ?: 0.0)/8000*270).coerceIn(0.0,270.0).toFloat(),false,paint)
        paint.strokeWidth = radius*.035f; paint.color = Color.rgb(249,110,105)
        canvas.drawArc(oval,135+270*.8f,270*.2f,false,paint)
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
        for (tick in 0..8) {
            val angle = Math.toRadians(135.0+tick*33.75)
            val x = cx + cos(angle).toFloat()*radius*.80f
            val y = cy + sin(angle).toFloat()*radius*.80f
            paint.textSize = radius*.115f; paint.color = Color.rgb(152,168,183)
            canvas.drawText(tick.toString(),x,y+paint.textSize/3,paint)
        }
        paint.color = Color.WHITE; paint.typeface = light
        paint.textSize = radius*.45f
        canvas.drawText(value?.let { String.format(Locale.US,"%.0f",it) } ?: "—",cx,cy+radius*.10f,paint)
        paint.textSize = radius*.12f; paint.color = Color.rgb(152,168,183)
        canvas.drawText("RPM × 1000",cx,cy+radius*.34f,paint)
        paint.typeface = Typeface.DEFAULT
    }
}
