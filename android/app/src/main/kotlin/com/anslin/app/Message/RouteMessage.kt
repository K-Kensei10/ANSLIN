package com.anslin.app

import android.content.Context
import com.anslin.app.MessageFormatFactor

// TEST -> 自治体管理と長距離通信者のアルゴリズム実装予定
val isMessenger: Boolean = false
val isGoverment: Boolean = false

class RelayMessage(
    private val context: Context,
    private val parsedMessage: ParsedMessage,
    private val displayMessageOnFlutter: (List<String?>) -> Unit,
    private val pushRelayMessage: (String) -> Unit
){
    private val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    private val myPhoneNumber = prefs.getString("flutter.my_phone_number", null)
    private val relayMessageData = MessageFormatFactor(context).buildRelayMessage(parsedMessage)
    private val saveMessageData = MessageFormatFactor(context).buildSaveFormat(parsedMessage)

    private val isRelayMessage: Boolean = parsedMessage.ttl > 0

    fun routeMessage() {
        // 送信条件分岐
        when (parsedMessage.messageType) {// TODO

            "1" -> { // SNS
                handleDisplay()
                handleRelay()
            }

            "2" -> { // 長距離通信、安否確認
                // 自分宛
                if (parsedMessage.toPhoneNumber == myPhoneNumber) {
                    handleDisplay()
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
                handleDisplay()
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
        pushRelayMessage(relayMessageData)
    }

    private fun handleDisplay() {
        displayMessageOnFlutter(saveMessageData.toList())
    }
}