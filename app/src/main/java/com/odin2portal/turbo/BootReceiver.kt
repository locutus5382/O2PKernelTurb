package com.odin2portal.turbo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            if (PServerExec.isBootEnabled(context)) {
                // Wait 5 seconds after boot so AYN's PServerBinder service is ready
                Handler(Looper.getMainLooper()).postDelayed({
                    PServerExec.applyTurbo()
                    Toast.makeText(
                        context,
                        "O2P Kernel Turbo: Auto-applied on boot ⚡",
                        Toast.LENGTH_SHORT
                    ).show()
                }, 5000)
            }
        }
    }
}
