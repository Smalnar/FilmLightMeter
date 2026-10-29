package com.filmlightmeter

import kotlin.math.pow

enum class FilmType { BW, COLOR_NEGATIVE, COLOR_SLIDE }

data class FilmProfile(
    val name: String,
    val manufacturer: String,
    val defaultIso: Int,
    val type: FilmType,
    val reciprocityP: Double = 1.0,
    val reciprocityThreshold: Double = 1.0
) {
    fun correctShutter(rawSeconds: Double): Double {
        if (rawSeconds <= 0.0 || rawSeconds < reciprocityThreshold || reciprocityP <= 1.0) {
            return rawSeconds
        }
        return rawSeconds.pow(reciprocityP)
    }
}

object FilmCatalog {
    val profiles = listOf(
        FilmProfile("Tri-X 400", "Kodak", 400, FilmType.BW, 1.25),
        FilmProfile("T-Max 100", "Kodak", 100, FilmType.BW, 1.15),
        FilmProfile("T-Max 400", "Kodak", 400, FilmType.BW, 1.15),
        FilmProfile("HP5 Plus", "Ilford", 400, FilmType.BW, 1.31),
        FilmProfile("FP4 Plus", "Ilford", 125, FilmType.BW, 1.26),
        FilmProfile("Delta 100", "Ilford", 100, FilmType.BW, 1.26),
        FilmProfile("Delta 400", "Ilford", 400, FilmType.BW, 1.30),
        FilmProfile("Delta 3200", "Ilford", 3200, FilmType.BW, 1.33),
        FilmProfile("Pan 100", "Ilford", 100, FilmType.BW, 1.26),
        FilmProfile("Pan 400", "Ilford", 400, FilmType.BW, 1.30),
        FilmProfile("Fomapan 100", "Foma", 100, FilmType.BW, 1.38),
        FilmProfile("Fomapan 200", "Foma", 200, FilmType.BW, 1.35),
        FilmProfile("Fomapan 400", "Foma", 400, FilmType.BW, 1.45),
        FilmProfile("Portra 160", "Kodak", 160, FilmType.COLOR_NEGATIVE, 1.20),
        FilmProfile("Portra 400", "Kodak", 400, FilmType.COLOR_NEGATIVE, 1.22),
        FilmProfile("Portra 800", "Kodak", 800, FilmType.COLOR_NEGATIVE, 1.25),
        FilmProfile("Gold 200", "Kodak", 200, FilmType.COLOR_NEGATIVE, 1.25),
        FilmProfile("Ektar 100", "Kodak", 100, FilmType.COLOR_NEGATIVE, 1.18),
        FilmProfile("CineStill 800T", "CineStill", 800, FilmType.COLOR_NEGATIVE, 1.25),
        FilmProfile("Velvia 50", "Fujifilm", 50, FilmType.COLOR_SLIDE, 1.15),
        FilmProfile("Provia 100F", "Fujifilm", 100, FilmType.COLOR_SLIDE, 1.14),
        FilmProfile("Пользовательский", "Custom", 400, FilmType.BW, 1.0)
    )
}
