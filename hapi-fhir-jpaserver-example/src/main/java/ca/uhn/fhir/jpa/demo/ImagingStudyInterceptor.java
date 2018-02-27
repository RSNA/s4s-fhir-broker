package ca.uhn.fhir.jpa.demo;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.method.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;

import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import com.google.gson.*;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.dstu3.model.codesystems.EndpointConnectionType;
import org.hl7.fhir.dstu3.model.codesystems.EndpointConnectionTypeEnumFactory;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class ImagingStudyInterceptor extends InterceptorAdapter implements Cmn {

	public ImagingStudyInterceptor() {
		super();
	}

	@Override
	public boolean incomingRequestPostProcessed(RequestDetails theRequestDetails,
															  HttpServletRequest theRequest, HttpServletResponse theResponse)
		throws AuthenticationException {

		try {

		// ImagingStudy searches.
		String rn = theRequestDetails.getResourceName();
		if (rn == null || rn.equals("ImagingStudy") == false) return true;
		RequestTypeEnum rt = theRequestDetails.getRequestType();
		if (rt == null || rt != RequestTypeEnum.GET) return true;
		RestOperationTypeEnum ot = theRequestDetails.getRestOperationType();
		if (ot == null || ot != RestOperationTypeEnum.SEARCH_TYPE) return true;
		System.out.println("ImageStudy intercepted");

		String mrn = authenticate(theRequestDetails, theRequest, theResponse);

		String url = theRequestDetails.getCompleteUrl();

		Map<String, String[]> fhirParams = theRequestDetails.getParameters();

		String pid = null;
		String lu = null;
		String patientReferenceStr = null;

		for (String key : fhirParams.keySet()) {
			String[] value = fhirParams.get(key);
			if (key.equalsIgnoreCase("patient")) {
				pid = value[0];
				continue;
			}
			if (key.equalsIgnoreCase("_lastUpdated")) {
				lu = value[0];
			}
		}

		if (pid == null)
			throw new InvalidRequestException("Required parameter 'patient' not found.");

		patientReferenceStr = "Patient/" + pid;

		String body = wadoQuery(mrn, lu, patientReferenceStr, url);
		theResponse.addHeader("Content-Type", "application/fhir+json");
		try {
			theResponse.getWriter().write(body);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			String em = "Error writing httpresponse body " + ioe.getMessage();
			throw new InternalErrorException(em, ioe);
		}

		return false;

		} catch (Exception e) {
			e.printStackTrace();
			String em = "Error processing image request " + e.getMessage();
			throw new InternalErrorException(em, e);
		}
	}

	/**
	 * Processes an ImageStudy query by FHIR Patient by forwarding it as a WADO RS query by PatientID
	 * @param mrn Patient Medical Record Number
	 * @param lu last updated date. null for all studies, yyyyMMddhhMMss (or some prefix) to exclude studies before then.
	 * @param patientReferenceStr The FHIR Patient reference string, for example, Patient/1234
	 * @param queryUrl The original FHIR ImageStudy query (gets put in the Bundle resource).
	 * @return A Bundle containing 0 or more ImageStudy resources, or an error resource.
	 */
	private String wadoQuery(String mrn, String lu, String patientReferenceStr, String queryUrl) {
		String cmd = null;

		try {
			List<Map<String, List<String>>> dcmCodeMaps = Utl.wadoQuery("/studies?PatientID=" + mrn, lu);
			List<ImagingStudy> studies = new ArrayList<ImagingStudy>();

			for (Map<String, List<String>> dcmCodeMap : dcmCodeMaps) {
				// These entries may need 'fleshing out', for example code set UIDs.

				ImagingStudy study = new ImagingStudy();
				study.setPatient(new Reference(patientReferenceStr));

				String s = getFirstValue(dcmCodeMap, DCM_TAG_STUDY_UID);
				if (isThere(s)) {
					study.setUidElement(new OidType("urn:oid:" + s));
					String str = Utl.getArchiveURL() + "/studies/" + s;
					// contained Endpoint reference
					Endpoint ce = new Endpoint();
					ce.setId("wado-endpoint-id");
					ce.setStatus(Endpoint.EndpointStatus.ACTIVE);
					// connection type
					Coding ect = new Coding();
					ect.setSystem("http://hl7.org/fhir/endpoint-connection-type");
					ect.setCode("dicom-wado-rs");
					ce.setConnectionType(ect);
					// payload type
					CodeableConcept ept = new CodeableConcept();
					ept.setText("DICOM WADO-RS");
					ce.addPayloadType(ept);
					ce.setAddress(str);
					study.addContained(ce);
					study.addEndpoint(new Reference().setReference("#wado-endpoint-id"));
				}

				s = getFirstValue(dcmCodeMap, DCM_TAG_ACCESSION);
				if (isThere(s))
					study.setAccession(new Identifier().setValue(s));

				s = getFirstValue(dcmCodeMap, DCM_TAG_STUDY_ID);
				if (isThere(s))
					study.addIdentifier(new Identifier().setValue(s));

				s = getFirstValue(dcmCodeMap, DCM_TAG_INSTANCE_AVAILABILITY);
				if (isThere(s))
					study.setAvailability(ImagingStudy.InstanceAvailability.fromCode(s));

				List<String> sl = dcmCodeMap.get(DCM_TAG_MODALITIES);
				if (sl != null) {
					for (String l : sl) {
						if (isThere(l))
							study.addModalityList(new Coding().setCode(l));
					}
				}

				s = getFirstValue(dcmCodeMap, DCM_TAG_REF_PHYS);
				if (isThere(s))
					study.setReferrer(new Reference().setDisplay(s));

				s = getFirstValue(dcmCodeMap, DCM_TAG_RETRIEVE_URL);
				if (isThere(s))
					study.addEndpoint(new Reference().setReference(s));

				s = getFirstValue(dcmCodeMap, DCM_TAG_NUM_SERIES);
				if (isThere(s))
					study.setNumberOfSeries(Integer.parseInt(s));

				s = getFirstValue(dcmCodeMap, DCM_TAG_NUM_INSTANCES);
				if (isThere(s))
					study.setNumberOfInstances(Integer.parseInt(s));

				String d = getFirstValue(dcmCodeMap, DCM_TAG_STUDY_DATE);
				String t = getFirstValue(dcmCodeMap, DCM_TAG_STUDY_TIME);
				if (isThere(t))
				   t = t.substring(0, t.indexOf("."));
				if (d.length() == 8) {
					String fmt = "yyyyMMdd";
					if (t.length() == 6) {
						fmt += "HHmmss";
						d += t;
					}
					SimpleDateFormat sdf = new SimpleDateFormat(fmt);
					Date sd = null;
					try {
						sd = sdf.parse(d);
					} catch (Exception e) {
					}
					;
					if (sd != null)
						study.setStarted(sd);
				}

				studies.add(study);

			} // pass json entries (studies)

			Bundle bundle = new Bundle();
			bundle.setId(UUID.randomUUID().toString());
			bundle.addLink(new Bundle.BundleLinkComponent().setRelation("self").setUrl(queryUrl));
			bundle.setType(Bundle.BundleType.SEARCHSET);
			bundle.setTotal(studies.size());

			for (ImagingStudy study : studies) {
				Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
				entry.setResource(study);
				bundle.addEntry(entry);
			}

			FhirContext ctx = FhirContext.forDstu3();
			IParser parser = ctx.newJsonParser();
			String body = parser.encodeResourceToString(bundle);

			return body;


		} catch (Exception e) {
			e.printStackTrace();
			String em = "Error processing image request " + e.getMessage() + " on command " + cmd;
			throw new InternalErrorException(em, e);
		}
	}

	private String getFirstValue(Map<String, List<String>> dcmCodeMap, String code) {
		List<String> lst = dcmCodeMap.get(code);
		if (lst != null && lst.isEmpty() == false) return lst.get(0);
		return null;
	}

	private boolean isThere(String val) {
		return val != null && val.isEmpty() == false;
	}

	private String authenticate(RequestDetails theRequestDetails,
									  HttpServletRequest theRequest, HttpServletResponse theResponse)
		throws AuthenticationException, InvalidRequestException, Exception {


		// Get, validate pid parameter
		Map<String, String[]> requestParameters = theRequestDetails.getParameters();
		if (requestParameters == null) throw new AuthenticationException("required parameter missing");
		if (requestParameters.containsKey("patient") == false)
			throw new AuthenticationException("required parameter 'patient' missing");
		String pid = StringUtils.trimToEmpty(requestParameters.get("patient")[0]);

		// Get, validate authorization token
		List<String> authHeaderFields = theRequestDetails.getHeaders("Authorization");
		if (authHeaderFields == null) throw new AuthenticationException("required header 'Authorization' missing");
		if (authHeaderFields.size() != 1) throw new AuthenticationException("Authorization header invalid format");
		String authTokenType = StringUtils.trimToEmpty(authHeaderFields.get(0));
		if (StringUtils.startsWithIgnoreCase(authTokenType, "Bearer ") == false)
			throw new AuthenticationException("Invalid Authorization token type");
		String authToken = StringUtils.trimToEmpty(authTokenType.substring(6));

		return Utl.validatePid(pid, authToken, "DiagnosticReport", "read");

	}
}
