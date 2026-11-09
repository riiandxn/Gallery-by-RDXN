package com.rdxn.gallery

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

enum class Access { NONE, LIMITED, FULL }

fun checkAccess(ctx: Context): Access {
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 33 -> {
            val img = granted(Manifest.permission.READ_MEDIA_IMAGES)
            val vid = granted(Manifest.permission.READ_MEDIA_VIDEO)
            when {
                img && vid -> Access.FULL
                Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.LIMITED
                img || vid -> Access.LIMITED
                else -> Access.NONE
            }
        }
        else -> if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) Access.FULL else Access.NONE
    }
}

fun permissionsToRequest(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private val AUTHS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** Buka kunci album Tersembunyi: sidik jari / wajah / PIN HP. Kalau HP tidak punya kunci layar, langsung dibuka. */
private fun authenticate(a: FragmentActivity, vm: GalleryViewModel) {
    if (BiometricManager.from(a).canAuthenticate(AUTHS) != BiometricManager.BIOMETRIC_SUCCESS) {
        vm.onAuthResult(true)
        return
    }
    val prompt = BiometricPrompt(
        a, ContextCompat.getMainExecutor(a),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { vm.onAuthResult(true) }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { vm.onAuthResult(false) }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka album Tersembunyi")
            .setAllowedAuthenticators(AUTHS)
            .build()
    )
}

class MainActivity : FragmentActivity() {
    private val vm: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GalleryTheme { AppRoot(vm, this) } }
    }
}

@Composable
fun AppRoot(vm: GalleryViewModel, activity: FragmentActivity) {
    val ctx = LocalContext.current
    var access by remember { mutableStateOf(checkAccess(ctx)) }
    var pending by remember { mutableStateOf<PendingAction?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        access = checkAccess(ctx)
        vm.onAccessChanged(access != Access.NONE)
    }
    // Dialog konfirmasi sistem untuk hapus / pulihkan.
    val senderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        pending?.onDone(res.resultCode == Activity.RESULT_OK)
        pending = null
    }
    LaunchedEffect(Unit) {
        vm.requests.collect { action ->
            pending = action
            senderLauncher.launch(IntentSenderRequest.Builder(action.sender).build())
        }
    }
    LaunchedEffect(Unit) {
        vm.authRequests.collect { authenticate(activity, vm) }
    }
    // Setiap kembali ke aplikasi: cek izin lagi dan muat ulang (realtime).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = checkAccess(ctx)
        vm.onAccessChanged(access != Access.NONE)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.lockHiddenNow() }

    val openSettings = {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
    }
    if (access == Access.NONE) {
        PermissionScreen(onRequest = { permLauncher.launch(permissionsToRequest()) }, onSettings = openSettings)
    } else {
        GalleryApp(vm, limited = access == Access.LIMITED, onOpenSettings = openSettings)
    }
}
