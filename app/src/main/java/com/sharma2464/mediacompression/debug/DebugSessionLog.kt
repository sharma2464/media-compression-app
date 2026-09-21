package com.sharma2464.mediacompression.debug

import android.content.Context
import android.util.Log
import org.json.JSONObject

object DebugSessionLog {
    private const val TAG = "DBG_D66E0"
    private const val FILE = "debug-d66e0a.ndjson"

    // #region agent log
    fun log(
        context: Context,
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "post-fix-v2",
    ) {
        val payload = JSONObject()
        payload.put("sessionId", "d66e0a")
        payload.put("hypothesisId", hypothesisId)
        payload.put("location", location)
        payload.put("message", message)
        payload.put("timestamp", System.currentTimeMillis())
        payload.put("runId", runId)
        val dataObj = JSONObject()
        data.forEach { (k, v) -> dataObj.put(k, v) }
        payload.put("data", dataObj)
        val line = payload.toString()
        Log.i(TAG, line)
        runCatching {
            context.openFileOutput(FILE, Context.MODE_APPEND).use {
                it.write((line + "\n").toByteArray())
            }
        }
    }
    // #endregion
}
