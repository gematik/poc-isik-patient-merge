package de.gematik.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Spring configuration class for registering HAPI FHIR subscription-related beans.
 * <p>
 * Imports the following configurations:
 * <ul>
 *   <li>{@code SubscriptionTopicConfig}: Provides {@code SubscriptionTopicRegistry} and related beans.</li>
 *   <li>{@code SubscriptionProcessorConfig}: Configures the subscription matching and processing pipeline.</li>
 * </ul>
 * This class enables FHIR subscription topic and processor support in the application context.
 * </p>
 */
@Configuration
@Import({
	ca.uhn.fhir.jpa.topic.SubscriptionTopicConfig.class,                 // provides SubscriptionTopicRegistry, etc.
	ca.uhn.fhir.jpa.subscription.match.config.SubscriptionProcessorConfig.class
})
public class HapiSubscriptionBeans {}