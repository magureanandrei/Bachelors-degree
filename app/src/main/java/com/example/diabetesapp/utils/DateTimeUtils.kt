package com.example.diabetesapp.utils

import java.util.Calendar

object DateTimeUtils {

    fun get24hStartTimestamp(): Long =
        System.currentTimeMillis() - 25 * 60 * 60 * 1000L

}