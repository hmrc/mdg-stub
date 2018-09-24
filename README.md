# mdg-stub

[![Build Status](https://travis-ci.org/hmrc/mdg-stub.svg)](https://travis-ci.org/hmrc/mdg-stub) [ ![Download](https://api.bintray.com/packages/hmrc/releases/mdg-stub/images/download.svg) ](https://bintray.com/hmrc/releases/mdg-stub/_latestVersion)


This repository contains stub of the MDG service. It accepts file transfer requests and allows to run and test
services relying on integration with MDG (e.g. `file-transmission`)

## Functionality

The service accepts file transfer a format supported by MDG.

The service has one endpoint: `http://localhost:9576/mdg-stub/request` which accepts POST requests with file transfers.

The format accepted by MDG is specified as a XML Schema in the following file: [mdg-schema.xml](conf/mdg-schema.xsd).

Sample request document can be found [here](test/resources/validRequest.xml).

In case the request is valid it immediately returns 204. In case the request is invalid it return HTTP 400 error.
The mock also allows to simulate erroneous behaviour of MDG. If properly prepared request is sent (see Mocking errors section),
HTTP 500 error is returned.

### Mocking errors

The mock allows to simulate errors returned by MDG.
This can be done by creating a MDG request that in `properties` section contains aproperty with key `SHOULD_FAIL` and value `true` to the XML request.
[Here](test/resources/validRequestCausingSimulatedFailure.xml) is an example request that triggers an error.

## Running the service

The service can be run locally either directly or using service manager.

To run service locally you have to execute the following command:

`sbt run`

To run service using service manager you have to execute the following command:

`sm --start MDG_STUB`

In both cases the service will be accepting requests on the endpoint `http://localhost:9576/mdg-stub/request`

## Related projects, useful links:
* [file-transmission](https://github.com/hmrc/file-transmission) - service that uses MDG

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")