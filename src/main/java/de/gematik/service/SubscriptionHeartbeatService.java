//package de.gematik.service;
//
//import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
//import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
//import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
//import ca.uhn.fhir.jpa.topic.SubscriptionTopicDispatcher;
//import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
//import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
//import ca.uhn.fhir.rest.param.TokenParam;
//import lombok.RequiredArgsConstructor;
//import org.hl7.fhir.instance.model.api.IBaseResource;
//import org.hl7.fhir.r4.model.*;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.Duration;
//import java.time.Instant;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.stream.Collectors;
//
//@Component
//@RequiredArgsConstructor
//public class SubscriptionHeartbeatService {
//
//	private static final String EXT_HEARTBEAT =
//		"http://hl7.org/fhir/uv/subscriptions-backport/StructureDefinition/backport-heartbeat-period";
//
//	private final DaoRegistry daoRegistry;
//	private final SubscriptionTopicDispatcher dispatcher;
//
//	// Letztes Heartbeat je Subscription
//	private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();
//
//	/** Alle 60s prüfen; tatsächlicher Versand richtet sich nach dem per-Subscription-Intervall. */
//	@Scheduled(fixedDelayString = "PT60S")
//	@Transactional
//	public void run() {
//		// 1) Aktive Subscriptions laden
//		IFhirResourceDao<Subscription> subDao = daoRegistry.getResourceDao(Subscription.class);
//		SystemRequestDetails srd = new SystemRequestDetails();
//
//		SearchParameterMap map = new SearchParameterMap()
//			.add(Subscription.SP_STATUS, new TokenParam(null, "active"));
//
//		List<IBaseResource> resources =
//			subDao.search(map, srd).getAllResources();
//
//		// 2) Aus Subscriptions: Topic + Heartbeat-Periode lesen, „fällige“ Subscriptions pro Topic sammeln
//		Map<String, List<Subscription>> dueByTopic = resources.stream()
//			.map(r -> (Subscription) r)
//			.map(sub -> new SubWithMeta(sub,
//				extractTopicFromCriteria(sub.getCriteria()),
//				readHeartbeatPeriodSeconds(sub)))
//			.filter(m -> m.topic() != null && m.periodSeconds() != null && m.periodSeconds() > 0)
//			.filter(this::isDue)
//			.collect(Collectors.groupingBy(SubWithMeta::topic,
//				Collectors.mapping(SubWithMeta::sub, Collectors.toList())));
//
//		// 3) Pro Topic genau ein Dispatch mit leerer Ressourcenliste (keine Filterkosten)
//		Instant now = Instant.now();
//		for (Map.Entry<String, List<Subscription>> entry : dueByTopic.entrySet()) {
//			String topic = entry.getKey();
//			List<Subscription> dueSubs = entry.getValue();
//			if (dueSubs.isEmpty()) continue;
//
//			int queued = dispatcher.dispatch(topic, List.of(), RestOperationTypeEnum.READ);
//
//			// 4) Nur wenn etwas gequeued wurde, „lastSent“ für die fälligen Subscriptions aktualisieren
//			if (queued > 0) {
//				for (Subscription s : dueSubs) {
//					String key = s.getIdElement().toUnqualifiedVersionless().getValue();
//					lastSent.put(key, now);
//				}
//			}
//		}
//
//		// 5) Aufräumen: Einträge entfernen, deren Subscriptions nicht mehr aktiv sind
//		cleanupStaleEntries(resources);
//	}
//
//	private boolean isDue(SubWithMeta m) {
//		String key = m.sub().getIdElement().toUnqualifiedVersionless().getValue();
//		Instant last = lastSent.getOrDefault(key, Instant.EPOCH);
//		long elapsed = Duration.between(last, Instant.now()).getSeconds();
//		// Mini-„grace“ von 2s gegen Scheduler-Drift
//		return elapsed + 2 >= m.periodSeconds();
//	}
//
//	private void cleanupStaleEntries(List<IBaseResource> activeSubs) {
//		Set<String> activeIds = activeSubs.stream()
//			.map(r -> r.getIdElement().toUnqualifiedVersionless().getValue())
//			.collect(Collectors.toSet());
//		lastSent.keySet().removeIf(id -> !activeIds.contains(id));
//	}
//
//	/** R4 Backport: Topic steckt in criteria: "_topic=<canonical>[&...]" */
//	private static String extractTopicFromCriteria(String criteria) {
//		if (criteria == null) return null;
//		for (String part : criteria.split("&")) {
//			String[] kv = part.split("=", 2);
//			if (kv.length == 2 && "_topic".equals(kv[0])) {
//				return kv[1];
//			}
//		}
//		return null;
//	}
//
//	/** Heartbeat-Periode (Sekunden) aus der Backport-Extension am channel lesen. */
//	private static Integer readHeartbeatPeriodSeconds(Subscription sub) {
//		if (sub.getChannel() == null) return null;
//		for (Extension ext : sub.getChannel().getExtension()) {
//			if (EXT_HEARTBEAT.equals(ext.getUrl()) && ext.getValue() instanceof UnsignedIntType u) {
//				return u.getValue();
//			}
//		}
//		return null;
//	}
//
//	private record SubWithMeta(Subscription sub, String topic, Integer periodSeconds) {}
//}