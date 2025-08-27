package de.gematik.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.storage.TransactionDetails;
import de.gematik.service.SubscriptionHandshakeSender;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Subscription;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Interceptor that handles the creation of FHIR Subscription resources to enforce a handshake process.
 * <p>
 * When a Subscription is created with status REQUESTED, this interceptor:
 * <ul>
 *   <li>Prevents auto-activation by setting the status to OFF before storage.</li>
 *   <li>Adds a one-time tag to the Subscription for later identification.</li>
 *   <li>Registers a post-commit callback to initiate the handshake process via {@link SubscriptionHandshakeSender}.</li>
 * </ul>
 * This ensures that Subscriptions are only activated after a successful handshake with the endpoint.
 * </p>
 */
@Component
@Interceptor
@RequiredArgsConstructor
public class SubscriptionCreateHandshakeInterceptor {
    /**
     * System identifier for the handshake marker tag.
     */
	private static final String MARK_SYS = "urn:gematik:handshake";
    /**
     * Prefix for the handshake marker code.
     */
	private static final String MARK_CODE_PREFIX = "pending-";
    /**
     * Service responsible for sending handshake requests and finalizing Subscription status.
     */
	private final SubscriptionHandshakeSender handshakeSender;

    /**
     * Intercepts the creation of Subscription resources before they are stored.
     * <p>
     * If the resource is a Subscription with status REQUESTED, this method:
     * <ul>
     *   <li>Sets its status to OFF to prevent auto-activation.</li>
     *   <li>Adds a unique tag for handshake tracking.</li>
     *   <li>Registers a post-commit callback to trigger the handshake process.</li>
     * </ul>
     * </p>
     *
     * @param resource the resource being created
     * @param rd the request details
     * @param tx the transaction details
     */
	@Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
	public void onPreStorageCreate(IBaseResource resource,
		RequestDetails rd,
		TransactionDetails tx) {
		if (!(resource instanceof Subscription sub)) {
			return;
		}
		if (sub.getStatus() != Subscription.SubscriptionStatus.REQUESTED) {
			return;
		}

		// prevent auto-activation
		sub.setStatus(Subscription.SubscriptionStatus.OFF);

		// attach a one-time tag to find it post-commit
		String token = MARK_CODE_PREFIX + java.util.UUID.randomUUID();
		sub.getMeta().addTag().setSystem(MARK_SYS).setCode(token);

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override public void afterCommit() {
				handshakeSender.findByMarkerAndHandshake(MARK_SYS, token); // will remove tag inside
			}
		});
	}
}