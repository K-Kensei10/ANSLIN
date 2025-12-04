package com.anslin.app

import android.Manifest
import android.app.Activity

import android.content.Context

import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.os.postDelayed
import android.content.BroadcastReceiver
import io.flutter.plugin.common.EventChannel
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.*
import com.anslin.app.MessageFormatFactor

val SERVICE_UUID = UUID.fromString(Constants.SERVICE_UUID)
val READ_CHARACTERISTIC_UUID = UUID.fromString(Constants.READ_CHARACTERISTIC_UUID)
val WRITE_CHARACTERISTIC_UUID = UUID.fromString(Constants.WRITE_CHARACTERISTIC_UUID)
val NOTIFY_CHARACTERISTIC_UUID = UUID.fromString(Constants.NOTIFY_CHARACTERISTIC_UUID)

var ISSCANNING = false
var ISADVERTISING = false

// Flutter
class MainActivity : FlutterActivity() {
    private val CHANNEL = "anslin.flutter.dev/contact"
    private lateinit var channel: MethodChannel
    private val BLUETOOTH_STATE_CHANNEL = "bluetoothStatus"
    private var bluetoothStateReceiver: BroadcastReceiver? = null

    private lateinit var scanner: BluetoothScanner
    private lateinit var advertiser: BluetoothAdvertiser

    //FlutterSQLに再送データを送る
    fun pushRelayMessage(q: String) {
        runOnUiThread() {
            if (::channel.isInitialized) {
                // dart側の 'saveRelayMessage' メソッドを呼び出す
                channel.invokeMethod("saveRelayMessage", q)
            } else {
                println("MethodChannelが初期化されていません。")
            }
        }
    }

    // FlutterSQLにメッセージを送る
    fun displayMessageOnFlutter(q: List<String?>) {
        runOnUiThread() {
            if (::channel.isInitialized) {
                channel.invokeMethod("displayMessage", q)
            } else {
                println("MethodChannelが初期化されていません。")
            }
        }
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)

        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "startCatchMessage" -> {
                    if (!ISSCANNING) {
                        ISSCANNING = true
                            // スキャン開始
                        scanner = BluetoothScanner(
                            context = this,
                            onSuccess = { messageData ->
                                ISSCANNING = false
                                MessageBridge.onMessageReceived(messageData)
                                result.success("メッセージ受信＆処理完了")
                            },
                            onError = { error ->
                                ISSCANNING = false
                                result.error("SCAN_FAILED", error, null)
                            }
                        )
                        scanner.startScan()
                    }
                }
                "startSendMessage" -> {
                    val messageData = MessageFormatFactor(context).buildOriginMessage(
                        call.argument<String>("message") ?: "",
                        call.argument<String>("messageType") ?: "",
                        call.argument<String>("toPhoneNumber") ?: "",
                        call.argument<String>("coordinates")
                    )
                    if (!ISADVERTISING) {
                        ISADVERTISING = true
                        
                        advertiser = BluetoothAdvertiser(
                            context = this,
                            onSuccess = { status ->
                                result.success(status)
                            },
                            onError = { error ->
                                result.error("ADVERTISE_FAILED", error, null)
                            }
                        )
                        advertiser.startAdvertise(messageData)
                    } else {
                        // キューに追加
                        channel.invokeMethod("saveRelayMessage", messageData)
                        result.success("メッセージを送信キューに追加しました。")
                    }
                }
                "autoAdvertise" -> {
                    if (!ISADVERTISING) {
                        ISADVERTISING = true

                        val messageData = call.argument<String>("message") ?: ""
                        
                        advertiser = BluetoothAdvertiser(
                            context = this,
                            onSuccess = { status ->
                                result.success(status)
                            },
                            onError = { error ->
                                result.error("ADVERTISE_FAILED", error, null)
                            }
                        )
                        advertiser.startAdvertise(messageData)
                    }
                }
                else -> result.notImplemented()
            }
        }

        // Bluetoothの状態
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, BLUETOOTH_STATE_CHANNEL)
            .setStreamHandler(BluetoothStateStreamHandler(this))

        // メッセージ処理待ちキュー
        MessageBridge.registerActivityHandler { receivedData ->
            runOnUiThread {
                val parsed = MessageParser.messageParse(receivedData) ?: return@runOnUiThread
                RelayMessage(
                    context = this,
                    parsedMessage = parsed,
                    displayMessageOnFlutter = ::displayMessageOnFlutter,
                    pushRelayMessage = ::pushRelayMessage
                ).routeMessage()
            }
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
