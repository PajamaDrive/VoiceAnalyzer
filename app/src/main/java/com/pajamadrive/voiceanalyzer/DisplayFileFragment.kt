package com.pajamadrive.voiceanalyzer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.fragment.app.Fragment

class DisplayFileFragment : FragmentBase() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        rootView = inflater.inflate(R.layout.display_file, container, false)
        createdMarker()
        return rootView
    }

    fun getListView(): ListView = (rootView!! as ViewGroup).getChildAt(0) as ListView
}