package com.pajamadrive.voiceanalyzer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

open class VisualizerSurfaceView : SurfaceView, SurfaceHolder.Callback, Runnable {

    private val paint = Paint()
    private val STROKE_WIDTH = 10F
    private val SUPPRESS = 256
    private var surfaceHolder: SurfaceHolder
    private var thread: Thread? = null
    private var allBuffer: Queue<Short> = Queue()
    private var buffer: ShortArray = ShortArray(0)

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
    override fun surfaceCreated(tmp_holder: SurfaceHolder?) {
        if(tmp_holder != null){
            //排他処理
            val canvas = tmp_holder.lockCanvas()
            tmp_holder.unlockCanvasAndPost(canvas)
        }
    }
    //onResumeが呼ばれたときに呼ばれる
    override fun surfaceChanged(tmp_holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        startDrawSurfaceThreed()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        stopDrawSurfaceThread()
    }

    fun update(new_buffer: ShortArray, size: Int) {
        if(thread != null){
            buffer = new_buffer.copyOf(size)
            allBuffer.dequeueByMutableList(size)
            allBuffer.enqueueArray(buffer.toTypedArray())
        }
    }

    fun initializeBuffer(size: Int){
        allBuffer.clear()
        allBuffer.setElement(size,0)
        buffer = ShortArray(0)
    }

    private fun doDrawSurface(tmp_holder: SurfaceHolder) {
        if(buffer.size == 0){
            return
        }
        try {
            val canvas: Canvas = tmp_holder.lockCanvas()
            if (canvas != null) {
                canvas.drawColor(Color.BLACK)
                val baseLine: Float = canvas.height / 2F
                var oldX: Float = 0F
                var oldY: Float = baseLine
                for (index in 0..(allBuffer.size() - 1)) {
                    val x: Float = canvas.width.toFloat() / allBuffer.size().toFloat() * index.toFloat()
                    val y: Float = allBuffer.getElement(index) / SUPPRESS + baseLine
                    canvas.drawLine(oldX, oldY, x, y, paint)
                    oldX = x
                    oldY = y
                }
                buffer = ShortArray(0)
                tmp_holder.unlockCanvasAndPost(canvas)
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
