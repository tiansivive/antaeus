/*
    Implements endpoints related to invoices.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.*

class InvoiceService(private val dal: AntaeusDal) {
    fun fetchAll(): List<Invoice> {
       return dal.fetchInvoices()
    }

    fun fetch(id: Int): Invoice {
        return dal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
    }

    fun convertCurrency(invoice: Invoice, to: Currency) : Invoice {
        val multiplier = ExchangeRates.getMultiplier( invoice.amount.currency, to).toBigDecimal()
        val updated = Invoice(
            invoice.id,
            invoice.customerId,
            Money(invoice.amount.value * multiplier, to),
            invoice.status
        )

        return dal.update(updated)
    }

    fun markAsPaid(invoice: Invoice): Invoice{
       return  dal.update(
            Invoice(
                invoice.id,
                invoice.customerId,
                invoice.amount,
                InvoiceStatus.PAID
            )
        )
    }

    // TODO: create a new PENDING invoice and mark the old one as UNPAID
    fun addInterest(invoice: Invoice): Invoice{
        return  dal.update(
                Invoice(
                        invoice.id,
                        invoice.customerId,
                        Money(invoice.amount.value * 1.05.toBigDecimal(), invoice.amount.currency),
                        InvoiceStatus.PENDING
                )
        )
    }
}
