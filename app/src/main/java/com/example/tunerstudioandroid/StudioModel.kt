package com.example.tunerstudioandroid

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class StudioModel(application: Application) : AndroidViewModel(application) {
    var session = StudioSession(application)
    override fun onCleared() { session.close() }
}
