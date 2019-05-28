package io.pleo.antaeus.models

enum class InvoiceStatus {
    PENDING,
    PAID,
    UNPAID,
    NETWORK_ERROR,
    ERROR // Not sure how to deal with wrong customer ID -> figure it out later
}
