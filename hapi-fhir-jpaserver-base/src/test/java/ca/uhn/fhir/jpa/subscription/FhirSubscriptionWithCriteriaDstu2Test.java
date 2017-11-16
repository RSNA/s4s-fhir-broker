package ca.uhn.fhir.jpa.subscription;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import ca.uhn.fhir.jpa.dao.DaoConfig;
import ca.uhn.fhir.jpa.provider.BaseResourceProviderDstu2Test;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.dstu2.resource.Subscription;
import ca.uhn.fhir.model.dstu2.resource.Subscription.Channel;
import ca.uhn.fhir.model.dstu2.valueset.ObservationStatusEnum;
import ca.uhn.fhir.model.dstu2.valueset.SubscriptionChannelTypeEnum;
import ca.uhn.fhir.model.dstu2.valueset.SubscriptionStatusEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.EncodingEnum;

/**
 * Adds a FHIR subscription with criteria through the rest interface. Then creates a websocket with the id of the
 * subscription
 * <p>
 * Note: This test only returns a ping with the subscription id, Check FhirSubscriptionWithSubscriptionIdDstu3Test for
 * a test that returns the xml of the observation
 * <p>
 * To execute the following test, execute it the following way:
 * 0. execute 'clean' test
 * 1. Execute the 'createSubscription' test
 * 2. Update the subscription id in the 'attachWebSocket' test
 * 3. Execute the 'attachWebSocket' test
 * 4. Execute the 'sendObservation' test
 * 5. Look in the 'attachWebSocket' terminal execution and wait for your ping with the subscription id
 */
public class FhirSubscriptionWithCriteriaDstu2Test extends BaseResourceProviderDstu2Test {
	private static final Logger ourLog = org.slf4j.LoggerFactory.getLogger(FhirSubscriptionWithCriteriaDstu2Test.class);

	private String myPatientId;
	private String mySubscriptionId;
	private WebSocketClient myWebSocketClient;
	private SocketImplementation mySocketImplementation;

	@After
	public void after() throws Exception {
		super.after();
		myDaoConfig.setSubscriptionEnabled(new DaoConfig().isSubscriptionEnabled());
		myDaoConfig.setSubscriptionPollDelay(new DaoConfig().getSubscriptionPollDelay());
	}
	
	@Before
	public void before() throws Exception {
		super.before();
		
		myDaoConfig.setSubscriptionEnabled(true);
		myDaoConfig.setSubscriptionPollDelay(0L);
		
		/*
		 * Create patient
		 */
		
		Patient patient = FhirDstu2Util.getPatient();
		MethodOutcome methodOutcome = ourClient.create().resource(patient).execute();
		myPatientId = methodOutcome.getId().getIdPart();

		/* 
		 * Create subscription
		 */
		Subscription subscription = new Subscription();
		subscription.setReason("Monitor new neonatal function (note, age will be determined by the monitor)");
		subscription.setStatus(SubscriptionStatusEnum.ACTIVE);
		// subscription.setCriteria("Observation?subject=Patient/" + PATIENT_ID);
		subscription.setCriteria("Observation?code=SNOMED-CT|82313006&_format=xml");

		Channel channel = new Channel();
		channel.setType(SubscriptionChannelTypeEnum.WEBSOCKET);
		channel.setPayload("application/json");
		subscription.setChannel(channel);

		methodOutcome = ourClient.create().resource(subscription).execute();
		mySubscriptionId = methodOutcome.getId().getIdPart();
		
		/*
		 * Attach websocket
		 */

		myWebSocketClient = new WebSocketClient();
		mySocketImplementation = new SocketImplementation(mySubscriptionId, EncodingEnum.JSON);

		myWebSocketClient.start();
		URI echoUri = new URI("ws://localhost:" + ourPort + "/websocket/dstu2");
		ClientUpgradeRequest request = new ClientUpgradeRequest();
		ourLog.info("Connecting to : {}", echoUri);
		Future<Session> connection = myWebSocketClient.connect(mySocketImplementation, echoUri, request);
		Session session = connection.get(2, TimeUnit.SECONDS);
		
		ourLog.info("Connected to WS: {}", session.isOpen());
	}

	@After
	public void afterCloseWebsocket() throws Exception {
		ourLog.info("Shutting down websocket client");
		myWebSocketClient.stop();
	}
	
	@Test
	public void createObservation() throws Exception {
		Observation observation = new Observation();
		CodeableConceptDt cc = new CodeableConceptDt();
		observation.setCode(cc);
		CodingDt coding = cc.addCoding();
		coding.setCode("82313006");
		coding.setSystem("SNOMED-CT");
		ResourceReferenceDt reference = new ResourceReferenceDt();
		reference.setReference("Patient/" + myPatientId);
		observation.setSubject(reference);
		observation.setStatus(ObservationStatusEnum.FINAL);

		MethodOutcome methodOutcome2 = ourClient.create().resource(observation).execute();
		String observationId = methodOutcome2.getId().getIdPart();
		observation.setId(observationId);

		ourLog.info("Observation id generated by server is: " + observationId);
		
		int changes = mySubscriptionDao.pollForNewUndeliveredResources();
		ourLog.info("Polling showed {}", changes);
		assertEquals(1, changes);

		Thread.sleep(2000);
		
		ourLog.info("WS Messages: {}", mySocketImplementation.getMessages());
		assertThat(mySocketImplementation.getMessages(), contains("bound " + mySubscriptionId, "ping " + mySubscriptionId));
	}

	@Test
	public void createObservationThatDoesNotMatch() throws Exception {
		Observation observation = new Observation();
		CodeableConceptDt cc = new CodeableConceptDt();
		observation.setCode(cc);
		CodingDt coding = cc.addCoding();
		coding.setCode("8231");
		coding.setSystem("SNOMED-CT");
		ResourceReferenceDt reference = new ResourceReferenceDt();
		reference.setReference("Patient/" + myPatientId);
		observation.setSubject(reference);
		observation.setStatus(ObservationStatusEnum.FINAL);

		MethodOutcome methodOutcome2 = ourClient.create().resource(observation).execute();
		String observationId = methodOutcome2.getId().getIdPart();
		observation.setId(observationId);

		ourLog.info("Observation id generated by server is: " + observationId);
		
		int changes = mySubscriptionDao.pollForNewUndeliveredResources();
		ourLog.info("Polling showed {}", changes);
		assertEquals(0, changes);

		Thread.sleep(2000);
		
		ourLog.info("WS Messages: {}", mySocketImplementation.getMessages());
		assertThat(mySocketImplementation.getMessages(), contains("bound " + mySubscriptionId));
	}
}
