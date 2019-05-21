package io.pleo.antaeus.models

import kotlin.random.Random

enum class Currency {
    EUR,
    USD,
    DKK,
    SEK,
    GBP
}





object ExchangeRates {

    fun getMultiplier(from: Currency, to: Currency): Float{
        return Random.nextFloat() * 2 // Shifting range from (0,1) to (0,2)
    }

}