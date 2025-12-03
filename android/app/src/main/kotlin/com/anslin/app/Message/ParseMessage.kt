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

    //メッセージを分割して、リストで返す
    fun messageSeparate(receivedString: String) {
        
        try {

            fun displayMessageOnFlutter(datalist: List<String?>) {
                runOnUiThread() {
                    if (::channel.isInitialized) {
                        channel.invokeMethod("displayMessage", datalist)
                    } else {
                        println("MethodChannelが初期化されていません。")
                    }
                }
            }

                val relayData = ""
                runOnUiThread() {
                    if (::channel.isInitialized) {
                        // dart側の 'saveRelayMessage' メソッドを呼び出す
                        channel.invokeMethod("saveRelayMessage", relayData)
                    } else {
                        println("MethodChannelが初期化されていません。")
                    }
                }

        } catch (e: Exception) {
            Log.d("ERROR", "エラー: $e")
        }
    }
}