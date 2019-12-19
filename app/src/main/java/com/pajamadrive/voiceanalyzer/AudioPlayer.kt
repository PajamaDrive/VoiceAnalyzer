package com.pajamadrive.voiceanalyzer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import kotlin.experimental.and
import kotlin.math.*

class AudioPlayer(val fileName: String): Runnable{
    var file: File? = null
    var byteData = ByteArray(0)
    var bufSize: Int = 0
    var audioTrack: AudioTrack? = null
    var audioThread: Thread? = null
    var samplingRate: Int = 0
    var chCount: Int = 0
    var bitPerSample: Int = 0
    var currentPosition: Int = 0
    var isLoop: Boolean = false

    init{
        val chCountBuffer = ByteArray(2)
        val samplingRateBuffer = ByteArray(4)
        val bitPerSampleBuffer = ByteArray(2)
        val headerSize = WaveFile.getHeaderSize()
        val clickFile = RandomAccessFile(fileName, "r")
        clickFile.seek(WaveFile.getChCountPosition())
        clickFile.read(chCountBuffer)
        clickFile.seek(WaveFile.getSamplingRatePosition())
        clickFile.read(samplingRateBuffer)
        clickFile.seek(WaveFile.getBitPerSamplePosition())
        clickFile.read(bitPerSampleBuffer)
        chCount = byteArrayToShort(convertEndian(chCountBuffer))
        samplingRate = byteArrayToInt(convertEndian(samplingRateBuffer))
        bitPerSample = byteArrayToShort(convertEndian(bitPerSampleBuffer))
        file = File(fileName)
        byteData = ByteArray(file!!.length().toInt())
        var inputStream: FileInputStream? = null
        try{
            inputStream = FileInputStream(file)
            inputStream?.read(byteData)
        }
        finally{
            inputStream?.close()
        }
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
    }

    fun byteArrayToInt(byteArray: ByteArray): Int {
        var result: Int = 0
        for (index in 0..3) {
            result = result shl 8
            result = result or (byteArray[index].toUByte() and 0xFF.toUByte()).toInt()
        }
        return result
    }

    fun byteArrayToShort(byteArray: ByteArray): Int {
        var result: Int = 0
        for (index in 0..1) {
            result = result shl 8
            result = result or (byteArray[index].toUByte() and 0xFF.toUByte()).toInt()
        }
        return result
    }

    override fun run() {
        if(audioTrack != null){
            audioTrack?.play()
            audioTrack?.write(
                byteData,
                WaveFile.getHeaderSize() + currentPosition,
                byteData.size - WaveFile.getHeaderSize() - currentPosition
            )
            if (audioTrack?.playState != AudioTrack.PLAYSTATE_STOPPED) {
                audioTrack?.stop()
                audioTrack?.flush()
            }
        }
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
        currentPosition += audioTrack!!.playbackHeadPosition * (bitPerSample / 8) * chCount
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
        currentPosition = min(currentPosition + sec * samplingRate * (bitPerSample / 8) * chCount, byteData.size - WaveFile.getHeaderSize())
        if(unPauseFlag)
            unPauseAudio()
    }

    fun rewindAudio(sec: Int){
        val unPauseFlag = if(audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) true else false
        pauseAudio()
        currentPosition = max(currentPosition - sec * samplingRate * (bitPerSample / 8) * chCount, 0)
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


    fun isPlaying(): Boolean = if(audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) true else false
}