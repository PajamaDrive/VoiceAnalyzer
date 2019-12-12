package com.pajamadrive.voiceanalyzer

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter

class FragmetnPagerAdapter(val fm: FragmentManager, val vs: VisualizeSurfaceFragment, val df: DisplayFileFragment) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT){

    override fun getCount(): Int {
        return 2
    }

    override fun getItem(position: Int): Fragment {
        when(position){
            0 -> return vs
            else -> return df
        }
    }
}