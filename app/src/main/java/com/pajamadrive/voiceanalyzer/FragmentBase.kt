package com.pajamadrive.voiceanalyzer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

open class FragmentBase: Fragment(){
    var rootView: View? = null
    private var isCreated = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        return rootView
    }

    fun isCreated(): Boolean = isCreated
    fun createdMarker(){
        isCreated = true
    }
}