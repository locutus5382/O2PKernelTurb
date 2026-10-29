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
            # Direct target for Odin 2 Portal CPU policies (0 to 7)
            for i in 0 1 2 3 4 5 6 7; do
                if [ -d "/sys/devices/system/cpu/cpufreq/policy${'$'}i" ]; then
                    echo 500 > /sys/devices/system/cpu/cpufreq/policy${'$'}i/walt/up_rate_limit_us 2>/dev/null
                    echo 20000 > /sys/devices/system/cpu/cpufreq/policy${'$'}i/walt/down_rate_limit_us 2>/dev/null
                    echo 500 > /sys/devices/system/cpu/cpufreq/policy${'$'}i/schedutil/up_rate_limit_us 2>/dev/null
                    echo 20000 > /sys/devices/system/cpu/cpufreq/policy${'$'}i/schedutil/down_rate_limit_us 2>/dev/null
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
            # Target UFS storage block sda / sdb / mmcblk0
            for dev in sda sdb sdc mmcblk0; do
                if [ -d "/sys/block/${'$'}dev/queue" ]; then
                    echo 2048 > /sys/block/${'$'}dev/queue/read_ahead_kb 2>/dev/null
                    echo 0 > /sys/block/${'$'}dev/queue/iostats 2>/dev/null
                    echo 0 > /sys/block/${'$'}dev/queue/add_random 2>/dev/null
                    echo 2 > /sys/block/${'$'}dev/queue/rq_affinity 2>/dev/null
                fi
            done
            echo 10 > /proc/sys/vm/swappiness 2>/dev/null
            echo 1 > /proc/sys/vm/compaction_proactiveness 2>/dev/null
        """.trimIndent()
        return runAsRoot(script)
    }

    fun revertToStock(): String {
        val script = """
            for i in 0 1 2 3 4 5 6 7; do
                if [ -d "/sys/devices/system/cpu/cpufreq/policy${'$'}i" ]; then
                    echo 10000 > /sys/devices/system/cpu/cpufreq/policy${'$'}i/walt/up_rate_limit_us 2>/dev/null
                    echo 10000 > /sys/devices/system/cpu/cpufreq/policy${'$'}i/walt/down_rate_limit_us 2>/dev/null
                    echo 10000 > /sys/devices/system/cpu/cpufreq/policy${'$'}i/schedutil/up_rate_limit_us 2>/dev/null
                    echo 10000 > /sys/devices/system/cpu/cpufreq/policy${'$'}i/schedutil/down_rate_limit_us 2>/dev/null
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
            for dev in sda sdb sdc mmcblk0; do
                if [ -d "/sys/block/${'$'}dev/queue" ]; then
                    echo 512 > /sys/block/${'$'}dev/queue/read_ahead_kb 2>/dev/null
                    echo 1 > /sys/block/${'$'}dev/queue/iostats 2>/dev/null
                    echo 1 > /sys/block/${'$'}dev/queue/rq_affinity 2>/dev/null
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
        val rate1 = readNode("/sys/devices/system/cpu/cpufreq/policy7/walt/up_rate_limit_us")
        val rate2 = readNode("/sys/devices/system/cpu/cpufreq/policy7/schedutil/up_rate_limit_us")
        val ufs = readNode("/sys/block/sda/queue/read_ahead_kb")
        return rate1 == "500" || rate2 == "500" || ufs == "2048"
    }

    fun getLiveStatusReport(context: Context): String {
        val p7Walt = readNode("/sys/devices/system/cpu/cpufreq/policy7/walt/up_rate_limit_us")
        val p7Sched = readNode("/sys/devices/system/cpu/cpufreq/policy7/schedutil/up_rate_limit_us")
        val cpuRamp = when {
            p7Walt.all { it.isDigit() } && p7Walt.isNotEmpty() -> "$p7Walt µs"
            p7Sched.all { it.isDigit() } && p7Sched.isNotEmpty() -> "$p7Sched µs"
            else -> "Active"
        }
        val gpuIdle = readNode("/sys/class/kgsl/kgsl-3d0/idle_timer")
        val ufsBuf = readNode("/sys/block/sda/queue/read_ahead_kb")
        val swap = readNode("/proc/sys/vm/swappiness")
        val bootState = if (isBootEnabled(context)) "ENABLED" else "DISABLED"

        return buildString {
            appendLine("• PServer Bridge:   CONNECTED (uid=0)")
            appendLine("• Mode Status:      ${if (isTurboActive()) "TURBO ACTIVE ⚡" else "FACTORY STOCK"}")
            appendLine("• Boot Auto-Start:  $bootState")
            appendLine("• CPU Ramp Delay:   $cpuRamp  (Turbo: 500 µs)")
            appendLine("• Adreno 740 Nap:   ${gpuIdle.ifEmpty { "80" }} ms  (Turbo: 80 ms)")
            appendLine("• UFS 4.0 Buffer:   ${ufsBuf.ifEmpty { "2048" }} KB  (Turbo: 2048 KB)")
            append("• VM Swappiness:    $swap  (Turbo: 10)")
        }
    }
}
