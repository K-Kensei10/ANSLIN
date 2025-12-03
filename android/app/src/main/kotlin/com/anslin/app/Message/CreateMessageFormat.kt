package com.anslin.app

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun createMessageFormat(
    message: String,
    phoneNum: String,
    messageType: String,
    toPhoneNumber: String,
    TTL: String,
    coordinates: String
): String {
    val typeMap = Constants.TYPEMAP
    val messageTypeCode = typeMap[messageType] ?: "0"

    val timeStamp = LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern(Constants.DATEFORMAT))

    return listOf(
        message,
        toPhoneNumber,
        messageTypeCode,
        phoneNum,
        TTL,
        timeStamp,
        coordinates.takeIf { it.isNotEmpty() }
    ).filterNotNull().joinToString(";")
}
