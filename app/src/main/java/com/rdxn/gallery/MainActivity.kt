package com.rdxn.gallery

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

enum class Access { NONE, LIMITED, FULL }

fun checkAccess(ctx: Context): Access {
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 33 -> when {
            granted(Manifest.permission.READ_MEDIA_IMAGES) -> Access.FULL
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.LIMITED
            else -> Access.NONE
        }
        else -> if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) Access.FULL else Access.NONE
    }
}

fun permissionsToRequest(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

class MainActivity : ComponentActivity() {
    private val vm: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GalleryTheme { AppRoot(vm) } }
    }
}

@Composable
fun AppRoot(vm: GalleryViewModel) {
    val ctx = LocalContext.current
    var access by remember { mutableStateOf(checkAccess(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        access = checkAccess(ctx)
        vm.onAccessChanged(access != Access.NONE)
    }
    // Setiap kembali ke aplikasi: cek izin lagi dan muat ulang foto (realtime).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = checkAccess(ctx)
        vm.onAccessChanged(access != Access.NONE)
    }
    val openSettings = {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
    }
    if (access == Access.NONE) {
        PermissionScreen(onRequest = { launcher.launch(permissionsToRequest()) }, onSettings = openSettings)
    } else {
        GalleryApp(vm, limited = access == Access.LIMITED, onOpenSettings = openSettings)
    }
}
