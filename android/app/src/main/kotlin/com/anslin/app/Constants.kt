package com.anslin.app

class Constants {
    companion object {
        val SERVICE_UUID = "86411acb-96e9-45a1-90f2-e392533ef877"
        val READ_CHARACTERISTIC_UUID = "a3f9c1d2-96e9-45a1-90f2-e392533ef877"
        val WRITE_CHARACTERISTIC_UUID = "7e4b8a90-96e9-45a1-90f2-e392533ef877"
        val NOTIFY_CHARACTERISTIC_UUID = "1d2e3f4a-96e9-45a1-90f2-e392533ef877"

        val RSSI = -90

        val TYPEMAP = mapOf(
        "SNS" to "1",
        "SafetyCheck" to "2",
        "ToLocalGovernment" to "3",
        "FromLocalGovernment" to "4"
        )

        val DATEFORMAT = "yyyyMMddHHmm"
    }
}
