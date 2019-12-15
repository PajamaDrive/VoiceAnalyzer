package com.pajamadrive.voiceanalyzer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.lang.Math.pow
import kotlin.math.log10

open class DecibelVisualizerSurfaceView : SurfaceView, SurfaceHolder.Callback, Runnable {

    private val paint = Paint()
    private val STROKE_WIDTH = 10F
    private val SUPPRESS = 256
    private var surfaceHolder: SurfaceHolder
    private var thread: Thread? = null
    private var buffer = DoubleArray(0)

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

    fun update(buffer: DoubleArray, size: Int) {
        if(thread != null){
            this.buffer = buffer.copyOf(size)
        }
    }

    fun initializeBuffer(size: Int){
        buffer = DoubleArray(0)
    }

    private fun doDrawSurface(holder: SurfaceHolder) {
        if(buffer.size == 0){
            return
        }
        try {
            val canvas: Canvas = holder.lockCanvas()
            if (canvas != null) {
                canvas.drawColor(Color.BLACK)
                var oldX: Float = 0F
                var oldY: Float = (this.buffer[0].toFloat() / -120F) * canvas.height.toFloat()
                for (index in 1..(this.buffer.size - 1)) {
                    val x: Float = (log10( (100F - 10F) / this.buffer.size * index.toFloat() + 10) - 1) * canvas.width.toFloat()
                    val y: Float = (this.buffer[index].toFloat() / -120F) * canvas.height.toFloat()
                    canvas.drawLine(oldX, oldY, x, y, paint)
                    oldX = x
                    oldY = y
                }
                buffer = DoubleArray(0)
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
