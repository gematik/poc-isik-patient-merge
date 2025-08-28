package de.gematik.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.searchparam.MatchUrlService;
import ca.uhn.fhir.jpa.subscription.match.registry.ActiveSubscription;
import ca.uhn.fhir.jpa.topic.SubscriptionTopicPayloadBuilder;
import ca.uhn.fhir.jpa.topic.SubscriptionTopicRegistry;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import de.gematik.service.HeartBeatDispatchService.NotificationType;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Payload builder that is aware of heartbeat and handshake notification types.
 * Modifies the payload bundle to reflect the current notification type and
 * removes certain parameters for heartbeat or handshake notifications.
 */
@Primary
@Component
public class HeartbeatAwarePayloadBuilder extends SubscriptionTopicPayloadBuilder {

	/**
	 * Constructs a new HeartbeatAwarePayloadBuilder.
	 *
	 * @param ctx  the FHIR context
	 * @param dao  the DAO registry
	 * @param reg  the subscription topic registry
	 * @param mus  the match URL service
	 */
	public HeartbeatAwarePayloadBuilder(
			FhirContext ctx, DaoRegistry dao, SubscriptionTopicRegistry reg, MatchUrlService mus) {
		super(ctx, dao, reg, mus);
	}

	/**
	 * Builds a FHIR bundle payload for the given resources and subscription.
	 * Updates the bundle to set the notification type and removes event-related parameters
	 * for heartbeat or handshake notifications.
	 *
	 * @param resources the list of resources to include in the bundle
	 * @param sub the active subscription
	 * @param topicUrl the topic URL
	 * @param op the REST operation type
	 * @return the constructed FHIR bundle
	 */
	@Override
	public IBaseBundle buildPayload(
			List<IBaseResource> resources, ActiveSubscription sub, String topicUrl, RestOperationTypeEnum op) {
		IBaseBundle bundle = super.buildPayload(resources, sub, topicUrl, op);

		NotificationType type = HeartBeatDispatchService.currentTypeOrDefault();

		if (bundle instanceof org.hl7.fhir.r4.model.Bundle b && !b.getEntry().isEmpty()) {
			var first = b.getEntryFirstRep().getResource();
			if (first instanceof org.hl7.fhir.r4.model.Parameters params) {
				params.getParameter().stream()
						.filter(p ->
								"type".equals(p.getName()) && p.getValue() instanceof org.hl7.fhir.r4.model.CodeType)
						.findFirst()
						.ifPresent(p -> ((org.hl7.fhir.r4.model.CodeType) p.getValue()).setValue(mapType(type)));

				if (type == NotificationType.HEARTBEAT || type == NotificationType.HANDSHAKE) {
					params.getParameter()
							.removeIf(p -> "notification-event".equals(p.getName())
									|| "events-since-subscription-start".equals(p.getName()));
				}
			}
		}
		return bundle;
	}

	/**
	 * Maps a NotificationType to its corresponding string representation.
	 *
	 * @param t the notification type
	 * @return the string value for the notification type
	 */
	private static String mapType(NotificationType t) {
		return switch (t) {
			case HANDSHAKE -> "handshake";
			case HEARTBEAT -> "heartbeat";
			case QUERY_STATUS -> "query-status";
			case QUERY_EVENT -> "query-event";
			default -> "event-notification";
		};
	}
}
