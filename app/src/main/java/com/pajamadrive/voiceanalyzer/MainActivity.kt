package com.pajamadrive.voiceanalyzer

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.SurfaceView
import android.widget.Button
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast
import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    private var record: Record? = null
    private var isRecording = false
    private var visualizer: VisualizerSurfaceView? = null
    private var button: Button? = null
    private var permissionCheck: AccessPermissionCheck? = null
    private var file: WaveFile? = null
    private val OFFSET = 0
    private val PERMISSION_REQUEST_CODE = 1
    private val needPermissions = arrayOf(RECORD_AUDIO, WRITE_EXTERNAL_STORAGE)
    private val isExternalStorageReadable: Boolean
        get(){
            val state = Environment.getExternalStorageState()
            return Environment.MEDIA_MOUNTED == state
        }
    private val isExternalStorageWritable: Boolean
        get(){
            val state = Environment.getExternalStorageState()
            return Environment.MEDIA_MOUNTED_READ_ONLY == state || Environment.MEDIA_MOUNTED == state
        }
    private val externalDirectoryPath: String
        get(){
            return getExternalFilesDir(null)?.path + "/voiceanalyzer"
        }
    private val externalFilePath: String
        get(){
            return externalDirectoryPath + "/" + fileName
        }
    private val fileName: String
        get(){
            return SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Date())
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val surface = findViewById(R.id.visualizer) as SurfaceView
        file = WaveFile()
        visualizer = VisualizerSurfaceView(this, surface)
        permissionCheck = AccessPermissionCheck()
        permissionCheck?.setPermissionExplain(needPermissions, PERMISSION_REQUEST_CODE,
            arrayOf("このアプリは録音を行うのでマイクの許可が必要です．", "このアプリは録音した音声を保存するためにストレージの許可が必要です．"))
        button = findViewById(R.id.button_record) as Button
        button?.setOnClickListener{
            if(isRecording)
                stopRecord()
            else
                startRecord()
        }
    }

    //端末の戻るボタンを押下した時の処理
    override fun onBackPressed(){
        super.onBackPressed()
        stopRecord()
    }

    //アクティビティが非表示になった際の処理
    override fun onPause(){
        super.onPause()
        visualizer?.stopDrawSurfaceThread()
    }

    fun stopRecord(){
        isRecording = false
        button?.text = getString(R.string.button_record_text)
        record?.cancel(true)
        file?.close(applicationContext)
    }

    fun startRecord(){
        // すでにユーザーがパーミッションを許可
        if (permissionCheck?.checkAllPermissions(this) == true){
            isRecording = true
            button?.text = getString(R.string.button_stop_text)
            record = Record()
            record?.initRecord()
            record?.execute()
        }
        // ユーザーはパーミッションを許可していない
        else if(permissionCheck?.checkAllPermissions(this) == false){
            val deniedPermissions = permissionCheck?.getPermissionStringThatStateEqualDENIED() ?: arrayOf()
            ActivityCompat.requestPermissions(this, deniedPermissions, permissionCheck!!.getRequestCode(needPermissions))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

        permissionCheck?.requestPermissionsResult(this, packageName, requestCode, permissions, grantResults)
        val deniedPermissions = permissionCheck?.getPermissionStringThatStateEqualDENIED() ?: arrayOf()
        if(permissionCheck?.containNeverDenied() == true){
            AlertDialog.Builder(this).setTitle("パーミッションエラー").setMessage("\"今後は許可しない\"が選択されました．アプリ情報の許可をチェックしてください．")
                .setPositiveButton("OK", {dialig, which -> openSettings()}).show()
        }
        permissionCheck?.showPermissionRationale(this, deniedPermissions)
        if(deniedPermissions.isNotEmpty()){
            sizeView.text = deniedPermissions[0]
            AlertDialog.Builder(this).setTitle("パーミッションエラー").setMessage("機器にアクセスする許可がないため録音を開始できません．")
                .setPositiveButton("OK", {dialig, which -> }).show()
        }
    }

    fun openSettings(){
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.setData(Uri.fromParts("package", packageName, null))
        startActivity(intent)
    }

    inner class Record : AsyncTask<Void, DoubleArray, Void>() {
        private val samplingRate = 44100
        private val INTERVAL = samplingRate / 500
        private val chCount = AudioFormat.CHANNEL_IN_MONO
        private val bitPerSample = AudioFormat.ENCODING_PCM_16BIT
        private val minBufferSize = AudioRecord.getMinBufferSize(
            samplingRate,
            chCount,
            bitPerSample)
        private val sec = 2
        private var buffer = ShortArray(0)
        private var audioRecord: AudioRecord? = null

        fun initRecord(){
            visualizer?.initializeBuffer(samplingRate * sec / INTERVAL)
            buffer = ShortArray(minBufferSize / 2)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                samplingRate,
                chCount,
                bitPerSample,
                minBufferSize)
            file?.createFile(externalFilePath, chCount.toShort(), samplingRate, bitPerSample.toShort())
            visualizer?.startDrawSurfaceThreed()
        }

        @SuppressLint("WrongThread")
        override fun doInBackground(vararg params: Void): Void?{
            audioRecord?.startRecording()

            try{
                while(isRecording){
                    val readSize = audioRecord?.read(buffer, OFFSET, minBufferSize / 2) ?: -1
                    if(readSize < 0){
                        break
                    }
                    if(readSize == 0){
                        continue
                    }
                    file?.addBigEndianData(buffer)
                    visualizer?.update(buffer.filterIndexed{idx, num -> idx % INTERVAL == 0}.toShortArray(), readSize / INTERVAL)
                    sizeView.text = file?.getFileName()
                }
            }
            finally{
                audioRecord?.stop()
                audioRecord?.release()
                file?.close(applicationContext)
            }
            return null
        }
    }
}
