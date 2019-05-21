/*
    Implements endpoints related to invoices.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.ExchangeRates
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.Money
import java.math.BigDecimal

class InvoiceService(private val dal: AntaeusDal) {
    fun fetchAll(): List<Invoice> {
       return dal.fetchInvoices()
    }

    fun fetch(id: Int): Invoice {
        return dal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
    }

    fun convertCurrency(invoice: Invoice, to: Currency) {
        val multiplier = ExchangeRates.getMultiplier( invoice.amount.currency, to).toBigDecimal()
        dal.convert(invoice.id, Money(invoice.amount.value * multiplier, to))
    }
}
