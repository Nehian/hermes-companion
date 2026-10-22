package com.hermes.companion.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.companion.Perms
import com.hermes.companion.PermStatus
import com.hermes.companion.alarm.AlarmScheduler
import com.hermes.companion.data.Job
import com.hermes.companion.data.Store
import com.hermes.companion.net.Api
import com.hermes.companion.sync.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

data class JobRow(
    val jobId: String,
    val timeMs: Long,
    val title: String,
    val message: String,
    val state: String,
    val answerLabel: String?,
    val open: Boolean,
    val group: String?,
    val priority: Boolean,
)

data class UiState(
    val serverUrl: String = "",
    val registered: Boolean = false,
    val deviceId: String = "",
    val lastSyncOkMs: Long = 0,
    val lastError: String? = null,
    val pendingUploads: Int = 0,
    val today: List<JobRow> = emptyList(),
    val upcoming: List<JobRow> = emptyList(),
    val nextAlarmMs: Long? = null,
    val perms: PermStatus = PermStatus(),
    val busy: Boolean = false,
    val message: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store.get(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { Store.changes.collect { rebuild() } }
    }

    private fun rebuild() {
        viewModelScope.launch(Dispatchers.IO) {
            val fresh = buildState()
            _state.update { fresh.copy(busy = it.busy, message = it.message) }
        }
    }

    /** Called when the app comes to the front: re-checks permissions and re-creates alarms (a force-stop clears them). */
    fun onForeground() {
        viewModelScope.launch(Dispatchers.IO) {
            AlarmScheduler.reconcile(getApplication())
            if (store.token != null) SyncEngine.syncNow(getApplication(), "foreground")
            rebuild()
        }
    }

    fun refresh() = rebuild()

    private fun row(job: Job) = JobRow(
        jobId = job.jobId,
        timeMs = job.effectiveFireMs,
        title = job.title,
        message = job.message,
        state = job.localState,
        answerLabel = job.resolvedActionId?.let { id -> job.actions.firstOrNull { it.actionId == id }?.label },
        open = job.isOpen,
        group = job.cancelGroup,
        priority = job.priority != "normal",
    )

    private fun buildState(): UiState {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val horizon = today.plusDays(8).atStartOfDay(zone).toInstant().toEpochMilli()
        val ctx = getApplication<Application>()
        return UiState(
            serverUrl = store.serverUrl ?: "",
            registered = store.token != null,
            deviceId = store.deviceId.orEmpty().take(8),
            lastSyncOkMs = store.getKv("last_sync_ok_ms")?.toLongOrNull() ?: 0,
            lastError = store.getKv("last_error"),
            pendingUploads = store.pendingUploadCount(),
            today = (store.overdueFired(start) + store.jobsBetween(start, end)).map(::row),
            upcoming = store.jobsBetween(end, horizon).map(::row),
            nextAlarmMs = AlarmScheduler.nextAlarmMs(ctx),
            perms = Perms.status(ctx),
        )
    }

    private fun setBusy(busy: Boolean) = _state.update { it.copy(busy = busy) }
    private fun say(text: String?) = _state.update { it.copy(message = text) }

    fun register(urlRaw: String, code: String) {
        val url = Api.normalizeUrl(urlRaw)
        if (!url.startsWith("http://") && !url.startsWith("https://")) return say("Enter the server address, starting with http:// or https://")
        if (code.isBlank()) return say("Enter the pairing code from Hermes")
        viewModelScope.launch {
            setBusy(true)
            say(null)
            try {
                val (id, token) = withContext(Dispatchers.IO) { Api.register(url, code.trim(), Build.MODEL) }
                store.serverUrl = url
                store.deviceId = id
                store.token = token
                store.lastRev = 0
                store.putKv("last_error", null)
                store.log("pair", "registered device $id")
                val r = SyncEngine.syncNow(getApplication(), "pair")
                say(if (r.ok) "Paired and synced." else "Paired. ${r.message}")
            } catch (e: Api.ApiException) {
                say(
                    when (e.code) {
                        400 -> "That pairing code is wrong or has expired. Ask Hermes for a new one."
                        429 -> "Too many attempts. Wait a minute and try again."
                        else -> "Server said: ${e.message}"
                    },
                )
            } catch (e: IOException) {
                say("Cannot reach the server at $url")
            } finally {
                setBusy(false)
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            setBusy(true)
            val r = SyncEngine.syncNow(getApplication(), "manual")
            say(r.message)
            setBusy(false)
        }
    }

    fun forget() {
        store.token = null
        store.deviceId = null
        store.lastRev = 0
        store.log("pair", "device forgotten")
        say("This phone is no longer paired.")
    }

    fun testAlarm(priority: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            store.insertLocalTestJob(System.currentTimeMillis() + 10_000, if (priority) "high" else "normal")
            AlarmScheduler.reconcile(getApplication())
            say(if (priority) "Priority test alarm will ring in 10 seconds. Open another app now to see it take over the screen." else "Test alarm will ring in 10 seconds.")
        }
    }
}
