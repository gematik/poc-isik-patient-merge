package de.gematik.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.searchparam.MatchUrlService;
import ca.uhn.fhir.jpa.subscription.match.registry.ActiveSubscription;
import ca.uhn.fhir.jpa.topic.SubscriptionTopicPayloadBuilder;
import ca.uhn.fhir.jpa.topic.SubscriptionTopicRegistry;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;

import de.gematik.service.TopicNotifyService.NotificationType;
import java.util.List;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class HeartbeatAwarePayloadBuilder extends SubscriptionTopicPayloadBuilder {

	public HeartbeatAwarePayloadBuilder(FhirContext ctx, DaoRegistry dao, SubscriptionTopicRegistry reg, MatchUrlService mus) {
		super(ctx, dao, reg, mus);
	}

	@Override
	public IBaseBundle buildPayload(List<IBaseResource> resources, ActiveSubscription sub, String topicUrl, RestOperationTypeEnum op) {
		IBaseBundle bundle = super.buildPayload(resources, sub, topicUrl, op);

		NotificationType type = TopicNotifyService.currentTypeOrDefault();

		if (bundle instanceof org.hl7.fhir.r4.model.Bundle b && !b.getEntry().isEmpty()) {
			var first = b.getEntryFirstRep().getResource();
			if (first instanceof org.hl7.fhir.r4.model.Parameters params) {
				params.getParameter().stream()
					.filter(p -> "type".equals(p.getName()) && p.getValue() instanceof org.hl7.fhir.r4.model.CodeType)
					.findFirst()
					.ifPresent(p -> ((org.hl7.fhir.r4.model.CodeType) p.getValue())
						.setValue(mapType(type)));

				if (type == NotificationType.HEARTBEAT || type == NotificationType.HANDSHAKE) {
					params.getParameter().removeIf(p ->
						"notification-event".equals(p.getName()) ||
							"events-since-subscription-start".equals(p.getName()));
				}
			}
		}
		return bundle;
	}

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