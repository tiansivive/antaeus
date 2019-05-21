package io.pleo.antaeus.core.services


import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.external.PaymentProvider

import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import java.lang.Exception

class BillingService(
		private val paymentProvider: PaymentProvider,
		private val dal: AntaeusDal
) {

	// TODO - Add code e.g. here

	private fun attemptCharging(invoice: Invoice): Boolean{

		return try {
			this.paymentProvider.charge(invoice)
		}catch (e: CurrencyMismatchException){
			val customerCurrency = CustomerService(dal).fetch(invoice.customerId).currency
			InvoiceService(dal).convertCurrency(invoice, customerCurrency)
			attemptCharging(invoice)
		}catch (e: Exception){
			false
		}
	}

	fun bill() : List<Boolean>{
		return InvoiceService(dal)
				.fetchAll()
				.map { attemptCharging(it) }
	}
}
