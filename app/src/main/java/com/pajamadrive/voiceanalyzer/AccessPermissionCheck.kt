package com.pajamadrive.voiceanalyzer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class AccessPermissionCheck{
    private var permissions: Array<String> = arrayOf()
    private var permissionState: MutableMap<String, PermissionState> = mutableMapOf()
    private var permissionRequestCode: MutableMap<String, Int> = mutableMapOf()
    private var permissionExplain: MutableMap<String, String> = mutableMapOf()

    fun setPermission(_permissions: Array<String>, requestCode: Int){
        for(permission in _permissions) {
            permissions += permission
            permissionRequestCode[permission] = requestCode
        }
    }

    fun setPermissionExplain(_permissions: Array<String>, requestCode: Int, explains: Array<String>){
        for(index in 0..(_permissions.size - 1)) {
            permissions += _permissions[index]
            permissionRequestCode[_permissions[index]] = requestCode
            permissionExplain[_permissions[index]] = explains[index]
        }
    }

    fun checkAllPermissions(context: Context): Boolean{
        var result = true
        for(permission in permissions) {
            // ユーザーはパーミッションを許可していない
            if (checkPermission(context, permission) == false)
                result = false
        }
        return result
    }

    fun checkPermission(context: Context, permission: String): Boolean{
        // ユーザはパーミッションを許可している
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            permissionState[permission] = PermissionState.GRANTED
            return true
        }
        //ユーザはパーミッションを許可していない
        else {
            permissionState[permission] = PermissionState.DENIED
            return false
        }
    }

    fun showPermissionRationale(context: Context, _permissions: Array<String>){
        for(permission in _permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, permission)) {
                AlertDialog.Builder(context).setTitle("パーミッションの説明").setMessage(permissionExplain[permission])
                    .setPositiveButton("OK", { dialig, which -> }).show()
            }
        }
    }

    fun requestPermissionsResult(context: Context, packageName: String, requestCode: Int, _permissions: Array<out String>, grantResults: IntArray){
        for(permission in _permissions) {
            if (requestCode == permissionRequestCode[permission]) {
                if (grantResults.isNotEmpty() && permissions.indexOf(permission) != -1) {
                    //パーミッションが許可されていない
                    if (grantResults[_permissions.indexOf(permission)] != PackageManager.PERMISSION_GRANTED) {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, permission)){
                            permissionState[permission] = PermissionState.DENIED
                        }
                        //今後も許可しない場合
                        else{
                            permissionState[permission] = PermissionState.NEVER_DENIED
                        }
                        continue
                    }
                    //パーミッションが許可されている
                    permissionState[permission] = PermissionState.GRANTED
                    continue
                }
                //結果が空またはパーミッションがリスト内になかった
                permissionState[permission] = PermissionState.DENIED
                continue
            }
            //リクエストコードが違う
            permissionState[permission] = PermissionState.NEVER_DENIED
        }
    }

    fun getPermissionString(): Array<String>{
        return permissions
    }

    fun getPermissionState(permission: String): PermissionState{
        return permissionState?.get(permission) ?: PermissionState.UNKNOWN
    }

    fun getPermissionStringThatStateEqualDENIED(): Array<String>{
        return permissions.filter{permissionState[it] == PermissionState.DENIED}.toTypedArray()
    }

    fun getRequestCode(_permissions: Array<String>): Int{
        val requestCode = permissionRequestCode[_permissions[0]]
        for(index in 1..(_permissions.size - 1)){
            if(permissionRequestCode[_permissions[index]] != requestCode)
                return -1
        }
        return requestCode!!
    }

    fun containNeverDenied(): Boolean{
        return permissionState.filter {  it.value == PermissionState.NEVER_DENIED}.isNotEmpty()
    }

    enum class PermissionState{
        GRANTED,
        DENIED,
        NEVER_DENIED,
        UNKNOWN
    }
}