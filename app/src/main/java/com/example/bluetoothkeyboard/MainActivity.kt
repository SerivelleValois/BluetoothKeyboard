package com.example.bluetoothkeyboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private val logBuffer = StringBuilder()
    private val logHandler = Handler(Looper.getMainLooper())

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
        val logButton = findViewById<Button>(R.id.logButton)
        keyboardView = findViewById(R.id.keyboardView)

        keyboardView.setKeyListener(this)

        connectButton.setOnClickListener {
            checkBluetoothAndConnect()
        }

        disconnectButton.setOnClickListener {
            disconnect()
        }

        logButton.setOnClickListener {
            showLogDialog()
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

    /**
     * Check if device supports HID Device profile
     */
    private fun isHidDeviceSupported(): Boolean {
        return try {
            // Try to get HID Device profile
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return false
            val method = bluetoothAdapter.javaClass.getMethod("getProfileProxy",
                Context::class.java,
                Class.forName("android.bluetooth.BluetoothProfile\$ServiceListener"),
                Int::class.javaPrimitiveType
            )
            // HID_DEVICE profile value is 19
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun showHidNotSupportedDialog() {
        AlertDialog.Builder(this)
            .setTitle("设备不支持")
            .setMessage("您的设备不支持蓝牙HID设备功能。\n\n" +
                    "部分手机厂商（如小米、OPPO、vivo、一加等）禁用了此功能。\n\n" +
                    "解决方法：\n" +
                    "1. 使用支持HID功能的手机（原生Android或三星）\n" +
                    "2. 刷入支持HID的ROM\n" +
                    "3. 使用ROOT权限启用该功能")
            .setPositiveButton("确定") { _, _ ->
                finish()
            }
            .show()
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
            // Check if device supports HID Device profile
            if (!isHidDeviceSupported()) {
                showHidNotSupportedDialog()
                return
            }
            bluetoothHidService = BluetoothHidService(this).apply {
                setLogListener(object : BluetoothHidService.LogListener {
                    override fun onLog(message: String) {
                        addLog("[HID] $message")
                    }
                })
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
        if (bluetoothHidService == null) {
            Toast.makeText(this, "蓝牙服务未初始化", Toast.LENGTH_SHORT).show()
            checkBluetoothSupport()
            return
        }
        showDeviceList()
    }

    private fun showDeviceList() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return

        // Check permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "需要蓝牙权限", Toast.LENGTH_SHORT).show()
            return
        }

        // Get paired devices
        val pairedDevices = bluetoothAdapter.bondedDevices?.toList() ?: emptyList()

        if (pairedDevices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("没有配对设备")
                .setMessage("请先与目标设备配对:\n1. 打开系统蓝牙设置\n2. 搜索并配对目标设备\n3. 返回本应用重试")
                .setPositiveButton("打开蓝牙设置") { _, _ ->
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        // Show device list
        val deviceNames = pairedDevices.map { it.name ?: "未知设备 (${it.address})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择要连接的设备")
            .setItems(deviceNames) { _, which ->
                val device = pairedDevices[which]
                connectToDevice(device)
            }
            .setPositiveButton("刷新") { _, _ ->
                showDeviceList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothHidService?.connectToDevice(device) ?: run {
            Toast.makeText(this, "蓝牙服务未初始化", Toast.LENGTH_SHORT).show()
        }
    }

    private fun disconnect() {
        bluetoothHidService?.disconnect()
        keyboardView.releaseAllKeys()
    }

    private fun showPairingInstructions() {
        AlertDialog.Builder(this)
            .setTitle("配对说明")
            .setMessage("重要：请确保删除之前与电脑的所有配对记录！\n\n" +
                    "1. 先在手机蓝牙设置中删除与电脑的配对\n" +
                    "2. 在电脑蓝牙设置中删除与手机的配对\n" +
                    "3. 保持本应用打开\n" +
                    "4. 从电脑蓝牙设置中搜索并发起配对请求\n" +
                    "5. 在手机弹出的配对请求中点击\"配对\"\n\n" +
                    "如果连接失败，请重启蓝牙后重试。")
            .setPositiveButton("打开蓝牙设置") { _, _ ->
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
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

    private fun showLogDialog() {
        val logView = ScrollView(this)
        val textView = TextView(this).apply {
            text = logBuffer.toString()
            textSize = 10f
            textColor = Color.WHITE
            setBackgroundColor(Color.BLACK)
            setPadding(16, 16, 16, 16)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        logView.addView(textView)

        AlertDialog.Builder(this)
            .setTitle("Debug 日志")
            .setView(logView)
            .setPositiveButton("刷新") { _, _ ->
                showLogDialog()
            }
            .setNegativeButton("复制") { _, _ ->
                copyToClipboard(logBuffer.toString())
            }
            .setNeutralButton("清除") { _, _ ->
                logBuffer.clear()
                addLog("=== 日志已清除 ===")
                showLogDialog()
            }
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Debug Log", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        logBuffer.append("[$timestamp] $message\n")
        // Limit log size
        if (logBuffer.length > 50000) {
            val start = logBuffer.indexOf("\n", 1000)
            if (start > 0) {
                logBuffer.delete(0, start + 1)
            }
        }
    }
}
