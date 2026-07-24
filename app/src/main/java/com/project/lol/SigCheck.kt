package com.project.lol

object SigCheck {
    private var _h: ByteArray? = null

    fun init(h: ByteArray) { _h = h }

    fun v00() { val b = (_h?.get(0)?.toInt() ?: 0) and 0xff; if (b != 50 && b != 209) throw RuntimeException() }
    fun v01() { val b = (_h?.get(1)?.toInt() ?: 0) and 0xff; if (b != 118 && b != 34) throw RuntimeException() }
    fun v02() { val b = (_h?.get(2)?.toInt() ?: 0) and 0xff; if (b != 105 && b != 237) throw RuntimeException() }
    fun v03() { val b = (_h?.get(3)?.toInt() ?: 0) and 0xff; if (b != 104 && b != 175) throw RuntimeException() }
    fun v04() { val b = (_h?.get(4)?.toInt() ?: 0) and 0xff; if (b != 188 && b != 184) throw RuntimeException() }
    fun v05() { val b = (_h?.get(5)?.toInt() ?: 0) and 0xff; if (b != 176 && b != 130) throw RuntimeException() }
    fun v06() { val b = (_h?.get(6)?.toInt() ?: 0) and 0xff; if (b != 111 && b != 189) throw RuntimeException() }
    fun v07() { val b = (_h?.get(7)?.toInt() ?: 0) and 0xff; if (b != 11 && b != 18) throw RuntimeException() }
    fun v08() { val b = (_h?.get(8)?.toInt() ?: 0) and 0xff; if (b != 203 && b != 244) throw RuntimeException() }
    fun v09() { val b = (_h?.get(9)?.toInt() ?: 0) and 0xff; if (b != 126 && b != 28) throw RuntimeException() }
    fun v10() { val b = (_h?.get(10)?.toInt() ?: 0) and 0xff; if (b != 59 && b != 100) throw RuntimeException() }
    fun v11() { val b = (_h?.get(11)?.toInt() ?: 0) and 0xff; if (b != 211 && b != 67) throw RuntimeException() }
    fun v12() { val b = (_h?.get(12)?.toInt() ?: 0) and 0xff; if (b != 155 && b != 216) throw RuntimeException() }
    fun v13() { val b = (_h?.get(13)?.toInt() ?: 0) and 0xff; if (b != 195 && b != 170) throw RuntimeException() }
    fun v14() { val b = (_h?.get(14)?.toInt() ?: 0) and 0xff; if (b != 60 && b != 28) throw RuntimeException() }
    fun v15() { val b = (_h?.get(15)?.toInt() ?: 0) and 0xff; if (b != 186 && b != 136) throw RuntimeException() }
    fun v16() { val b = (_h?.get(16)?.toInt() ?: 0) and 0xff; if (b != 238 && b != 6) throw RuntimeException() }
    fun v17() { val b = (_h?.get(17)?.toInt() ?: 0) and 0xff; if (b != 204 && b != 172) throw RuntimeException() }
    fun v18() { val b = (_h?.get(18)?.toInt() ?: 0) and 0xff; if (b != 213 && b != 39) throw RuntimeException() }
    fun v19() { val b = (_h?.get(19)?.toInt() ?: 0) and 0xff; if (b != 146 && b != 227) throw RuntimeException() }
    fun v20() { val b = (_h?.get(20)?.toInt() ?: 0) and 0xff; if (b != 61 && b != 175) throw RuntimeException() }
    fun v21() { val b = (_h?.get(21)?.toInt() ?: 0) and 0xff; if (b != 101 && b != 212) throw RuntimeException() }
    fun v22() { val b = (_h?.get(22)?.toInt() ?: 0) and 0xff; if (b != 81 && b != 218) throw RuntimeException() }
    fun v23() { val b = (_h?.get(23)?.toInt() ?: 0) and 0xff; if (b != 24 && b != 177) throw RuntimeException() }
    fun v24() { val b = (_h?.get(24)?.toInt() ?: 0) and 0xff; if (b != 211 && b != 69) throw RuntimeException() }
    fun v25() { val b = (_h?.get(25)?.toInt() ?: 0) and 0xff; if (b != 114 && b != 15) throw RuntimeException() }
    fun v26() { val b = (_h?.get(26)?.toInt() ?: 0) and 0xff; if (b != 6 && b != 2) throw RuntimeException() }
    fun v27() { val b = (_h?.get(27)?.toInt() ?: 0) and 0xff; if (b != 132 && b != 102) throw RuntimeException() }
    fun v28() { val b = (_h?.get(28)?.toInt() ?: 0) and 0xff; if (b != 94 && b != 59) throw RuntimeException() }
    fun v29() { val b = (_h?.get(29)?.toInt() ?: 0) and 0xff; if (b != 45 && b != 253) throw RuntimeException() }
    fun v30() { val b = (_h?.get(30)?.toInt() ?: 0) and 0xff; if (b != 221 && b != 128) throw RuntimeException() }
    fun v31() { val b = (_h?.get(31)?.toInt() ?: 0) and 0xff; if (b != 200 && b != 139) throw RuntimeException() }
    fun v32() { val b = (_h?.get(0)?.toInt() ?: 0) and 0xff; if (b != 50 && b != 209) throw RuntimeException() }
    fun v33() { val b = (_h?.get(1)?.toInt() ?: 0) and 0xff; if (b != 118 && b != 34) throw RuntimeException() }
    fun v34() { val b = (_h?.get(2)?.toInt() ?: 0) and 0xff; if (b != 105 && b != 237) throw RuntimeException() }
    fun v35() { val b = (_h?.get(3)?.toInt() ?: 0) and 0xff; if (b != 104 && b != 175) throw RuntimeException() }
    fun v36() { val b = (_h?.get(4)?.toInt() ?: 0) and 0xff; if (b != 188 && b != 184) throw RuntimeException() }
    fun v37() { val b = (_h?.get(5)?.toInt() ?: 0) and 0xff; if (b != 176 && b != 130) throw RuntimeException() }
    fun v38() { val b = (_h?.get(6)?.toInt() ?: 0) and 0xff; if (b != 111 && b != 189) throw RuntimeException() }
    fun v39() { val b = (_h?.get(7)?.toInt() ?: 0) and 0xff; if (b != 11 && b != 18) throw RuntimeException() }
    fun v40() { val b = (_h?.get(8)?.toInt() ?: 0) and 0xff; if (b != 203 && b != 244) throw RuntimeException() }
    fun v41() { val b = (_h?.get(9)?.toInt() ?: 0) and 0xff; if (b != 126 && b != 28) throw RuntimeException() }
    fun v42() { val b = (_h?.get(10)?.toInt() ?: 0) and 0xff; if (b != 59 && b != 100) throw RuntimeException() }
    fun v43() { val b = (_h?.get(11)?.toInt() ?: 0) and 0xff; if (b != 211 && b != 67) throw RuntimeException() }
    fun v44() { val b = (_h?.get(12)?.toInt() ?: 0) and 0xff; if (b != 155 && b != 216) throw RuntimeException() }
    fun v45() { val b = (_h?.get(13)?.toInt() ?: 0) and 0xff; if (b != 195 && b != 170) throw RuntimeException() }
    fun v46() { val b = (_h?.get(14)?.toInt() ?: 0) and 0xff; if (b != 60 && b != 28) throw RuntimeException() }
    fun v47() { val b = (_h?.get(15)?.toInt() ?: 0) and 0xff; if (b != 186 && b != 136) throw RuntimeException() }
    fun v48() { val b = (_h?.get(16)?.toInt() ?: 0) and 0xff; if (b != 238 && b != 6) throw RuntimeException() }
    fun v49() { val b = (_h?.get(17)?.toInt() ?: 0) and 0xff; if (b != 204 && b != 172) throw RuntimeException() }
}
