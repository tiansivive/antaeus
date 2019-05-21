package io.pleo.antaeus.core.services


import io.pleo.antaeus.core.external.PaymentProvider

import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import java.lang.Exception

class BillingService(
		private val paymentProvider: PaymentProvider
) {

	// TODO - Add code e.g. here

	private fun attemptCharging(invoice: Invoice): Boolean{

		return try {
			this.paymentProvider.charge(invoice)
		}catch (e: Exception){
			false
		}
	}

	fun bill(dal: AntaeusDal) : List<Boolean>{
		return InvoiceService(dal)
				.fetchAll()
				.map { attemptCharging(it) }
	}
}
