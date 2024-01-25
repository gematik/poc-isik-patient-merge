package de.gematik.provider;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.subscription.triggering.ISubscriptionTriggeringSvc;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Patient.LinkType;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Subscription;
import org.springframework.stereotype.Service;

@Service
public class PatientMergeOperationProvider {

	private static final String MERGE_TOPIC_CRITERIA = "https://gematik.de/fhir/isik/v4/Basismodul/topics/patient-merge";
	private final DaoRegistry daoRegistry;
	private final ISubscriptionTriggeringSvc subscriptionTriggeringSvc;

	public PatientMergeOperationProvider(DaoRegistry daoRegistry,
		ISubscriptionTriggeringSvc subscriptionTriggeringSvc) {
		this.daoRegistry = daoRegistry;
		this.subscriptionTriggeringSvc = subscriptionTriggeringSvc;
	}

	@Operation(name = "$patient-merge")
	public OperationOutcome patientMerge(
		@OperationParam(name = "source-patient", min = 1, max = 1) Reference sourcePatientRef,
		@OperationParam(name = "target-patient", min = 1, max = 1) Reference targetPatientRef) {

		IFhirResourceDao patientDao = daoRegistry.getResourceDao(ResourceType.Patient.name());

		Patient sourcePatient = (Patient) patientDao.read(
			new IdType(sourcePatientRef.getReference()));
		Patient targetPatient = (Patient) patientDao.read(
			new IdType(targetPatientRef.getReference()));

		sourcePatient.setActive(false);
		sourcePatient.addLink().setType(LinkType.REPLACEDBY).setOther(targetPatientRef);

		targetPatient.addLink().setType(LinkType.REPLACES).setOther(sourcePatientRef);

		patientDao.update(sourcePatient);
		patientDao.update(targetPatient);

		//trigger Subscriptions
//		IFhirResourceDao subDao = daoRegistry.getResourceDao(ResourceType.Subscription.name());
//		SearchParameterMap searchParameterMap = new SearchParameterMap();
//		List<IBaseResource> allResources = subDao.search(searchParameterMap).getAllResources();
//
//		List<Subscription> mergeSubscriptions = allResources.stream().map(r -> (Subscription) r)
//			.filter(r -> r.getCriteria().equals(MERGE_TOPIC_CRITERIA)).collect(Collectors.toList());
//
//		List<String> mergeSubIds = mergeSubscriptions.stream().map(Resource::getId)
//			.collect(Collectors.toList());
//		List<IPrimitiveType<String>> resourceIds = List.of(
//			new StringType(sourcePatientRef.getReference()));
//
//		mergeSubIds.forEach(
//			s -> subscriptionTriggeringSvc.triggerSubscription(resourceIds, null, new IdType(s)));

		return new OperationOutcome();
	}

}
