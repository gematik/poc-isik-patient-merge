package de.gematik.service;

import ca.uhn.fhir.jpa.topic.SubscriptionTopicDispatcher;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for dispatching heartbeat notifications using a {@link SubscriptionTopicDispatcher}.
 * Maintains a thread-local notification type to track the context of the current dispatch operation.
 */
@Service
@RequiredArgsConstructor
public class HeartBeatDispatchService {

	public enum NotificationType {
		HANDSHAKE,
		HEARTBEAT,
		EVENT_NOTIFICATION,
		QUERY_STATUS,
		QUERY_EVENT
	}

	/**
	 * Thread-local storage for the current notification type.
	 */
	private static final ThreadLocal<NotificationType> TL_TYPE = new ThreadLocal<>();

	private final SubscriptionTopicDispatcher dispatcher;

	/**
	 * Dispatches a heartbeat notification for the given topic URL.
	 * Sets the thread-local notification type to HEARTBEAT for the duration of the dispatch.
	 *
	 * @param topicUrl the URL of the topic to dispatch the heartbeat to
	 * @return the number of notifications dispatched
	 */
	public int dispatchHeartbeat(String topicUrl) {
		TL_TYPE.set(NotificationType.HEARTBEAT);
		try {
			return dispatcher.dispatch(topicUrl, List.of(), RestOperationTypeEnum.UPDATE);
		} finally {
			TL_TYPE.remove();
		}
	}

	/**
	 * Returns the current thread-local notification type, or EVENT_NOTIFICATION if not set.
	 *
	 * @return the current notification type or EVENT_NOTIFICATION as default
	 */
	static NotificationType currentTypeOrDefault() {
		return Optional.ofNullable(TL_TYPE.get()).orElse(NotificationType.EVENT_NOTIFICATION);
	}
}
