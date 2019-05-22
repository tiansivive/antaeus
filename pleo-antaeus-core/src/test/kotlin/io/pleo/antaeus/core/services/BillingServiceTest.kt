package io.pleo.antaeus.core.services;


import kotlin.random.Random

import io.mockk.every
import io.mockk.mockk

import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.NetworkException

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

import io.pleo.antaeus.core.external.PaymentProvider

import io.pleo.antaeus.models.*

class BillingServiceTest
{
    private val nInvoices = 10

    private val invoiceService = mockk<InvoiceService> {
        every { fetchAll() } returns List( nInvoices) {  Invoice(it, 0, mockk(), InvoiceStatus.PENDING)  }
        every { convertCurrency(any(), any()) } returns mockk()
    }
    private val customerService = mockk<CustomerService>()


    @Test
    fun `will charge all pending invoices`() {

        val paymentProvider = mockk< PaymentProvider > {
            every { charge(any()) } returns Random.nextBoolean()
        }

        val results = BillingService(paymentProvider, invoiceService, customerService).bill()
        assertEquals(nInvoices, results.size)

    }

    @Test
    fun `will skip non pending invoices`() {

        val paymentProvider = mockk< PaymentProvider > ()
        val iSrv = mockk<InvoiceService> {
            every { fetchAll() } returns List( nInvoices) {  Invoice(it, 0, mockk(), InvoiceStatus.PAID)  }
        }

        val results = BillingService(paymentProvider, iSrv, customerService).bill()
        assertEquals(0, results.size)

    }


    @Test
    fun `will handle CustomerNotFoundException`() {

        val paymentProvider = mockk< PaymentProvider > {
            every { charge(any()) } throws CustomerNotFoundException(0)
        }

        val results =  BillingService(paymentProvider, invoiceService, customerService).bill()
        results.forEach { assertEquals(it.value, BillingStatus.CUSTOMER_NOT_FOUND) }
        assertEquals(nInvoices, results.size)

    }


    @Test
    fun `will handle CurrencyMismatchException`() {

        val paymentProvider = mockk< PaymentProvider > {
            every { charge(any()) } throws CurrencyMismatchException(0, 0)
        }

        val results =  BillingService(paymentProvider, invoiceService, customerService).bill()
        results.forEach { assertEquals(it.value, BillingStatus.CURRENCY_MISMATCH) }
        assertEquals(nInvoices, results.size)

    }


    @Test
    fun `will handle NetworkException`() {

        val paymentProvider = mockk<PaymentProvider> {
            every { charge(any()) } throws NetworkException()
        }

        val results = BillingService(paymentProvider, invoiceService, customerService).bill()
        results.forEach { assertEquals(it.value, BillingStatus.NETWORK_ERROR) }
        assertEquals(nInvoices, results.size)
    }

}

