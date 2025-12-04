package com.anslin.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.*
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.os.postDelayed
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import io.flutter.plugin.common.EventChannel
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.*
import com.anslin.app.MessageFormatFactor
import com.anslin.app.

val SERVICE_UUID = UUID.fromString(Constants.SERVICE_UUID)
val READ_CHARACTERISTIC_UUID = UUID.fromString(Constants.READ_CHARACTERISTIC_UUID)
val WRITE_CHARACTERISTIC_UUID = UUID.fromString(Constants.WRITE_CHARACTERISTIC_UUID)
val NOTIFY_CHARACTERISTIC_UUID = UUID.fromString(Constants.NOTIFY_CHARACTERISTIC_UUID)

var ISSCANNING = false
var ISADVERTISING = false
val RSSI = Constants.RSSI

// Flutter
class MainActivity : FlutterActivity() {
    private val CHANNEL = "anslin.flutter.dev/contact"
    private lateinit var channel: MethodChannel
    private lateinit var prefs: SharedPreferences
    private val BLUETOOTH_STATE_CHANNEL = "bluetoothStatus"
    private var bluetoothStateReceiver: BroadcastReceiver? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)

        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "startCatchMessage" -> {
                    if (!ISSCANNING) {
                        ISSCANNING = true
                        val bleController = BluetoothLeController(this)
                        bleController.ScanAndConnect { resultMap ->
                            when (resultMap["status"]) {
                                "RECEIVE_MESSAGE_SUCCESSFUL" -> {
                                    val messageData = resultMap["data"]
                                    if (messageData != null) {
                                        MessageBridge.onMessageReceived(messageData)
                                    }
                                    result.success("メッセージ受信＆処理完了")
                                }
                                "device_not_found" -> {
                                    result.error("SCAN_FAILED", resultMap["message"], null)
                                }
                                else -> {
                                    result.error("UNKNOWN_STATUS", "予期せぬエラーが発生しました。", null)
                                }
                            }
                        }
                    }
                }
                "startSendMessage" -> {
                    val messageData = 
                        MessageFormatFactor(context).buildOriginMessage(
                            call.argument<String>("message") ?: "",
                            call.argument<String>("messageType") ?: "",
                            toPhoneNumber = call.argument<String>("toPhoneNumber") ?: "", 
                            call.argument<String>("coordinates")
                        )
                    Log.d("Advertise", "$messageData")
                    if (!ISADVERTISING) {
                        ISADVERTISING = true
                        val bleController = BluetoothLeController(this)
                        bleController.SendingMessage(messageData) { resultMap ->
                            when (resultMap["status"]) {
                                "SEND_MESSAGE_SUCCESSFUL" -> {
                                    result.success("SEND_MESSAGE_SUCCESSFUL")
                                }
                                "ADVERTISE_FAILED" -> {
                                    result.error("FAILED_ADVERTISING", "送信するデバイスが見つかりませんでした。", null)
                                }
                                else -> {
                                    result.error("UNKNOWN_STATUS", "予期せぬエラーが発生しました", null)
                                }
                            }
                        }
                    } else {
                        runOnUiThread() {
                            if (::channel.isInitialized) {
                                // dart側の 'saveRelayMessage' メソッドを呼び出す
                                channel.invokeMethod("saveRelayMessage", messageData)
                                result.success("メッセージを送信キューに追加しました。")
                            } else {
                                println("MethodChannelが初期化されていません。")
                                result.error("UNKNOWN_STATUS", "予期せぬエラーが発生しました", null)
                            }
                        }
                    }
                }
                "autoAdvertise" -> {
                    val messageData: String = call.argument<String>("message") ?: ""
                    if (!ISADVERTISING) {
                        ISADVERTISING = true
                        val bleController = BluetoothLeController(this)
                        bleController.SendingMessage(messageData) { resultMap ->
                            when (resultMap["status"]) {
                                "SEND_MESSAGE_SUCCESSFUL" -> {
                                    result.success("メッセージを送信キューに追加しました。")
                                }
                                "ADVERTISE_FAILED" -> {
                                    result.error("FAILED_ADVERTISING", "送信エラーが発生しました。", null)
                                }
                                else -> {
                                    result.error("UNKNOWN_STATUS", "予期せぬエラーが発生しました", null)
                                }
                            }
                        }
                    }
                }
                else -> result.notImplemented()
            }
        }
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, BLUETOOTH_STATE_CHANNEL)
            .setStreamHandler(BluetoothStateStreamHandler(this))
        MessageBridge.registerActivityHandler { receivedData ->
            runOnUiThread() { MessageParser.messageParse(receivedData) }
        }
    }
}

// メッセージの一時保管
object MessageBridge {
    // メッセージを一時的に保管
    private val messageQueue = mutableListOf<String>()
    private var activityHandler: ((jsonData: String) -> Unit)? = null

    fun onMessageReceived(jsonData: String) {
        activityHandler?.let { handler -> handler(jsonData) } ?: run { messageQueue.add(jsonData) }
    }

    fun registerActivityHandler(handler: (jsonData: String) -> Unit) {
        activityHandler = handler
        if (messageQueue.isNotEmpty()) {
            messageQueue.forEach { jsonData -> handler(jsonData) }
            messageQueue.clear()
        }
    }
}

// BLE class
class BluetoothLeController(public val activity: Activity) {
    private val bluetoothManager =
            activity.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val context: Context = activity
    private var _isScanning: Boolean = false
    private var isAdvertising: Boolean = false
    private var scanFilter: ScanFilter? = null
    private val handler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner
    private val advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser
    private var scanResults = mutableListOf<ScanResult>()
    private var bluetoothGatt: BluetoothGatt? = null
    private lateinit var mScanCallback: ScanCallback
    private lateinit var mAdvertiseCallback: AdvertiseCallback
    private lateinit var mGattServerCallback: BluetoothGattServerCallback
    private lateinit var mBluetoothGattServer: BluetoothGattServer
    private var scanResultCallback: ((Map<String, String>) -> Unit)? = null

    init {
        adapter?.name = "AL"
    }

    // 停止までの時間
    private val SCAN_PERIOD: Long = 3 * 1000
    private val ADVERTISE_PERIOD: Long = 60 * 1000

    // characteristic
    private var readCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    // ================= セントラル（メッセージ受信者） =================
    fun ScanAndConnect(onResult: (Map<String, String>) -> Unit) {
        scanResultCallback = onResult

        // 権限チェック
        if (!PermissionUtils.check(context)) {
            Log.d("Scan", "通信に必要な権限がありません。設定から許可してください。")
            ISSCANNING = false
            return
        }
        // BluetoothがOnになっているか
        if (adapter?.isEnabled != true) {
            Log.d("Scan", "BluetoothがOFFになっています。設定からONにしてください。")
            ISSCANNING = false
            return
        }
        // スキャン結果リセット
        scanResults.clear()
        // スキャン結果
        if (!_isScanning) {
            handler.postDelayed(
                    {
                        try {
                            scanner?.stopScan(mScanCallback)
                            _isScanning = false
                            Log.d("Scan", "スキャンストップ")
                            if (scanResults.isEmpty()) {
                                scanResults.clear()
                                Log.d("Scan", "検出されたデバイスはありません")
                                ISSCANNING = false
                                scanResultCallback?.invoke(mapOf("status" to "SCAN_FAILED", "message" to "付近にデバイスが見つかりませんでした。"))
                            } else {
                                val bestDevice = scanResults.maxByOrNull { it.rssi }
                                bestDevice?.let { result ->
                                    val name = result.scanRecord?.deviceName ?: result.device.name ?: "Unknown"
                                    val address = result.device.address
                                    val rssi = result.rssi
                                    Log.d("Scan", "接続対象: $name, アドレス: $address, RSSI: $rssi")
                                    try {
                                        cleanupGatt(bluetoothGatt)
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            connect(address)
                                        }, 200)
                                    } catch (e: Exception) {
                                        Log.d("Gatt", "通信開始失敗: ${e.message}")
                                        ISSCANNING = false
                                        scanResultCallback?.invoke(
                                            mapOf("status" to "SCAN_FAILED", "message" to "接続可能なデバイスが見つかりませんでした。")
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Scan", "スキャン停止時に例外: ${e.message}")
                            scanner?.stopScan(mScanCallback)
                            _isScanning = false
                            ISSCANNING = false
                        }
                    },
                    SCAN_PERIOD
            )
            startBleScan()
        } else {
            scanResultCallback?.invoke(
                mapOf("status" to "SCAN_FAILED", "message" to "スキャンに失敗しました")
            )
            ISSCANNING = false
        }
    }

    // ================= デバイススキャン =================
    fun startBleScan() {
        // スキャン設定
        val scanSettings: ScanSettings =
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        val scanFilterList = arrayListOf<ScanFilter>()
        val scanUuidFilter: ScanFilter =
                ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        scanFilterList.add(scanUuidFilter)

        // コールバック
        mScanCallback =
                object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        Log.d("Scan", "$result")
                        // 前に取得したことがない&&信号強度が強いもののみ
                        if (result.rssi >= RSSI &&
                                        scanResults.none {
                                            it.device.address == result.device.address
                                        }
                        ) {
                            val uuids = result.scanRecord?.serviceUuids
                            if (uuids?.contains(ParcelUuid(SERVICE_UUID)) == true) {
                                Log.d("Scan", "$result")
                            }
                            scanResults.add(result)
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        super.onScanFailed(errorCode)
                        scanResultCallback?.invoke(
                            mapOf("status" to "SCAN_FAILED", "message" to "スキャンに失敗しました（コード: $errorCode）")
                        )
                        Log.d("Scan", "スキャンに失敗しました（コード: $errorCode）")
                        _isScanning = false
                        ISSCANNING = false
                        scanner?.stopScan(mScanCallback)
                    }
                }
        if (_isScanning || scanner == null) {
            Log.d("Scan", "通信中に予期せぬエラーが発生しました。")
            ISSCANNING = false
            return
        }
        try {
            scanner.startScan(scanFilterList, scanSettings, mScanCallback)
            _isScanning = true
        } catch (e: Exception) {
            Log.d("Scan", "スキャン開始時に予期せぬエラーが発生しました。${e.message}")
            ISSCANNING = false
        }
    }

    // ================= ペリフェラル（メッセージ送信者） =================
    fun SendingMessage(messageData: String, onResult: (Map<String, String>) -> Unit) {
        var hasResponded = false
        fun safeResult(resultMap: Map<String, String>) {
            if (!hasResponded) {
                onResult(resultMap)
                hasResponded = true
            }
        }
        var isConnected: Boolean = false
        // 権限チェック
        if (advertiser == null) {
            Log.e("Advertise", "このデバイスはBLEアドバタイズに対応していません")
            safeResult(mapOf("status" to "ADVERTISE_FAILED","message" to "このデバイスはBLEアドバタイズに対応していません"))
            ISADVERTISING = false
            return
        }
        if (!PermissionUtils.check(context)) {
            Log.d("Advertise", "通信に必要な権限がありません。設定から許可してください。")
            safeResult(mapOf("status" to "ADVERTISE_FAILED","message" to "通信に必要な権限がありません。設定から許可してください。"))
            ISADVERTISING = false
            return
        }
        // BluetoothがOnになっているか
        if (adapter?.isEnabled != true) {
            Log.d("Advertise", "BluetoothがOFFになっています。設定からONにしてください。")
            safeResult(mapOf("status" to "ADVERTISE_FAILED","message" to "BluetoothがOFFになっています。設定からONにしてください。"))
            ISADVERTISING = false
            return
        }
        Log.d("Advertise", "$messageData")
        // セントラル側が切断した後の処理
        val mGattServerCallback =
                object : BluetoothGattServerCallback() {
                    override fun onConnectionStateChange(
                            device: BluetoothDevice?,
                            status: Int,
                            newState: Int
                    ) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            Log.e("Gatt", "接続失敗 status: $status")
                            safeResult(
                                mapOf(
                                    "status" to "ADVERTISE_FAILED",
                                    "message" to "通信に失敗しました。"
                                )
                            )
                            mBluetoothGattServer.clearServices()
                            mBluetoothGattServer.close()
                            ISSCANNING = false
                            return
                        }                        
                        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            Log.d("GATT", "セントラルが切断しました")
                            ISADVERTISING = false
                            // 変数初期化
                            readCharacteristic = null
                            writeCharacteristic = null
                            notifyCharacteristic = null
                            mBluetoothGattServer.clearServices()
                            mBluetoothGattServer.close()
                            safeResult(mapOf("status" to "SEND_MESSAGE_SUCCESSFUL"))
                        } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.d("GATT", "セントラルと交信しています")
                            advertiser.stopAdvertising(mAdvertiseCallback)
                            isConnected = true
                        }
                    }

                    override fun onCharacteristicReadRequest(
                            device: BluetoothDevice,
                            requestId: Int,
                            offset: Int,
                            characteristic: BluetoothGattCharacteristic
                    ) {
                        val value = characteristic.value ?: byteArrayOf()
                        val responseValue =
                                if (offset < value.size) value.copyOfRange(offset, value.size)
                                else byteArrayOf()
                        mBluetoothGattServer.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                offset,
                                responseValue
                        )
                    }
                }

        // Gatt通信用
        mBluetoothGattServer = bluetoothManager.openGattServer(context, mGattServerCallback)
        // Gattサービスの取得
        var BluetoothGattService =
                BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // キャラクタリスティック
        writeCharacteristic =
                BluetoothGattCharacteristic(
                        WRITE_CHARACTERISTIC_UUID,
                        BluetoothGattCharacteristic.PROPERTY_WRITE,
                        BluetoothGattCharacteristic.PERMISSION_WRITE
                )
        readCharacteristic =
                BluetoothGattCharacteristic(
                        READ_CHARACTERISTIC_UUID,
                        BluetoothGattCharacteristic.PROPERTY_READ,
                        BluetoothGattCharacteristic.PERMISSION_READ
                )

        // メッセージデータの書き込み
        // message, to_phone_number, message_type, from_phone_number, TTL
        readCharacteristic?.let { readChar ->
            readChar.value = messageData.toByteArray(Charsets.UTF_8)
        }

        // サービスに追加
        BluetoothGattService.addCharacteristic(readCharacteristic)
        BluetoothGattService.addCharacteristic(writeCharacteristic)

        // Gattキャラクタリスティックの追加
        mBluetoothGattServer.addService(BluetoothGattService)

        // アドバタイズ設定
        val advertiseSetting =
                AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                        .setConnectable(true)
                        .build()

        val advertiseData =
                AdvertiseData.Builder()
                        .setIncludeDeviceName(true)
                        .addServiceUuid(ParcelUuid(SERVICE_UUID))
                        .build()

        // コールバック
        mAdvertiseCallback =
                object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                        handler.postDelayed(
                                {
                                    if (isConnected) {
                                        Log.d("Advertise", "接続済みなのでアドバタイズ停止のみ")
                                        advertiser.stopAdvertising(mAdvertiseCallback)
                                        ISADVERTISING = false
                                        return@postDelayed
                                    }
                                    Log.e("Advertise", "接続が確立されませんでした")
                                    advertiser.stopAdvertising(mAdvertiseCallback)
                                    ISADVERTISING = false
                                    mBluetoothGattServer.clearServices()
                                    mBluetoothGattServer.close()
                                    safeResult(mapOf(
                                        "status" to "ADVERTISE_FAILED",
                                        "message" to "一定時間内に接続が確立されませんでした。再試行してください。"
                                    ))                                
                                },
                                ADVERTISE_PERIOD
                        )
                    }

                    override fun onStartFailure(errorCode: Int) {
                        Log.e("Advertise", "アドバタイズ失敗: $errorCode")
                        advertiser.stopAdvertising(mAdvertiseCallback)
                        ISADVERTISING = false
                        safeResult(
                                mapOf(
                                        "status" to "ADVERTISE_FAILED",
                                        "message" to
                                                "通信の開始に失敗しました。もう一度お試しください。（コード: $errorCode）"
                                )
                        )
                    }
                }
        advertiser.stopAdvertising(mAdvertiseCallback)
        handler.postDelayed(
                {
                    advertiser.startAdvertising(
                            advertiseSetting,
                            advertiseData,
                            mAdvertiseCallback
                    )
                },
                300
        )
    }

    // ================= GATT通信 =================
    private fun connect(address: String): Boolean {
        adapter?.let { adapter ->
            try {
                val device: BluetoothDevice? = adapter.getRemoteDevice(address)
                bluetoothGatt = device?.connectGatt(context, false, bluetoothGattCallback)
                return true
            } catch (exception: IllegalArgumentException) {
                Log.d("GATT", "デバイスが見つかりませんでした。")
                ISSCANNING = false
                return false
            }
        }
                ?: run {
                    Log.d("GATT", "Bluetoothが使用できません。")
                    ISSCANNING = false
                    return false
                }
    }

    // Gatt接続、コールバック
    private val bluetoothGattCallback =
            object : BluetoothGattCallback() {

                // ペリフェラルとの接続状態が変化したとき
                override fun onConnectionStateChange(
                        gatt: BluetoothGatt,
                        status: Int,
                        newState: Int
                ) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                      Log.e("Gatt", "接続失敗 status: $status")
                      ISSCANNING = false
                      cleanupGatt(gatt)
                      scanResultCallback?.invoke(mapOf("status" to "UNKNOWN_STATUS", "message" to "サービス検出に失敗しました"))
                      return
                    }
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d("Gatt", "接続成功")
                        // gatt通信量のサイズ変更
                        gatt.requestMtu(512)
                    }
                    if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d("Gatt", "接続が切断されました")
                        cleanupGatt(gatt)
                        ISSCANNING = false
                    }
                }

                override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d("Gatt", "MTU変更成功: $mtu バイト")
                    } else {
                        Log.e("Gatt", "MTU変更失敗")
                    }
                    gatt.discoverServices()
                }

                // サービスが検出されたとき
                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    super.onServicesDiscovered(gatt, status)
                    gatt ?: return
                    Log.d("Gatt", "サービス検出 gatt: $gatt, status: $status")
                    // 対象のサービスの取得
                    val service: BluetoothGattService? = gatt.getService(SERVICE_UUID)
                    if (service == null) {
                        Log.e("GATT", "指定されたサービスが見つかりません: $SERVICE_UUID")
                        ISSCANNING = false
                        return
                    }
                    readCharacteristic = service.getCharacteristic(READ_CHARACTERISTIC_UUID)
                    if (readCharacteristic != null) {
                        Log.d("GATT", "Read Characteristic取得成功")
                        Handler(Looper.getMainLooper())
                                .postDelayed(
                                        { gatt.readCharacteristic(readCharacteristic) },
                                        300
                                )
                    }
                    writeCharacteristic = service.getCharacteristic(WRITE_CHARACTERISTIC_UUID)
                    if (writeCharacteristic != null) {
                        Log.d("GATT", "Write Characteristic取得成功")
                    }
                }

                @Deprecated("Deprecated in API level 33")
                override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val rawData: ByteArray? = characteristic.getValue()
                        val data = rawData?.let { String(it, Charsets.UTF_8) } ?: ""
                        Log.d("BLE_READ", "受信メッセージ: $data")
                        scanResultCallback?.invoke(
                                mapOf("status" to "RECEIVE_MESSAGE_SUCCESSFUL", "data" to data)
                        )
                        cleanupGatt(gatt)
                        ISSCANNING = false
                    } else {
                        Log.e("BLE_READ", "読み取り失敗 status: $status")
                        cleanupGatt(gatt)
                        ISSCANNING = false
                    }
                }
            }
            private fun cleanupGatt(gatt: BluetoothGatt?) {
              gatt?.disconnect()
              gatt?.close()
              bluetoothGatt = null
            }
}

