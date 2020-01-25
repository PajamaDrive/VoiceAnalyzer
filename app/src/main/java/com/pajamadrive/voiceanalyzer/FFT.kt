package com.pajamadrive.voiceanalyzer

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

class FFT(val samplingRate: Int, val minFrequency: Int, val maxFrequency: Int, var buffer: DoubleArray){
    //1回のFFTで使用する標本数
    //4096 / 44100 = 約0.1秒でFFTを1回行う
    private val fftSize = 4096
    //分解能(認識できる最小周波数)
    //44100 / 4096 = 約11hz
    //mid1C以下は正確に計測できる保証はない
    private val resolution = samplingRate.toDouble() / fftSize
    private val dbBaseLine = Math.pow(2.0, 15.0) * fftSize * sqrt(2.0)
    private var pitch: VocalRange? =  null
    private var fftData: List<Double>? = null

    init{
        buffer = buffer.copyOfRange(0, fftSize)
    }

    fun execute(){
        var fft = FFT4g(fftSize)
        fft.rdft(1, buffer)
        fftData = buffer.asList().chunked(2)
            .map { (left, right) -> 20 * log10(sqrt(left.pow(2) + right.pow(2)) / dbBaseLine) }
            .filterIndexed { index, it -> index * resolution >= minFrequency && index * resolution <= maxFrequency}
        val maxDB = fftData!!.max()
        val maxIndex = fftData!!.indexOf(maxDB)
        pitch = VocalRange(maxIndex * resolution + minFrequency)
    }

    fun getPitch(): VocalRange{
        return pitch!!
    }

    fun getFFTData(): DoubleArray{
        return fftData!!.toDoubleArray()
    }
}