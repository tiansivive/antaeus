package io.pleo.antaeus.core.services;


import kotlin.random.Random

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

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
    private val customerService = mockk<CustomerService>()





    @Test
    fun `will handle successful billing` (){

        val paymentProvider = mockk< PaymentProvider > {
            every { charge(any()) } returns true
        }

        val invoiceService = mockk<InvoiceService> {

            val invoices = List( nInvoices) {  Invoice(it, 0, mockk(), InvoiceStatus.PENDING) }
            every { fetchAll() } returns invoices

            invoices.forEach {
                every { markAsPaid(it) } returns Invoice(it.id, it.customerId, it.amount, InvoiceStatus.PAID)
            }
        }


        val results = BillingService(paymentProvider, invoiceService, customerService).bill()
        assertEquals(nInvoices, results.size)

        results.forEach {
            assertEquals(it.value, BillingStatus.SUCCESS)
            assertEquals(it.key.status, InvoiceStatus.PAID)
        }

    }


    @Test
    fun `will handle unsuccessful billing` (){

        val paymentProvider = mockk< PaymentProvider > {
            every { charge(any()) } returns false
        }

        val invoiceService = mockk<InvoiceService> {

            val invoices = List( nInvoices) {  Invoice(it, 0, Money(10.toBigDecimal(), Currency.USD), InvoiceStatus.PENDING) }
            every { fetchAll() } returns invoices

            invoices.forEach {
               every { newInvoiceWithInterest(it) } returns Invoice(it.id, it.customerId, Money(it.amount.value * 2.toBigDecimal(), it.amount.currency), InvoiceStatus.PENDING)
            }
        }


        val results = BillingService(paymentProvider, invoiceService, customerService).bill()
        assertEquals(nInvoices, results.size)

        results.forEach {
            assertEquals(it.value, BillingStatus.INSUFFICIENT_BALANCE)
            assertEquals(it.key.status, InvoiceStatus.PENDING)
            assertEquals(it.key.amount.value, 20.toBigDecimal())
        }

    }


    @Test
    fun `will handle billing when network is down` (){

        val paymentProvider = mockk< PaymentProvider > {
            every { charge(any()) } throws NetworkException()
        }

        val invoiceService = mockk<InvoiceService> {

            val invoices = List( nInvoices) {  Invoice(it, 0, mockk(), InvoiceStatus.PENDING) }
            every { fetchAll() } returns invoices

            invoices.forEach {
                every { markError(it, InvoiceStatus.NETWORK_ERROR) } returns Invoice(it.id, it.customerId, it.amount, InvoiceStatus.NETWORK_ERROR)
            }
        }


        val spy = spyk(BillingService(paymentProvider, invoiceService, customerService), recordPrivateCalls = true)
        val results = spy.bill()

        // TODO: verify how many times it was called
        // verify(exactly = 5) { spy["handleNetworkErrors"](5, any(), any()) }

        assertEquals(nInvoices, results.size)

        results.forEach {
            assertEquals(it.value, BillingStatus.NETWORK_ERROR)
            assertEquals(it.key.status, InvoiceStatus.NETWORK_ERROR)
        }
    }


    @Test
    fun `will handle currency mismatches` (){

        val invoices = List( nInvoices) {  Invoice(it, 0, Money(mockk(), Currency.USD), InvoiceStatus.PENDING) }
        val converted = invoices.map{ Invoice(it.id, it.customerId, Money(mockk(), Currency.EUR), it.status) }

        val paymentProvider = mockk< PaymentProvider > {

            invoices.forEach {
                every { charge(it) } throws CurrencyMismatchException(0,0)
            }
            converted.forEach {
                every { charge(it) } returns true
            }
        }

        val invoiceService = mockk<InvoiceService> {

            every { fetchAll() } returns invoices

            invoices.forEachIndexed { index, invoice ->

                every { convertCurrency(invoice, Currency.EUR) } returns converted[index]
                every { markAsPaid(converted[index]) } returns Invoice(converted[index].id, converted[index].customerId, converted[index].amount, InvoiceStatus.PAID)
            }
        }

        val customerService = mockk<CustomerService>{
            every { fetch(0) } returns Customer(0, Currency.EUR)
        }


        val results = BillingService(paymentProvider, invoiceService, customerService).bill()
        assertEquals(nInvoices, results.size)

        results.forEach {
            assertEquals(it.value, BillingStatus.SUCCESS)
            assertEquals(it.key.status, InvoiceStatus.PAID)
            assertEquals(it.key.amount.currency, Currency.EUR)
        }
    }


    @Test
    fun `will skip non pending invoices`() {

        val paymentProvider = mockk< PaymentProvider > ()
        val invoiceService = mockk<InvoiceService> {
            every { fetchAll() } returns List( nInvoices) {  Invoice(it, 0, mockk(), InvoiceStatus.PAID)  }
        }

        val results = BillingService(paymentProvider, invoiceService, customerService).bill()
        assertEquals(0, results.size)

    }

}

