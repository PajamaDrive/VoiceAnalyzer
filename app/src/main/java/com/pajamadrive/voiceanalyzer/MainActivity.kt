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
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.lang.Math.pow
import kotlin.collections.ArrayList
import kotlin.collections.RandomAccess
import kotlin.experimental.and
import kotlin.math.*

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
    private var currentFilePosition: Int = -1
    private var isShuffle: Boolean = false
    private var isLoop: Boolean = false
    private var storageCheck: ExternalStorageCheck? = null
    private var viewPager: ViewPager? = null
    private var vs: VisualizeSurfaceFragment? = null
    private var df: DisplayFileFragment? = null
    private var fc: FragmentCheck? = null
    private var thread: Thread? = null
    private var fileStringList: MutableList<String>? = null
    private var listView: ListView? = null
    private var frequencyText1: TextView? = null
    private var frequencyText2: TextView? = null
    private var frequencyText3: TextView? = null
    private var frequencyText4: TextView? = null
    private var audioPlayer: AudioPlayer? = null
    private var audioPlayerConstant: ArrayList<Int> = arrayListOf()
    private var viewContainer: ArrayList<View> = arrayListOf()
    private var minFrequency = 50
    private var maxFrequency = 3000
    private val sec = 2
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
        file = WaveFile()
        storageCheck = ExternalStorageCheck(this)
        permissionCheck = AccessPermissionCheck()
        permissionCheck?.setPermissionExplain(needPermissions, PERMISSION_REQUEST_CODE,
            arrayOf("このアプリは録音を行うのでマイクの許可が必要です．", "このアプリは録音した音声を保存するためにストレージ書き込みの許可が必要です．", "このアプリは音声を再生するためにストレージ読み込みの許可が必要です．"))
        val surface = vs?.getSurface()
        val decSurface = vs?.getDecSurface()
        pitchVisualizer = PitchVisualizerSurfaceView(this, surface!!)
        decibelVisualizer = DecibelVisualizerSurfaceView(this, decSurface!!)
        //setPlayFrame()
        frequencyText1 = vs?.getFrequencyText1()
        frequencyText2 = vs?.getFrequencyText2()
        frequencyText3 = vs?.getFrequencyText3()
        frequencyText4 = vs?.getFrequencyText4()
        frequencyText1?.text = minFrequency.toString() + "Hz"
        frequencyText4?.text = maxFrequency.toString() + "Hz"
        val logInterval = (log10(maxFrequency.toDouble()) - log10(minFrequency.toDouble())) / 3
        frequencyText2?.text = pow(10.0, logInterval + log10(minFrequency.toDouble())).toInt().toString() + "Hz"
        frequencyText3?.text = pow(10.0, logInterval * 2 + log10(minFrequency.toDouble())).toInt().toString() + "Hz"
        setRecordFrame()

        listView = df?.getListView()
        fileStringList = File(externalDirectoryPath).listFiles().map{it.name}.toMutableList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, fileStringList!!)
        //別スレッドからUIを操作するとエラーが出るのでhandlerを使用してメインスレッドでviewの更新を行う
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            listView?.adapter = adapter
            listView?.setOnItemClickListener { adapterView, view, position, id ->
                val isRecodeFrame = if (audioPlayer == null) true else false
                if (isRecodeFrame) {
                    val layout = findViewById(R.id.controlFrame) as LinearLayout
                    layout.removeAllViews()
                    layoutInflater.inflate(R.layout.play_frame, layout)
                    setPlayFrame()
                }
                if (audioPlayer?.isPlaying() == true) {
                    audioPlayer?.stopAudio()
                }
                currentFilePosition = position
                //val clickFileString = externalDirectoryPath + "/" + fileStringList!![currentFilePosition]
                startAudio()

                setMusicDetail()
            }
        }
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
        currentTimetext = findViewById(R.id.musicCurrentTimeText)
        musicLengthtext = findViewById(R.id.musicWholeTimeText)
        stopButton?.setOnClickListener {
            val layout = findViewById(R.id.controlFrame) as LinearLayout
            layout.removeAllViews()
            layoutInflater.inflate(R.layout.record_frame, layout)
            setRecordFrame()
            audioPlayer?.stopAudio()
            audioPlayer = null
        }
        playButton?.setOnClickListener {
            if(audioPlayer?.isPlaying() == true) {
                playButton?.setImageResource(android.R.drawable.ic_media_play)
                audioPlayer?.pauseAudio()
            }
            else{
                playButton?.setImageResource(android.R.drawable.ic_media_pause)
                audioPlayer?.unPauseAudio()
            }
        }
        fastForwardButton?.setOnClickListener {
            audioPlayer?.fastForwardAudio(2)
        }
        rewindButton?.setOnClickListener {
            audioPlayer?.rewindAudio(2)
        }
        previousButton?.setOnClickListener {
            if(audioPlayer?.isPlaying() == true) {
                audioPlayer?.stopAudio()
            }
            currentFilePosition = max(currentFilePosition - 1, 0)
            //val clickFileString = externalDirectoryPath + "/" + fileStringList!![currentFilePosition]
            startAudio()
            titleText?.text = fileStringList!![currentFilePosition]
        }
        nextButton?.setOnClickListener {
            if(audioPlayer?.isPlaying() == true) {
                audioPlayer?.stopAudio()
            }
            currentFilePosition = min(currentFilePosition + 1, fileStringList!!.size - 1)
            //val clickFileString = externalDirectoryPath + "/" + fileStringList!![currentFilePosition]
            startAudio()
            titleText?.text = fileStringList!![currentFilePosition]
        }
        repeatButton?.setOnClickListener {
            if(isLoop == false){
                isLoop = true
                audioPlayer?.convertIsLoop()
                repeatButton?.setBackgroundColor(resources.getColor(R.color.availableColor))
                if(isShuffle == true){
                    isShuffle = false
                    audioPlayer?.convertIsShuffle()
                    shuffleButton?.setBackgroundColor(resources.getColor(R.color.defaultColor))
                }
            }
            else{
                isLoop = false
                audioPlayer?.convertIsLoop()
                repeatButton?.setBackgroundColor(resources.getColor(R.color.defaultColor))
            }
        }
        shuffleButton?.setOnClickListener {
            if(isShuffle == false){
                isShuffle = true
                audioPlayer?.convertIsShuffle()
                shuffleButton?.setBackgroundColor(resources.getColor(R.color.availableColor))
                if(isLoop == true){
                    isLoop = false
                    audioPlayer?.convertIsLoop()
                    repeatButton?.setBackgroundColor(resources.getColor(R.color.defaultColor))
                }
            }
            else{
                isShuffle = false
                audioPlayer?.convertIsShuffle()
                shuffleButton?.setBackgroundColor(resources.getColor(R.color.defaultColor))
            }
        }
    }

    fun startAudio(){
        viewContainer.clear()
        viewContainer.add(pitchVisualizer!! as View)
        viewContainer.add(decibelVisualizer!! as View)
        viewContainer.add(pitchText!! as View)
        viewContainer.add(playButton!! as View)
        viewContainer.add(titleText!! as View)
        audioPlayerConstant.clear()
        audioPlayerConstant.add(currentFilePosition)
        audioPlayerConstant.add(minFrequency)
        audioPlayerConstant.add(maxFrequency)
        audioPlayerConstant.add(sec)
        audioPlayerConstant.add(isShuffle.compareTo(true))
        audioPlayerConstant.add(isLoop.compareTo(true))
        audioPlayer = AudioPlayer(externalDirectoryPath, fileStringList!!.toTypedArray(), audioPlayerConstant, viewContainer)
        audioPlayer?.startAudio()
    }

    fun setMusicDetail(){
        /*
        musicSeekBar?.progress = 0
        musicSeekBar?.max = audioPlayer!!.getMusicSec()
        musicSeekBar?.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            val isPlay = audioPlayer?.isPlaying()
            var position = 0
            override fun onStartTrackingTouch(p0: SeekBar?) {
                audioPlayer?.pauseAudio()
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                position = progress
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                audioPlayer?.setCurrentPosition(position)
                audioPlayer?.unPauseAudio()
            }
        })

         */
        titleText?.text = fileStringList!![currentFilePosition]
        musicLengthtext?.text = audioPlayer?.getMusicSec().toString()
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
        private val chCount = AudioFormat.CHANNEL_IN_MONO
        private val bitPerSample = AudioFormat.ENCODING_PCM_16BIT
        private val minBufferSize = AudioRecord.getMinBufferSize(samplingRate, chCount, bitPerSample) * 2
        //1回のFFTで使用する標本数
        //4096 / 44100 = 約0.1秒でFFTを1回行う
        private val fftSize = pow(2.0, floor(log2(minBufferSize.toDouble()))).toInt()
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

                    val fft = FFT(samplingRate, fftSize, minFrequency, maxFrequency, buffer.map{it.toDouble()}.toDoubleArray())
                    handler.post {
                        pitchText?.text = fft.getPitch().getOctaveName()
                        pitchText?.setTextColor(fft.getPitch().getPitchColor())
                    }
                    pitchVisualizer?.update(fft.getPitch())
                    decibelVisualizer?.update(fft.getFFTData(), fft.getFFTData().size)
                }
            }
            finally{
                audioRecord?.stop()
                audioRecord?.release()
                if(fileCreateMode) {
                    file?.close(applicationContext)
                    handler.post {
                        fileStringList!!.add(file!!.getFileName())
                        (listView?.adapter as ArrayAdapter<String>).notifyDataSetChanged()
                    }
                }
            }
            return null
        }
    }
}
