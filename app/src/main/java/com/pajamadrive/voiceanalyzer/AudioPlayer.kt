package com.pajamadrive.voiceanalyzer

import android.content.Context
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import kotlin.experimental.and
import kotlin.math.*
import kotlin.random.Random

class AudioPlayer(private val dirPath: String, private val fileNameList: Array<String>, private val constantContainer: ArrayList<Int>,
                  private val viewContainer: ArrayList<View>): Runnable{
    private var file: File? = null
    private var byteData = ByteArray(0)
    private var doubleData = DoubleArray(0)
    private var bufSize: Int = 0
    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null
    private var samplingRate: Int = 0
    private var chCount: Int = 0
    private var bitPerSample: Int = 0
    private var musicSec : Int = 0
    private var currentTimePosition: Int = 0
    private var minBufferSize: Int = 0
    private var fftSize: Int = 0
    private var currentFilePosition = constantContainer.get(0)
    private val minFrequency = constantContainer.get(1)
    private val maxFrequency = constantContainer.get(2)
    private val sec = constantContainer.get(3)
    private var isShuffle = if(constantContainer.get(4) == 0) true else false
    private var isLoop = if(constantContainer.get(5) == 0) true else false
    private val pitchVisualizer = viewContainer.get(0) as PitchVisualizerSurfaceView
    private val decibelVisualizer = viewContainer.get(1) as DecibelVisualizerSurfaceView
    private val pitchText = viewContainer.get(2) as TextView
    private val playButton = viewContainer.get(3) as ImageButton
    private val titleText = viewContainer.get(4) as TextView
    private val handler = Handler(Looper.getMainLooper())

    init{
        initialize()
    }

    fun byteArrayToInt(byteArray: ByteArray): Int {
        var result: Int = 0
        for (index in 0..3) {
            result = result shl 8
            result = result or (byteArray[index].toUByte() and 0xFF.toUByte()).toInt()
        }
        return result
    }

    fun byteArrayToShort(byteArray: ByteArray): Short {
        var result: Int = 0
        for (index in 0..1) {
            result = result shl 8
            result = result or (byteArray[index].toUByte() and 0xFF.toUByte()).toInt()
        }
        return result.toShort()
    }

    fun byteArrayToDoubleArray(byteArray: ByteArray): DoubleArray {
        var result = arrayListOf<Double>()
        for (index in 0..(byteArray.size / 2 - 1)) {
            result.add(byteArrayToShort(convertEndian(byteArray.copyOfRange(index * 2, (index + 1) * 2))).toDouble())
        }
        return result.toDoubleArray()
    }

    override fun run() {
        if (audioTrack != null) {
            audioTrack?.play()
            GlobalScope.launch {
                var offset = currentTimePosition / 2
                while (audioThread != null) {
                    if (fftSize < doubleData.size - offset) {
                        val fft = FFT(samplingRate, fftSize, minFrequency, maxFrequency, doubleData.copyOfRange(offset, offset + fftSize))
                        handler.post {
                            pitchText.text = fft.getPitch().getOctaveName()
                            pitchText.setTextColor(fft.getPitch().getPitchColor())
                        }
                        pitchVisualizer.update(fft.getPitch())
                        decibelVisualizer.update(fft.getFFTData(), fft.getFFTData().size)
                    }
                    Thread.sleep((fftSize.toDouble() / samplingRate * 1000 * 2).toLong())
                    offset += (fftSize.toDouble() / samplingRate * 2 * samplingRate * (bitPerSample / 8) * chCount / 2).toInt()
                }
            }
            audioTrack?.setNotificationMarkerPosition(doubleData.size - currentTimePosition / 2)
            audioTrack?.write(byteData, WaveFile.getHeaderSize() + currentTimePosition, byteData.size - WaveFile.getHeaderSize() - currentTimePosition)
        }
    }

    fun initialize(){
        val chCountBuffer = ByteArray(2)
        val samplingRateBuffer = ByteArray(4)
        val bitPerSampleBuffer = ByteArray(2)
        val musicSizeBuffer = ByteArray(4)
        val clickFile = RandomAccessFile(dirPath + "/" + fileNameList[currentFilePosition], "r")
        clickFile.seek(WaveFile.getChCountPosition())
        clickFile.read(chCountBuffer)
        clickFile.seek(WaveFile.getSamplingRatePosition())
        clickFile.read(samplingRateBuffer)
        clickFile.seek(WaveFile.getBitPerSamplePosition())
        clickFile.read(bitPerSampleBuffer)
        clickFile.seek(WaveFile.getDataSizePosition())
        clickFile.read(musicSizeBuffer)
        chCount = byteArrayToShort(convertEndian(chCountBuffer)).toInt()
        samplingRate = byteArrayToInt(convertEndian(samplingRateBuffer))
        bitPerSample = byteArrayToShort(convertEndian(bitPerSampleBuffer)).toInt()
        minBufferSize = AudioRecord.getMinBufferSize(samplingRate, if(chCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
            , if(bitPerSample == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT) * 2
        fftSize = Math.pow(2.0, floor(log2(minBufferSize.toDouble()))).toInt()
        musicSec = byteArrayToInt(convertEndian(musicSizeBuffer))
        file = File(dirPath + "/" + fileNameList[currentFilePosition])
        byteData = ByteArray(file!!.length().toInt())
        var inputStream: FileInputStream? = null
        try{
            inputStream = FileInputStream(file)
            inputStream?.read(byteData)
        }
        finally{
            inputStream?.close()
        }
        doubleData = byteArrayToDoubleArray(byteData.copyOfRange(WaveFile.getHeaderSize(), byteData.size))
        pitchVisualizer?.initializeBuffer(samplingRate / fftSize * sec)
        decibelVisualizer?.initializeBuffer(fftSize / 2)
        bufSize = android.media.AudioTrack.getMinBufferSize(samplingRate, if(chCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO, if(bitPerSample == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(if(bitPerSample == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT).setSampleRate(samplingRate)
                    .setChannelMask(if(chCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(bufSize).build()
        }
        else{
            audioTrack = AudioTrack(AudioManager.STREAM_MUSIC, samplingRate, chCount, bitPerSample, bufSize, AudioTrack.MODE_STREAM)
        }

        audioTrack?.setPlaybackPositionUpdateListener(object: AudioTrack.OnPlaybackPositionUpdateListener{
            override fun onPeriodicNotification(track: AudioTrack?) {
            }

            override fun onMarkerReached(track: AudioTrack?) {
                if(track!!.playState == AudioTrack.PLAYSTATE_PLAYING){
                    handler.post{
                        playButton?.setImageResource(android.R.drawable.ic_media_play)
                    }
                    stopAudio()
                    if(isLoop)
                        startAudio()
                    if(isShuffle){
                        currentFilePosition = Random.nextInt(fileNameList.size)
                        initialize()
                        startAudio()
                    }
                }
            }
        })

    }

    fun stopAudio(){
        audioThread = null
        audioTrack?.stop()
        currentTimePosition = 0
        //audioTrack?.release()
    }

    fun startAudio(){
        if(audioThread == null){
            pitchVisualizer?.initializeBuffer(samplingRate / fftSize * sec)
            decibelVisualizer?.initializeBuffer(fftSize / 2)
            handler.post{
                playButton?.setImageResource(android.R.drawable.ic_media_pause)
                titleText.text = fileNameList[currentFilePosition]
            }
            audioThread = Thread(this)
            audioThread?.start()
        }
    }

    fun pauseAudio(){
        currentTimePosition += audioTrack!!.playbackHeadPosition * (bitPerSample / 8) * chCount
        audioTrack?.stop()
        audioTrack?.reloadStaticData()
        audioThread = null
    }

    fun unPauseAudio(){
        if(audioThread == null){
            pitchVisualizer?.initializeBuffer(samplingRate / fftSize * sec)
            decibelVisualizer?.initializeBuffer(fftSize / 2)
            audioThread = Thread(this)
            audioThread?.start()
        }
    }

    fun fastForwardAudio(sec: Int){
        val unPauseFlag = if(audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) true else false
        pauseAudio()
        currentTimePosition = min(currentTimePosition + sec * samplingRate * (bitPerSample / 8) * chCount, byteData.size - WaveFile.getHeaderSize())
        if(unPauseFlag)
            unPauseAudio()
    }

    fun rewindAudio(sec: Int){
        val unPauseFlag = if(audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) true else false
        pauseAudio()
        currentTimePosition = max(currentTimePosition - sec * samplingRate * (bitPerSample / 8) * chCount, 0)
        if(unPauseFlag)
            unPauseAudio()
    }

    fun convertEndian(data: ByteArray): ByteArray{
        val result = ByteArray(data.size)
        for(index in 0..(data.size - 1))
            result[index] = data[data.size - 1 - index]
        return result
    }

    fun convertIsLoop(){
        isLoop = !isLoop
    }

    fun convertIsShuffle(){
        isShuffle = !isShuffle
    }

    fun setCurrentTimePosition(element: Int){
        currentTimePosition = element
    }

    fun getMusicSec(): Int = musicSec

    fun isPlaying(): Boolean = if(audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) true else false
}