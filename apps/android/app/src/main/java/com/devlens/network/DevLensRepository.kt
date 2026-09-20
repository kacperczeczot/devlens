package com.devlens.network

import android.content.Context
import android.content.SharedPreferences
import com.devlens.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import java.io.File

class DevLensRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("devlens_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scanner = LanScanner()

    private val _nodes = MutableStateFlow<List<DevLensNode>>(emptyList())
    val nodes: StateFlow<List<DevLensNode>> = _nodes.asStateFlow()

    init {
        loadSavedNodes()
    }

    private fun loadSavedNodes() {
        val jsonStr = prefs.getString("saved_nodes", null)
        if (jsonStr != null) {
            try {
                val type = object : TypeToken<List<DevLensNode>>() {}.type
                val saved: List<DevLensNode> = gson.fromJson(jsonStr, type) ?: emptyList()
                _nodes.value = saved
            } catch (_: Exception) {
                _nodes.value = emptyList()
            }
        } else {
            _nodes.value = emptyList()
        }
    }

    private fun saveNodes(nodesList: List<DevLensNode>) {
        val jsonStr = gson.toJson(nodesList)
        prefs.edit().putString("saved_nodes", jsonStr).apply()
    }

    suspend fun refreshAllNodes(): Unit = withContext(Dispatchers.IO) {
        val current = _nodes.value
        if (current.isEmpty()) return@withContext
        val updated = coroutineScope {
            current.map { node ->
                async { refreshNode(node) }
            }.awaitAll()
        }
        _nodes.value = updated
        saveNodes(updated)
    }

    suspend fun refreshNode(node: DevLensNode): DevLensNode = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val api = DevLensApiService.create("http://${node.host}:${node.port}")
            val health = api.checkHealth(node.token)
            val elapsed = System.currentTimeMillis() - start
            val sysInfo = try {
                api.getSystemInfo(node.token)
            } catch (_: Exception) {
                null
            }
            val platformFinal = health.platform.ifBlank { node.platform }
            node.copy(
                isOnline = true,
                lastPingMs = elapsed,
                platform = platformFinal,
                systemInfo = sysInfo
            )
        } catch (_: Exception) {
            node.copy(isOnline = false, lastPingMs = -1)
        }
    }

    suspend fun scanAndPair(): List<PairResponse> = withContext(Dispatchers.IO) {
        val foundIps = scanner.scanSubnet(subnetPrefix = null, port = 8888)
        val paired = mutableListOf<PairResponse>()

        for (ip in foundIps) {
            try {
                val alreadyPairedNode = _nodes.value.find { it.host == ip && it.token.isNotBlank() }
                if (alreadyPairedNode != null) {
                    try {
                        val healthApi = DevLensApiService.create("http://${alreadyPairedNode.host}:${alreadyPairedNode.port}")
                        val health = healthApi.checkHealth(alreadyPairedNode.token)
                        if (health.status == "ok" || health.status.isNotBlank()) {
                            _nodes.value = _nodes.value.map {
                                if (it.id == alreadyPairedNode.id) it.copy(
                                    isOnline = true,
                                    name = health.node.ifBlank { it.name }
                                ) else it
                            }
                            continue
                        }
                    } catch (_: Exception) {
                        // Health check failed, proceed to pair
                    }
                }

                val api = DevLensApiService.create("http://$ip:8888")
                val res = api.pairNode(
                    PairRequest(
                        nodeName = "DevLens-Client",
                        token = alreadyPairedNode?.token?.ifBlank { null } ?: "devlens-client-token",
                        pin = alreadyPairedNode?.token?.ifBlank { null }
                    )
                )
                paired.add(res)

                val safeNodeName = res.nodeName.trim().ifBlank { ip }
                val rawId = safeNodeName.lowercase().replace(Regex("[^a-z0-9_-]"), "-").trim('-').ifBlank { "node-${ip.replace('.', '-')}" }

                val existing = _nodes.value.toMutableList()
                val idx = existing.indexOfFirst {
                    (it.host == ip && it.port == 8888) || it.id == rawId
                }

                val targetId = if (idx >= 0) {
                    existing[idx].id
                } else {
                    var candidate = rawId
                    var counter = 2
                    while (existing.any { it.id == candidate }) {
                        candidate = "$rawId-$counter"
                        counter++
                    }
                    candidate
                }

                val newNode = DevLensNode(
                    id = targetId,
                    name = safeNodeName,
                    host = ip,
                    port = 8888,
                    token = res.token,
                    platform = res.platform.ifBlank { "Linux" },
                    isOnline = true
                )

                if (idx >= 0) {
                    val prev = existing[idx]
                    existing[idx] = newNode.copy(
                        customName = prev.customName,
                        isPinned = prev.isPinned
                    )
                } else {
                    existing.add(newNode)
                }
                _nodes.value = existing
                saveNodes(existing)
            } catch (_: Exception) {
                // Ignore unreachable IPs
            }
        }
        paired
    }

    suspend fun pairWithHost(host: String, port: Int = 8888, pinOrToken: String? = null): Result<DevLensNode> = withContext(Dispatchers.IO) {
        try {
            withTimeout(32000L) {
                var clean = host.trim()
                if (clean.startsWith("http://", ignoreCase = true)) clean = clean.substring(7)
                if (clean.startsWith("https://", ignoreCase = true)) clean = clean.substring(8)
                clean = clean.trimEnd('/')

                val actualHost = if (clean.contains(":")) clean.substringBefore(":").trim() else clean.trim()
                val actualPort = if (clean.contains(":")) clean.substringAfter(":").trim().toIntOrNull() ?: port else port

                if (actualHost.isBlank() || actualHost.contains(" ") || actualHost.contains("/")) {
                    return@withTimeout Result.failure<DevLensNode>(IllegalArgumentException("Nieprawidłowy adres hosta: $host"))
                }
                if (actualPort !in 1..65535) {
                    return@withTimeout Result.failure<DevLensNode>(IllegalArgumentException("Nieprawidłowy port: $actualPort"))
                }

                val api = DevLensApiService.create("http://$actualHost:$actualPort", isPairing = true)
                val res = api.pairNode(
                    PairRequest(
                        nodeName = "DevLens-Client",
                        token = pinOrToken?.trim()?.ifBlank { "devlens-client-token" } ?: "devlens-client-token",
                        pin = pinOrToken?.trim()?.ifBlank { null }
                    )
                )

                val safeNodeName = res.nodeName.trim().ifBlank { actualHost }
                val rawId = safeNodeName.lowercase().replace(Regex("[^a-z0-9_-]"), "-").trim('-').ifBlank { "node-${actualHost.replace('.', '-')}" }

                val existing = _nodes.value.toMutableList()
                val idx = existing.indexOfFirst {
                    (it.host.equals(actualHost, ignoreCase = true) && it.port == actualPort) || it.id == rawId
                }

                val targetId = if (idx >= 0) {
                    existing[idx].id
                } else {
                    var candidate = rawId
                    var counter = 2
                    while (existing.any { it.id == candidate }) {
                        candidate = "$rawId-$counter"
                        counter++
                    }
                    candidate
                }

                val baseNode = DevLensNode(
                    id = targetId,
                    name = safeNodeName,
                    host = actualHost,
                    port = actualPort,
                    token = res.token,
                    platform = res.platform.ifBlank { "Linux" },
                    isOnline = true
                )

                val refreshed = refreshNode(baseNode)

                if (idx >= 0) {
                    val prev = existing[idx]
                    existing[idx] = refreshed.copy(
                        id = prev.id,
                        customName = prev.customName,
                        isPinned = prev.isPinned
                    )
                } else {
                    existing.add(refreshed)
                }

                _nodes.value = existing
                saveNodes(existing)
                Result.success(refreshed)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Przekroczono limit czasu (brak zatwierdzenia na maszynie)"))
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val customMsg = try {
                if (!errorBody.isNullOrBlank()) {
                    org.json.JSONObject(errorBody).optString("error").takeIf { it.isNotBlank() }
                } else null
            } catch (_: Exception) {
                null
            } ?: when (e.code()) {
                403 -> "Połączenie zostało odrzucone lub podano błędny PIN"
                401 -> "Błąd autoryzacji: nieprawidłowy token"
                else -> "Błąd serwera (HTTP ${e.code()})"
            }
            Result.failure(Exception(customMsg))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun removeNode(nodeId: String) {
        val existing = _nodes.value.toMutableList()
        existing.removeAll { it.id == nodeId }
        _nodes.value = existing
        saveNodes(existing)
    }

    fun renameNode(nodeId: String, newName: String?) {
        updateNodeDetails(nodeId, newName, null, null)
    }

    fun updateNodeDetails(nodeId: String, newName: String?, newHost: String? = null, newPort: Int? = null) {
        val updated = _nodes.value.map { node ->
            if (node.id == nodeId) {
                var modified = node.copy(customName = newName?.trim()?.ifBlank { null })
                if (!newHost.isNullOrBlank()) {
                    modified = modified.copy(host = newHost.trim())
                }
                if (newPort != null && newPort in 1..65535) {
                    modified = modified.copy(port = newPort)
                }
                modified
            } else {
                node
            }
        }
        _nodes.value = updated
        saveNodes(updated)
    }

    fun togglePinNode(nodeId: String) {
        val updated = _nodes.value.map { node ->
            if (node.id == nodeId) {
                node.copy(isPinned = !node.isPinned)
            } else {
                node
            }
        }
        _nodes.value = updated
        saveNodes(updated)
    }

    suspend fun listFiles(nodeId: String, path: String? = null): Result<FileQueryResponse> =
        withContext(Dispatchers.IO) {
            val target = _nodes.value.find { it.id == nodeId }
                ?: return@withContext Result.failure(Exception("Nie znaleziono węzła '$nodeId'"))

            try {
                val api = DevLensApiService.create("http://${target.host}:${target.port}", isFile = true)
                val queryPath = path?.trim()?.ifBlank { "." } ?: "."
                val res = api.queryFiles(target.token, FileQueryRequest(path = queryPath, maxDepth = 1))
                Result.success(res)
            } catch (e: Exception) {
                val errorMsg = when (e) {
                    is java.net.SocketTimeoutException ->
                        "Przekroczono limit czasu oczekiwania na węzeł (możliwa blokada uprawnień macOS TCC do dysków zewnętrznych lub uśpiony dysk). Upewnij się, że DevLens posiada uprawnienia do dysków zewnętrznych w Ustawieniach systemowych."
                    else -> e.localizedMessage ?: "Błąd połączenia z węzłem"
                }
                Result.failure(Exception(errorMsg, e))
            }
        }

    suspend fun readFile(nodeId: String, filePath: String): Result<ReadFileResponse> =
        withContext(Dispatchers.IO) {
            val target = _nodes.value.find { it.id == nodeId }
                ?: return@withContext Result.failure(Exception("Nie znaleziono węzła '$nodeId'"))

            val cleanPath = filePath.trim()
            val api = DevLensApiService.create("http://${target.host}:${target.port}", isFile = true)

            try {
                val res = api.readFile(target.token, ReadFileRequest(path = cleanPath))
                if (res.isDir) {
                    return@withContext Result.success(res)
                }
                if (res.error != null && res.content.isBlank()) {
                    throw Exception(res.error)
                }
                Result.success(res)
            } catch (e: Exception) {
                val errorMsg = when (e) {
                    is java.net.SocketTimeoutException ->
                        "Przekroczono limit czasu odczytu pliku (możliwa blokada uprawnień macOS TCC do dysków zewnętrznych lub uśpiony dysk)."
                    else -> e.localizedMessage ?: "Błąd podczas odczytu pliku"
                }
                Result.failure(Exception(errorMsg, e))
            }
        }

    suspend fun checkPermissions(nodeId: String): Result<PermissionAuditReport> =
        withContext(Dispatchers.IO) {
            val target = _nodes.value.find { it.id == nodeId }
                ?: return@withContext Result.failure(Exception("Nie znaleziono węzła '$nodeId'"))

            try {
                val api = DevLensApiService.create("http://${target.host}:${target.port}", isAudit = true)
                val report = api.checkPermissions(target.token)
                Result.success(report)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fixPermission(nodeId: String, action: String): Result<PermissionFixResponse> =
        withContext(Dispatchers.IO) {
            val target = _nodes.value.find { it.id == nodeId }
                ?: return@withContext Result.failure(Exception("Nie znaleziono węzła '$nodeId'"))

            try {
                val api = DevLensApiService.create("http://${target.host}:${target.port}", isAudit = true)
                val response = api.fixPermission(target.token, PermissionFixRequest(action))
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun getRawFileStreamUrl(nodeId: String, filePath: String): String? {
        val target = _nodes.value.find { it.id == nodeId } ?: return null
        val encodedPath = java.net.URLEncoder.encode(filePath.trim(), "UTF-8")
        val tokenParam = if (target.token.isNotBlank()) "&token=${java.net.URLEncoder.encode(target.token, "UTF-8")}" else ""
        return "http://${target.host}:${target.port}/file-raw?path=$encodedPath$tokenParam"
    }

    suspend fun downloadRawFile(
        nodeId: String,
        filePath: String,
        destFile: File,
        onProgress: ((Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val target = _nodes.value.find { it.id == nodeId }
            ?: return@withContext Result.failure(Exception("Nie znaleziono węzła '$nodeId'"))

        val cleanPath = filePath.trim()
        val encodedPath = java.net.URLEncoder.encode(cleanPath, "UTF-8")
        val tokenParam = if (target.token.isNotBlank()) "&token=${java.net.URLEncoder.encode(target.token, "UTF-8")}" else ""
        val rawUrl = "http://${target.host}:${target.port}/file-raw?path=$encodedPath$tokenParam"

        var conn: java.net.HttpURLConnection? = null
        try {
            coroutineContext.ensureActive()
            val url = java.net.URL(rawUrl)
            conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 120_000
                requestMethod = "GET"
                if (target.token.isNotBlank()) {
                    setRequestProperty("X-Mesh-Token", target.token)
                }
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(Exception("Błąd serwera ($code) podczas pobierania pliku"))
            }

            val contentLength = conn.contentLengthLong
            if (destFile.exists()) destFile.delete()

            conn.inputStream.use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (contentLength > 0 && onProgress != null) {
                            onProgress((bytesCopied.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }
            coroutineContext.ensureActive()
            Result.success(destFile)
        } catch (e: kotlinx.coroutines.CancellationException) {
            try {
                if (destFile.exists()) destFile.delete()
            } catch (_: Exception) {}
            throw e
        } catch (e: Exception) {
            try {
                if (destFile.exists()) destFile.delete()
            } catch (_: Exception) {}
            Result.failure(e)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun uploadFile(
        nodeId: String,
        targetDir: String,
        fileName: String,
        fileUri: android.net.Uri,
        contentResolver: android.content.ContentResolver,
        onProgress: ((Float) -> Unit)? = null
    ): Result<UploadFileResponse> = withContext(Dispatchers.IO) {
        val target = _nodes.value.find { it.id == nodeId }
            ?: return@withContext Result.failure(Exception("Nie znaleziono węzła '$nodeId'"))

        val cleanDir = targetDir.trim()
        val encodedDir = java.net.URLEncoder.encode(cleanDir, "UTF-8")
        val encodedName = java.net.URLEncoder.encode(fileName.trim(), "UTF-8")
        val tokenParam = if (target.token.isNotBlank()) "&token=${java.net.URLEncoder.encode(target.token, "UTF-8")}" else ""
        val uploadUrl = "http://${target.host}:${target.port}/upload?dir=$encodedDir&filename=$encodedName$tokenParam"

        var conn: java.net.HttpURLConnection? = null
        try {
            coroutineContext.ensureActive()
            val fileSize = contentResolver.query(fileUri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIdx != -1) cursor.getLong(sizeIdx) else -1L
                } else -1L
            } ?: -1L

            val url = java.net.URL(uploadUrl)
            conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 300_000
                requestMethod = "POST"
                doOutput = true
                if (target.token.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${target.token}")
                    setRequestProperty("X-Mesh-Token", target.token)
                }
                setRequestProperty("Content-Type", "application/octet-stream")
                if (fileSize > 0) {
                    setFixedLengthStreamingMode(fileSize)
                } else {
                    setChunkedStreamingMode(32 * 1024)
                }
            }

            contentResolver.openInputStream(fileUri)?.use { input ->
                conn.outputStream.use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesWritten = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        bytesWritten += read
                        if (fileSize > 0 && onProgress != null) {
                            onProgress((bytesWritten.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            } ?: return@withContext Result.failure(Exception("Nie można odczytać pliku źródłowego"))

            coroutineContext.ensureActive()
            val code = conn.responseCode
            val responseStream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseBody = responseStream?.bufferedReader()?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                return@withContext Result.failure(Exception("Błąd serwera ($code): $responseBody"))
            }

            val parsedResponse = try {
                gson.fromJson(responseBody, UploadFileResponse::class.java)
            } catch (_: Exception) {
                UploadFileResponse(success = true, path = "$cleanDir/$fileName")
            }

            Result.success(parsedResponse)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }
}
