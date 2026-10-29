package com.example.tunerstudioandroid.dashboard

/** Session ranges are retained independently from Android View lifecycles. */
class DashHistory {
    data class Range(var minimum:Double,var maximum:Double,var peak:Double,var peakAt:Long)
    private val values=hashMapOf<String,Range>()
    fun accept(id:String,value:Double,timeMs:Long,delayMs:Long) {
        if(!value.isFinite())return
        val range=values[id]
        if(range==null)values[id]=Range(value,value,value,timeMs)
        else {
            range.minimum=minOf(range.minimum,value);range.maximum=maxOf(range.maximum,value)
            if(value>=range.peak || timeMs-range.peakAt>delayMs) { range.peak=value;range.peakAt=timeMs }
        }
    }
    fun range(id:String)=values[id]
    fun clear()=values.clear()
}
