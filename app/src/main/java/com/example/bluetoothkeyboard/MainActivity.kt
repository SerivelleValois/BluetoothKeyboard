package com.example.bluetoothkeyboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Main activity for Bluetooth Keyboard application
 * Manages Bluetooth connection and keyboard input
 */
class MainActivity : AppCompatActivity(), MultiTouchKeyboardView.KeyListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 2
    }

    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var keyboardView: MultiTouchKeyboardView

    private var bluetoothHidService: BluetoothHidService? = null
    private var isConnected = false

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            updateStatus("蓝牙已关闭", false)
                        }
                        BluetoothAdapter.STATE_ON -> {
                            updateStatus("蓝牙已开启", false)
                            initHidService()
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        checkPermissions()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        keyboardView = findViewById(R.id.keyboardView)

        keyboardView.setKeyListener(this)

        connectButton.setOnClickListener {
            checkBluetoothAndConnect()
        }

        disconnectButton.setOnClickListener {
            disconnect()
        }

        // Disable keyboard until connected
        updateConnectionState(false)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires BLUETOOTH_CONNECT and BLUETOOTH_ADVERTISE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                REQUEST_BLUETOOTH_PERMISSIONS
            )
        } else {
            checkBluetoothSupport()
        }
    }

    private fun checkBluetoothSupport() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            // Request to enable Bluetooth
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        } else {
            initHidService()
        }
    }

    private fun initHidService() {
        try {
            bluetoothHidService = BluetoothHidService(this).apply {
                setConnectionListener(object : BluetoothHidService.ConnectionListener {
                    override fun onConnectionStateChanged(state: BluetoothHidService.ConnectionState) {
                        runOnUiThread {
                            when (state) {
                                BluetoothHidService.ConnectionState.DISCONNECTED -> {
                                    updateStatus("未连接", false)
                                    updateConnectionState(false)
                                }
                                BluetoothHidService.ConnectionState.ADVERTISING -> {
                                    updateStatus("等待配对... 请在电脑蓝牙设置中搜索 \"Android BT Keyboard\"", false)
                                    showPairingInstructions()
                                }
                                BluetoothHidService.ConnectionState.CONNECTING -> {
                                    updateStatus("正在连接...", false)
                                }
                                BluetoothHidService.ConnectionState.CONNECTED -> {
                                    updateStatus("已连接", true)
                                    updateConnectionState(true)
                                }
                            }
                        }
                    }

                    override fun onDeviceConnected(device: BluetoothDevice) {
                        Log.d(TAG, "Device connected: ${device.name}")
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "已连接到: ${device.name ?: "未知设备"}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onDeviceDisconnected(device: BluetoothDevice) {
                        Log.d(TAG, "Device disconnected: ${device.name}")
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "设备已断开",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "Error: $error")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize HID service", e)
            Toast.makeText(this, "初始化蓝牙键盘失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkBluetoothAndConnect() {
        bluetoothHidService?.startAdvertising() ?: run {
            Toast.makeText(this, "蓝牙服务未初始化", Toast.LENGTH_SHORT).show()
            checkBluetoothSupport()
        }
    }

    private fun disconnect() {
        bluetoothHidService?.disconnect()
        keyboardView.releaseAllKeys()
    }

    private fun showPairingInstructions() {
        AlertDialog.Builder(this)
            .setTitle("配对说明")
            .setMessage("1. 打开电脑蓝牙设置\n2. 搜索 \"Android BT Keyboard\"\n3. 点击配对\n4. 配对成功后即可使用")
            .setPositiveButton("打开蓝牙设置") { _, _ ->
                // Open system Bluetooth settings
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateStatus(status: String, connected: Boolean) {
        statusText.text = "状态: $status"
        isConnected = connected

        connectButton.isEnabled = !connected
        disconnectButton.isEnabled = connected
    }

    private fun updateConnectionState(connected: Boolean) {
        keyboardView.isEnabled = connected
        connectButton.visibility = if (connected) View.GONE else View.VISIBLE
        disconnectButton.visibility = if (connected) View.VISIBLE else View.GONE
    }

    // MultiTouchKeyboardView.KeyListener implementation

    override fun onKeyDown(keyCode: Int, modifier: Int) {
        if (isConnected) {
            bluetoothHidService?.sendKeyPress(keyCode, modifier)
        }
    }

    override fun onKeyUp(keyCode: Int) {
        if (isConnected) {
            bluetoothHidService?.sendKeyRelease()
        }
    }

    override fun onKeyLongPress(keyCode: Int, modifier: Int) {
        // For long press, we can send the key repeatedly or special handling
        // For now, just send the key again
        if (isConnected) {
            bluetoothHidService?.sendKey(keyCode, modifier)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_BLUETOOTH_PERMISSIONS -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    checkBluetoothSupport()
                } else {
                    Toast.makeText(this, "需要蓝牙权限才能使用", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                if (resultCode == RESULT_OK) {
                    initHidService()
                } else {
                    Toast.makeText(this, "需要开启蓝牙", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Register Bluetooth state receiver
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(bluetoothStateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothHidService?.cleanup()
        keyboardView.releaseAllKeys()
    }
}
