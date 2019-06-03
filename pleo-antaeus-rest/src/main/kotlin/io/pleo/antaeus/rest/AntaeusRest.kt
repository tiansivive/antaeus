/*
    Configures the rest app along with basic exception handling and URL endpoints.
 */

package io.pleo.antaeus.rest

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.apibuilder.ApiBuilder.path
import io.pleo.antaeus.core.exceptions.EntityNotFoundException
import io.pleo.antaeus.core.services.BillingPeriod
import io.pleo.antaeus.core.services.BillingService

import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import mu.KotlinLogging

import kotlinx.coroutines.*

private val logger = KotlinLogging.logger {}

class AntaeusRest (
    private val invoiceService: InvoiceService,
    private val customerService: CustomerService,
    private val billingService: BillingService
) : Runnable {

    override fun run() {
        app.start(7000)
    }

    // Set up Javalin rest app
    private val app = Javalin
        .create()
        .apply {
            // InvoiceNotFoundException: return 404 HTTP status code
            exception(EntityNotFoundException::class.java) { _, ctx ->
                ctx.status(404)
            }
            // Unexpected exception: return HTTP 500
            exception(Exception::class.java) { e, _ ->
                logger.error(e) { "Internal server error" }
            }
            // On 404: return message
            error(404) { ctx -> ctx.json("not found") }
        }

    init {
        // Set up URL endpoints for the rest app
        app.routes {
           path("rest") {
               // Route to check whether the app is running
               // URL: /rest/health
               get("health") {
                   it.json("ok")
               }

               // V1
               path("v1") {
                   path("invoices") {
                       // URL: /rest/v1/invoices
                       get {
                           it.json(invoiceService.fetchAll())
                       }

                       // URL: /rest/v1/invoices/{:id}
                       get(":id") {
                          it.json(invoiceService.fetch(it.pathParam("id").toInt()))
                       }
                   }

                   path("customers") {
                       // URL: /rest/v1/customers
                       get {
                           it.json(customerService.fetchAll())
                       }

                       // URL: /rest/v1/customers/{:id}
                       get(":id") {
                           it.json(customerService.fetch(it.pathParam("id").toInt()))
                       }
                   }

                   path("billing") {
                       // URL: /rest/v1/billing
                       var job: Job? = null

                       path("do") {
                           // URL: /rest/v1/billing/do
                           post {
                               it.json( billingService.bill() )
                           }
                       }

                       path("start") {
                           // URL: /rest/v1/billing/start
                           post {

                               try {

                                   if (job != null) {
                                       it.res.sendError(400, "Automatic Billing is already running")
                                   }
                                   else {

                                       val body = it.body<Map<String, String>>()
                                       val period = body.get(key = "Period")

                                       if (period == null) {
                                           it.res.sendError(400, "No 'Period' argument")
                                       }
                                       else {
                                           when (period.toLowerCase()) {
                                               "monthly" -> job = billingService.startAutomaticBilling(BillingPeriod.MONTHLY, 0)
                                               "weekly" -> job = billingService.startAutomaticBilling(BillingPeriod.WEEKLY, 0)
                                               "daily" -> job = billingService.startAutomaticBilling(BillingPeriod.DAILY, 0)
                                               "custom" -> {
                                                   val value = body.get(key = "Value")
                                                   if (value == null) {
                                                       it.res.sendError(400, "No 'Value' argument for 'Custom Period'")
                                                   }
                                                   else {
                                                       val parsed = value.toLong()
                                                       job = billingService.startAutomaticBilling(BillingPeriod.CUSTOM, parsed)
                                                   }
                                               }
                                               else -> it.res.sendError(400, "Unrecognised 'Period' argument")
                                           }
                                           it.res.setStatus(200, "Started Automatic Billing")
                                       }
                                   }
                               } catch (e: Exception){
                                   it.res.sendError(400, e.message)
                               }
                           }
                       }

                       path("stop") {
                           // URL: /rest/v1/billing/stop
                           post {
                               if( job != null){
                                   job!!.cancel()
                                   job = null
                                   it.json( "Stopped Automatic Billing" )
                               } else {
                                   it.res.sendError(400, "Automatic Billing is not running")
                               }
                           }
                       }
                   }
               }
           }
        }
    }
}
