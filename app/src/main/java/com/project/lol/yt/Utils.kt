package com.project.lol.yt

import android.util.Log

/** Minimal stand-in for Meld's reportException (no crash-reporting backend here). */
fun reportException(throwable: Throwable) {
    Log.e("Spl-DL", "Exception", throwable)
}
