package com.anslin.app

import android.content.Context

// リレーデータ
object FormatRelayMessage {
    fun relay(parsedMessageData: ParsedMessage): String {
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

class RelayMessage(private val context: Context, private val parsedMessage: ParsedMessage, ){
    private val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    private val myPhoneNumber = prefs.getString("flutter.my_phone_number", null)
    private val formatRelayMessage = FormatRelayMessage.relay(parsedMessage)

    pricate val isRelayMessage = parsedMessage.ttl > 0

    fun routeMessage() {
        // 送信条件分岐
        when (parsedMessage.messageType) {// TODO
            "1" -> { // SNS

                if (isRelayMessage) 
            }
            "2" -> { // 長距離通信、安否確認
                if (isRelayMessage) 
            }
            "3" -> { // 自治体への連絡
                if (isRelayMessage) 
            }
            "4" -> { // 自治体からの連絡
                if (isRelayMessage) 
            }
            else -> {
                println("不明なメッセージタイプです。")
            }
        }
    }
}