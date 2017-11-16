package ca.uhn.fhir.jpa.demo;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.AUTH;
import org.hl7.fhir.dstu3.model.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class Utl implements Cmn {

	// TODO For development only
	static final String FHIR_SERVER_URL = "http://localhost:8080/baseDstu3";
	static final String WADO_SERVER_URL = "http://localhost:9090/dcm4chee-arc/aets/DCM4CHEE/rs";
	static final String INTROSPECTION_SERVICE_URL = "http://localhost:9004/api/introspect";
	public static final boolean AUTHENTICATION_ENABLED = true;

	public static String getWadoSrvUrl() {
		return WADO_SERVER_URL;
	}

	/**
	 * Gets MRN for patient reference.
	 * @param pid the patient resource id
	 * @return the MRN (first MRN for now)
	 * @throws Exception on error, including patient not found.
	 */
	public static String getPatientMrn(String pid) throws Exception {

		// TODO this is the cludge, until we get consistent test data
		if (pid.equals("smart-1288992")) pid = "34952";

		String body = fhirQuery("/Patient/" + pid);

		FhirContext ctx = FhirContext.forDstu3();

// Create a JSON parser
		IParser parser = ctx.newJsonParser();
		Patient pat = parser.parseResource(Patient.class, body);

		List<String> mrns = new ArrayList<String>();
		nxtId: for (Identifier id : pat.getIdentifier()) {
			if (id.hasType() && id.hasValue()) {
				CodeableConcept cc = id.getType();
				if (cc.hasCoding()) {
					List<Coding> codings = cc.getCoding();
					for (Coding coding : codings) {
						if (coding.hasCode() && coding.getCode().startsWith("MR")) {
							mrns.add(id.getValue());
							continue nxtId;
						}
					}
				}
			}
		}

		if (mrns.isEmpty()) return null;
		return mrns.get(0);
	}

	/**
	 * WADO RS query for studies for patient.
	 * @param mrn patient medical record number
	 * @param lastUpdated if not null, represents a datetime; studies started before this
	 *                    should be ignored. looks for yyyyMMddhhMMss, or any prefix thereof.
	 *                    All non-numerics will be striped.
	 * @return List with one entry for each returned study. Each entry will be map of dcm tags with corresponding
	 * value(s) List.
	 * @throws Exception on error.
	 */
	public static List<Map<String, List<String>>> wadoQueryMrn(String mrn, String lastUpdated)
		throws Exception {

		// Creates a string which can be matched to study start time for last updated check.
		if (lastUpdated != null) {
			lastUpdated = lastUpdated.replaceAll("[^\\d]", "");
			if (lastUpdated.isEmpty()) lastUpdated = null;
		}

		return wadoQuery("/studies?PatientID=" + mrn, lastUpdated);
	}

	/**
	 * WADO query
	 * @param cmd URL to query
	 * @param lastUpdated if not null, represents a datetime; studies started before this
	 *                    should be ignored. looks for yyyyMMddhhMMss, or any prefix thereof.
	 *                    All non-numerics will be striped.
	 * @return List with one entry for each returned study. Each entry will be map of dcm tags with corresponding
	 * value(s) List.
	 * @throws Exception on error.
	 */
		public static List<Map<String, List<String>>> wadoQuery(String cmd, String lastUpdated)
		throws Exception {
			if (cmd.startsWith("http") == false) {
				String prefix = Utl.getWadoSrvUrl();
				if (cmd.startsWith("/") == false) {
					prefix += "/";
				}
				cmd = prefix + cmd;
			}
		final URL url = new URL(cmd);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");

		conn.setUseCaches(false);
		conn.setDoInput(true);
		conn.connect();

		int responseCode = conn.getResponseCode();
		if (responseCode != 200) throw new Exception("invalid returned status " + responseCode);

		Map<String, List<String>> responseHeaders = conn.getHeaderFields();
		List<String> responseContentTypes = responseHeaders.get("Content-Type");
		if (responseContentTypes == null)
			throw new Exception("Required header missing, 'Content-Type'");
		if (responseContentTypes.size() != 1)
			throw new Exception("Required header invalid, 'Content-Type'");
		String responseContentType = responseContentTypes.get(0);
		if (responseContentType == null)
			throw new Exception("Required header empty, 'Content-Type'");
		if (responseContentType.equalsIgnoreCase("application/dicom+json") == false)
			throw new Exception("Content-Type " + responseContentType + " not supported");

		StringWriter writer = new StringWriter();
		IOUtils.copy(conn.getInputStream(), writer, "UTF-8");
		String responseBody = writer.toString();
		if (responseBody == null || responseBody.isEmpty())
			throw new Exception("Response body empty");

		/*
		 * parse the json returned by the WADO query. At this point we presume the
		 * format is valid. An Exception will be thrown if it isn't (I think).
	    */

		List<Map<String, List<String>>> studies = new ArrayList<Map<String, List<String>>>();
		// The highest level is an array.
		JsonArray responseJson = new JsonParser().parse(responseBody).getAsJsonArray();
		// pass the array members
		for (int i = 0; i < responseJson.size(); i++) {
			// each member of the array is a linked map of the members
			Set<Map.Entry<String, JsonElement>> members = responseJson.get(i).getAsJsonObject().entrySet();
			// Pass the map, getting the keys (DICOM tags) and values as strings.
			Map<String, List<String>> dcmCodeMap = new HashMap<String, List<String>>();
			for (Map.Entry<String, JsonElement> member : members) {
				String dcmKey = member.getKey();
				List<String> dcmValues = new ArrayList<String>();
				try {
					JsonArray dcmJsonValues = member.getValue().getAsJsonObject().get("Value").getAsJsonArray();
					for (int k = 0; k < dcmJsonValues.size(); k++) {
						String dcmv = dcmJsonValues.get(k).toString();
						dcmv = dcmv.replaceAll("^\"|\"$", "");
						dcmValues.add(dcmv);
					}
				} catch (NullPointerException e) {
					System.out.println("null ptr for key " + dcmKey);
				}
				if (dcmValues.isEmpty()) dcmValues.add("");
				dcmCodeMap.put(dcmKey, dcmValues);
			}
			if (lastUpdated != null) {
				String s = dcmCodeMap.get(DCM_TAG_STUDY_DATE).get(0) + dcmCodeMap.get(DCM_TAG_STUDY_TIME).get(0);
				if (lastUpdated.compareTo(s) > 0) continue;
			}
			studies.add(dcmCodeMap);
		}
		return studies;
	}

	/**
	 * FHIR query
	 * @param cmd URL. If it doesn't begin with "http", the test fhir server base will be prefixed.
	 * @return String body of the response.
	 * @throws Exception on error.
	 */
	public static String fhirQuery(String cmd) throws Exception {
		if (cmd.startsWith("http") == false) {
			String prefix = FHIR_SERVER_URL;
			if (cmd.startsWith("/") == false) {
				prefix += "/";
			}
			cmd = prefix + cmd;
		}final URL url = new URL(cmd);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Accept-Charset", "utf-8");
		conn.setRequestProperty("Accept", "application/fhir+json;q=1.0, application/json+fhir;q=0.9");
	//	conn.setRequestProperty("Accept-Encoding", "gzip");

		conn.setUseCaches(false);
		conn.setDoInput(true);
		conn.connect();

		int responseCode = conn.getResponseCode();
		if (responseCode != 200) throw new Exception("invalid returned status " + responseCode);

		Map<String, List<String>> responseHeaders = conn.getHeaderFields();
		List<String> responseContentTypes = responseHeaders.get("Content-Type");
		if (responseContentTypes == null)
			throw new Exception("Required header missing, 'Content-Type'");
		if (responseContentTypes.size() != 1)
			throw new Exception("Required header invalid, 'Content-Type'");
		String responseContentType = responseContentTypes.get(0);
		if (responseContentType == null)
			throw new Exception("Required header empty, 'Content-Type'");
		if (responseContentType.contains("json") == false)
			throw new Exception("Content-Type " + responseContentType + " not supported");

		StringWriter writer = new StringWriter();
		IOUtils.copy(conn.getInputStream(), writer, "UTF-8");
		String responseBody = writer.toString();
		if (responseBody == null || responseBody.isEmpty())
			throw new Exception("Response body empty");

		return responseBody;
	}

	/**
	 * Determines if a requested scope has been granted.
	 * @param requestedResource FHIR Resource name or "*" for all FHIR resources
	 * @param requestedAccess "read", "write", or "*" for read and write
	 * @param grantedScopes All scopes granted by SMART.
	 * @return true if requested scope has been granted, false otherwise,
	 * including errors.
	 */
	public static boolean isScopeAuthorized(String requestedResource, String requestedAccess, String grantedScopes) {
		requestedResource = StringUtils.trimToEmpty(requestedResource);
		requestedAccess = StringUtils.trimToEmpty(requestedAccess.toLowerCase());
		grantedScopes = StringUtils.trimToEmpty(grantedScopes);
		if (requestedResource.isEmpty() || grantedScopes.isEmpty()) return false;
		if (isOneOf(requestedAccess, "read", "write", "*") == false) return false;
		boolean needRead = true;
		boolean needWrite = true;
		if (requestedAccess.equals("read")) needWrite = false;
		if (requestedAccess.equals("write")) needRead = false;
		for (String grantedScope : grantedScopes.split("\\s+")) {
			String[] tokens = grantedScope.split("[/\\.]");
			String resource = tokens[1];
			String grant = tokens[2];
			if (isOneOf(resource, "*", requestedResource) == false) continue;
			if (grant.equals("*")) return true;
			if (grant.equals("read")) needRead = false;
			if (grant.equals("write")) needWrite = false;
			if (!needRead && !needWrite) return true;
		}
		return false;
	}

	/**
	 * Does string match any of the matches
	 * @param str to example
	 * @param matches to match against
	 * @return true if string is equal to any of the matches, false otherwise.
	 */
	public static boolean isOneOf(String str, String... matches) {
		for (String match : matches) if (str.equals(match)) return true;
		return false;
	}


	/**
	 * Authorization validation
	 * @param pid patient resource id
	 * @param authToken authorization token
	 * @param requestedResource resource, or "*"
	 * @param requestedAccess "read", "write", or "*" for both.
	 * @throws AuthenticationException unless token is valid for this patient and requested access.
	 */
	public static void validate(String pid, String authToken, String requestedResource, String requestedAccess)
		throws AuthenticationException {
		if (pid == null || pid.isEmpty()) throw new AuthenticationException("Missing/Invalid Patient id");
		if (authToken == null || authToken.isEmpty()) throw new AuthenticationException("Authorization token missing");

		// Query token instrospection service
		URL url = null;
		try {
			url = new URL(INTROSPECTION_SERVICE_URL);
			String postData = "token=" + authToken + "&patient=" + pid;
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setUseCaches(false);
			conn.setDoInput(true);
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setRequestProperty("Content-Length", "" + postData.getBytes().length);
			conn.setRequestProperty("Content-Language", "en-US");
			OutputStream os = conn.getOutputStream();
			os.write(postData.getBytes("UTF-8"));
			os.close();
			conn.connect();

			if (conn.getResponseCode() != 200) throw new AuthenticationException("Authorization failed");

			Map<String, List<String>> responseHeaders = conn.getHeaderFields();
			List<String> responseContentTypes = responseHeaders.get("Content-Type");
			if (responseContentTypes == null)
				throw new AuthenticationException("Required header missing, 'Content-Type'");
			if (responseContentTypes.size() != 1)
				throw new AuthenticationException("Required header invalid, 'Content-Type'");
			String responseContentType = responseContentTypes.get(0);
			if (responseContentType == null)
				throw new AuthenticationException("Required header empty, 'Content-Type'");
			if (responseContentType.contains("json") == false)
				throw new AuthenticationException("Content-Type " + responseContentType + " not supported");

			StringWriter writer = new StringWriter();
			IOUtils.copy(conn.getInputStream(), writer, "UTF-8");
			String responseBody = writer.toString();
			if (responseBody == null || responseBody.isEmpty())
				throw new AuthenticationException("Response body empty");

			JsonObject responseJson = (JsonObject) new JsonParser().parse(responseBody);
			JsonElement scope = responseJson.get("scope");
			if (scope == null) throw new AuthenticationException("Authorization failed");
			String scopes = scope.getAsString();
			if (Utl.isScopeAuthorized(requestedResource, requestedAccess, scopes)) return;
			throw new AuthenticationException("Authorization failed");

		} catch (MalformedURLException e ) {
			throw new AuthenticationException(e.getMessage());
		} catch (IOException io) {
			throw new AuthenticationException(io.getMessage());
		}

	}
}
