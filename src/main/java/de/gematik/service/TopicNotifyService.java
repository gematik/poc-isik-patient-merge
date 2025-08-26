package de.gematik.service;

import ca.uhn.fhir.jpa.topic.SubscriptionTopicDispatcher;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TopicNotifyService {

	public enum NotificationType {
		HANDSHAKE, HEARTBEAT, EVENT_NOTIFICATION, QUERY_STATUS, QUERY_EVENT
	}

	private static final ThreadLocal<NotificationType> TL_TYPE = new ThreadLocal<>();

	private final SubscriptionTopicDispatcher dispatcher;

	public int dispatchEvent(String topicUrl, List<IBaseResource> resources, RestOperationTypeEnum op) {
		TL_TYPE.set(NotificationType.EVENT_NOTIFICATION);
		try { return dispatcher.dispatch(topicUrl, resources, op); }
		finally { TL_TYPE.remove(); }
	}

	public int dispatchHeartbeat(String topicUrl) {
		TL_TYPE.set(NotificationType.HEARTBEAT);
		try { return dispatcher.dispatch(topicUrl, List.of(), RestOperationTypeEnum.UPDATE); }
		finally { TL_TYPE.remove(); }
	}

	public int dispatchHandshake(String topicUrl) {
		TL_TYPE.set(NotificationType.HANDSHAKE);
		try { return dispatcher.dispatch(topicUrl, List.of(), RestOperationTypeEnum.UPDATE); }
		finally { TL_TYPE.remove(); }
	}

	static NotificationType currentTypeOrDefault() {
		return Optional.ofNullable(TL_TYPE.get()).orElse(NotificationType.EVENT_NOTIFICATION);
	}
}

