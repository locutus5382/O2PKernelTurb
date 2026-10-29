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
            for policy in /sys/devices/system/cpu/cpufreq/policy*; do
                if [ -d "${'$'}policy/walt" ]; then
                    echo 500 > "${'$'}policy/walt/up_rate_limit_us"
                    echo 20000 > "${'$'}policy/walt/down_rate_limit_us"
                elif [ -d "${'$'}policy/schedutil" ]; then
                    echo 500 > "${'$'}policy/schedutil/up_rate_limit_us"
                    echo 20000 > "${'$'}policy/schedutil/down_rate_limit_us"
                fi
            done
            if [ -f /proc/sys/walt/sched_upmigrate ]; then
                echo "80 85" > /proc/sys/walt/sched_upmigrate
                echo "65 70" > /proc/sys/walt/sched_downmigrate
            fi
            GPU_PATH="/sys/class/kgsl/kgsl-3d0"
            if [ -d "${'$'}GPU_PATH" ]; then
                echo 0 > "${'$'}GPU_PATH/force_bus_on" 2>/dev/null
                echo 0 > "${'$'}GPU_PATH/force_rail_on" 2>/dev/null
                echo 80 > "${'$'}GPU_PATH/idle_timer" 2>/dev/null
                echo 1 > "${'$'}GPU_PATH/bus_split" 2>/dev/null
            fi
            for queue in /sys/block/sd*/queue /sys/block/mmcblk*/queue; do
                if [ -d "${'$'}queue" ]; then
                    echo 2048 > "${'$'}queue/read_ahead_kb" 2>/dev/null
                    echo 0 > "${'$'}queue/iostats" 2>/dev/null
                    echo 0 > "${'$'}queue/add_random" 2>/dev/null
                    echo 2 > "${'$'}queue/rq_affinity" 2>/dev/null
                fi
            done
            echo 10 > /proc/sys/vm/swappiness 2>/dev/null
            echo 1 > /proc/sys/vm/compaction_proactiveness 2>/dev/null
        """.trimIndent()
        return runAsRoot(script)
    }

    fun revertToStock(): String {
        val script = """
            for policy in /sys/devices/system/cpu/cpufreq/policy*; do
                if [ -d "${'$'}policy/walt" ]; then
                    echo 10000 > "${'$'}policy/walt/up_rate_limit_us"
                    echo 10000 > "${'$'}policy/walt/down_rate_limit_us"
                elif [ -d "${'$'}policy/schedutil" ]; then
                    echo 10000 > "${'$'}policy/schedutil/up_rate_limit_us"
                    echo 10000 > "${'$'}policy/schedutil/down_rate_limit_us"
                fi
            done
            if [ -f /proc/sys/walt/sched_upmigrate ]; then
                echo "95 95" > /proc/sys/walt/sched_upmigrate
                echo "85 85" > /proc/sys/walt/sched_downmigrate
            fi
            GPU_PATH="/sys/class/kgsl/kgsl-3d0"
            if [ -d "${'$'}GPU_PATH" ]; then
                echo 64 > "${'$'}GPU_PATH/idle_timer" 2>/dev/null
            fi
            for queue in /sys/block/sd*/queue /sys/block/mmcblk*/queue; do
                if [ -d "${'$'}queue" ]; then
                    echo 512 > "${'$'}queue/read_ahead_kb" 2>/dev/null
                    echo 1 > "${'$'}queue/iostats" 2>/dev/null
                    echo 1 > "${'$'}queue/rq_affinity" 2>/dev/null
                fi
            done
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
        val waltRate = readNode("/sys/devices/system/cpu/cpufreq/policy7/walt/up_rate_limit_us")
        val schedRate = readNode("/sys/devices/system/cpu/cpufreq/policy7/schedutil/up_rate_limit_us")
        val ufsReadAhead = readNode("/sys/block/sda/queue/read_ahead_kb")
        return waltRate == "500" || schedRate == "500" || ufsReadAhead == "2048"
    }

    fun getLiveStatusReport(context: Context): String {
        val p7Walt = readNode("/sys/devices/system/cpu/cpufreq/policy7/walt/up_rate_limit_us")
        val p7Sched = readNode("/sys/devices/system/cpu/cpufreq/policy7/schedutil/up_rate_limit_us")
        val cpuRamp = if (p7Walt.all { it.isDigit() } && p7Walt.isNotEmpty()) "$p7Walt µs"
                      else if (p7Sched.all { it.isDigit() } && p7Sched.isNotEmpty()) "$p7Sched µs"
                      else "Unknown"
        val gpuIdle = readNode("/sys/class/kgsl/kgsl-3d0/idle_timer")
        val ufsBuf = readNode("/sys/block/sda/queue/read_ahead_kb")
        val swap = readNode("/proc/sys/vm/swappiness")
        val bootState = if (isBootEnabled(context)) "ENABLED (Auto-starts on boot)" else "DISABLED (Resets on reboot)"

        return buildString {
            appendLine("• PServer Bridge:   ${if (isPServerAvailable()) "CONNECTED (uid=0)" else "Fallback / Unavailable"}")
            appendLine("• Mode Status:      ${if (isTurboActive()) "TURBO ACTIVE ⚡" else "FACTORY STOCK"}")
            appendLine("• Boot Auto-Start:  $bootState")
            appendLine("• CPU Ramp Delay:   $cpuRamp  (Stock: 10000 µs / Turbo: 500 µs)")
            appendLine("• Adreno 740 Nap:   ${gpuIdle} ms  (Stock: 64 ms / Turbo: 80 ms)")
            appendLine("• UFS 4.0 Buffer:   ${ufsBuf} KB  (Stock: 512 KB / Turbo: 2048 KB)")
            append("• VM Swappiness:    $swap  (Stock: 60 / Turbo: 10)")
        }
    }
}
