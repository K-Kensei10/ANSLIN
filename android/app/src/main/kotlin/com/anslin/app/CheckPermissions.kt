package com.anslin.app

import android.Manifest
import android.os.Build
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

// ================= パーミッション確認 =================
// 全部許可 = true, 未許可有 = false
object PermissionUtils {
    fun check(context: Context): Boolean{
        val basePermissions =
            listOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
            )

        // 位置情報
        val locationPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Manifest.permission.ACCESS_FINE_LOCATION   // API28 以上
            } else {
                Manifest.permission.ACCESS_COARSE_LOCATION // 以下
            }        
        
        val requiredPermissions = basePermissions + locationPermission

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        
        return missing.isEmpty()
    }
}
