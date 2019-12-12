package com.pajamadrive.voiceanalyzer

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.viewpager.widget.ViewPager
import java.lang.Math.pow
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), FragmentCheckListener, Runnable {
    private var record: Record? = null
    private var isRecording = false
    private var visualizer: VisualizerSurfaceView? = null
    private var button: Button? = null
    private var permissionCheck: AccessPermissionCheck? = null
    private var file: WaveFile? = null
    private var storageCheck: ExternalStorageCheck? = null
    private var viewPager: ViewPager? = null
    private var sizeView: TextView? = null
    private var vs: VisualizeSurfaceFragment? = null
    private var df: DisplayFileFragment? = null
    private var fc: FragmentCheck? = null
    private var thread: Thread? = null
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
            if(dirPath != null)
                if (dirPath != "")
                    return dirPath
            return getExternalFilesDir(null)!!.path
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewPager = findViewById(R.id.pageView)
        vs = VisualizeSurfaceFragment()
        df = DisplayFileFragment()
        fc = FragmentCheck(arrayOf(vs!!, df!!))
        fc?.setListener(this)
        viewPager?.setAdapter(FragmetnPagerAdapter(supportFragmentManager, vs!!, df!!))
        thread = Thread(this)
        thread?.start()
    }

    override fun run() {
        while(thread != null) {
            fc?.checkFragment()
        }
    }

    //fragmentの準備ができたら各種準備
    override fun createListener(){
        thread = null
        val surface = vs?.getSurface()
        file = WaveFile()
        storageCheck = ExternalStorageCheck(this)
        permissionCheck = AccessPermissionCheck()
        permissionCheck?.setPermissionExplain(needPermissions, PERMISSION_REQUEST_CODE,
            arrayOf("このアプリは録音を行うのでマイクの許可が必要です．", "このアプリは録音した音声を保存するためにストレージ書き込みの許可が必要です．", "このアプリは音声を再生するためにストレージ読み込みの許可が必要です．"))
        visualizer = VisualizerSurfaceView(this, surface!!)
        button = vs?.getRecordButton()
        sizeView = vs?.getText()
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
        //1回のFFTで使用する標本数
        //4096 / 44100 = 約0.1秒でFFTを1回行う
        private val fftSize = 4096
        //分解能(認識できる最小周波数)
        //44100 / 4096 = 約11hz
        //mid1C以下は正確に計測できる保証はない
        private val resolution = samplingRate.toDouble() / fftSize
        private val dbBaseLine = pow(2.0, 15.0) * fftSize * sqrt(2.0)
        private val INTERVAL = samplingRate / 300
        private val chCount = AudioFormat.CHANNEL_IN_MONO
        private val bitPerSample = AudioFormat.ENCODING_PCM_16BIT
        private val minBufferSize = AudioRecord.getMinBufferSize(samplingRate, chCount, bitPerSample) * 2
        private val sec = 2
        private var buffer = ShortArray(0)
        private var audioRecord: AudioRecord? = null

        init{
            visualizer?.initializeBuffer(samplingRate * sec / INTERVAL)
            buffer = ShortArray(minBufferSize)
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, samplingRate, chCount, bitPerSample, minBufferSize)
            file?.createFile(externalDirectoryPath, fileName, if(chCount == AudioFormat.CHANNEL_IN_MONO) 1 else 2
                , samplingRate, if(bitPerSample == AudioFormat.ENCODING_PCM_16BIT) 16 else 8)
            visualizer?.startDrawSurfaceThreed()
        }

        @SuppressLint("WrongThread")
        override fun doInBackground(vararg params: Void): Void?{
            audioRecord?.startRecording()
            try{
                while(isRecording){
                    //byteArrayとかを使用するとリトルエンディアンのデータ(raw形式)が返ってくるっぽい
                    //shortArrayだと普通にビッグエンディアンで格納される
                    val readSize = audioRecord?.read(buffer, OFFSET, buffer.size) ?: -1
                    if(readSize < 0)
                        break
                    if(readSize == 0)
                        continue
                    file?.addBigEndianData(buffer)

                    val bigEndianDoubleBuffer = buffer.map{it.toDouble()}.toDoubleArray().copyOfRange(0, fftSize)
                    visualizer?.update(buffer.filterIndexed{idx, value -> idx % INTERVAL == 0}.toShortArray(), readSize / INTERVAL)
                    var fft = FFT4g(fftSize)
                    fft.rdft(1, bigEndianDoubleBuffer)
                    val fftData = bigEndianDoubleBuffer.asList().chunked(2)
                        .map { (left, right) -> 20 * log10(sqrt(left.pow(2) + right.pow(2)) / dbBaseLine) }
                    val maxDB = fftData.max()
                    val maxIndex = fftData.indexOf(maxDB)
                    val pitch = VocalRange(maxIndex * resolution)
                    sizeView?.text = (maxIndex * resolution).toString() + "Hz  " + pitch.getOctaveName()
                    sizeView?.setTextColor(pitch.getPitchColor())
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
