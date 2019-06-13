## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will pay those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

### Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── pleo-antaeus-app
|
|       Packages containing the main() application. 
|       This is where all the dependencies are instantiated.
|
├── pleo-antaeus-core
|
|       This is where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|
|       Module interfacing with the database. Contains the models, mappings and access layer.
|
├── pleo-antaeus-models
|
|       Definition of models used throughout the application.
|
├── pleo-antaeus-rest
|
|        Entry point for REST API. This is where the routes are defined.
└──
```

## Instructions
Fork this repo with your solution. We want to see your progression through commits (don’t commit the entire solution in 1 step) and don't forget to create a README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

Happy hacking 😁!

## How to run
```
./docker-start.sh
```

## Libraries currently in use
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library



## My Solution

#Billing the invoices

The idea for the API is to have one single method, `bill`, that attempts to charge all the *PENDING* invoices and handles all the different result scenarios.
To simplify the process and have better testability, I tried to have each function do only one thing and avoid internal state.
This means that first I attempt to charge all the invoices, and keep track of their result. From there, I ran all the invoices through a sequence of functions designed to process and handle the different outcomes.

For `CurrencyMismatches` I introduces a mechanism to convert the currency of the invoice and then attempt charging it again. I fake the exchange rate, but ideally that would be retrieved from some external API.
For `CustomerNotFound` errors, I couldn't quite see how that could happen and what would be an appropriate automatic response so I just marked the invoice as in an *ERROR* state.
For `NetworkErrors` I use coroutines to repeatedly retry the process, only with a bigger delay between each request. If it reaches the limit it just marks them as in *NETWORK_ERROR* state. I have a small limit, but ideally it would be higher, and if it still fails, then maybe notify the Customer? I guess it depends a bit.

If it's a successful billing, then I just mark the invoice as *PAID*, otherwise, I mark it as *UNPAID* and create a new *PENDING* invoice with added interest.

#Starting up the service

I've added some extra REST endpoints.

GET `/rest/v1/billing/do` runs the billing once and returns the results

POST `/rest/v1/billing/stop` stops the automatic billing or returns an error if the service isn't running.

POST `/rest/v1/billing/start` starts the automatic billing with a specified period, which is sent in the JSON body of the request.
The period can be one of `MONTHLY`, `WEEKLY`, `DAILY` or `CUSTOM`. In case of `CUSTOM`, a value field should also be specified, which is the period in milliseconds. This should be a string, just for simplicity purposes. It's more for testing, but still, could be useful :P

