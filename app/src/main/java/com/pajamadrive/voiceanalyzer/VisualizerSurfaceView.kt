package com.pajamadrive.voiceanalyzer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

open class VisualizerSurfaceView : SurfaceView, SurfaceHolder.Callback, Runnable {

    val paint = Paint()
    val STROKE_WIDTH = 10F
    val SUPPRESS = 256
    var surfaceHolder: SurfaceHolder
    var thread: Thread? = null
    var allBuffer: Queue<Short> = Queue()
    var buffer: ShortArray = ShortArray(0)

    override fun run() {
        while(thread != null){
            doDraw(surfaceHolder)
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
    //threadの開始はここに書いといたほうが良いらしい
    override fun surfaceChanged(tmp_holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        thread = Thread(this)
        thread?.start()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        thread = null
    }

    fun update(new_buffer: ShortArray, size: Int, sec: Int) {
        buffer = new_buffer.copyOf(size)
        if(allBuffer.size() >= size * sec){
            allBuffer.dequeueByMutableList(size)
        }
        allBuffer.enqueueArray(buffer.toTypedArray())
    }

    fun initializeBuffer(){
        allBuffer.clear()
        buffer = ShortArray(0)
    }

    private fun doDraw(tmp_holder: SurfaceHolder) {
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
}

class Queue<T: Number>(list: MutableList<T> = mutableListOf()){
    var items: MutableList<T> = list

    fun isEmpty(): Boolean = items?.isEmpty()
    fun size(): Int = items?.count()
    override fun toString() = items?.toString()
    fun enqueue(element: T){
        items?.add(element)
    }
    fun enqueueArray(element: Array<T>){
        items?.addAll(element)
    }
    fun dequeue(): T?{
        if(this.isEmpty()) {
            return null
        }
        else{
            return items?.removeAt(0)
        }
    }
    fun dequeueByMutableList(size: Int): MutableList<T>?{
        if(this.isEmpty()){
            return null
        }else{
            val ret: MutableList<T>? = items?.subList(0, size)
            items?.subList(0, size)?.clear()
            return ret ?: null
        }
    }
    fun peek(): T{
        return items?.get(0)
    }
    fun getElement(index: Int): T {
        return items?.get(index)
    }
    fun clear(){
        items?.clear()
    }
}