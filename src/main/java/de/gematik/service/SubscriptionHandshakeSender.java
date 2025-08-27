package de.gematik.service;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.param.TokenParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionHandshakeSender {

	private final DaoRegistry daoRegistry;
	private final ca.uhn.fhir.context.FhirContext fhirContext;

	// NEW: delegate that commits in its own transaction
	private final SubscriptionHandshakeFinalizer finalizer;

	// run the finalizer off-thread to be safely outside the request context
	private final java.util.concurrent.Executor exec = java.util.concurrent.Executors.newSingleThreadExecutor();

	// RestTemplate with sensible timeouts
	private static RestTemplate createRestTemplate() {
		SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
		f.setConnectTimeout(5_000);
		f.setReadTimeout(5_000);
		return new RestTemplate(f);
	}
	private final RestTemplate restTemplate = createRestTemplate();

	@Value("${fhir.server.base:http://localhost:8080/fhir}")
	private String serverBaseUrl;

	public void findByMarkerAndHandshake(String system, String code) {
		IFhirResourceDao<Subscription> subDao = daoRegistry.getResourceDao(Subscription.class);
		SystemRequestDetails srd = new SystemRequestDetails();

		// search Subscription?_tag=system|code
		SearchParameterMap map = new SearchParameterMap().add("_tag", new TokenParam(system, code));
		List<IBaseResource> matches = subDao.search(map, srd).getAllResources();
		if (matches.isEmpty()) {
			log.warn("HS marker not found: {}|{}", system, code);
			return;
		}

		Subscription found = (Subscription) matches.get(matches.size() - 1);
		String id = found.getIdElement().toUnqualifiedVersionless().getValue();
		log.info("HS start: id={} status={} tags={}", id, found.getStatus(), found.getMeta().getTag().size());

		attemptHandshakeAndFinalize(id, system, code);
	}

	private void attemptHandshakeAndFinalize(String subscriptionId, String markSys, String markCode) {
		IFhirResourceDao<Subscription> subDao = daoRegistry.getResourceDao(Subscription.class);
		SystemRequestDetails srd = new SystemRequestDetails();

		// fresh read
		Subscription sub = subDao.read(new IdType(subscriptionId), srd);

		// Only OFF/REQUESTED proceed
		if (sub.getStatus() != Subscription.SubscriptionStatus.OFF &&
			sub.getStatus() != Subscription.SubscriptionStatus.REQUESTED) {
			log.debug("HS skip {} – status now {}", subscriptionId, sub.getStatus());
			return;
		}
		// Must be REST-hook with endpoint
		if (sub.getChannel() == null ||
			sub.getChannel().getType() != Subscription.SubscriptionChannelType.RESTHOOK ||
			sub.getChannel().getEndpoint() == null || sub.getChannel().getEndpoint().isBlank()) {
			log.debug("HS skip {} – no rest-hook endpoint", subscriptionId);
			return;
		}

		boolean ok;
		try {
			Bundle handshake = buildHandshakeBundle(sub);
			String json = fhirContext.newJsonParser().encodeResourceToString(handshake);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.valueOf("application/fhir+json"));

			String endpoint = sub.getChannel().getEndpoint();
			ResponseEntity<String> resp = restTemplate.exchange(
				endpoint, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);

			ok = resp.getStatusCode().is2xxSuccessful();
			log.info("HS POST endpoint={} http={}", endpoint, resp.getStatusCodeValue());

		} catch (Exception e) {
			log.warn("HS EXCEPTION → {} (endpoint={})", subscriptionId, sub.getChannel().getEndpoint(), e);
			ok = false;
		}

		// IMPORTANT: finalize in a separate bean & thread (REQUIRES_NEW tx inside)
		boolean finalOk = ok;
		exec.execute(() -> finalizer.finalizeStatus(subscriptionId, markSys, markCode, finalOk));
	}

	private Bundle buildHandshakeBundle(Subscription sub) {
		Bundle b = new Bundle();
		b.setType(Bundle.BundleType.HISTORY);
		b.getMeta().addProfile(
			"http://hl7.org/fhir/uv/subscriptions-backport/StructureDefinition/backport-subscription-notification-r4");
		b.setTimestamp(new Date());

		Parameters status = new Parameters();
		status.setId(UUID.randomUUID().toString());
		status.getMeta().addProfile(
			"http://hl7.org/fhir/uv/subscriptions-backport/StructureDefinition/backport-subscription-status-r4");

		String subRef = serverBaseUrl + "/" + sub.getIdElement().toUnqualifiedVersionless().getValue();
		status.addParameter().setName("subscription").setValue(new Reference(subRef));
		status.addParameter().setName("topic").setValue(new CanonicalType(sub.getCriteria()));
		status.addParameter().setName("status").setValue(new CodeType("requested"));
		status.addParameter().setName("type").setValue(new CodeType("handshake"));
		status.addParameter().setName("events-since-subscription-start").setValue(new StringType("0"));

		b.addEntry().setFullUrl("urn:uuid:" + status.getId()).setResource(status);
		return b;
	}
}