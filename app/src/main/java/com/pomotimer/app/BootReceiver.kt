package com.pomotimer.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reagenda o alarme da etapa em andamento depois que o aparelho reinicia ou o app é atualizado. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Alarms.restore(ctx)
        }
    }
}
