package com.odin2portal.turbo

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var badgeText: TextView
    private lateinit var bootSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.textStatusReport)
        badgeText = findViewById(R.id.badgeStatus)
        bootSwitch = findViewById(R.id.switchBoot)
        val btnEnable = findViewById<Button>(R.id.btnEnable)
        val btnRevert = findViewById<Button>(R.id.btnRevert)

        bootSwitch.isChecked = PServerExec.isBootEnabled(this)
        bootSwitch.setOnCheckedChangeListener { _, isChecked ->
            PServerExec.setBootEnabled(this, isChecked)
            refreshStatus()
            val msg = if (isChecked) "Auto-Start on Boot: ON" else "Auto-Start on Boot: OFF"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        btnEnable.setOnClickListener {
            PServerExec.applyTurbo()
            refreshStatus()
            Toast.makeText(this, "O2P Turbo Pacing Enabled ⚡", Toast.LENGTH_SHORT).show()
        }

        btnRevert.setOnClickListener {
            PServerExec.revertToStock()
            refreshStatus()
            Toast.makeText(this, "Reverted to Factory Stock", Toast.LENGTH_SHORT).show()
        }

        refreshStatus()
        btnEnable.requestFocus()
    }

    private fun refreshStatus() {
        val active = PServerExec.isTurboActive()
        if (active) {
            badgeText.text = "TURBO ACTIVE ⚡"
            badgeText.setBackgroundResource(R.drawable.btn_primary)
            badgeText.setTextColor(Color.WHITE)
        } else {
            badgeText.text = "FACTORY STOCK"
            badgeText.setBackgroundResource(R.drawable.badge_stock)
            badgeText.setTextColor(Color.parseColor("#94A3B8"))
        }
        statusText.text = PServerExec.getLiveStatusReport(this)
    }
}
