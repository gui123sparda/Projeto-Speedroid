package com.example.tunerstudioandroid.dashboard

import android.view.View

/** Common host contract for file-defined dashboards and the user's custom vector clusters. */
interface PanelSurface {
    val view:View
    val diagnostics:Set<String>
    fun refresh()
    fun resetHistory()
    fun extrema():List<String>
}
