package com.anslin.app

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.content.Context
import android.content.SharedPreferences

// 保存フォーマット
data class FormatSaveData(
    val message: String,
    val messageType: String,
    val fromPhoneNumber: String,
    val timestamp: String,
    val coordinates: String?
)

fun FormatSaveData.toList(): List<String?> {
    return listOf(
        message,
        messageType,
        fromPhoneNumber,
        timestamp,
        coordinates
    )
}

// message; to_phone_number; message_type; from_phone_number; TTL; coordinates
class MessageFormatFactor(private val context: Context) {

    private val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    // TTL
    companion object {
        private const val DEFAULT_TTL = "150"
    }

    //初期データ構築
    fun buildOriginMessage(
        message: String,
        messageType: String,
        toPhoneNumber: String,
        coordinates: String?
    ): String {
        val phoneNum = getMyPhoneNumber()
        val messageTypeCode = Constants.TYPEMAP[messageType] ?: "0"

        val timeStamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern(Constants.DATEFORMAT))

        return listOf(
            message,
            toPhoneNumber,
            messageTypeCode,
            phoneNum,
            DEFAULT_TTL,
            timeStamp,
            coordinates.takeIf { !it.isNullOrEmpty() }
        ).filterNotNull().joinToString(";")
    }

    // リレーデータ構築
    fun buildRelayMessage(parsed: ParsedMessage): String {
        val newTTL = (parsed.ttl - 1).toString()

        return listOfNotNull(
            parsed.message,
            parsed.toPhoneNumber,
            parsed.messageType,
            parsed.fromPhoneNumber,
            newTTL,
            parsed.timestamp,
            (parsed.coordinates).takeIf { !it.isNullOrEmpty() }
        ).joinToString(";")
    }

    // 保存フォーマット構築
    fun buildSaveFormat(parsed: ParsedMessage): FormatSaveData {
        return FormatSaveData(
            parsed.message,
            parsed.messageType,
            parsed.fromPhoneNumber,
            parsed.timestamp,
            parsed.coordinates
        )
    }

    private fun getMyPhoneNumber():String {
        val q = prefs.getString("flutter.my_phone_number", null)
        return q ?: "00000000000"
    }
}
