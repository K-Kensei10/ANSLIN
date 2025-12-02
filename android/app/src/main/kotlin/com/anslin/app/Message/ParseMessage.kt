package com.anslin.app

import android.content.Context

// 戻り値
data class ParsedMessage(
    val message: String,
    val toPhoneNumber: String,
    val messageType: String,
    val fromPhoneNumber: String,
    val ttl: Int,
    val timestamp: String,
    val coordinates: String?
)

//メッセージを分割して、リストで返す
object MessageParser {
    fun messageParse(receivedString: String): ParsedMessage? {
        val parts = receivedString.trim().split(";")
        if (parts.size !in 6..7) return null
        return ParsedMessage(
            message = parts[0],
            toPhoneNumber = parts[1],
            messageType = parts[2],
            fromPhoneNumber = parts[3],
            ttl = parts[4].toInt(),
            timestamp = parts[5],
            coordinates = parts.getOrNull(6)
        )
    }
}


class ReceiveMessage(private val context: Context){
    private val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    private val myPhoneNumber = prefs.getString("flutter.my_phone_number", null)

    //メッセージを分割して、リストで返す
    fun messageSeparate(receivedString: String) {
        
        try {
            // // message;to_phone_number;message_type;from_phone_number;TTL;TimeStamp;coordinars?
            // val SeparatedString: List<String> = receivedString.trim().split(";")

            // // メッセージサイズ検知
            // if (SeparatedString.size !in listOf(6, 7)) return

            // // 要素に分割
            // val message = SeparatedString[0]
            // val toPhoneNumber = SeparatedString[1]
            // val messageType = SeparatedString[2]
            // val fromPhoneNumber = SeparatedString[3]
            // val TTL = SeparatedString[4].toInt()
            // val timestampString = SeparatedString[5]
            // var coordinatesToDart: String? = null
            // // 位置情報あり
            // if (SeparatedString.size == 7) {
            //     coordinatesToDart = SeparatedString[6]
            // }

            //Flutterに渡すリスト
            val dataForFlutter =
                listOf(
                        message,
                        messageType,
                        fromPhoneNumber,
                        timestampString,
                        coordinatesToDart
                )
            var isMessenger: Boolean = false

            fun displayMessageOnFlutter(datalist: List<String?>) {
                runOnUiThread() {
                    if (::channel.isInitialized) {
                        channel.invokeMethod("displayMessage", datalist)
                    } else {
                        println("MethodChannelが初期化されていません。")
                    }
                }
            }

            fun relayMessage(
                    message: String,
                    toPhoneNumber: String,
                    messageType: String,
                    fromPhoneNumber: String,
                    TTL: Int,
                    timestampString: String,
                    coordinatesToDart: String?
            ) {
                val newTTL = (TTL - 1).toString()
                val relayData =
                        when (coordinatesToDart) {
                            null ->
                                    listOf(
                                                    message,
                                                    toPhoneNumber,
                                                    messageType,
                                                    fromPhoneNumber,
                                                    newTTL,
                                                    timestampString
                                            )
                                            .joinToString(";")
                            else ->
                                    listOf(
                                                    message,
                                                    toPhoneNumber,
                                                    messageType,
                                                    fromPhoneNumber,
                                                    newTTL,
                                                    timestampString,
                                                    coordinatesToDart
                                            )
                                            .joinToString(";")
                        }
                runOnUiThread() {
                    if (::channel.isInitialized) {
                        // dart側の 'saveRelayMessage' メソッドを呼び出す
                        channel.invokeMethod("saveRelayMessage", relayData)
                    } else {
                        println("MethodChannelが初期化されていません。")
                    }
                }
            }

            when (messageType) {
                "1" -> { // SNS
                    Log.d("get_message", " [処理]Type 1 (SNS)を受信")
                    displayMessageOnFlutter(dataForFlutter) // Flutter側に表示を依頼

                    if (TTL > 0) {
                        Log.d("get_message", " [処理]Type 1 メッセージを転送")
                        relayMessage(
                                message,
                                toPhoneNumber,
                                messageType,
                                fromPhoneNumber,
                                TTL,
                                timestampString,
                                coordinatesToDart
                        )
                    } else {
                        return
                    }
                }
                "2" -> { // 長距離通信、安否確認
                    if (toPhoneNumber == myPhoneNumber) {
                        Log.d("get_message", " [処理]Type 2 (自分宛)を受信")
                        displayMessageOnFlutter(dataForFlutter) // Flutter側に表示を依頼
                    } else {
                        if (TTL > 0) {
                            Log.d("get_message", " [処理]Type 2 メッセージを転送")
                            relayMessage(
                                    message,
                                    toPhoneNumber,
                                    messageType,
                                    fromPhoneNumber,
                                    TTL,
                                    timestampString,
                                    coordinatesToDart
                            )
                        } else {
                            return
                        }
                    }
                }
                "3" -> { // 自治体への連絡
                    if (isMessenger) {
                        // メッセージを保存する人のアルゴリズム->メッセージを一時保存
                    }
                    if (TTL > 0) {
                        Log.d("get_message", " [処理]Type 3 メッセージを転送")
                        relayMessage(
                                message,
                                toPhoneNumber,
                                messageType,
                                fromPhoneNumber,
                                TTL,
                                timestampString,
                                coordinatesToDart
                        )
                    } else {
                        return
                    }
                }
                "4" -> { // 自治体からの連絡
                    Log.d("get_message", " [処理]Type 4 (自治体)を受信")
                    displayMessageOnFlutter(dataForFlutter) // Flutter側に表示を依頼

                    if (TTL > 0) {
                        Log.d("get_message", " [処理]Type 4 メッセージを転送")
                        relayMessage(
                                message,
                                toPhoneNumber,
                                messageType,
                                fromPhoneNumber,
                                TTL,
                                timestampString,
                                coordinatesToDart
                        )
                    } else {
                        return
                    }
                }
                else -> println(" [不明] メッセージタイプです。内容: $message")
            }
        } catch (e: Exception) {
            Log.d("ERROR", "エラー: $e")
        }
    }
}