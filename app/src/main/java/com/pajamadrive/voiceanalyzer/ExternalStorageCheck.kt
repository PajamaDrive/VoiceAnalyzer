package com.pajamadrive.voiceanalyzer

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class ExternalStorageCheck(val context: Context){

    fun getExternalStorageBaseDir(): String{
        var sdCardPath: MutableList<String> = mutableListOf()
        if(context == null)
            return ""
        //Andriod5.0以上はisExternalStorageRemovableが使用可能
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for(file in context.getExternalFilesDirs(null)){
                //SDカード
                if(Environment.isExternalStorageRemovable(file)){
                    if(!sdCardPath.contains(file.path))
                        sdCardPath.add(file.path)
                }
            }
        }
        //Android4.2~4.4はisExternalStorageRemovableが使用できない
        else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1){
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            //privateメソッドはgetDeclearMethodとinvokeを使って実行する
            val getVolumeFunction = sm.javaClass.getDeclaredMethod("getVolumeList")
            val volumeList = getVolumeFunction?.invoke(sm) as MutableList<Object>
            for(volume in volumeList){
                //volumeのパスを取得
                val getPathFileFunction = volume?.javaClass.getDeclaredMethod("getPathFile")
                val file = getPathFileFunction?.invoke(volume) as File
                val storagePath = file?.absolutePath
                //取り外し可能か調べる
                val isRemovableFunction = volume?.javaClass.getDeclaredMethod("isRemovable")
                val isRemovable = isRemovableFunction?.invoke(volume) as Boolean
                if(isRemovable == true){
                    //マウントされているか調べる
                    //機種によっては/mnt/privateなどが含まれる場合があるらしい
                    if(isMountedPath(storagePath) == true){
                        if(!sdCardPath.contains(storagePath))
                            sdCardPath.add(storagePath + "/Android/data/" + context.packageName + "/files")
                    }
                }
            }
            //Android4.4はmkdirsでfilesディレクトリを作成できないっぽいのでfilesディレクトリを作成する必要があるらしい
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                context.getExternalFilesDirs(null);
        }
        //Android4.2以下は知らない
        else{
            sdCardPath.add("")
        }
        return sdCardPath[0]
    }

    fun isMountedPath(filePath: String): Boolean{
        var isMounted = false
        var br: BufferedReader? = null
        val file = File("/proc/mounts")
        //このファイルがない場合はマウントされていない
        if(!file.exists())
            return isMounted
        try {
            br = BufferedReader(FileReader(file))
            var line: String?
            do{
                line = br?.readLine()
                if(line == null)
                    break
                if(line.contains(filePath)){
                    isMounted = true
                    break
                }
            }while(true)
        }
        finally{
            br?.close()
        }
        return isMounted
    }
}