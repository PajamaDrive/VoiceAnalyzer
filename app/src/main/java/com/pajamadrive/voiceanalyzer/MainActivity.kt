package com.pajamadrive.voiceanalyzer

import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.content.Intent
import android.media.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.viewpager.widget.ViewPager
import java.io.File
import java.lang.Math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), FragmentCheckListener, Runnable {
    private var record: Record? = null
    private var isRecording = false
    private var isPlaying = false
    private var pitchVisualizer: PitchVisualizerSurfaceView? = null
    private var decibelVisualizer: DecibelVisualizerSurfaceView? = null
    private var recordButton: ImageButton? = null
    private var recordPauseButton: ImageButton? = null
    private var playButton: ImageButton? = null
    private var fastForwardButton: ImageButton? = null
    private var rewindButton: ImageButton? = null
    private var previousButton: ImageButton? = null
    private var nextButton: ImageButton? = null
    private var repeatButton: ImageButton? = null
    private var shuffleButton: ImageButton? = null
    private var stopButton: ImageButton? = null
    private var musicSeekBar: SeekBar? = null
    private var switch: Switch? = null
    private var pitchText: TextView? = null
    private var titleText: TextView? = null
    private var currentTimetext: TextView? = null
    private var musicLengthtext: TextView? = null
    private var fileCreateMode: Boolean = false
    private var permissionCheck: AccessPermissionCheck? = null
    private var file: WaveFile? = null
    private var storageCheck: ExternalStorageCheck? = null
    private var viewPager: ViewPager? = null
    private var vs: VisualizeSurfaceFragment? = null
    private var df: DisplayFileFragment? = null
    private var fc: FragmentCheck? = null
    private var thread: Thread? = null
    private var fileStringList:Array<String>? = null
    private var listView: ListView? = null
    private var mediaPlayer: MediaPlayer? = null
    private val PERMISSION_REQUEST_CODE = 1
    private val needPermissions = arrayOf(RECORD_AUDIO, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE)
    private val fileName: String
        get(){
            //外部ストレージではファイル名にコロンが使えないらしくそれで数時間手間取った
            return SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
        }
    private val externalDirectoryPath: String
        get(){
            val dirPath = storageCheck?.getExternalStorageBaseDir()
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
        viewPager?.adapter = ExtendFragmentPagerAdapter(supportFragmentManager, arrayOf(vs!!, df!!))
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
        pitchVisualizer = PitchVisualizerSurfaceView(this, surface!!)
        val decSurface = vs?.getDecSurface()
        decibelVisualizer = DecibelVisualizerSurfaceView(this, decSurface!!)
        setRecordFrame()
        listView = df?.getListView()
        fileStringList = File(externalDirectoryPath).listFiles().map{it.name}.toTypedArray()
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, fileStringList!!)
        listView?.adapter = adapter
        var clickFile: String? = null
        listView?.setOnItemClickListener{
            adapterView, view, position, id ->
            mediaPlayer?.stop()
            clickFile = externalDirectoryPath + "/" + fileStringList!![position]
            mediaPlayer = MediaPlayer()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer?.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            }
            else{
                mediaPlayer?.setAudioStreamType(AudioAttributes.CONTENT_TYPE_MUSIC)
            }
            val uri = Uri.fromFile(File(clickFile))
            mediaPlayer?.setDataSource(this, uri)
            mediaPlayer?.prepare()
            mediaPlayer?.start()
            val layout = findViewById(R.id.controlFrame) as LinearLayout
            layout.removeAllViews()
            layoutInflater.inflate(R.layout.play_frame, layout)
            setPlayFrame()
            titleText?.text = fileStringList!![position]
        }
        viewPager?.addOnPageChangeListener(object: ViewPager.SimpleOnPageChangeListener(){
            override fun onPageSelected(position: Int) {
                val layout = findViewById(R.id.controlFrame) as LinearLayout
                /*
                when{
                    position == 0 && !isPlaying && !isRecording ->{
                        layout.removeAllViews()
                        layoutInflater.inflate(R.layout.record_frame, layout)
                        setRecordFrame()
                    }
                    position == 1 && !isPlaying && !isRecording ->{
                        layout.removeAllViews()
                        layoutInflater.inflate(R.layout.play_frame, layout)
                        setPlayFrame()
                    }
                }

                 */
            }
        })
    }

    fun setRecordFrame(){
        recordButton = findViewById(R.id.recordStartButton)
        recordPauseButton = findViewById(R.id.recordPauseButton)
        switch = findViewById(R.id.recordableSwitch)
        pitchText = findViewById(R.id.pitchText)
        titleText = findViewById(R.id.musicTitleText)
        recordButton?.setOnClickListener{
            if(isRecording)
                stopRecord()
            else
                startRecord()
        }
        recordPauseButton?.setOnClickListener{
            if(isRecording)
                pauseRecord()
            else
                unpauseRecord()
        }
        switch?.setOnCheckedChangeListener{ _, isChecked ->
            if(isChecked){
                fileCreateMode = true
            }
            else{
                fileCreateMode = false
            }
        }
    }

    fun setPlayFrame(){
        playButton = findViewById(R.id.playButton)
        fastForwardButton = findViewById(R.id.fastForwardButton)
        rewindButton = findViewById(R.id.rewindButton)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        repeatButton = findViewById(R.id.repeatButton)
        shuffleButton = findViewById(R.id.shuffleButton)
        stopButton = findViewById(R.id.musicStopButton)
        musicSeekBar = findViewById(R.id.musicPlaySeekBar)
        pitchText = findViewById(R.id.pitchText)
        titleText = findViewById(R.id.musicTitleText)
        stopButton?.setOnClickListener {
            val layout = findViewById(R.id.controlFrame) as LinearLayout
            layout.removeAllViews()
            layoutInflater.inflate(R.layout.record_frame, layout)
            setRecordFrame()
            mediaPlayer?.stop()
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
        pitchVisualizer?.stopDrawSurfaceThread()
        decibelVisualizer?.stopDrawSurfaceThread()
    }

    fun stopRecord(){
        isRecording = false
        recordButton?.setImageResource(R.drawable.record_icon)
        recordPauseButton?.setImageResource(android.R.drawable.ic_media_pause)
        recordPauseButton?.visibility = View.INVISIBLE
        switch?.visibility = View.VISIBLE
        record?.cancel(true)
        titleText?.text = getString(R.string.emptyString)
        if(fileCreateMode)
            file?.close(applicationContext)
    }

    fun startRecord(){
        // すでにユーザーがパーミッションを許可
        if (permissionCheck?.checkAllPermissions(this) == true){
            isRecording = true
            //button?.text = getString(R.string.button_stop_text)
            record = Record()
            record?.execute()
            recordButton?.setImageResource(R.drawable.stop_icon)
            recordPauseButton?.visibility = View.VISIBLE
            switch?.visibility = View.INVISIBLE
            Log.d("debug", viewPager!!.currentItem.toString())
        }
        // ユーザーはパーミッションを許可していない
        else if(permissionCheck?.checkAllPermissions(this) == false){
            val deniedPermissions = permissionCheck?.getPermissionStringThatStateEqualDENIED() ?: arrayOf()
            ActivityCompat.requestPermissions(this, deniedPermissions, permissionCheck!!.getRequestCode(needPermissions))
        }
    }

    fun pauseRecord(){
        isRecording = false
        recordPauseButton?.setImageResource(android.R.drawable.ic_btn_speak_now)
        record?.cancel(true)
    }

    fun unpauseRecord(){
        isRecording = true
        recordPauseButton?.setImageResource(android.R.drawable.ic_media_pause)
        record = Record()
        record?.execute()
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
        private val handler = Handler(Looper.getMainLooper())

        init{
            pitchVisualizer?.initializeBuffer(samplingRate / fftSize * sec)
            decibelVisualizer?.initializeBuffer(fftSize / 2)
            buffer = ShortArray(minBufferSize)
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, samplingRate, chCount, bitPerSample, minBufferSize)
            if(fileCreateMode) {
                file?.createFile(externalDirectoryPath, fileName, if (chCount == AudioFormat.CHANNEL_IN_MONO) 1 else 2,
                    samplingRate, if (bitPerSample == AudioFormat.ENCODING_PCM_16BIT) 16 else 8
                )
                titleText?.text = fileName
            }
            pitchVisualizer?.startDrawSurfaceThreed()
            decibelVisualizer?.startDrawSurfaceThreed()
        }

        @SuppressLint("WrongThread")
        override fun doInBackground(vararg params: Void): Void?{
            audioRecord?.startRecording()
            try{
                while(isRecording){
                    //byteArrayとかを使用するとリトルエンディアンのデータ(raw形式)が返ってくるっぽい
                    //shortArrayだと普通にビッグエンディアンで格納される
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if(readSize < 0)
                        break
                    if(readSize == 0)
                        continue
                    if(fileCreateMode)
                        file?.addBigEndianData(buffer)

                    //val bigEndianDoubleBuffer = buffer.map{it.toDouble()}.toDoubleArray().copyOfRange(0, fftSize)
                    //visualizer?.update(buffer.filterIndexed{idx, value -> idx % INTERVAL == 0}.toShortArray(), readSize / INTERVAL)
                    val fft = FFT(samplingRate, buffer.map{it.toDouble()}.toDoubleArray())
                    fft.execute()
                    handler.post { ->
                        pitchText?.text = fft.getPitch().getOctaveName()
                        pitchText?.setTextColor(fft.getPitch().getPitchColor())
                    }
                    pitchVisualizer?.update(fft.getPitch())
                    decibelVisualizer?.update(fft.getFFTData(), fftSize / 2)
/*
                    var fft = FFT4g(fftSize)
                    fft.rdft(1, bigEndianDoubleBuffer)
                    val fftData = bigEndianDoubleBuffer.asList().chunked(2)
                        .map { (left, right) -> 20 * log10(sqrt(left.pow(2) + right.pow(2)) / dbBaseLine) }
                    val maxDB = fftData.max()
                    val maxIndex = fftData.indexOf(maxDB)
                    val pitch = VocalRange(maxIndex * resolution)

                    pitchText?.text = (maxIndex * resolution).toString() + "Hz  " + pitch.getOctaveName()
                    pitchText?.setTextColor(pitch.getPitchColor())
                    visualizer?.update(DoubleArray((readSize / INTERVAL),{pitch.getPitchFrequency()}), readSize / INTERVAL)
                    decibelVisualizer?.update(fftData.toDoubleArray(), fftSize / 2)

 */
                }
            }
            finally{
                audioRecord?.stop()
                audioRecord?.release()
                if(fileCreateMode)
                    file?.close(applicationContext)
            }
            return null
        }
    }
}
