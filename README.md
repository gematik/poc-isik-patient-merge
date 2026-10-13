[![No Maintenance Intended](http://unmaintained.tech/badge.svg)](http://unmaintained.tech/)
[![Maintenance](https://img.shields.io/badge/Maintained%3F-no-red.svg)](https://bitbucket.org/lbesson/ansi-colors)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

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

Only **REST-hook subscriptions** are mandatory in ISiK and have been implemented in this POC 

### Handshake
When a new subscription is created with status `requested`, the server immediately sends a **handshake notification** to the subscriber’s endpoint.  
- If the handshake succeeds (2xx HTTP response), the subscription is activated (`status=active`).  
- If it fails (connection error or non-2xx response), the subscription is set to `error`.  

This ensures that only reachable subscribers become active.

### Heartbeat
For active subscriptions, the server supports **heartbeat notifications** according to the backport IG.  
- The heartbeat interval is read from the `backport-heartbeat-period` extension on the subscription channel.  
- If due, the server sends an empty notification bundle of type `heartbeat` to the subscriber’s endpoint.  
- This allows the client to detect broken connections even if no real events occur.  

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

## License

Copyright 2025 gematik GmbH

Apache License, Version 2.0

See the [LICENSE](./LICENSE) for the specific language governing permissions and limitations under the License

## Additional Notes and Disclaimer from gematik GmbH

1. Copyright notice: Each published work result is accompanied by an explicit statement of the license conditions for use. These are regularly typical conditions in connection with open source or free software. Programs described/provided/linked here are free software, unless otherwise stated.
2. Permission notice: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
    1. The copyright notice (Item 1) and the permission notice (Item 2) shall be included in all copies or substantial portions of the Software.
    2. The software is provided "as is" without warranty of any kind, either express or implied, including, but not limited to, the warranties of fitness for a particular purpose, merchantability, and/or non-infringement. The authors or copyright holders shall not be liable in any manner whatsoever for any damages or other claims arising from, out of or in connection with the software or the use or other dealings with the software, whether in an action of contract, tort, or otherwise.
    3. The software is the result of research and development activities, therefore not necessarily quality assured and without the character of a liable product. For this reason, gematik does not provide any support or other user assistance (unless otherwise stated in individual cases and without justification of a legal obligation). Furthermore, there is no claim to further development and adaptation of the results to a more current state of the art.
3. Gematik may remove published results temporarily or permanently from the place of publication at any time without prior notice or justification.
4. Please note: Parts of this code may have been generated using AI-supported technology. Please take this into account, especially when troubleshooting, for security analyses and possible adjustments.


## <a name='Contact'></a>Contact

**Team Data – ISiK and ISiP**

For issues and requests please refer to:
https://service.gematik.de/servicedesk/customer/portal/16
