package pl.kserocki.gravityjar.data

import java.text.NumberFormat

/** Formats minor-unit prices (pence) into localized currency strings. */
object PriceFormatter {

    fun formatPrice(
        minorUnitsPrice: Int,
        currency: String,
        fractionDigits: Int = 2,
        minimumFractionDigits: Int = 0,
    ): String {
        val majorUnitsPrice = minorUnitsPrice.toFloat() / 100f
        val isPriceWithFraction = minorUnitsPrice.rem(100) > 0
        val numberFormatter = NumberFormat.getNumberInstance().apply {
            this.minimumFractionDigits = if (isPriceWithFraction) 2 else minimumFractionDigits
            maximumFractionDigits = fractionDigits
        }
        val formatted = numberFormatter.format(majorUnitsPrice)
        val negativeSign = if (minorUnitsPrice < 0) "-" else ""
        val abs = formatted.removePrefix("-")
        return when (currency) {
            "GBP" -> "$negativeSign£$abs"
            "PLN" -> "$negativeSign$abs zł"
            "EUR" -> "$negativeSign€$abs"
            else -> "$negativeSign$$abs"
        }
    }
}
