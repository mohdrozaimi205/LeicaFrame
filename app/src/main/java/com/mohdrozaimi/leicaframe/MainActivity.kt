package com.mohdrozaimi.leicaframe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FilterVintage
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.mohdrozaimi.leicaframe.export.ExportOptions
import com.mohdrozaimi.leicaframe.ui.BusyOverlay
import com.mohdrozaimi.leicaframe.ui.ExportDialog
import com.mohdrozaimi.leicaframe.ui.FiltersScreen
import com.mohdrozaimi.leicaframe.ui.EditScreen
import com.mohdrozaimi.leicaframe.ui.HomeScreen
import com.mohdrozaimi.leicaframe.ui.LF
import com.mohdrozaimi.leicaframe.ui.LFColors
import com.mohdrozaimi.leicaframe.ui.Label
import com.mohdrozaimi.leicaframe.ui.PhotosScreen
import com.mohdrozaimi.leicaframe.ui.PortraitScreen
import com.mohdrozaimi.leicaframe.ui.WatermarkScreen
import com.mohdrozaimi.leicaframe.ui.UpdateDialog
import com.mohdrozaimi.leicaframe.update.UpdateChecker
import com.mohdrozaimi.leicaframe.update.UpdateInfo
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: EditorViewModel by viewModels()
    private var pendingExport: ExportOptions? = null

    // ---- kemas kini aplikasi
    private val update = mutableStateOf<UpdateInfo?>(null)
    private val showUpdate = mutableStateOf(false)
    private var downloadRequested = false

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        update.value?.let { UpdateChecker.notify(this, it) }
    }

    fun checkUpdate(manual: Boolean) {
        lifecycleScope.launch {
            val info = UpdateChecker.check(this@MainActivity)
            if (info == null) {
                if (manual) vm.message("Anda guna versi terkini (v${UpdateChecker.currentVersion(this@MainActivity)}).")
                return@launch
            }
            update.value = info
            if (downloadRequested) { downloadRequested = false; startDownload(); return@launch }
            showUpdate.value = true
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else UpdateChecker.notify(this@MainActivity, info)
        }
    }

    fun startDownload() {
        val info = update.value ?: run { downloadRequested = true; checkUpdate(false); return }
        showUpdate.value = false
        UpdateChecker.download(this, info)
        vm.message("Memuat turun v${info.version}… pemasang akan dibuka bila siap.")
    }

    override fun onResume() {
        super.onResume()
        UpdateChecker.resumePending(this)
    }

    private val storagePerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val p = pendingExport
        pendingExport = null
        if (ok && p != null) vm.export(p) else if (!ok) vm.message("Kebenaran storan diperlukan untuk simpan (Android 8–9).")
    }

    fun requestExport(opt: ExportOptions) {
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingExport = opt
            storagePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else vm.export(opt)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
        if (intent?.getBooleanExtra(UpdateChecker.EXTRA_DOWNLOAD, false) == true) downloadRequested = true
        setContent {
            MaterialTheme(colorScheme = LFColors) {
                App(vm, ::requestExport, onCheckUpdate = { checkUpdate(true) })
                val info = update.value
                if (showUpdate.value && info != null) UpdateDialog(info,
                    current = UpdateChecker.currentVersion(this),
                    onDownload = ::startDownload, onLater = { showUpdate.value = false })
            }
        }
        checkUpdate(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
        if (intent.getBooleanExtra(UpdateChecker.EXTRA_DOWNLOAD, false)) startDownload()
    }

    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)?.let { vm.load(it) }
        }
    }
}

enum class Tab(val title: String, val icon: ImageVector) {
    HOME("HOME", Icons.Outlined.Home),
    FILTERS("FILTERS", Icons.Outlined.FilterVintage),
    PORTRAIT("AI PORTRAIT", Icons.Outlined.Camera),
    EDIT("EDIT", Icons.Outlined.Tune),
    WATERMARK("WATERMARK", Icons.Outlined.Edit),
    PHOTOS("MY PHOTOS", Icons.Outlined.Collections),
}

@Composable
fun App(vm: EditorViewModel, onExport: (ExportOptions) -> Unit, onCheckUpdate: () -> Unit = {}) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    val status by vm.status.collectAsState()
    val photo by vm.photo.collectAsState()

    LaunchedEffect(status.message) {
        if (status.message != null) { delay(4500); vm.message(null) }
    }

    Box(Modifier.fillMaxSize().background(LF.Black)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Bar atas
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("LEICA", color = LF.White, fontSize = 16.sp, letterSpacing = 5.sp)
                Text(" FRAME", color = LF.Amber, fontSize = 16.sp, letterSpacing = 5.sp)
                Spacer(Modifier.weight(1f))
                if (photo != null) {
                    Label("RESET", modifier = Modifier.clickable { vm.resetAll() }.padding(10.dp))
                    Row(
                        Modifier.background(LF.Amber).clickable { showExport = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = LF.Black, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("EXPORT", color = LF.Black, fontSize = 11.sp, letterSpacing = 2.sp)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (Tab.entries[tab]) {
                    Tab.HOME -> HomeScreen(vm, onCheckUpdate) { tab = it.ordinal }
                    Tab.FILTERS -> FiltersScreen(vm)
                    Tab.PORTRAIT -> PortraitScreen(vm)
                    Tab.EDIT -> EditScreen(vm)
                    Tab.WATERMARK -> WatermarkScreen(vm)
                    Tab.PHOTOS -> PhotosScreen(vm) { tab = Tab.EDIT.ordinal }
                }
            }
            // Navigasi bawah
            Row(Modifier.fillMaxWidth().background(LF.Charcoal).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                Tab.entries.forEachIndexed { i, t ->
                    val sel = i == tab
                    Column(
                        Modifier.weight(1f).clickable { tab = i }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(t.icon, t.title, tint = if (sel) LF.Amber else LF.Mute, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(3.dp))
                        Text(t.title, color = if (sel) LF.White else LF.Mute, fontSize = 8.sp, letterSpacing = 1.sp, maxLines = 1)
                    }
                }
            }
        }
        status.message?.let { m ->
            Snackbar(Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 90.dp),
                containerColor = LF.Graphite, contentColor = LF.White) { Text(m, fontSize = 13.sp) }
        }
        if (status.exporting) BusyOverlay("Exporting high resolution…")
        if (showExport) ExportDialog(vm, onDismiss = { showExport = false }) { opt ->
            showExport = false; onExport(opt)
        }
    }
}
