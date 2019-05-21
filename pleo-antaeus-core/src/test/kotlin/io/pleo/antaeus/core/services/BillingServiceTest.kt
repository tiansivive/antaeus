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
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.*

class BillingServiceTest
{
    private val nInvoices = 10
    private val invoice = mockk<Invoice> ()

    private val dal = mockk<AntaeusDal> {
        every { fetchInvoices() } returns List( nInvoices) { invoice }
    }


    @Test
    fun `will charge all invoices`() {

        val paymentProvider = mockk< PaymentProvider > {
            every { charge(invoice) } returns Random.nextBoolean()
        }

        val results = BillingService(paymentProvider, dal).bill()
        assertEquals(nInvoices, results.size)
    }


    @Test
    fun `will handle CustomerNotFoundException`() {

        val paymentProvider = mockk< PaymentProvider > {
            every { charge(invoice) } throws CustomerNotFoundException(0)
        }

        val results =  BillingService(paymentProvider, dal).bill()
        assertEquals(nInvoices, results.size)
    }


    @Test
    fun `will handle CurrencyMismatchException`() {

        val paymentProvider = mockk< PaymentProvider > {
            every { charge(invoice) } throws CurrencyMismatchException(0, 0)
        }

        val results =  BillingService(paymentProvider, dal).bill()
        assertEquals(nInvoices, results.size)
    }


    @Test
    fun `will handle NetworkException`() {

        val paymentProvider = mockk< PaymentProvider > {
            every { charge(invoice) } throws NetworkException()
        }

        val results =  BillingService(paymentProvider, dal).bill()
        assertEquals(nInvoices, results.size)
    }
}

