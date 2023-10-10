package com.bignerdranch.android.criminalintent

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity
data class Crime(
    @PrimaryKey val id: UUID,
    val title: String,
    val date: Date,
    val isSolved: Boolean,
    val suspect: String = "",
    val photoFileName0: String? = null,
    val photoFileName1: String? = null,
    val photoFileName2: String? = null,
    val photoFileName3: String? = null,
    val photoEffect0: Int = 0,
    val photoEffect1: Int = 0,
    val photoEffect2: Int = 0,
    val photoEffect3: Int = 0,
    val mostRecentPhoto: Int = 0,
    var numFaces: String = "0"
)
