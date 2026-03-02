package com.rpeters.cinefintv.utils

import android.content.Context
import androidx.annotation.StringRes

object AppResources {
    @Volatile
    private var context: Context? = null

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    fun getString(@StringRes stringResId: Int): String {
        val appContext = context
        return appContext?.getString(stringResId).orEmpty()
    }
}
