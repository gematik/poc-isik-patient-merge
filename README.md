<img align="right" width="250" height="47" src="/material/Gematik_Logo_Flag.svg"/> <br/>

# Prove of Concept for ISiK Patient merge

<!-- vscode-markdown-toc -->
* [About the Project](#AbouttheProject)
	* [Prerequisites](#Prerequisites)
		* [Fast Track Running locally](#FastTrackRunninglocally)
	* [How to test](#Howtotest)
	* [Components](#Components)
		* [ Server - HAPI-Server (modified)](#Server-HAPI-Servermodified)
		* [Client - Postman](#Client-Postman)
* [Contact](#Contact)

<!-- vscode-markdown-toc-config
	numbering=false
	autoSave=true
	/vscode-markdown-toc-config -->
<!-- /vscode-markdown-toc -->

## <a name='AbouttheProject'></a>About the Project
This POC aims to prove a Patient merge Notification based on FHIR Subscription Topics (see [Subscriptions R5 Backport](https://hl7.org/fhir/uv/subscriptions-backport/)).

### <a name='Prerequisites'></a>Prerequisites

- Postman to use the Postman Collection of the poc-server
- A rest endpoint accepting the notification bundle (POST on /Bundle)
  - this can be done with Postman:
    - Select: Mock servers, create mock server (+)
    - Request Method: POST
    - Next
    - choose a name and create the server
    - use the displayed url as the endpoint url of your subscription in step: Postman: 2. Subscribe to Patient merge topic
  - Instead of Postman, a second FHIR server can be used to store the notification bundles
    - easiest way to run another fhir server is to use the hapi-fhir docker image: `docker run -p 8081:8080 -e hapi.fhir.allowed_bundle_types=COLLECTION,DOCUMENT,MESSAGE,TRANSACTION,TRANSACTIONRESPONSE,BATCH,BATCHRESPONSE,HISTORY,SEARCHSET hapiproject/hapi:latest`

POC is built on top of [HAPI-FHIR](https://github.com/hapifhir/hapi-fhir-jpaserver-starter).

#### <a name='FastTrackRunninglocally'></a>Fast Track Running locally

 Using jetty
```bash
mvn clean jetty:run -U
```
For more information on running see [HAPI-FHIR](https://github.com/hapifhir/hapi-fhir-jpaserver-starter).

### <a name='Howtotest'></a>How to test
The following steps simulate the merge notification workflow (see Postman Collection in folder `PostmanCollection`):

1. create patients which should be merged
   1. (Postman: 1. Send Patients)
1. Subscribe to Topic: "http://hl7.org/SubscriptionTopic/patient-merge"
   1. (Postman: 2. Subscribe to Patient merge topic)
   2. modify `.endpoint` to your FHIR-Endpoint which should receive the notifications, Postman Postman mock-server can be used as test-endpoint
1. trigger a patient merge `$patient-merge`with source and target patient
   2.  (Postman: 3. merge patients)
1. receive a notification Bundle

**Known issue:**  if you are using the Mock Servers from Postman, a stacktrace will be shown in hapi, as the response has the content-type: text/html, and application/fhir+json or application/fhir+xml is expected.
This has no impact on the delivery of the notification.

### <a name='Components'></a>Components

#### <a name='Server-HAPI-Servermodified'></a> Server - HAPI-Server (modified)
This extended hapi server supports a `$patient-merge` operation and serves as a "KIS" mock-up:
- Patient merge operation ($patient-merge) was implemented (as MVP) and is used to trigger a patient merge
- Support for Subscription criteria based on ...

#### <a name='Client-Postman'></a>Client - Postman
Postman Collection with examples (folder: `PostmanCollection`)

## <a name='Contact'></a>Contact

**Team Data – ISiK and ISiP**

For issues and requests please refer to:
https://service.gematik.de/servicedesk/customer/portal/16
