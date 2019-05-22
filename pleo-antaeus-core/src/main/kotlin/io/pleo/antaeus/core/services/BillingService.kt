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
	NOT_ENOUGH_MONEY,
	CURRENCY_MISMATCH,
	CUSTOMER_NOT_FOUND,
	NETWORK_ERROR,
	UNKNOWN
}


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


	private val attemptCharging = { acc: MutableMap<Invoice, BillingStatus>, invoice: Invoice ->
		try {
			if (paymentProvider.charge(invoice)){
				acc[invoice] = BillingStatus.SUCCESS
			}else {
				acc[invoice] = BillingStatus.NOT_ENOUGH_MONEY
			}
		}catch (e: CurrencyMismatchException) {
			acc[invoice] =  BillingStatus.CURRENCY_MISMATCH
		}catch (e: CustomerNotFoundException){
			acc[invoice] =  BillingStatus.CUSTOMER_NOT_FOUND
		}catch (e: NetworkException){
			acc[invoice] =  BillingStatus.NETWORK_ERROR
		}catch (e: Exception){
			acc[invoice] =  BillingStatus.UNKNOWN
		}
		acc
	}

	fun bill() : MutableMap<Invoice, BillingStatus>{
		return invoiceService
				.fetchAll()
				.filter { it.status == InvoiceStatus.PENDING }
				.fold(HashMap<Invoice, BillingStatus>(), attemptCharging )
	}


	fun handleResults(res:  MutableMap<Invoice, BillingStatus>){

		if(res.isEmpty()) {
			return
		}

		res.filter { it.value == BillingStatus.SUCCESS }
			.map {  invoiceService.markAsPaid(it.key) }

		res.filter { it.value == BillingStatus.NOT_ENOUGH_MONEY }
			.map { invoiceService.addInterest(it.key) }

		val wrongCurrencyResults = res
				.filter { it.value == BillingStatus.CURRENCY_MISMATCH }
				.map { handleCurrencyMismatch(it.key) }
				.fold(HashMap<Invoice, BillingStatus>(), attemptCharging )

		return handleResults(wrongCurrencyResults)
	}

}
