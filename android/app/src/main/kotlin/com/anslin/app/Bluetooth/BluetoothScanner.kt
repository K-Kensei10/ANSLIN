package com.anslin.app

import android.content.Context
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.*
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID
import android.util.Log


class BluetoothScanner (
    private val context: Context,
    private val onSuccess: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val bluetoothManager =
        context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())

    private val scanResults = mutableListOf<ScanResult>()
    private val lastConnectedMap = mutableMapOf<String, Long>()
    private var scanCallback: ScanCallback? = null


    private val SCAN_PERIOD: Long = 3 * 1000
    private val RSSI = Constants.RSSI

    // スキャン
    fun startScan() {

        cleanLastConnectedMap()

        if (!PermissionUtils.check(context)) {
            onError("通信に必要な権限がありません")
            return
        }

        if (adapter?.isEnabled != true) {
            onError("BluetoothがOFFになっています")
            return
        }

        if (scanner == null) {
            onError("スキャナーがありません")
            return
        }

        handler.postDelayed({
            stopScanAndConnect()
        }, SCAN_PERIOD)

        startBleScan()
    }

    private fun startBleScan() {
        val scanSettings: ScanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        val scanFilter = ScanFilter.Builder()
        .setServiceUuid(ParcelUuid(SERVICE_UUID))
        .build()

        scanCallback = object : ScanCallback() {

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Log.d("Scan", "$result")

                // RSSI が弱い or 既に追加済みならスキップ
                if (result.rssi < RSSI ||
                    scanResults.any { it.device.address == result.device.address }
                ) return

                // UUID フィルタ
                val uuids = result.scanRecord?.serviceUuids
                if (uuids?.contains(ParcelUuid(SERVICE_UUID)) == true) {
                    scanResults.add(result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                onError("スキャンに失敗しました（コード: $errorCode）")
                stopScan()
            }
        }

        try{
            scanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
        }catch (e: Exception) {
            onError("スキャン開始時にエラー: ${e.message}")
        }
    }

    private fun cleanLastConnectedMap() {
        val now = System.currentTimeMillis()
        val iterator = lastConnectedMap.entries.iterator()
    
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value >= 30_000) {
                iterator.remove()
            }
        }
    }

    private fun stopScanAndConnect() {
        scanner?.stopScan(scanCallback)

        if (scanResults.isEmpty()) {
            onError("付近にデバイスが見つかりませんでした")
            return
        }

        val now = System.currentTimeMillis()

        // デバイスが1台だけならルールを無視して接続
        if (scanResults.size == 1) {
            connectToDevice(scanResults[0].device)
            return
        }

        // 過去30秒以内に接続したデバイスを除外
        val filtered = scanResults.filter { result ->
            val last = lastConnectedMap[result.device.address]
            last == null || now - last >= 30_000
        }

        // ルールを無視して接続
        val targetList = if (filtered.isEmpty()) scanResults else filtered
    
        // 最も強いデバイスを選ぶ
        val bestDevice = targetList.maxByOrNull { it.rssi } ?: run {
            onError("接続可能なデバイスが見つかりませんでした")
            return
        }

        connectToDevice(bestDevice.device)
    }

    private fun stopScan() {
        scanner?.stopScan(scanCallback)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.requestMtu(244)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt.close()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("Gatt", "MTU変更成功: $mtu バイト")
            } else {
                onError("接続に失敗しました")
                return
            }
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d("Gatt", "サービス検出 gatt: $gatt, status: $status")
            val service = gatt.getService(SERVICE_UUID) ?: return
            val readCharacteristic = service.getCharacteristic(READ_CHARACTERISTIC_UUID) ?: return
            val writeCharacteristic = service.getCharacteristic(WRITE_CHARACTERISTIC_UUID) ?: return
            gatt.readCharacteristic(readCharacteristic)
        }
        
        @Deprecated("Deprecated in API level 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val value = characteristic.value ?: return
            val message = String(value, Charsets.UTF_8)

            lastConnectedMap[gatt.device.address] = System.currentTimeMillis()

            onSuccess(message)

            gatt.disconnect()
        }
    }
}