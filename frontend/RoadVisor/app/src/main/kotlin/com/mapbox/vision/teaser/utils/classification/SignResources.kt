package com.mapbox.vision.teaser.utils.classification

import android.content.Context
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.teaser.models.UiSign
import com.mapbox.vision.teaser.utils.ResourceType
import com.mapbox.vision.teaser.utils.getResId

interface SignResources {

    fun getSignResource(uiSign: UiSign, country: Country): Int
    fun getSpeedSignResource(uiSign: UiSign, speed: Float, country: Country): Int

    class Impl(val context: Context) : SignResources {

        private val speedLimitWithOverspeeding = arrayOf(
            UiSign.SignType.SpeedLimit,
            UiSign.SignType.SpeedLimitNight,
            UiSign.SignType.SpeedLimitComplementary,
            UiSign.SignType.SpeedLimitTrucks
        )

        private fun getResourceNameForSign(
            uiSign: UiSign,
            country: Country
        ): String {
            val resourceName = when (country) {
                Country.Unknown, Country.USA -> uiSign.signType.usResourceName
                Country.UK, Country.Germany, Country.Other -> uiSign.signType.ukResourceName
                Country.Japan -> uiSign.signType.jpResourceName
                Country.China -> uiSign.signType.chinaResourceName
            }

            return if (uiSign is UiSign.WithNumber) {
                resourceName + uiSign.signNumber.value
            } else {
                resourceName
            }
        }

        private fun getSignResId(name: String, country: Country): Int {
            val resId = context.getResId(
                name = name,
                type = ResourceType.Drawable
            )

            return if (resId == 0) {
                context.getResId(
                    name = getResourceNameForSign(UiSign.Simple(UiSign.SignType.Unknown), country),
                    type = ResourceType.Drawable
                )
            } else {
                resId
            }
        }

        override fun getSignResource(uiSign: UiSign, country: Country) = getSignResId(
            name = getResourceNameForSign(uiSign, country),
            country = country
        )

        override fun getSpeedSignResource(uiSign: UiSign, speed: Float, country: Country): Int {
            val resourceName = getResourceNameForSign(uiSign, country)
            val fullResourceName = if (
                uiSign is UiSign.WithNumber &&
                uiSign.signType in speedLimitWithOverspeeding &&
                speed > uiSign.signNumber.value
            ) {
                "over_$resourceName"
            } else {
                resourceName
            }
            return getSignResId(
                name = fullResourceName,
                country = country
            )
        }
    }
}
