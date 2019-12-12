package com.pajamadrive.voiceanalyzer

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.postOnAnimationDelayed
import androidx.fragment.app.Fragment

class VisualizeSurfaceFragment: FragmentBase(){

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        rootView = inflater.inflate(R.layout.visualize_surface, container, false)
        createdMarker()
        Log.d("view", rootView.toString())
        return rootView
    }

    fun getSurface(): SurfaceView = ((rootView!! as ViewGroup).getChildAt(0) as ViewGroup).getChildAt(0) as SurfaceView
    fun getRecordButton(): Button = (((rootView!! as ViewGroup).getChildAt(0) as ViewGroup).getChildAt(1) as ViewGroup).getChildAt(1) as Button
    fun getText(): TextView = (((rootView!! as ViewGroup).getChildAt(0) as ViewGroup).getChildAt(1) as ViewGroup).getChildAt(0) as TextView
}