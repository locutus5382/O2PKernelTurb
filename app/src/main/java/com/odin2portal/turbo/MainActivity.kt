package com.odin2portal.turbo

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var badgeText: TextView
    private lateinit var bootSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F141C"))
            setPadding(56, 44, 56, 44)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "Odin 2 Portal — Kernel Turbo"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }

        badgeText = TextView(this).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(32, 12, 32, 12)
        }

        statusText = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(36, 28, 36, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 20f
            }
        }

        bootSwitch = Switch(this).apply {
            text = "Auto-Apply Turbo on Boot   "
            textSize = 15f
            setTextColor(Color.parseColor("#E2E8F0"))
            isChecked = PServerExec.isBootEnabled(this@MainActivity)
            setPadding(24, 24, 24, 12)
            setOnCheckedChangeListener { _, isChecked ->
                PServerExec.setBootEnabled(this@MainActivity, isChecked)
                refreshStatus()
                val msg = if (isChecked) "Auto-Start on Boot: ON" else "Auto-Start on Boot: OFF (Safe Stock Reboot)"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }

        val btnOn = Button(this).apply {
            text = "⚡ ENABLE TURBO"
            textSize = 15f
            setTextColor(Color.WHITE)
            isFocusable = true
            setPadding(44, 24, 44, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
                cornerRadius = 18f
            }
            setOnClickListener {
                PServerExec.applyTurbo()
                refreshStatus()
                Toast.makeText(context, "O2P Turbo Pacing Enabled!", Toast.LENGTH_SHORT).show()
            }
        }

        val btnOff = Button(this).apply {
            text = "↩ REVERT TO STOCK"
            textSize = 15f
            setTextColor(Color.WHITE)
            isFocusable = true
            setPadding(44, 24, 44, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EF4444"))
                cornerRadius = 18f
            }
            setOnClickListener {
                PServerExec.revertToStock()
                refreshStatus()
                Toast.makeText(context, "Reverted to Factory Stock!", Toast.LENGTH_SHORT).show()
            }
        }

        val spacer = TextView(this).apply { width = 32 }
        btnRow.addView(btnOn)
        btnRow.addView(spacer)
        btnRow.addView(btnOff)

        root.addView(title)
        root.addView(badgeText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 14; bottomMargin = 20 })
        root.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(bootSwitch, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12 })
        root.addView(btnRow)

        setContentView(root)
        refreshStatus()
        btnOn.requestFocus()
    }

    private fun refreshStatus() {
        val active = PServerExec.isTurboActive()
        badgeText.text = if (active) "STATUS: TURBO ACTIVE ⚡" else "STATUS: FACTORY STOCK"
        badgeText.background = GradientDrawable().apply {
            setColor(Color.parseColor(if (active) "#065F46" else "#334155"))
            cornerRadius = 50f
        }
        badgeText.setTextColor(Color.parseColor(if (active) "#6EE7B7" else "#94A3B8"))
        statusText.text = PServerExec.getLiveStatusReport(this)
    }
}
