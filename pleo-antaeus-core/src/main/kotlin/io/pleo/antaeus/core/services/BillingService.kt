package io.pleo.antaeus.core.services


import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider


import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import java.lang.Exception


enum class BillingStatus {
	SUCCESS,
	INSUFFICIENT_BALANCE,
	CURRENCY_MISMATCH,
	CUSTOMER_NOT_FOUND,
	NETWORK_ERROR,
	UNKNOWN
}

const val MAX_RETRY_ATTEMPTS = 5

class BillingService(
		private val paymentProvider: PaymentProvider,
		private val invoiceService: InvoiceService,
		private val customerService: CustomerService
) {


	// TODO - Add code e.g. here



	private fun handleCurrencyMismatch(invoice: Invoice): Invoice{
		val customerCurrency = customerService.fetch(invoice.customerId).currency
		return invoiceService.convertCurrency(invoice, customerCurrency)
	}

	private fun charge( invoice: Invoice ): BillingStatus{
		return try {
			if (paymentProvider.charge(invoice)){
				BillingStatus.SUCCESS
			}else {
				BillingStatus.INSUFFICIENT_BALANCE
			}
		}catch (e: CurrencyMismatchException) {
			BillingStatus.CURRENCY_MISMATCH
		}catch (e: CustomerNotFoundException){
			BillingStatus.CUSTOMER_NOT_FOUND
		}catch (e: NetworkException){
			BillingStatus.NETWORK_ERROR
		}catch (e: Exception){
			BillingStatus.UNKNOWN
		}
	}


	private val attemptCharging = { acc: MutableMap<Invoice, BillingStatus>, invoice: Invoice ->
		acc[invoice] = charge(invoice)
		acc
	}


	private fun handleNetworkErrors (limit: Int, current: Int, invoices: Map<Invoice, BillingStatus> ): Map<Invoice, BillingStatus>{

		if(current > limit){
			return invoices
					.mapKeys {
						if(it.value == BillingStatus.NETWORK_ERROR ) {
							invoiceService.markError(it.key, InvoiceStatus.NETWORK_ERROR)
						}else{
							it.key
						}
					}

		}

		val networkErrors = invoices
				.filter { it.value == BillingStatus.NETWORK_ERROR }
				.map { it.key }

		return when (networkErrors.isEmpty()) {
			true -> invoices
			false -> {
				val results = networkErrors.fold(invoices.toMutableMap(), attemptCharging )
				handleNetworkErrors(limit, current +1, results)
			}
		}
	}


	private fun handleCurrencyMismatches (invoices: Map<Invoice, BillingStatus>): Map<Invoice, BillingStatus> {
		return invoices
				.mapKeys {
					if( it.value == BillingStatus.CURRENCY_MISMATCH ){
						handleCurrencyMismatch(it.key)
					} else {
						it.key
					}
				}
				.mapValues {
					if( it.value == BillingStatus.CURRENCY_MISMATCH ){
						charge(it.key)
					} else {
						it.value
					}
				}

	}

	private fun handleBillingSuccess(invoices: Map<Invoice, BillingStatus>):  Map<Invoice, BillingStatus>{
		return invoices
				.mapKeys {
					if( it.value == BillingStatus.SUCCESS ){
						invoiceService.markAsPaid(it.key)
					} else {
						it.key
					}
				}
	}

	private fun handleInsufficientBalance(invoices: Map<Invoice, BillingStatus>):  Map<Invoice, BillingStatus>{
		return invoices
				.mapKeys {
					if(it.value == BillingStatus.INSUFFICIENT_BALANCE ){
						invoiceService.newInvoiceWithInterest(it.key)
					} else {
						it.key
					}
				}

	}


	private fun handleResults(results:  Map<Invoice, BillingStatus>): Map<Invoice, BillingStatus>{

		var processed = handleCurrencyMismatches(results)
		processed = handleNetworkErrors(MAX_RETRY_ATTEMPTS, 1, processed)
		processed = handleInsufficientBalance(processed)
		processed = handleBillingSuccess(processed)

		return processed
	}



	fun bill() : Map<Invoice, BillingStatus>{

		val results = invoiceService.fetchAll()
				.filter { it.status == InvoiceStatus.PENDING }
				.fold(HashMap<Invoice, BillingStatus>(), attemptCharging )

		return handleResults(results)

	}

}
