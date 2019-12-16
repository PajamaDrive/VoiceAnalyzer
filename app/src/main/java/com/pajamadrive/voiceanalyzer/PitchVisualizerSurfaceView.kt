package com.pajamadrive.voiceanalyzer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.VolumeAutomation
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.lang.Math.pow

open class PitchVisualizerSurfaceView : SurfaceView, SurfaceHolder.Callback, Runnable {

    private val paint = Paint()
    private val STROKE_WIDTH = 25F
    private val SUPPRESS = 256
    private var surfaceHolder: SurfaceHolder
    private var thread: Thread? = null
    private var buffer: Queue<VocalRange> = Queue()
    private var isUpdate: Boolean = false

    override fun run() {
        while(thread != null){
            doDrawSurface(surfaceHolder)
        }
    }

    constructor(context: Context, surface: SurfaceView) : super(context) {
        surfaceHolder = surface.holder
        surfaceHolder.addCallback(this)
        // 線の太さ、アンチエイリアス、色、とか
        paint.strokeWidth  = STROKE_WIDTH
        paint.isAntiAlias  = true
        paint.color        = Color.GREEN
    }

    //surfaceが作られたときに呼ばれる
    override fun surfaceCreated(holder: SurfaceHolder?) {
        if(holder != null){
            //排他処理
            val canvas = holder.lockCanvas()
            holder.unlockCanvasAndPost(canvas)
        }
    }
    //onResumeが呼ばれたときに呼ばれる
    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        startDrawSurfaceThreed()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        stopDrawSurfaceThread()
    }

    fun update(data: VocalRange) {
        if(thread != null){
            buffer.dequeue()
            buffer.enqueue(data)
            isUpdate = true
        }
    }

    fun initializeBuffer(size: Int){
        buffer.clear()
        buffer.setElement(size, VocalRange(-1.0))
    }

    private fun doDrawSurface(holder: SurfaceHolder) {
        if(!isUpdate){
            return
        }
        try {
            val canvas: Canvas = holder.lockCanvas()
            if (canvas != null) {
                canvas.drawColor(Color.BLACK)
                val notMinusData = buffer.getAllElement().filter{it.getFrequency() >= 0.0}
                val newestData = notMinusData[notMinusData.size - 1]
                val maxFrequency = notMinusData.map{it.getFrequency()}.max()
                val minFrequency = notMinusData.map{it.getFrequency()}.min()
                val baseOctave = 2F
                /*val baseOctave: Float = if(maxFrequency!! <= newestData.getFrequency()) (newestData.getOctave() - 2).toFloat()
                    else if(minFrequency!! >= newestData.getFrequency()) newestData.getOctave().toFloat()
                    else (notMinusData[notMinusData.map{it.getFrequency()}.indexOf(minFrequency)].getOctave()).toFloat()

                 */
                for (index in (buffer.size() - notMinusData.size)..(buffer.size() - 1)) {
                    if(buffer.getElement(index).getFrequency() < pow(2.0, baseOctave.toDouble()) * VocalRange.Pitch.A.getFrequency() || buffer.getElement(index).getFrequency() > pow(2.0 ,(baseOctave + 3).toDouble()) * VocalRange.Pitch.A.getFrequency())
                        continue
                    val x1: Float = canvas.width.toFloat() / buffer.size() * index.toFloat()
                    val x2: Float = canvas.width.toFloat() / buffer.size() * (index.toFloat() + 1)
                    val y: Float = canvas.height.toFloat() - ((buffer.getElement(index).getOctave() - baseOctave) * VocalRange.Pitch.values().size + buffer.getElement(index).getPitchNo()) * (canvas.height.toFloat() / (3 * VocalRange.Pitch.values().size))
                    paint.color = buffer.getElement(index).getPitchColor()
                    canvas.drawLine(x1, y, x2, y, paint)
                }
                isUpdate = false
                holder.unlockCanvasAndPost(canvas)
            }
        }catch(e: Exception){
            Log.e(this.javaClass.name, "doDraw", e)
        }
    }

    fun stopDrawSurfaceThread(){
        thread = null
    }

    fun startDrawSurfaceThreed(){
        if(thread == null){
            thread = Thread(this)
            thread?.start()
        }
    }
}
