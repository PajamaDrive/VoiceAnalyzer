package com.pajamadrive.voiceanalyzer

import android.content.Context
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
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

class AudioPlayer(private val dirPath: String, private val fileNameList: Array<String>, private var currentFilePosition: Int,
                  private val minFrequency: Int, private val maxFrequency: Int, private val sec: Int, private val pitchText: TextView,
                  private val pitchVisualizer: PitchVisualizerSurfaceView, private val decibelVisualizer: DecibelVisualizerSurfaceView): Runnable{
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
    private var isLoop: Boolean = false
    private var isShuffle: Boolean = false
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
        for (index in 0..(byteArray.size / 4 - 1)) {
            result.add(byteArrayToShort(byteArray.copyOfRange(index * 4, (index + 1) * 4)).toDouble())
        }
        return result.toDoubleArray()
    }

    override fun run() {
        if(audioTrack != null){
            audioTrack?.play()

            GlobalScope.launch{
                var offset = currentTimePosition / 4
                while(audioThread != null){
                    if(fftSize < doubleData.size - offset) {
                        val fft = FFT(samplingRate, fftSize, minFrequency, maxFrequency, doubleData.copyOfRange(offset, offset + fftSize))
                        handler.post {
                            pitchText.text = fft.getPitch().getOctaveName()
                            pitchText.setTextColor(fft.getPitch().getPitchColor())
                        }
                        pitchVisualizer.update(fft.getPitch())
                        decibelVisualizer.update(fft.getFFTData(), fft.getFFTData().size)
                    }
                    Thread.sleep(150)
                    offset += (0.15 * samplingRate * (bitPerSample / 8) * chCount / 4).toInt()
                }
            }


            audioTrack?.write(byteData, WaveFile.getHeaderSize() + currentTimePosition, byteData.size - WaveFile.getHeaderSize() - currentTimePosition)
            stopAudio()
            /*
            if (audioTrack?.playState != AudioTrack.PLAYSTATE_STOPPED) {
                audioTrack?.stop()
                audioTrack?.flush()
            }
            */
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
        doubleData = byteArrayToDoubleArray(convertEndian(byteData.copyOfRange(WaveFile.getHeaderSize(), byteData.size)))
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
                    track!!.stop()
                    track!!.flush()
                    audioThread = null
                    if(isLoop)
                        startAudio()
                    else if(isShuffle){
                        currentFilePosition = Random.nextInt(fileNameList.size)
                        initialize()
                    }
                }
            }
        })
    }

    fun stopAudio(){
        audioThread = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    fun startAudio(){
        if(audioThread == null){
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

    fun getIsLoop(): Boolean = isLoop

    fun setIsLoop(element: Boolean){
        isLoop = element
    }

    fun getIsShuffle(): Boolean = isShuffle

    fun setIsShuffle(element: Boolean){
        isShuffle = element
    }

    fun setCurrentTimePosition(element: Int){
        currentTimePosition = element
    }

    fun getMusicSec(): Int = musicSec

    fun isPlaying(): Boolean = if(audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) true else false

}