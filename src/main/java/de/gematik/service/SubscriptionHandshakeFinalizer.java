package de.gematik.service;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionHandshakeFinalizer {

	private final DaoRegistry daoRegistry;

  /**
   * Commit the final status in an independent transaction.
   * Runs OFF the request thread (caller executes via an Executor).
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