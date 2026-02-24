package com.example.recemotion.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Detected cognitive bias in the thought content.
 */
@Parcelize
data class BiasDetection(
    val name: String,
    val evidence: String
) : Parcelable
