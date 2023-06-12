package com.mapbox.vision.teaser.ar

data class SearchLocation (
    val name : String = "",
    val coordinates: DoubleArray = DoubleArray(2) //longtitude, latitude
) {
    override fun toString(): String {
        return name
    }
}