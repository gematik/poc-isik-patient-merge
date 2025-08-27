package de.gematik.provider;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.topic.SubscriptionTopicDispatcher;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Patient.LinkType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Provider for the custom FHIR operation `$patient-merge`.
 * <p>
 * This service merges two Patient resources by deactivating the source patient,
 * linking it to the target patient, and updating the target patient with a reference
 * to the source's identifier. It also dispatches a notification to the patient-merge
 * subscription topic.
 * </p>
 * <ul>
 *   <li>Deactivates the source patient and links it as replaced by the target.</li>
 *   <li>Adds a "replaces" link from the target to the source's identifier.</li>
 *   <li>Updates both patients in the repository.</li>
 *   <li>Dispatches a subscription topic notification for the merge event.</li>
 *   <li>Returns an OperationOutcome indicating success or failure.</li>
 * </ul>
 */
@Service
public class PatientMergeOperationProvider {

	/**
	 * The criteria URL for the patient-merge subscription topic.
	 */
	private static final String MERGE_TOPIC_CRITERIA = "https://gematik.de/fhir/isik/SubscriptionTopic/patient-merge";

	/**
	 * Registry for accessing FHIR resource DAOs.
	 */
	private final DaoRegistry daoRegistry;

	/**
	 * Dispatcher for sending subscription topic notifications.
	 */
	private final SubscriptionTopicDispatcher subscriptionTopicDispatcher;

	/**
	 * Constructs a new PatientMergeOperationProvider.
	 *
	 * @param daoRegistry the DAO registry for FHIR resources
	 * @param subscriptionTopicDispatcher the dispatcher for subscription topic notifications
	 */
	public PatientMergeOperationProvider(
			DaoRegistry daoRegistry, SubscriptionTopicDispatcher subscriptionTopicDispatcher) {
		this.daoRegistry = daoRegistry;
		this.subscriptionTopicDispatcher = subscriptionTopicDispatcher;
	}

	/**
	 * FHIR operation to merge two Patient resources.
	 * <p>
	 * Deactivates the source patient, links it as replaced by the target,
	 * adds a "replaces" link from the target to the source's identifier,
	 * updates both patients, and dispatches a patient-merge notification.
	 * </p>
	 *
	 * @param sourcePatientRef reference to the source patient (to be deactivated)
	 * @param targetPatientRef reference to the target patient (to remain active)
	 * @return OperationOutcome indicating the result of the merge
	 * @throws PreconditionFailedException if the source patient does not have a PID identifier
	 */
	@Operation(name = "$patient-merge")
	public OperationOutcome patientMerge(
			@OperationParam(name = "source-patient", min = 1, max = 1) Reference sourcePatientRef,
			@OperationParam(name = "target-patient", min = 1, max = 1) Reference targetPatientRef) {

		IFhirResourceDao patientDao = daoRegistry.getResourceDao(ResourceType.Patient.name());

		Patient sourcePatient = (Patient) patientDao.read(new IdType(sourcePatientRef.getReference()));
		Patient targetPatient = (Patient) patientDao.read(new IdType(targetPatientRef.getReference()));

		// Deactivate source patient and link as replaced by target
		sourcePatient.setActive(false);
		sourcePatient.addLink().setType(LinkType.REPLACEDBY).setOther(targetPatientRef);

		// Find PID (identifier with type MR) on source patient
		Optional<Identifier> pid = sourcePatient.getIdentifier().stream()
				.filter(i -> i.getType().getCoding().stream()
						.anyMatch(t -> t.getCode().equals("MR")))
				.findFirst();

		if (pid.isEmpty()) {
			throw new PreconditionFailedException("Patients need a populated PID (Identifier.type = MR)");
		}

		// Add "replaces" link from target to source's PID
		targetPatient.addLink().setType(LinkType.REPLACES).getOther().setIdentifier(pid.get());

		// Update both patients in the repository
		patientDao.update(sourcePatient);
		patientDao.update(targetPatient);

		// Dispatch patient-merge subscription topic notification
		subscriptionTopicDispatcher.dispatch(
				MERGE_TOPIC_CRITERIA, List.of(targetPatient), RestOperationTypeEnum.UPDATE);

		// Return OperationOutcome indicating success
		OperationOutcome operationOutcome = new OperationOutcome();
		operationOutcome.addIssue().setSeverity(IssueSeverity.INFORMATION).setDiagnostics("Patient merge successful");
		return operationOutcome;
	}
}
