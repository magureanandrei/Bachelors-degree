package com.example.diabetesapp.utils

object DateTimeUtils {

    fun get24hStartTimestamp(): Long =
        System.currentTimeMillis() - 24 * 60 * 60 * 1000L

}