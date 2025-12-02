package com.anslin.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.flutter.plugin.common.EventChannel


class BluetoothStateStreamHandler(private val context: Context) : EventChannel.StreamHandler {

    private var bluetoothStateReceiver: BroadcastReceiver? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        // OSからの通知を受け取る
        bluetoothStateReceiver = createBluetoothStateReceiver(events)

        // OSからbluetoothの状態変化を受け取る
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothStateReceiver, filter)

        // アプリ起動時の状態
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        events.success(adapter?.isEnabled ?: false)
    }

    override fun onCancel(arguments: Any?) {
        println("[EventChannel] Bluetooth状態の監視を停止します。")
        bluetoothStateReceiver?.let {
            context.unregisterReceiver(it)
            bluetoothStateReceiver = null
        }
    }

    private fun createBluetoothStateReceiver(events: EventChannel.EventSink): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            println("bluetoothがOFFになりました。")
                            events.success(false)
                        }
                        BluetoothAdapter.STATE_ON -> {
                            println("bluetoothがONになりました。")
                            events.success(true)
                        }
                    }
                }
            }
        }
    }
}

