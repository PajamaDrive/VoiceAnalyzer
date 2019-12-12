package com.pajamadrive.voiceanalyzer

class Queue<T: Number>(list: MutableList<T> = mutableListOf()){
    private var items: MutableList<T> = list

    fun isEmpty(): Boolean = items?.isEmpty()
    fun size(): Int = items?.count()
    override fun toString() = items?.toString()
    fun enqueue(element: T) = items?.add(element)
    fun enqueueArray(element: Array<T>) = items?.addAll(element)
    fun peek(): T = items?.get(0)
    fun getElement(index: Int): T = items?.get(index)
    fun clear() = items?.clear()

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

    fun setElement(size: Int, element: T){
        val setArray = MutableList(size, {element})
        items?.addAll(setArray)
    }
}