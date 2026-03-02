package com.rpeters.cinefintv.utils

import android.net.TrafficStats
import okhttp3.EventListener
import okhttp3.OkHttpClient

private const val NET_TAG = 0x534D // 'SM'

class StrictModeSocketTagger : EventListener() {
    override fun callStart(call: okhttp3.Call) {
        TrafficStats.setThreadStatsTag(NET_TAG)
    }
    override fun callEnd(call: okhttp3.Call) {
        TrafficStats.clearThreadStatsTag()
    }
    override fun callFailed(call: okhttp3.Call, ioe: java.io.IOException) {
        TrafficStats.clearThreadStatsTag()
    }
}

fun OkHttpClient.Builder.withStrictModeTagger(): OkHttpClient.Builder =
    eventListener(StrictModeSocketTagger())
