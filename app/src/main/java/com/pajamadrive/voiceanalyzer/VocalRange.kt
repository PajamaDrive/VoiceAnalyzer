package com.pajamadrive.voiceanalyzer

import android.graphics.Color
import java.lang.Math.pow
import kotlin.math.abs

class VocalRange(private val frequency: Double){
    private var octave = 0
    private var pitch: Pitch? = null

    init{
        octave = getOctave()
        pitch = getHighestPitch()
    }

    enum class Pitch{
        A{
            override fun getFrequency(): Double = 27.5
            override fun getNextPitch(): Pitch = A_Sharp
            override fun getPreviousPitch(): Pitch = G_Sharp
            override fun toString(): String = "A"
            override fun getColor(): Int = Color.rgb(0,120,0)
            override fun getNo(): Int = 1
        }, A_Sharp{
            override fun getFrequency(): Double = 29.135
            override fun getNextPitch(): Pitch = B
            override fun getPreviousPitch(): Pitch = A
            override fun toString(): String = "A#"
            override fun getColor(): Int = Color.rgb(30, 120,0)
            override fun getNo(): Int = 2
        }, B{
            override fun getFrequency(): Double = 30.868
            override fun getNextPitch(): Pitch = C
            override fun getPreviousPitch(): Pitch = A_Sharp
            override fun toString(): String = "B"
            override fun getColor(): Int = Color.rgb(60, 120,0)
            override fun getNo(): Int = 3
        }, C{
            override fun getFrequency(): Double = 32.703
            override fun getNextPitch(): Pitch = C_Sharp
            override fun getPreviousPitch(): Pitch = B
            override fun toString(): String = "C"
            override fun getColor(): Int = Color.rgb(90, 120,0)
            override fun getNo(): Int = 4
        }, C_Sharp{
            override fun getFrequency(): Double = 34.648
            override fun getNextPitch(): Pitch = D
            override fun getPreviousPitch(): Pitch = C
            override fun toString(): String = "C#"
            override fun getColor(): Int = Color.rgb(120, 120,0)
            override fun getNo(): Int = 5
        }, D{
            override fun getFrequency(): Double = 36.708
            override fun getNextPitch(): Pitch = D_Sharp
            override fun getPreviousPitch(): Pitch = C_Sharp
            override fun toString(): String = "D"
            override fun getColor(): Int = Color.rgb(120, 90,0)
            override fun getNo(): Int = 6
        }, D_Sharp{
            override fun getFrequency(): Double = 38.891
            override fun getNextPitch(): Pitch = E
            override fun getPreviousPitch(): Pitch = D
            override fun toString(): String = "D#"
            override fun getColor(): Int = Color.rgb(120, 60,0)
            override fun getNo(): Int = 7
        }, E{
            override fun getFrequency(): Double = 41.203
            override fun getNextPitch(): Pitch = F
            override fun getPreviousPitch(): Pitch = D_Sharp
            override fun toString(): String = "E"
            override fun getColor(): Int = Color.rgb(120, 30,0)
            override fun getNo(): Int = 8
        }, F{
            override fun getFrequency(): Double = 43.654
            override fun getNextPitch(): Pitch = F_Sharp
            override fun getPreviousPitch(): Pitch = E
            override fun toString(): String = "F"
            override fun getColor(): Int = Color.rgb(120, 0,0)
            override fun getNo(): Int = 9
        }, F_Sharp{
            override fun getFrequency(): Double = 46.249
            override fun getNextPitch(): Pitch = G
            override fun getPreviousPitch(): Pitch = F
            override fun toString(): String = "F#"
            override fun getColor(): Int = Color.rgb(120, 0,30)
            override fun getNo(): Int = 10
        }, G{
            override fun getFrequency(): Double = 48.999
            override fun getNextPitch(): Pitch = G_Sharp
            override fun getPreviousPitch(): Pitch = F_Sharp
            override fun toString(): String = "G"
            override fun getColor(): Int = Color.rgb(120, 0,60)
            override fun getNo(): Int = 11
        }, G_Sharp{
            override fun getFrequency(): Double = 51.913
            override fun getNextPitch(): Pitch = A
            override fun getPreviousPitch(): Pitch = G
            override fun toString(): String = "G#"
            override fun getColor(): Int = Color.rgb(120, 0,90)
            override fun getNo(): Int = 12
        };

        abstract fun getFrequency(): Double
        abstract fun getNextPitch(): Pitch
        abstract fun getPreviousPitch(): Pitch
        override abstract fun toString(): String
        abstract fun getColor():Int
        abstract  fun getNo(): Int
    }

    fun getOctave(): Int{
        var octave = 0
        while(this.frequency >= Pitch.A.getFrequency() * pow(2.0, (octave + 1).toDouble())){
            octave += 1
        }
        return octave
    }

    fun getHighestPitch(): Pitch{
        for(pitch in Pitch.values()){
            if(this.frequency < pitch.getFrequency() * pow(2.0, this.octave.toDouble()))
                return pitch.getPreviousPitch()
        }
        return Pitch.G_Sharp
    }

    fun setMostNearPitch(){
        var lowerDiff = this.frequency - this.pitch!!.getFrequency() * pow(2.0, (this.octave.toDouble()))
        var upperDiff = 0.0
        if(this.pitch == Pitch.G_Sharp){
            upperDiff = this.pitch!!.getNextPitch().getFrequency() * pow(2.0, (this.octave + 1).toDouble()) - this.frequency
            if(abs(lowerDiff) > abs(upperDiff)) {
                this.pitch = this.pitch!!.getNextPitch()
                this.octave += 1
            }
        }
        else{
            upperDiff = this.pitch!!.getNextPitch().getFrequency() * pow(2.0, this.octave.toDouble()) - this.frequency
            if(abs(lowerDiff) > abs(upperDiff))
                this.pitch = this.pitch!!.getNextPitch()
        }
    }

    fun getOctaveName(isNickName: Boolean = true): String{
        setMostNearPitch()
        var octaveName = ""
        if(isNickName == true) {
            octaveName = when(this.octave){
                0 -> "lowlow"
                1 -> "low"
                2 -> "mid1"
                3 -> "mid2"
                else -> "hi".repeat(this.octave - 3)
            } + this.pitch.toString()
        }
        else{
            octaveName = this.pitch.toString() + this.octave.toString()
        }
        return octaveName
    }

    fun getPitchColor(): Int{
        return this.pitch!!.getColor()
    }

    fun getPitchFrequency(): Double{
        return this.pitch!!.getFrequency() * this.octave
    }

    fun getFrequency(): Double{
        return this.frequency
    }

    fun getPitchNo(): Int{
        return this.pitch!!.getNo()
    }
}