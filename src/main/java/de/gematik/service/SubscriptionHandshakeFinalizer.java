package de.gematik.service;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

/**
 * Service responsible for finalizing the status of a FHIR Subscription resource
 * after a handshake process. This is executed in a new transaction, typically
 * off the request thread, to ensure the final status is committed independently.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionHandshakeFinalizer {

    /**
     * Registry for accessing FHIR resource DAOs.
     */
	private final DaoRegistry daoRegistry;

  /**
     * Finalizes the status of a Subscription resource by updating its status and removing
     * a specific tag, all within a new transaction. This method is intended to be called
     * asynchronously (e.g., via an Executor).
     *
     * @param subscriptionId the ID of the Subscription resource to update
     * @param markSys the system of the tag to remove
     * @param markCode the code of the tag to remove
     * @param ok whether the handshake was successful (true for ACTIVE, false for ERROR)
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
	public void finalizeStatus(String subscriptionId, String markSys, String markCode, boolean ok) {
		IFhirResourceDao<Subscription> subDao = daoRegistry.getResourceDao(Subscription.class);

    // fresh read in NEW tx/EM
    Subscription latest = subDao.read(new IdType(subscriptionId), null);

		if (latest.getStatus() == Subscription.SubscriptionStatus.OFF ||
			latest.getStatus() == Subscription.SubscriptionStatus.REQUESTED) {

			latest.setStatus(ok ? Subscription.SubscriptionStatus.ACTIVE : Subscription.SubscriptionStatus.ERROR);
      latest.getMeta().getTag().forEach(t ->
          log.debug("HS tag before rm: sys='{}' code='{}'", t.getSystem(), t.getCode()));
			latest.getMeta().getTag().removeIf(t -> markSys.equals(t.getSystem()) && markCode.equals(t.getCode()));

      var outcome = subDao.update(latest);
			String newVid = outcome.getId().getVersionIdPart();

      Subscription verify = subDao.read(new IdType(subscriptionId));
      log.info("HS finalize: id={} -> {} (version={}, tags={})",
          subscriptionId, verify.getStatus(), newVid, verify.getMeta().getTag().size());
		} else {
			log.debug("HS finalize skipped – status now {}", latest.getStatus());
		}
	}
}