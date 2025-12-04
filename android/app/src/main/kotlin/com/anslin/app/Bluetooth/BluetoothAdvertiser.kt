package com.anslin.app

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log


class BluetoothAdvertiser(
    private val context: Context,
    private val onSuccess: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser
    private val handler = Handler(Looper.getMainLooper())

    private var gattServer: BluetoothGattServer? = null
    private var advertiseCallback: AdvertiseCallback? = null

    private var readCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    private val ADVERTISE_PERIOD: Long = 60 * 1000

    fun startAdvertise(messageData: String) {

        if (advertiser == null) {
            onError("このデバイスはBLEアドバタイズに対応していません")
            return
        }

        if (!PermissionUtils.check(context)) {
            onError("通信に必要な権限がありません")
            return
        }

        if (adapter?.isEnabled != true) {
            onError("BluetoothがOFFになっています")
            return
        }

        setupGattServer(messageData)
        startBleAdvertise()
    }

    private fun setupGattServer(messageData: String) {

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        val service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        readCharacteristic = BluetoothGattCharacteristic(
            READ_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            value = messageData.toByteArray(Charsets.UTF_8)
        }

        writeCharacteristic = BluetoothGattCharacteristic(
            WRITE_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        service.addCharacteristic(readCharacteristic)
        service.addCharacteristic(writeCharacteristic)

        gattServer?.addService(service)
    }

    private fun startBleAdvertise() {

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        advertiseCallback = object : AdvertiseCallback() {

            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d("Advertise", "アドバタイズ開始")

                handler.postDelayed({
                    stopAdvertise()
                    onError("一定時間内に接続が確立されませんでした")
                }, ADVERTISE_PERIOD)
            }

            override fun onStartFailure(errorCode: Int) {
                stopAdvertise()
                onError("アドバタイズ失敗（コード: $errorCode）")
            }
        }

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private fun stopAdvertise() {
        advertiser?.stopAdvertising(advertiseCallback)
        gattServer?.clearServices()
        gattServer?.close()
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {

            if (status != BluetoothGatt.GATT_SUCCESS) {
                stopAdvertise()
                onError("通信に失敗しました")
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("Advertise", "接続されました")
                advertiser?.stopAdvertising(advertiseCallback)
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("Advertise", "切断されました")
                stopAdvertise()
                onSuccess("SEND_MESSAGE_SUCCESSFUL")
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

            gattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                responseValue
            )
        }
    }
}
