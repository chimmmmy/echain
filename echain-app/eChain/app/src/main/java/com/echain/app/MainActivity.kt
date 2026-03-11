package com.echain.app

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var statusLabel: TextView
    private lateinit var connectButton: Button

    private var bluetoothGatt: BluetoothGatt? = null
    private var titleCharacteristic: BluetoothGattCharacteristic? = null
    private var artistCharacteristic: BluetoothGattCharacteristic? = null
    private var albumCharacteristic: BluetoothGattCharacteristic? = null

    private val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    private val TITLE_UUID   = UUID.fromString("12345678-1234-1234-1234-123456789001")
    private val ARTIST_UUID  = UUID.fromString("12345678-1234-1234-1234-123456789002")
    private val ALBUM_UUID   = UUID.fromString("12345678-1234-1234-1234-123456789003")

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                if (device.name == "Tomogatchi") {
                    bluetoothAdapter.bluetoothLeScanner.stopScan(this)
                    runOnUiThread { statusLabel.text = "Found Tomogatchi, connecting..." }
                    bluetoothGatt = device.connectGatt(this@MainActivity, false, gattCallback)
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread { statusLabel.text = "Connected! Discovering services..." }
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread { statusLabel.text = "Disconnected" }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            titleCharacteristic  = service?.getCharacteristic(TITLE_UUID)
            artistCharacteristic = service?.getCharacteristic(ARTIST_UUID)
            albumCharacteristic  = service?.getCharacteristic(ALBUM_UUID)
            runOnUiThread { statusLabel.text = "Ready! Sending media info..." }
            this@MainActivity.sendMediaInfo()
            MediaListenerService.onMediaChanged = { this@MainActivity.sendMediaInfo() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusLabel   = findViewById(R.id.statusLabel)
        connectButton = findViewById(R.id.connectButton)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            ), 1)
        }

        if (!isNotificationListenerEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        connectButton.setOnClickListener {
            statusLabel.text = "Scanning..."
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.bluetoothLeScanner.startScan(scanCallback)
            }
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(ComponentName(this, MediaListenerService::class.java).flattenToString()) == true
    }

    fun sendMediaInfo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        fun writeChar(char: BluetoothGattCharacteristic?, value: String) {
            char?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt?.writeCharacteristic(it, value.toByteArray(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } else {
                    @Suppress("DEPRECATION")
                    it.value = value.toByteArray()
                    @Suppress("DEPRECATION")
                    bluetoothGatt?.writeCharacteristic(it)
                }
                Thread.sleep(100)
            }
        }

        writeChar(titleCharacteristic,  MediaListenerService.songTitle)
        writeChar(artistCharacteristic, MediaListenerService.artistName)
        writeChar(albumCharacteristic,  MediaListenerService.albumName)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt?.close()
        }
    }
}