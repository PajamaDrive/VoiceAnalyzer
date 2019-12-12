package com.pajamadrive.voiceanalyzer

import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.util.*

interface FragmentCheckListener : EventListener{
    fun createListener()
}

class FragmentCheck(val fragments: Array<FragmentBase>) {
    private var listener: FragmentCheckListener? = null

    fun checkFragment(){
        if(listener != null){
            //全部のfragmentが作成されたらlistenerを実行
            if(isAllFragmentCreated() == true) {
                listener?.createListener()
            }
        }
    }

    fun isAllFragmentCreated(): Boolean{
        for(fragment in fragments){
            if(fragment.isCreated() == false)
                return false
        }
        return true
    }

    fun setListener(listener: FragmentCheckListener){
        this.listener = listener
    }
}