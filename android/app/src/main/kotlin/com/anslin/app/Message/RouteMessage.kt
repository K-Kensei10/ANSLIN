package com.anslin.app

import android.content.Context
import com.anslin.app.RelayedMessageBuilder

// TEST
val isMessenger: Boolean = false
val isGoverment: Boolean = false



// リレーフォーマット
object RelayedMessageBuilder {
    fun create(parsedMessageData: ParsedMessage): String {
        val newTTL = (parsedMessageData.ttl - 1).toString()
        return listOfNotNull(
            parsedMessageData.message,
            parsedMessageData.toPhoneNumber,
            parsedMessageData.messageType,
            parsedMessageData.fromPhoneNumber,
            newTTL,
            parsedMessageData.timestamp,
            parsedMessageData.coordinates
        ).joinToString(";")
    }
}

// 保存フォーマット
private data class FormatSaveData(
    val message: String,
    val messageType: String,
    val fromPhoneNumber: String,
    val timestamp: String,
    val coordinates: String?
)


// 保存フォーマット作成
private object SavedMessageBuilder{
    fun create(parsedMessageData: ParsedMessage): FormatSaveData {
        return FormatSaveData(
            parsedMessageData.message,
            parsedMessageData.messageType,
            parsedMessageData.fromPhoneNumber,
            parsedMessageData.timestamp,
            parsedMessageData.coordinates
        )
    }
}

class RelayMessage(private val context: Context, private val parsedMessage: ParsedMessage, ){
    private val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    private val myPhoneNumber = prefs.getString("flutter.my_phone_number", null)
    private val formatRelayMessage = RelayedMessageBuilder.create(parsedMessage)
    private val formatSaveMessage = SavedMessageBuilder.create(parsedMessage)

    pricate val isRelayMessage: Boolean = parsedMessage.ttl > 0

    fun routeMessage() {
        // 送信条件分岐
        when (parsedMessage.messageType) {// TODO

            "1" -> { // SNS
                displayMessageOnFlutter(formatSaveMessage)
                handleRelay()
            }

            "2" -> { // 長距離通信、安否確認
                // 自分宛
                if (parsedMessage.toPhoneNumber == myPhoneNumber) {
                    displayMessageOnFlutter(formatSaveMessage)
                    return
                }

                if (isMessenger) {
                    // メッセージを保存する人のアルゴリズム->メッセージを一時保存
                    return
                }
                handleRelay()
            }

            "3" -> { // 自治体への連絡
                if (isGoverment) {
                    // メッセージを保存する人のアルゴリズム->メッセージを一時保存
                    return
                }
                handleRelay()
            }

            "4" -> { // 自治体からの連絡
                displayMessageOnFlutter(formatSaveMessage)
                handleRelay()
            }

            else -> {
                println("不明なメッセージタイプです。")
                return
            }
        }
    }

    private fun handleRelay() {
        if (!isRelayMessage) return
        relayMessage(formatRelayMessage)
    }    
}