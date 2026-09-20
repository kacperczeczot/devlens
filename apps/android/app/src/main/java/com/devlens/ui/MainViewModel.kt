package com.devlens.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devlens.data.*
import com.devlens.network.DevLensRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository: DevLensRepository = DevLensRepository(application)
    val nodes: StateFlow<List<DevLensNode>> = repository.nodes

    private var autoRefreshJob: Job? = null

    init {
        refreshAllNodes()
    }

    fun startAutoRefresh(intervalMs: Long = 4000L) {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                repository.refreshAllNodes()
                delay(intervalMs)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun refreshAllNodes() {
        viewModelScope.launch {
            repository.refreshAllNodes()
        }
    }

    fun scanAndPair(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.scanAndPair()
            repository.refreshAllNodes()
            onComplete()
        }
    }

    fun pairWithHost(host: String, port: Int = 8888, pinOrToken: String? = null, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.pairWithHost(host, port, pinOrToken)
            res.onSuccess {
                repository.refreshAllNodes()
                onComplete(true, null)
            }.onFailure {
                onComplete(false, it.localizedMessage ?: "Błąd parowania")
            }
        }
    }

    fun removeNode(nodeId: String) {
        repository.removeNode(nodeId)
    }

    fun renameNode(nodeId: String, newName: String?) {
        repository.renameNode(nodeId, newName)
    }

    fun updateNodeDetails(nodeId: String, newName: String?, newHost: String?, newPort: Int?) {
        repository.updateNodeDetails(nodeId, newName, newHost, newPort)
    }

    fun togglePinNode(nodeId: String) {
        repository.togglePinNode(nodeId)
    }

    // ==========================================
    // File Explorer & Transfer
    // ==========================================

    fun loadFiles(nodeId: String, path: String? = null, onResult: (Result<FileQueryResponse>) -> Unit) {
        viewModelScope.launch {
            val res = repository.listFiles(nodeId, path)
            onResult(res)
        }
    }

    fun readFile(nodeId: String, filePath: String, onResult: (Result<ReadFileResponse>) -> Unit) {
        viewModelScope.launch {
            val res = repository.readFile(nodeId, filePath)
            onResult(res)
        }
    }

    fun getRawFileStreamUrl(nodeId: String, filePath: String): String? {
        return repository.getRawFileStreamUrl(nodeId, filePath)
    }

    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()
    private val activeUploadJobs = ConcurrentHashMap<String, Job>()

    private fun getDownloadKey(nodeId: String, filePath: String): String = "$nodeId:${filePath.trim()}"
    private fun getUploadKey(nodeId: String, targetDir: String, fileName: String): String = "$nodeId:${targetDir.trim()}:${fileName.trim()}"

    fun isDownloading(nodeId: String, filePath: String): Boolean {
        val key = getDownloadKey(nodeId, filePath)
        return activeDownloadJobs[key]?.isActive == true
    }

    fun isUploading(nodeId: String, targetDir: String, fileName: String): Boolean {
        val key = getUploadKey(nodeId, targetDir, fileName)
        return activeUploadJobs[key]?.isActive == true
    }

    fun cancelDownload(nodeId: String, filePath: String) {
        val key = getDownloadKey(nodeId, filePath)
        activeDownloadJobs.remove(key)?.cancel()
    }

    fun cancelUpload(nodeId: String, targetDir: String, fileName: String) {
        val key = getUploadKey(nodeId, targetDir, fileName)
        activeUploadJobs.remove(key)?.cancel()
    }

    fun downloadRawFile(
        nodeId: String,
        filePath: String,
        destFile: File,
        onProgress: ((Float) -> Unit)? = null,
        onDone: (Result<File>) -> Unit
    ) {
        val key = getDownloadKey(nodeId, filePath)
        if (activeDownloadJobs[key]?.isActive == true) {
            return
        }

        val job = viewModelScope.launch {
            try {
                val res = repository.downloadRawFile(nodeId, filePath, destFile, onProgress)
                onDone(res)
            } catch (e: kotlinx.coroutines.CancellationException) {
                onDone(Result.failure(Exception("Pobieranie zostało anulowane")))
            } finally {
                activeDownloadJobs.remove(key)
            }
        }
        activeDownloadJobs[key] = job
    }

    fun uploadFile(
        nodeId: String,
        targetDir: String,
        fileName: String,
        fileUri: Uri,
        contentResolver: ContentResolver,
        onProgress: ((Float) -> Unit)? = null,
        onDone: (Result<UploadFileResponse>) -> Unit
    ) {
        val key = getUploadKey(nodeId, targetDir, fileName)
        if (activeUploadJobs[key]?.isActive == true) {
            return
        }

        val job = viewModelScope.launch {
            try {
                val res = repository.uploadFile(nodeId, targetDir, fileName, fileUri, contentResolver, onProgress)
                onDone(res)
            } catch (e: kotlinx.coroutines.CancellationException) {
                onDone(Result.failure(Exception("Wgrywanie zostało anulowane")))
            } finally {
                activeUploadJobs.remove(key)
            }
        }
        activeUploadJobs[key] = job
    }

    // ==========================================
    // Permissions & Security Audit
    // ==========================================

    private val _permissionsAuditReport = MutableStateFlow<PermissionAuditReport?>(null)
    val permissionsAuditReport: StateFlow<PermissionAuditReport?> = _permissionsAuditReport

    private val _isAuditLoading = MutableStateFlow(false)
    val isAuditLoading: StateFlow<Boolean> = _isAuditLoading

    private val _auditError = MutableStateFlow<String?>(null)
    val auditError: StateFlow<String?> = _auditError

    fun runPermissionsAudit(nodeId: String) {
        if (_isAuditLoading.value) return
        viewModelScope.launch {
            _isAuditLoading.value = true
            _auditError.value = null
            val res = repository.checkPermissions(nodeId)
            res.onSuccess {
                _permissionsAuditReport.value = it
            }.onFailure {
                _auditError.value = it.localizedMessage ?: "Błąd podczas audytu uprawnień"
            }
            _isAuditLoading.value = false
        }
    }

    private val _fixingAction = MutableStateFlow<String?>(null)
    val fixingAction: StateFlow<String?> = _fixingAction

    fun fixPermission(nodeId: String, action: String, onResult: (String) -> Unit) {
        if (_fixingAction.value != null) return
        viewModelScope.launch {
            _fixingAction.value = action
            val res = repository.fixPermission(nodeId, action)
            res.onSuccess {
                onResult(it.message)
                runPermissionsAudit(nodeId)
            }.onFailure {
                onResult("Błąd: ${it.localizedMessage}")
            }
            _fixingAction.value = null
        }
    }

    fun clearPermissionsAudit() {
        _permissionsAuditReport.value = null
        _auditError.value = null
    }
}
