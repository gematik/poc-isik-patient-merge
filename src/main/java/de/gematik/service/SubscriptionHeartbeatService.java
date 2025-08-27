package de.gematik.service;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.param.TokenParam;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service responsible for periodically sending heartbeat notifications for active FHIR Subscriptions.
 * Determines which subscriptions are due for a heartbeat based on their configured interval,
 * dispatches heartbeats per topic, and maintains the last sent time for each subscription.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionHeartbeatService {

    /**
     * URL of the backport heartbeat period extension.
     */
	private static final String EXT_HEARTBEAT =
		"http://hl7.org/fhir/uv/subscriptions-backport/StructureDefinition/backport-heartbeat-period";

    /**
     * Registry for accessing FHIR resource DAOs.
     */
	private final DaoRegistry daoRegistry;

    /**
     * Service for dispatching heartbeat notifications.
     */
	private final HeartBeatDispatchService topicNotifyService;

    /**
     * Stores the last heartbeat sent time for each subscription (by ID).
     */
	private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();

    /**
     * Periodically checks all active subscriptions and sends heartbeat notifications
     * for those that are due, based on their configured heartbeat period.
     * Runs every 60 seconds; actual dispatch depends on each subscription's interval.
     */
	@Scheduled(fixedDelayString = "PT60S")
	@Transactional
	public void run() {
        // 1) Load active subscriptions
		IFhirResourceDao<Subscription> subDao = daoRegistry.getResourceDao(Subscription.class);
		SystemRequestDetails srd = new SystemRequestDetails();

		SearchParameterMap map = new SearchParameterMap()
			.add(Subscription.SP_STATUS, new TokenParam(null, "active"));

		List<IBaseResource> resources =
			subDao.search(map, srd).getAllResources();

        // 2) For each subscription, read topic and heartbeat period, collect due subscriptions per topic
		Map<String, List<Subscription>> dueByTopic = resources.stream()
			.map(r -> (Subscription) r)
			.map(sub -> new SubWithMeta(sub,
				extractBackportCanonicalOrNull(sub.getCriteria()),
				readHeartbeatPeriodSeconds(sub)))
			.filter(m -> m.topic() != null && m.periodSeconds() != null && m.periodSeconds() > 0)
			.filter(this::isDue)
			.collect(Collectors.groupingBy(SubWithMeta::topic,
				Collectors.mapping(SubWithMeta::sub, Collectors.toList())));

        // 3) For each topic, dispatch a single heartbeat (empty resource list)
		Instant now = Instant.now();
		for (Map.Entry<String, List<Subscription>> entry : dueByTopic.entrySet()) {
			String topic = entry.getKey();
			List<Subscription> dueSubs = entry.getValue();
			if (dueSubs.isEmpty()) continue;

			int queued = topicNotifyService.dispatchHeartbeat(topic);

            // 4) If a heartbeat was dispatched, update lastSent for the due subscriptions
			if (queued > 0) {
				for (Subscription s : dueSubs) {
					String key = s.getIdElement().toUnqualifiedVersionless().getValue();
					lastSent.put(key, now);
				}
			}
		}

        // 5) Cleanup: remove entries for subscriptions that are no longer active
		cleanupStaleEntries(resources);
	}

    /**
     * Determines if a subscription is due for a heartbeat based on its last sent time and period.
     *
     * @param m subscription metadata
     * @return true if the subscription is due for a heartbeat, false otherwise
     */
	private boolean isDue(SubWithMeta m) {
		String key = m.sub().getIdElement().toUnqualifiedVersionless().getValue();
		Instant last = lastSent.getOrDefault(key, Instant.EPOCH);
		long elapsed = Duration.between(last, Instant.now()).getSeconds();
        // Add a small grace period (2s) to account for scheduler drift
		return elapsed + 2 >= m.periodSeconds();
	}

    /**
     * Removes entries from lastSent for subscriptions that are no longer active.
     *
     * @param activeSubs list of currently active subscriptions
     */
	private void cleanupStaleEntries(List<IBaseResource> activeSubs) {
		Set<String> activeIds = activeSubs.stream()
			.map(r -> r.getIdElement().toUnqualifiedVersionless().getValue())
			.collect(Collectors.toSet());
		lastSent.keySet().removeIf(id -> !activeIds.contains(id));
	}

    /**
     * Extracts the canonical topic URL from the subscription criteria if it is a valid URI.
     * Returns null if the criteria is not a canonical URL.
     *
     * @param criteria the subscription criteria string
     * @return the canonical topic URL, or null if not valid
     */
	private static String extractBackportCanonicalOrNull(String criteria) {
		if (criteria == null) return null;
		String c = criteria.trim();
		if (c.isEmpty()) return null;

        // Exclude classic R4 search queries
		if (c.contains("?") || c.contains("&") || c.contains("=") || c.contains(" ")) {
			return null;
		}

        // Must start with http/https
		if (!(c.startsWith("http://") || c.startsWith("https://"))) {
			return null;
		}

        // Check for syntactically valid URI
		try {
			new URI(c);
		} catch (URISyntaxException e) {
			return null;
		}

		return c;
	}

    /**
     * Reads the heartbeat period (in seconds) from the backport extension on the subscription channel.
     *
     * @param sub the Subscription resource
     * @return the heartbeat period in seconds, or null if not present
     */
	private static Integer readHeartbeatPeriodSeconds(Subscription sub) {
		if (sub.getChannel() == null) return null;
		for (Extension ext : sub.getChannel().getExtension()) {
			if (EXT_HEARTBEAT.equals(ext.getUrl()) && ext.getValue() instanceof UnsignedIntType u) {
				return u.getValue();
			}
		}
		return null;
	}

    /**
     * Record holding a subscription and its associated topic and heartbeat period.
     *
     * @param sub the Subscription resource
     * @param topic the canonical topic URL
     * @param periodSeconds the heartbeat period in seconds
     */
	private record SubWithMeta(Subscription sub, String topic, Integer periodSeconds) {}
}