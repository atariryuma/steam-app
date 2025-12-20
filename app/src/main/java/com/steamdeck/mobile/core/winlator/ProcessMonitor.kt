package com.steamdeck.mobile.core.winlator

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors process metrics (CPU, memory) via /proc filesystem.
 *
 * This implementation reads from /proc/[pid]/stat and /proc/[pid]/status
 * to gather CPU and memory usage statistics.
 */
@Singleton
class ProcessMonitor @Inject constructor() {

 companion object {
  private const val TAG = "ProcessMonitor"
  private const val UPDATE_INTERVAL_MS = 1000L // 1 second
 }

 /**
  * Starts monitoring a process and emits metrics periodically.
  *
  * @param pid Process ID to monitor
  * @param intervalMs Update interval in milliseconds (default: 1000ms)
  * @return Flow of ProcessMetrics
  */
 fun startMonitoring(pid: Int, intervalMs: Long = UPDATE_INTERVAL_MS): Flow<ProcessMetrics> = flow {
  Log.i(TAG, "Starting process monitoring for PID $pid")

  val startTime = System.currentTimeMillis()
  var lastCpuTime = 0L
  var lastCheckTime = System.currentTimeMillis()
  var consecutiveErrors = 0
  val maxConsecutiveErrors = 5 // CRITICAL FIX: Prevent infinite loop on zombie processes

  while (true) {
   try {
    val metrics = withContext(Dispatchers.IO) {
     readProcessMetrics(pid, startTime, lastCpuTime, lastCheckTime)
    }

    if (metrics != null) {
     lastCpuTime = metrics.totalCpuTime
     lastCheckTime = System.currentTimeMillis()
     consecutiveErrors = 0 // Reset error counter on success
     emit(metrics)
    } else {
     // Process no longer exists
     Log.d(TAG, "Process $pid no longer exists, stopping monitoring")
     break
    }
   } catch (e: Exception) {
    consecutiveErrors++
    Log.e(TAG, "Error reading process metrics for PID $pid (attempt $consecutiveErrors/$maxConsecutiveErrors)", e)

    // CRITICAL FIX: Break infinite loop if process is unreadable (zombie/permission denied)
    if (consecutiveErrors >= maxConsecutiveErrors) {
     Log.w(TAG, "Too many consecutive errors for PID $pid, assuming process is dead or zombie")
     break
    }
   }

   delay(intervalMs)
  }

  Log.i(TAG, "Stopped process monitoring for PID $pid")
 }

 /**
  * Reads process metrics from /proc filesystem.
  *
  * @param pid Process ID
  * @param startTime Process start time (milliseconds)
  * @param lastCpuTime Last CPU time reading (jiffies)
  * @param lastCheckTime Last check time (milliseconds)
  * @return ProcessMetrics or null if process doesn't exist
  */
 private fun readProcessMetrics(
  pid: Int,
  startTime: Long,
  lastCpuTime: Long,
  lastCheckTime: Long
 ): ProcessMetrics? {
  val statFile = File("/proc/$pid/stat")
  val statusFile = File("/proc/$pid/status")

  if (!statFile.exists() || !statusFile.exists()) {
   return null
  }

  try {
   // Read CPU usage from /proc/[pid]/stat
   val statContent = statFile.readText()
   val cpuStats = parseCpuStats(statContent)

   // Read memory usage from /proc/[pid]/status
   val statusContent = statusFile.readText()
   val memoryMB = parseMemoryUsage(statusContent)

   // Calculate CPU percentage
   val currentTime = System.currentTimeMillis()
   val timeDeltaMs = currentTime - lastCheckTime
   val cpuTimeDelta = cpuStats.totalCpuTime - lastCpuTime

   // Convert jiffies to milliseconds (assuming 100 jiffies/second on Android)
   val cpuTimeDeltaMs = cpuTimeDelta * 10L

   val cpuPercent = if (timeDeltaMs > 0) {
    ((cpuTimeDeltaMs.toFloat() / timeDeltaMs.toFloat()) * 100f).coerceIn(0f, 100f)
   } else {
    0f
   }

   val uptimeMs = currentTime - startTime

   return ProcessMetrics(
    pid = pid,
    cpuPercent = cpuPercent,
    memoryMB = memoryMB,
    uptimeMs = uptimeMs,
    totalCpuTime = cpuStats.totalCpuTime
   )
  } catch (e: Exception) {
   Log.w(TAG, "Failed to parse process metrics for PID $pid", e)
   return null
  }
 }

 /**
  * Parses CPU statistics from /proc/[pid]/stat.
  *
  * Format: pid (comm) state ppid pgrp session tty_nr tpgid flags minflt cminflt majflt cmajflt utime stime cutime cstime ...
  *
  * @param statContent Content of /proc/[pid]/stat
  * @return CpuStats with utime + stime (total CPU time in jiffies)
  */
 private fun parseCpuStats(statContent: String): CpuStats {
  // Find the last ')' to handle process names with spaces/parentheses
  val endOfComm = statContent.lastIndexOf(')')
  if (endOfComm == -1) {
   return CpuStats(0L, 0L, 0L)
  }

  val fields = statContent.substring(endOfComm + 2).split(" ")

  // Fields after comm: state(0) ppid(1) ... utime(11) stime(12) cutime(13) cstime(14)
  val utime = fields.getOrNull(11)?.toLongOrNull() ?: 0L
  val stime = fields.getOrNull(12)?.toLongOrNull() ?: 0L
  val cutime = fields.getOrNull(13)?.toLongOrNull() ?: 0L
  val cstime = fields.getOrNull(14)?.toLongOrNull() ?: 0L

  // Total CPU time = user time + system time + child user time + child system time
  val totalCpuTime = utime + stime + cutime + cstime

  return CpuStats(utime, stime, totalCpuTime)
 }

 /**
  * Parses memory usage from /proc/[pid]/status.
  *
  * Looks for "VmRSS:" line which indicates Resident Set Size (physical memory usage).
  *
  * @param statusContent Content of /proc/[pid]/status
  * @return Memory usage in MB
  */
 private fun parseMemoryUsage(statusContent: String): Int {
  val vmRssLine = statusContent.lines().find { it.startsWith("VmRSS:") }
  if (vmRssLine != null) {
   // Format: "VmRSS:  12345 kB"
   val parts = vmRssLine.split(Regex("\\s+"))
   val memoryKB = parts.getOrNull(1)?.toLongOrNull() ?: 0L
   return (memoryKB / 1024).toInt() // Convert KB to MB
  }
  return 0
 }

 private data class CpuStats(
  val utime: Long,
  val stime: Long,
  val totalCpuTime: Long
 )
}

/**
 * Process metrics snapshot.
 */
data class ProcessMetrics(
 val pid: Int,
 val cpuPercent: Float,
 val memoryMB: Int,
 val uptimeMs: Long,
 internal val totalCpuTime: Long = 0L // Internal: for CPU delta calculation
)
