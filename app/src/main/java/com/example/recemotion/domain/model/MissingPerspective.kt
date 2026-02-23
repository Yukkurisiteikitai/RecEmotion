package com.example.recemotion.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A perspective that is missing from the thought content.
 */
@Parcelize
data class MissingPerspective(
    val description: String
) : Parcelable
