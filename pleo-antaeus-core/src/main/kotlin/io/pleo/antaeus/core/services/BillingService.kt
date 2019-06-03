package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider

import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.*

import java.lang.Exception
import java.util.*
import kotlin.concurrent.schedule

enum class BillingStatus {
	SUCCESS,
	INSUFFICIENT_BALANCE,
	CURRENCY_MISMATCH,
	CUSTOMER_NOT_FOUND,
	NETWORK_ERROR,
	UNKNOWN
}

enum class BillingPeriod {
	DAILY,
	WEEKLY,
	MONTHLY,
	CUSTOM
}

const val MAX_RETRY_ATTEMPTS = 5
const val ATTEMPT_DELAY_FACTOR = 1000L
const val STOP_ATTEMPTING_TIMEOUT = 5 * 60 * 1000L

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


	private suspend fun handleNetworkErrors (limit: Int, current: Int, invoices: Map<Invoice, BillingStatus> ): Map<Invoice, BillingStatus>{

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
		delay(current * ATTEMPT_DELAY_FACTOR)

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


	private fun handleResults(results:  Map<Invoice, BillingStatus>): Map<Invoice, BillingStatus> = runBlocking {

		var processed = handleCurrencyMismatches(results)

		val job = launch {
			withTimeout(STOP_ATTEMPTING_TIMEOUT) {// Add a timeout here for safety in case we change ATTEMPT_DELAY_FACTOR at some point
				processed = handleNetworkErrors(MAX_RETRY_ATTEMPTS, 1, processed)
			}
		}
		job.join()


		processed = handleInsufficientBalance(processed)
		processed = handleBillingSuccess(processed)

		processed
	}



	fun bill() : Map<Invoice, BillingStatus>{

		val results = invoiceService.fetchAll()
				.filter { it.status == InvoiceStatus.PENDING }
				.fold(HashMap<Invoice, BillingStatus>(), attemptCharging )

		return handleResults(results)

	}



	fun startAutomaticBilling(period: BillingPeriod, value: Long?): Job {

		fun getNextDelay(): Long {

			val calendar = Calendar.getInstance()
			val dayToMilliseconds = 24 * 60 * 60 * 1000L

			return when(period) {
				BillingPeriod.DAILY -> dayToMilliseconds
				BillingPeriod.WEEKLY -> 7 * dayToMilliseconds
				BillingPeriod.MONTHLY -> {
					val today = calendar.get(Calendar.DAY_OF_MONTH)
					val daysTillNextBilling = calendar.getActualMaximum(Calendar.DAY_OF_MONTH) - today
					daysTillNextBilling * 24 * 60 * 60 * 1000L
				}
				BillingPeriod.CUSTOM -> value!!
			}
		}

		suspend fun next() {
			bill()
			delay(getNextDelay())
			next()
		}

		return GlobalScope.launch { next() }

	}

}
