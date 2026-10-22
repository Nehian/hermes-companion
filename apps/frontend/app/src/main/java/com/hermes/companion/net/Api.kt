package com.hermes.companion.net

import com.hermes.companion.data.OutboxRow
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Api {
    class ApiException(val code: Int, message: String) : IOException(message)

    data class SyncPayload(val serverRev: Long, val jobs: List<JSONObject>)

    fun normalizeUrl(raw: String): String = raw.trim().trimEnd('/')

    private fun request(
        method: String,
        url: String,
        token: String?,
        body: JSONObject?,
        connectMs: Int,
        readMs: Int,
    ): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = connectMs
            conn.readTimeout = readMs
            conn.setRequestProperty("Accept", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull().orEmpty()
                throw ApiException(code, detail.ifBlank { "HTTP $code" })
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    fun register(baseUrl: String, pairingCode: String, name: String): Pair<String, String> {
        val r = request(
            "POST", "$baseUrl/v1/devices/register", null,
            JSONObject().put("pairing_code", pairingCode).put("name", name), 8000, 15000,
        )
        return r.getString("device_id") to r.getString("token")
    }

    fun sync(baseUrl: String, token: String, since: Long, connectMs: Int, readMs: Int): SyncPayload {
        val r = request("GET", "$baseUrl/v1/sync?since=$since", token, null, connectMs, readMs)
        val arr = r.getJSONArray("jobs")
        return SyncPayload(r.getLong("server_rev"), List(arr.length()) { arr.getJSONObject(it) })
    }

    /** Returns response_id -> status as reported by the server. */
    fun submitResponses(
        baseUrl: String,
        token: String,
        rows: List<OutboxRow>,
        connectMs: Int,
        readMs: Int,
    ): Map<String, String> {
        val arr = JSONArray()
        for (row in rows) {
            arr.put(
                JSONObject().put("response_id", row.responseId).put("job_id", row.jobId)
                    .put("action_id", row.actionId).put("responded_at", row.respondedAt),
            )
        }
        val r = request("POST", "$baseUrl/v1/responses", token, JSONObject().put("responses", arr), connectMs, readMs)
        val results = r.getJSONArray("results")
        return (0 until results.length()).associate {
            val o = results.getJSONObject(it)
            o.getString("response_id") to o.getString("status")
        }
    }

    fun ack(baseUrl: String, token: String, received: List<String>) {
        request("POST", "$baseUrl/v1/ack", token, JSONObject().put("received", JSONArray(received)), 4000, 8000)
    }
}
