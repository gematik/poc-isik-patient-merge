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

@Component
@Interceptor
@RequiredArgsConstructor
public class SubscriptionCreateHandshakeInterceptor {

	private final SubscriptionHandshakeSender handshakeSender; // dein Service mit RestTemplate

	@Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
	public void onCreate(IBaseResource resource, RequestDetails rd, TransactionDetails tx) {
		if (!(resource instanceof Subscription sub)) {
			return;
		}
		if (sub.getStatus() != Subscription.SubscriptionStatus.REQUESTED) {
			return;
		}

		// nur wenn Client 'requested' schickt → wir setzen auf 'off'
		if (sub.getStatus() == Subscription.SubscriptionStatus.REQUESTED) {
			sub.setStatus(Subscription.SubscriptionStatus.OFF);

			// NACH Commit Handshake versuchen; der HandshakeSender setzt dann active/error
			TransactionSynchronizationManager.registerSynchronization(
				new TransactionSynchronization() {
					@Override
					public void afterCommit() {
						handshakeSender.attemptHandshakeAndUpdate(sub);
					}
				});
		}
	}
}