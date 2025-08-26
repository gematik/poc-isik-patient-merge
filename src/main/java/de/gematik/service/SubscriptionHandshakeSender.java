package de.gematik.service;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionHandshakeSender {

	private final DaoRegistry daoRegistry;
	private final ca.uhn.fhir.context.FhirContext fhirContext;
	private final RestTemplate restTemplate = new RestTemplate();

	/** TODO: aus Konfiguration laden */
	private final String serverBaseUrl = "https://example.org/fhir";

	/**
	 * Sendet einen Handshake an den REST-Hook der Subscription.
	 * Setzt bei Erfolg status=active, sonst status=error (persistiert).
	 */
	public void attemptHandshakeAndUpdate(Subscription sub) {
		if (sub.getChannel() == null
			|| sub.getChannel().getType() != Subscription.SubscriptionChannelType.RESTHOOK
			|| sub.getChannel().getEndpoint() == null
			|| sub.getChannel().getEndpoint().isBlank()) {
			return; // nur REST-Hook mit Endpoint
		}

		IFhirResourceDao<Subscription> subDao = daoRegistry.getResourceDao(Subscription.class);
		SystemRequestDetails srd = new SystemRequestDetails();

		try {
			Bundle handshake = buildHandshakeBundle(sub);
			String json = fhirContext.newJsonParser().encodeResourceToString(handshake);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.valueOf("application/fhir+json"));
			ResponseEntity<String> resp = restTemplate.exchange(
				sub.getChannel().getEndpoint(), HttpMethod.POST, new HttpEntity<>(json, headers), String.class);

			int code = resp.getStatusCodeValue();
			if (code >= 200 && code < 300) {
				sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
				subDao.update(sub, srd);
				log.info("Handshake OK → {} => active (http={})",
					sub.getIdElement().toUnqualifiedVersionless().getValue(), code);
			} else {
				sub.setStatus(Subscription.SubscriptionStatus.ERROR);
				subDao.update(sub, srd);
				log.warn("Handshake FAILED → {} => error (http={})",
					sub.getIdElement().toUnqualifiedVersionless().getValue(), code);
			}
		} catch (Exception e) {
			sub.setStatus(Subscription.SubscriptionStatus.ERROR);
			subDao.update(sub, srd);
			log.warn("Handshake EXCEPTION → {} => error (endpoint={})",
				sub.getIdElement().toUnqualifiedVersionless().getValue(), sub.getChannel().getEndpoint(), e);
		}
	}

	/** Backport-konformes Handshake-Bundle (R4), ohne unnötigen Ballast. */
	private Bundle buildHandshakeBundle(Subscription sub) {
		Bundle b = new Bundle();
		b.setType(Bundle.BundleType.HISTORY);
		b.getMeta().addProfile("http://hl7.org/fhir/uv/subscriptions-backport/StructureDefinition/backport-subscription-notification-r4");
		b.setTimestamp(Date.from(Instant.now()));

		Parameters status = new Parameters();
		status.setId(UUID.randomUUID().toString());
		status.getMeta().addProfile("http://hl7.org/fhir/uv/subscriptions-backport/StructureDefinition/backport-subscription-status-r4");

		String subRef = serverBaseUrl + "/" + sub.getIdElement().toUnqualifiedVersionless().getValue();
		status.addParameter().setName("subscription").setValue(new Reference(subRef));
		status.addParameter().setName("topic").setValue(new CanonicalType(sub.getCriteria())); // criteria = Canonical
		status.addParameter().setName("status").setValue(new CodeType("requested"));
		status.addParameter().setName("type").setValue(new CodeType("handshake"));
		status.addParameter().setName("events-since-subscription-start").setValue(new StringType("0"));

		Bundle.BundleEntryComponent e = b.addEntry();
		e.setFullUrl("urn:uuid:" + status.getId());
		e.setResource(status);

		return b;
	}
}