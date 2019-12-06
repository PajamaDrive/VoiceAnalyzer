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
import android.content.pm.PackageManager
import android.Manifest.permission
import android.Manifest.permission.*
import androidx.core.content.ContextCompat
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.webkit.PermissionRequest
import androidx.core.app.ActivityCompat



class MainActivity : AppCompatActivity() {
    var record: Record? = null
    var isRecording = false
    var visualizer: VisualizerSurfaceView? = null
    var button: Button? = null
    val OFFSET = 0
    val PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val surface = findViewById(R.id.visualizer) as SurfaceView
        visualizer = VisualizerSurfaceView(this, surface)
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
        visualizer?.stopDrawSurface()
    }

    fun stopRecord(){
        isRecording = false
        button?.text = getString(R.string.button_record_text)
        record?.cancel(true)
    }

    fun startRecord(){
        if (ContextCompat.checkSelfPermission(applicationContext, RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // すでにユーザーがパーミッションを許可
            isRecording = true
            button?.text = getString(R.string.button_stop_text)
            record = Record()
            record?.initRecord()
            record?.execute()
        } else {
            // ユーザーはパーミッションを許可していない
            ActivityCompat.requestPermissions(this, arrayOf(RECORD_AUDIO, WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if(requestCode == PERMISSION_REQUEST_CODE){
            if(grantResults.size > 0 && grantResults.slice(0..(grantResults.size)) == Array(grantResults.size, {PackageManager.PERMISSION_GRANTED})){
                startRecord()
            }
        }
    }

    inner class Record : AsyncTask<Void, DoubleArray, Void>() {
        val samplingRate = 16000
        val chCount = AudioFormat.CHANNEL_IN_MONO
        val bitPerSample = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(
            samplingRate,
            chCount,
            bitPerSample)
        val sec = 2
        var buffer = ShortArray(0)
        var audioRecord: AudioRecord? = null
        val file: WaveFile = WaveFile()

        fun initRecord(){
            visualizer?.initializeBuffer(samplingRate, sec)
            buffer = ShortArray(minBufferSize)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                samplingRate,
                chCount,
                bitPerSample,
                minBufferSize)
            //file.createFile("", chCount as Short, samplingRate, bitPerSample as Short)
        }

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
                    //file.addBigEndianData(buffer)
                    visualizer?.update(buffer, readSize)
                    sizeView.text = minBufferSize.toString()
                }
            }
            finally{
                audioRecord?.stop()
                audioRecord?.release()
               // file.close()
                //Toast.makeText(applicationContext, file.getFileName(), Toast.LENGTH_LONG).show()
            }
            return null
        }
    }
}
