package com.pesaflow.app

import android.app.Application
import android.content.Context
import com.pesaflow.app.data.time.KenyaTime

class PesaFlowApplication : Application() {
    override fun attachBaseContext(base: Context) {
        KenyaTime.installAsDefault()
        super.attachBaseContext(base)
    }
}
