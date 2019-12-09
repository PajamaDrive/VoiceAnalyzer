package com.pajamadrive.voiceanalyzer

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import java.io.RandomAccessFile
import java.io.File
import java.io.IOException

class WaveFile {
    private val FILESIZE_SEEK: Long = 4
    private val DATASIZE_SEEK: Long = 40
    private var raf: RandomAccessFile? = null //リアルタイム処理なのでランダムアクセスファイルクラスを使用する
    private var recFile: File? = null //録音後の書き込み、読み込みようファイル
    private val RIFF = byteArrayOf('R'.toByte(), 'I'.toByte(), 'F'.toByte(), 'F'.toByte()) //wavファイルリフチャンクに書き込むチャンクID用
    private var fileSize = 36
    private val WAVE = byteArrayOf('W'.toByte(), 'A'.toByte(), 'V'.toByte(), 'E'.toByte()) //WAV形式でRIFFフォーマットを使用する
    private val FMT = byteArrayOf('f'.toByte(), 'm'.toByte(), 't'.toByte(), ' '.toByte()) //fmtチャンク　スペースも必要
    private val fmtSize = 16 //fmtチャンクのバイト数
    private val fmtID = byteArrayOf(1, 0) // フォーマットID リニアPCMの場合01 00 2byte
    private var chCount: Short = 1
    private var samplingRate = 44100
    private var bytePerSec = 0 //データ速度
    private var blockSize: Short = 0 //ブロックサイズ (Byte/サンプリングレート * チャンネル数)
    private var bitPerSample: Short = 16 //サンプルあたりのビット数 WAVでは8bitか16ビットが選べる
    private val DATA = byteArrayOf('d'.toByte(), 'a'.toByte(), 't'.toByte(), 'a'.toByte()) //dataチャンク
    private var dataSize = 0 //波形データのバイト数
    private val isExternalStorageReadable: Boolean
        get(){
            val state = Environment.getExternalStorageState()
            return Environment.MEDIA_MOUNTED == state
        }
    private val isExternalStorageWritable: Boolean
        get(){
            val state = Environment.getExternalStorageState()
            return Environment.MEDIA_MOUNTED_READ_ONLY == state || Environment.MEDIA_MOUNTED == state
        }

    fun createFile(dirPath: String, name: String, ch: Short, sr: Int, bps: Short) {
        //書き込み可能かチェック
        if(!isExternalStorageWritable)
            return

        recFile = File(dirPath, name + ".wav")
        //親ディレクトリが存在しているか確認
        if(recFile?.parentFile?.exists() == false)
            recFile?.parentFile?.mkdirs()
        //同じファイル名が存在しているか確認
        if (recFile?.exists() == true) {
            recFile?.delete()
        }
        Log.d("debug", recFile?.path)
        chCount = ch
        samplingRate = sr
        bitPerSample = bps
        bytePerSec = samplingRate * (bps / 8) * chCount
        blockSize = (bps / 8 * chCount).toShort()
        recFile?.createNewFile()
        raf = RandomAccessFile(recFile, "rw")

        //wavのヘッダを書き込み
        raf?.seek(0)
        raf?.write(RIFF)
        raf?.write(littleEndianInteger(fileSize))
        raf?.write(WAVE)
        raf?.write(FMT)
        raf?.write(littleEndianInteger(fmtSize))
        raf?.write(fmtID)
        raf?.write(littleEndianShort(chCount))
        raf?.write(littleEndianInteger(samplingRate)) //サンプリング周波数
        raf?.write(littleEndianInteger(bytePerSec))
        raf?.write(littleEndianShort(blockSize))
        raf?.write(littleEndianShort(bitPerSample))
        raf?.write(DATA)
        raf?.write(littleEndianInteger(dataSize))
    }

    private fun littleEndianInteger(i: Int): ByteArray {
        val buffer = ByteArray(4)
        buffer[0] = i.toByte()
        buffer[1] = (i shr 8).toByte()
        buffer[2] = (i shr 16).toByte()
        buffer[3] = (i shr 24).toByte()
        return buffer
    }
    // PCMデータを追記するメソッド
    fun addBigEndianData(shortData: ShortArray) {
        // ファイルにデータを追記
        raf?.seek(raf!!.length())
        raf?.write(littleEndianShorts(shortData))
        // ファイルサイズを更新
        updateFileSize()
        // データサイズを更新
        updateDataSize()
    }

    // ファイルサイズを更新
    private fun updateFileSize() {
        fileSize = recFile?.length()!!.toInt() - 8
        val fileSizeBytes = littleEndianInteger(fileSize)
        raf?.seek(FILESIZE_SEEK)
        raf?.write(fileSizeBytes)
    }

    // データサイズを更新
    private fun updateDataSize() {
        dataSize = recFile?.length()!!.toInt() - 44
        val dataSizeBytes = littleEndianInteger(dataSize)
        raf?.seek(DATASIZE_SEEK)
        raf?.write(dataSizeBytes)
    }

    // short型変数をリトルエンディアンのbyte配列に変更
    private fun littleEndianShort(s: Short): ByteArray {
        val buffer = ByteArray(2)
        buffer[0] = s.toByte()
        buffer[1] = (s.toInt() shr 8).toByte()
        return buffer

    }

    // short型配列をリトルエンディアンのbyte配列に変更
    private fun littleEndianShorts(s: ShortArray): ByteArray {

        val buffer = ByteArray(s.size * 2)
        var i: Int

        i = 0
        while (i < s.size) {
            buffer[2 * i] = s[i].toByte()
            buffer[2 * i + 1] = (s[i].toInt() shr 8).toByte()
            i++
        }
        return buffer
    }


    // ファイルを閉じる
    fun close(context: Context) {
        raf?.close()
        Toast.makeText(context, recFile?.name, Toast.LENGTH_LONG).show()
    }

    fun getFileName(): String{
        return recFile?.path ?: "null"
    }
}