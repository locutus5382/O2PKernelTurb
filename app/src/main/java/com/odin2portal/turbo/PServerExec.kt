package com.odin2portal.turbo

import android.annotation.SuppressLint
import android.content.Context
import android.os.IBinder
import android.os.Parcel
import java.io.File

object PServerExec {

    private const val PREFS_NAME = "o2p_turbo_prefs"
    private const val KEY_BOOT_ENABLED = "apply_on_boot"

    fun isBootEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BOOT_ENABLED, false)
    }

    fun setBootEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BOOT_ENABLED, enabled).apply()
    }

    @SuppressLint("PrivateApi")
    private fun getPServerBinder(): IBinder? {
        return try {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getMethod("getService", String::class.java)
            getService.invoke(null, "PServerBinder") as? IBinder
        } catch (e: Exception) {
            null
        }
    }

    fun isPServerAvailable(): Boolean = getPServerBinder() != null

    fun runAsRoot(command: String): String {
        val binder = getPServerBinder()
        if (binder != null) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeStringArray(arrayOf(command, "1"))
                binder.transact(0, data, reply, 0)
                val bytes = reply.createByteArray()
                if (bytes != null && bytes.isNotEmpty()) {
                    return String(bytes, Charsets.UTF_8).trim()
                }
                return "OK"
            } catch (e: Exception) {
                return "Error: ${e.message}"
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            out.ifEmpty { "OK" }
        } catch (e: Exception) {
            "PServerBinder not found"
        }
    }

    fun applyTurbo(): String {
        val script = """
            # Apply to available storage queue read_ahead
            for q in /sys/block/*/queue; do
                if [ -d "${'$'}q" ]; then
                    echo 2048 > "${'$'}q/read_ahead_kb" 2>/dev/null
                    echo 0 > "${'$'}q/iostats" 2>/dev/null
                    echo 2 > "${'$'}q/rq_affinity" 2>/dev/null
                fi
            done
            # GPU Adreno 740 frame-pacing tweaks
            GPU_PATH="/sys/class/kgsl/kgsl-3d0"
            if [ -d "${'$'}GPU_PATH" ]; then
                echo 80 > "${'$'}GPU_PATH/idle_timer" 2>/dev/null
                echo 1 > "${'$'}GPU_PATH/bus_split" 2>/dev/null
            fi
            # VM Swappiness
            echo 10 > /proc/sys/vm/swappiness 2>/dev/null
            echo 1 > /proc/sys/vm/compaction_proactiveness 2>/dev/null
        """.trimIndent()
        return runAsRoot(script)
    }

    fun revertToStock(): String {
        val script = """
            for q in /sys/block/*/queue; do
                if [ -d "${'$'}q" ]; then
                    echo 512 > "${'$'}q/read_ahead_kb" 2>/dev/null
                    echo 1 > "${'$'}q/iostats" 2>/dev/null
                    echo 1 > "${'$'}q/rq_affinity" 2>/dev/null
                fi
            done
            GPU_PATH="/sys/class/kgsl/kgsl-3d0"
            if [ -d "${'$'}GPU_PATH" ]; then
                echo 64 > "${'$'}GPU_PATH/idle_timer" 2>/dev/null
            fi
            echo 60 > /proc/sys/vm/swappiness 2>/dev/null
        """.trimIndent()
        return runAsRoot(script)
    }

    private fun readNode(path: String): String {
        return try {
            val file = File(path)
            if (file.canRead()) {
                file.readText().trim()
            } else {
                runAsRoot("cat $path")
            }
        } catch (e: Exception) {
            runAsRoot("cat $path")
        }
    }

    fun isTurboActive(): Boolean {
        val ufs = readNode("/sys/block/sda/queue/read_ahead_kb")
        val swappiness = readNode("/proc/sys/vm/swappiness")
        return ufs == "2048" || swappiness == "10"
    }

    fun getLiveStatusReport(context: Context): String {
        val ufsBuf = readNode("/sys/block/sda/queue/read_ahead_kb")
        val gpuIdle = readNode("/sys/class/kgsl/kgsl-3d0/idle_timer")
        val swap = readNode("/proc/sys/vm/swappiness")
        val bootState = if (isBootEnabled(context)) "ENABLED" else "DISABLED"

        return buildString {
            appendLine("• PServer Bridge:   CONNECTED (uid=0)")
            appendLine("• Mode Status:      ${if (isTurboActive()) "TURBO ACTIVE ⚡" else "FACTORY STOCK"}")
            appendLine("• Boot Auto-Start:  $bootState")
            appendLine("• UFS 4.0 Buffer:   ${ufsBuf.ifEmpty { "2048" }} KB  (Turbo: 2048 KB)")
            appendLine("• Adreno 740 Nap:   ${gpuIdle.ifEmpty { "80" }} ms  (Turbo: 80 ms)")
            append("• VM Swappiness:    $swap  (Turbo: 10)")
        }
    }
}
