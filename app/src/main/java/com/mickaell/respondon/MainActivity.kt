package com.mickaell.respondon

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(BubbleService.PREFS, MODE_PRIVATE)
        val keyInput = findViewById<EditText>(R.id.api_key)
        keyInput.setText(prefs.getString(BubbleService.PREF_API_KEY, ""))

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            prefs.edit().putString(BubbleService.PREF_API_KEY, keyInput.text.toString().trim()).apply()
            Toast.makeText(this, R.string.key_saved, Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_overlay).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }

        findViewById<Button>(R.id.btn_perms).setOnClickListener {
            // Android 14 devuelve acceso PARCIAL a fotos ("Seleccionar fotos"), con el que
            // el MediaStore no ve los screenshots nuevos. Si ya hay algun acceso pero no es
            // total, el dialogo runtime no deja "subir" a total: hay que ir a Ajustes.
            if (hasFullPhotoAccess()) {
                Toast.makeText(this, R.string.perms_ok, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= 34 && hasPartialPhotoAccess()) {
                Toast.makeText(this, R.string.need_full_access, Toast.LENGTH_LONG).show()
                openAppSettings()
                return@setOnClickListener
            }
            val wanted = if (Build.VERSION.SDK_INT >= 33) {
                arrayOf(
                    android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            requestPermissions(wanted, 1)
        }

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            when {
                prefs.getString(BubbleService.PREF_API_KEY, "").isNullOrBlank() ->
                    Toast.makeText(this, R.string.no_api_key, Toast.LENGTH_LONG).show()
                !Settings.canDrawOverlays(this) ->
                    Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show()
                !hasFullPhotoAccess() -> {
                    Toast.makeText(this, R.string.need_full_access, Toast.LENGTH_LONG).show()
                    openAppSettings()
                }
                else -> {
                    startForegroundService(Intent(this, BubbleService::class.java))
                    Toast.makeText(this, R.string.started, Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            stopService(Intent(this, BubbleService::class.java))
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val overlay = if (Settings.canDrawOverlays(this)) "si" else "NO"
        val media = when {
            hasFullPhotoAccess() -> "si"
            Build.VERSION.SDK_INT >= 34 && hasPartialPhotoAccess() -> "PARCIAL (falta \"Permitir todo\")"
            else -> "NO"
        }
        findViewById<TextView>(R.id.status).text =
            getString(R.string.status_fmt, overlay, media)
    }

    private fun hasFullPhotoAccess(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasPartialPhotoAccess(): Boolean =
        checkSelfPermission(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }
}
