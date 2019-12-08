package com.pajamadrive.voiceanalyzer

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.SurfaceView
import android.widget.Button
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
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
    private var storageCheck: ExternalStorageCheck? = null
    private val OFFSET = 0
    private val PERMISSION_REQUEST_CODE = 1
    private val needPermissions = arrayOf(RECORD_AUDIO, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE)
    private val fileName: String
        get(){
            //外部ストレージではファイル名にコロンが使えないらしくそれで数時間手間取った
            return SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
        }
    private val externalDirectoryPath: String
        get(){
            val dirPath = storageCheck?.getExternalStoragePath()
            if(dirPath != null) {
                if (dirPath != "")
                    return dirPath
            }
            return getExternalFilesDir(null)!!.path
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val surface = findViewById(R.id.visualizer) as SurfaceView
        file = WaveFile()
        storageCheck = ExternalStorageCheck(this)
        visualizer = VisualizerSurfaceView(this, surface)
        permissionCheck = AccessPermissionCheck()
        permissionCheck?.setPermissionExplain(needPermissions, PERMISSION_REQUEST_CODE,
            arrayOf("このアプリは録音を行うのでマイクの許可が必要です．", "このアプリは録音した音声を保存するためにストレージ書き込みの許可が必要です．", "このアプリは音声を再生するためにストレージ読み込みの許可が必要です．"))
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
        permissionCheck?.requestPermissionsResult(this, {startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", packageName, null)))}
            , {->}, packageName, requestCode, permissions, grantResults)
    }

    inner class Record : AsyncTask<Void, DoubleArray, Void>() {
        private val samplingRate = 44100
        private val fftSize = 4096
        private val resolution = samplingRate.toDouble() / fftSize
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
            file?.createFile(externalDirectoryPath, fileName, if(chCount == AudioFormat.CHANNEL_IN_MONO) 1 else 2
                , samplingRate, if(bitPerSample == AudioFormat.ENCODING_PCM_16BIT) 16 else 8)
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
                    var fft = FFT4g(fftSize)
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
