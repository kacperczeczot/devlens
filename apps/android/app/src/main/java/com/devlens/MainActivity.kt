package com.devlens

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.devlens.ui.MainViewModel
import com.devlens.ui.components.PermissionsAuditDialog
import com.devlens.ui.components.UpdateDialog
import com.devlens.ui.screens.DashboardScreen
import com.devlens.ui.screens.FileExplorerScreen
import com.devlens.ui.theme.DevLensTheme
import com.devlens.updater.ApkInstaller
import com.devlens.updater.ReleaseUpdateChecker
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DevLensTheme {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("devlens_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    val nodes by viewModel.nodes.collectAsState()

    var activeFilesNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeFilesPath by rememberSaveable { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }

    // Permissions Audit state
    var nodeForPermissionsId by rememberSaveable { mutableStateOf<String?>(null) }
    val nodeForPermissions = nodes.find { it.id == nodeForPermissionsId }
    val permissionsAuditReport by viewModel.permissionsAuditReport.collectAsState()
    val isAuditLoading by viewModel.isAuditLoading.collectAsState()
    val auditError by viewModel.auditError.collectAsState()

    // Auto-update states
    var updateOffer by remember { mutableStateOf<ReleaseUpdateChecker.UpdateOffer?>(null) }
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgressFraction by remember { mutableStateOf(0f) }
    var downloadProgressText by remember { mutableStateOf("") }
    var updateError by remember { mutableStateOf<String?>(null) }
    var pendingApkToInstall by remember { mutableStateOf<File?>(null) }
    var startUpdateRef: ((ReleaseUpdateChecker.UpdateOffer) -> Unit)? by remember { mutableStateOf(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (ApkInstaller.canInstallPackages(context)) {
            val pendingVersion = prefs.getString("pending_install_version", null)
            val readyApk = ApkInstaller.isApkReady(context, pendingVersion) ?: pendingApkToInstall
            if (readyApk != null && readyApk.exists()) {
                pendingApkToInstall = null
                prefs.edit().remove("pending_install_version").apply()
                showUpdateDialog = false
                isDownloadingUpdate = false
                ApkInstaller.install(context, readyApk)
            }
        }
    }

    fun checkUpdates(showToastOnUpToDate: Boolean = false) {
        val currentVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"

        ReleaseUpdateChecker.check(currentVersion) { offer ->
            coroutineScope.launch {
                updateOffer = offer
                if (offer != null) {
                    showUpdateDialog = true
                } else if (showToastOnUpToDate) {
                    Toast.makeText(context, "Aplikacja DevLens jest aktualna ($currentVersion)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val pendingVersion = prefs.getString("pending_install_version", null)
        if (pendingVersion != null && ApkInstaller.canInstallPackages(context)) {
            val readyApk = ApkInstaller.isApkReady(context, pendingVersion)
            if (readyApk != null) {
                prefs.edit().remove("pending_install_version").apply()
                showUpdateDialog = false
                isDownloadingUpdate = false
                ApkInstaller.install(context, readyApk)
            }
        }
        checkUpdates(false)
    }

    val startUpdate: (ReleaseUpdateChecker.UpdateOffer) -> Unit = { offer ->
        val readyApk = ApkInstaller.isApkReady(context, offer.latestVersion)
        if (!ApkInstaller.canInstallPackages(context)) {
            pendingApkToInstall = readyApk
            prefs.edit().putString("pending_install_version", offer.latestVersion).apply()
            Toast.makeText(context, "Zezwól na instalację aktualizacji", Toast.LENGTH_LONG).show()
            permissionLauncher.launch(ApkInstaller.unknownSourcesSettingsIntent(context))
        } else if (readyApk != null) {
            showUpdateDialog = false
            isDownloadingUpdate = false
            ApkInstaller.install(context, readyApk)
        } else {
            isDownloadingUpdate = true
            updateError = null
            downloadProgressFraction = 0f
            downloadProgressText = "Inicjalizacja pobierania…"
            ApkInstaller.downloadThenInstall(
                context = context,
                apkUrl = offer.apkUrl,
                expectedVersion = offer.latestVersion,
                onProgress = { text, frac ->
                    downloadProgressText = text
                    downloadProgressFraction = frac
                },
                onError = { err ->
                    isDownloadingUpdate = false
                    updateError = err
                },
                onReadyToInstall = { apkFile ->
                    downloadProgressFraction = 1f
                    downloadProgressText = "Uruchamianie instalatora…"
                    isDownloadingUpdate = false
                    if (ApkInstaller.canInstallPackages(context)) {
                        ApkInstaller.install(context, apkFile)
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(400)
                            showUpdateDialog = false
                        }
                    } else {
                        showUpdateDialog = false
                        pendingApkToInstall = apkFile
                        prefs.edit().putString("pending_install_version", offer.latestVersion).apply()
                        Toast.makeText(context, "Zezwól na instalację aktualizacji", Toast.LENGTH_LONG).show()
                        permissionLauncher.launch(ApkInstaller.unknownSourcesSettingsIntent(context))
                    }
                }
            )
        }
    }
    startUpdateRef = startUpdate

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val currentFilesNodeId = activeFilesNodeId

        if (currentFilesNodeId != null) {
            val filesNode = nodes.find { it.id == currentFilesNodeId }
            if (filesNode != null) {
                FileExplorerScreen(
                    node = filesNode,
                    initialPath = activeFilesPath ?: ".",
                    onBack = {
                        activeFilesNodeId = null
                        activeFilesPath = null
                    },
                    onLoadFiles = { path, onResult ->
                        viewModel.loadFiles(filesNode.id, path, onResult)
                    },
                    onReadFile = { filePath, onResult ->
                        viewModel.readFile(filesNode.id, filePath, onResult)
                    },
                    onDownloadRawFile = { filePath, destFile, onProgress, onDone ->
                        viewModel.downloadRawFile(filesNode.id, filePath, destFile, onProgress, onDone)
                    },
                    onCancelDownloadRawFile = { filePath ->
                        viewModel.cancelDownload(filesNode.id, filePath)
                    },
                    getRawFileStreamUrl = { filePath ->
                        viewModel.getRawFileStreamUrl(filesNode.id, filePath)
                    },
                    onUploadFile = { targetDir, fileName, uri, onProgress, onDone ->
                        viewModel.uploadFile(filesNode.id, targetDir, fileName, uri, context.contentResolver, onProgress, onDone)
                    },
                    onCancelUploadFile = { targetDir, fileName ->
                        viewModel.cancelUpload(filesNode.id, targetDir, fileName)
                    }
                )
            } else {
                activeFilesNodeId = null
                activeFilesPath = null
            }
        } else {
            // Main Dashboard View: Remote Nodes Explorer & Auditor
            DashboardScreen(
                nodes = nodes,
                isScanning = isScanning,
                onRefreshAll = {
                    viewModel.refreshAllNodes()
                },
                onScanAndPair = {
                    isScanning = true
                    viewModel.scanAndPair {
                        isScanning = false
                    }
                },
                onNodeRefresh = {
                    viewModel.refreshAllNodes()
                },
                hasUpdateAvailable = updateOffer != null,
                updateVersion = updateOffer?.latestVersion,
                onCheckUpdates = { checkUpdates(true) },
                onOpenUpdateDialog = { showUpdateDialog = true },
                onAddManualNode = { host, port, pinOrToken, onComplete ->
                    viewModel.pairWithHost(host, port, pinOrToken) { success, err ->
                        if (success) {
                            val added = viewModel.nodes.value.find { it.host.equals(host.trim(), ignoreCase = true) }
                            if (added != null) {
                                onComplete(Result.success(added))
                            } else {
                                onComplete(Result.failure(Exception("Dodano węzeł")))
                            }
                        } else {
                            onComplete(Result.failure(Exception(err ?: "Błąd połączenia")))
                        }
                    }
                },
                onDeleteNode = { node ->
                    viewModel.removeNode(node.id)
                },
                onRenameNode = { nodeId, newName ->
                    viewModel.renameNode(nodeId, newName)
                },
                onUpdateNodeDetails = { nodeId, newName, newHost, newPort ->
                    viewModel.updateNodeDetails(nodeId, newName, newHost, newPort)
                },
                onTogglePinNode = { node ->
                    viewModel.togglePinNode(node.id)
                },
                onNodeFilesClick = { node ->
                    activeFilesNodeId = node.id
                    activeFilesPath = null
                },
                onPermissionsClick = { node ->
                    nodeForPermissionsId = node.id
                    viewModel.runPermissionsAudit(node.id)
                }
            )
        }

        // Permissions Audit Dialog Overlay
        if (nodeForPermissions != null) {
            PermissionsAuditDialog(
                node = nodeForPermissions,
                report = permissionsAuditReport,
                isLoading = isAuditLoading,
                errorMessage = auditError,
                onRefresh = { viewModel.runPermissionsAudit(nodeForPermissions.id) },
                onFixAction = { action ->
                    viewModel.fixPermission(nodeForPermissions.id, action) { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = {
                    nodeForPermissionsId = null
                    viewModel.clearPermissionsAudit()
                }
            )
        }

        // Auto-update dialog
        if (showUpdateDialog && updateOffer != null) {
            UpdateDialog(
                offer = updateOffer!!,
                isDownloading = isDownloadingUpdate,
                progressFraction = downloadProgressFraction,
                progressStatus = downloadProgressText,
                errorMessage = updateError,
                onDismiss = {
                    if (!isDownloadingUpdate) {
                        showUpdateDialog = false
                    }
                },
                onStartUpdate = {
                    startUpdateRef?.invoke(updateOffer!!)
                },
                onCancelDownload = {
                    isDownloadingUpdate = false
                    downloadProgressFraction = 0f
                    downloadProgressText = ""
                    showUpdateDialog = false
                }
            )
        }
    }
}
