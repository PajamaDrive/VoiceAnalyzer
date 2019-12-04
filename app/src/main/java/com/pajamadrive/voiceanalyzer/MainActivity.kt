package com.pajamadrive.voiceanalyzer

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.SurfaceView
import android.widget.Button
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    var record: Record? = null
    var isRecording = false
    var visualizer: VisualizerSurfaceView? = null
    var button: Button? = null
    val OFFSET = 0

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

    override fun onPause(){
        super.onPause()
        stopRecord()
    }

    fun stopRecord(){
        isRecording = false
        button?.text = getString(R.string.button_record_text)
        record?.cancel(true)
    }

    fun startRecord(){
        isRecording = true
        button?.text = getString(R.string.button_stop_text)
        record = Record()
        record?.execute()
        visualizer?.initializeBuffer()
    }

    inner class Record : AsyncTask<Void, DoubleArray, Void>() {
        override fun doInBackground(vararg params: Void): Void?{
            val sampleRate = 16000
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT) * 2

            if (minBufferSize < 0){
                return null
            }

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize)

            val sec = 10
            val buffer: ShortArray = ShortArray(minBufferSize)

            audioRecord.startRecording()

            try{
                while(isRecording){
                    val readSize = audioRecord.read(buffer, OFFSET, minBufferSize)
                    if(readSize < 0){
                        break
                    }
                    if(readSize == 0){
                        continue
                    }
                    visualizer?.update(buffer, readSize, sec)
                    sizeView.text = minBufferSize.toString()
                }
            }
            finally{
                audioRecord.stop()
                audioRecord.release()
            }

            return null
        }
    }
}
