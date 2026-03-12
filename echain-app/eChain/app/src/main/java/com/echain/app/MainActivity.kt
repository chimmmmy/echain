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
import android.graphics.Bitmap
import android.graphics.BitmapFactory

class MainActivity : AppCompatActivity() {

    private lateinit var statusLabel: TextView
    private lateinit var connectButton: Button

    private var bluetoothGatt: BluetoothGatt? = null
    private var titleCharacteristic: BluetoothGattCharacteristic? = null
    private var artistCharacteristic: BluetoothGattCharacteristic? = null
    private var albumCharacteristic: BluetoothGattCharacteristic? = null

    private var imageCharacteristic: BluetoothGattCharacteristic? = null

    private val IMAGE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789004")

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
            imageCharacteristic = service?.getCharacteristic(IMAGE_UUID)
            runOnUiThread { statusLabel.text = "Ready! Sending media info..." }
            this@MainActivity.sendMediaInfo()
            MediaListenerService.onMediaChanged = { this@MainActivity.sendMediaInfo() }
            runOnUiThread {
                statusLabel.text = "Ready! Sending media info..."
                findViewById<Button>(R.id.imageButton).isEnabled = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusLabel   = findViewById(R.id.statusLabel)
        connectButton = findViewById(R.id.connectButton)
        val imageButton = findViewById<Button>(R.id.imageButton)
        imageButton.setOnClickListener { pickImage() }

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

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            // Check if actually connected first
            if (bluetoothGatt == null || imageCharacteristic == null) {
                statusLabel.text = "Cannot send: Not connected to echain"
                return
            }
            val uri = data?.data ?: return
            val stream = contentResolver.openInputStream(uri) ?: return
            val original = BitmapFactory.decodeStream(stream)

            // Resize to 296x128
            val scaled = Bitmap.createScaledBitmap(original, 296, 128, true)

            // Convert to 1-bit black and white
            val buf = ByteArray(4736) // 296*128/8
            for (py in 0 until 128) {
                for (px in 0 until 296) {
                    val pixel = scaled.getPixel(px, py)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                    val black = gray < 128
                    if (black) {
                        val byteIdx = py * (296 / 8) + (px / 8)
                        val bitIdx  = 7 - (px % 8)
                        buf[byteIdx] = (buf[byteIdx].toInt() or (1 shl bitIdx)).toByte()
                    }
                }
            }

            // Send over BLE in 512 byte chunks
            Thread {
                var offset = 0
                while (offset < buf.size) {
                    val chunkSize = minOf(512, buf.size - offset)
                    val chunk = buf.copyOfRange(offset, offset + chunkSize)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        bluetoothGatt?.writeCharacteristic(
                            imageCharacteristic!!,
                            chunk,
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        imageCharacteristic!!.value = chunk
                        @Suppress("DEPRECATION")
                        bluetoothGatt?.writeCharacteristic(imageCharacteristic!!)
                    }
                    offset += chunkSize
                    Thread.sleep(200) // wait between chunks
                }
                runOnUiThread { statusLabel.text = "Sleep image sent!" }
            }.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt?.close()
        }
    }
}