package ca.uhn.fhir.jpa.demo;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.google.gson.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.AUTH;
import org.hl7.fhir.dstu3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class Utl implements Cmn {

	static String DICOM_RS_BROKER_QIDO_URL = "http://localhost:4567/qido-rs";
	static String DICOM_RS_BROKER_WADO_URL = "http://localhost:4567/wado-rs";
	static String INTROSPECTION_SERVICE_URL = "http://localhost:9004/api/introspect";
	static String IMAGE_ARCHIVE_WADO_RS_URL = "http://localhost:9090/dcm4chee-arc/aets/DCM4CHEE/rs";
	static String PID_LOOKUP_DB_URL = "jdbc:derby:directory:/data/pidLookup;create=true";
	static String DIAGNOSTIC_REPORT_DB_URL = "jdbc:postgresql://localhost:5433/rsnadb";
	static String DIAGNOSTIC_REPORT_PERFORMER_REFERENCE = "Organization/57";
	static String DIAGNOSTIC_REPORT_CODE_STRING = "http://loinc.org|37815-8|CR";

	static {
		String s = null;
		try {
			InputStream is = Utl.class.getClassLoader().getResourceAsStream("utl.properties");
			Properties properties = new Properties();
			properties.load(is);
			is.close();
			s = StringUtils.trimToNull(properties.getProperty("DICOM_RS_BROKER_QIDO_URL"));
			if (s != null) DICOM_RS_BROKER_QIDO_URL = s;
			s = StringUtils.trimToNull(properties.getProperty("DICOM_RS_BROKER_WADO_URL"));
			if (s != null) DICOM_RS_BROKER_WADO_URL = s;
			s = StringUtils.trimToNull(properties.getProperty("INTROSPECTION_SERVICE_URL"));
			if (s != null) INTROSPECTION_SERVICE_URL = s;
			s = StringUtils.trimToNull(properties.getProperty("IMAGE_ARCHIVE_WADO_RS_URL"));
			if (s != null) IMAGE_ARCHIVE_WADO_RS_URL = s;
			s = StringUtils.trimToNull(properties.getProperty("PID_LOOKUP_DB_URL"));
			if (s != null) PID_LOOKUP_DB_URL = s;
			s = StringUtils.trimToNull(properties.getProperty("DIAGNOSTIC_REPORT_DB_URL"));
			if (s != null) DIAGNOSTIC_REPORT_DB_URL = s;
			s = StringUtils.trimToNull(properties.getProperty("DIAGNOSTIC_REPORT_PERFORMER_REFERENCE"));
			if (s != null) DIAGNOSTIC_REPORT_PERFORMER_REFERENCE = s;
			s = StringUtils.trimToNull(properties.getProperty("DIAGNOSTIC_REPORT_CODE_STRING"));
			if (s != null) DIAGNOSTIC_REPORT_CODE_STRING = s;
		} catch (Exception e) {
			System.out.println("Missing/invalid utl.properties.");
		}
		try {
			s = StringUtils.trimToNull(System.getenv("DICOM_RS_BROKER_QIDO_URL"));
			if (s != null) DICOM_RS_BROKER_QIDO_URL = s;
			s = StringUtils.trimToNull(System.getenv("DICOM_RS_BROKER_WADO_URL"));
			if (s != null) DICOM_RS_BROKER_WADO_URL = s;
			s = StringUtils.trimToNull(System.getenv("INTROSPECTION_SERVICE_URL"));
			if (s != null) INTROSPECTION_SERVICE_URL = s;
			s = StringUtils.trimToNull(System.getenv("IMAGE_ARCHIVE_WADO_RS_URL"));
			if (s != null) IMAGE_ARCHIVE_WADO_RS_URL = s;
			s = StringUtils.trimToNull(System.getenv("PID_LOOKUP_DB_URL"));
			if (s != null) PID_LOOKUP_DB_URL = s;
			s = StringUtils.trimToNull(System.getenv("DIAGNOSTIC_REPORT_DB_URL"));
			if (s != null) DIAGNOSTIC_REPORT_DB_URL = s;
			s = StringUtils.trimToNull(System.getenv("DIAGNOSTIC_REPORT_PERFORMER_REFERENCE"));
			if (s != null) DIAGNOSTIC_REPORT_PERFORMER_REFERENCE = s;
			s = StringUtils.trimToNull(System.getenv("DIAGNOSTIC_REPORT_CODE_STRING"));
			if (s != null) DIAGNOSTIC_REPORT_CODE_STRING = s;
		} catch (SecurityException se) {
			System.out.println("Security Exception accessing environment variables.");
		}
	}

	public static String getQidoURL() {
		return DICOM_RS_BROKER_QIDO_URL;
	}
	public static String getWadoURL() {
		return DICOM_RS_BROKER_WADO_URL;
	}
	public static String getArchiveURL() { return IMAGE_ARCHIVE_WADO_RS_URL; }
	public static String getPidLookupDbURL() { return PID_LOOKUP_DB_URL; }
	public static String getDiagnosticReportDbURL() { return DIAGNOSTIC_REPORT_DB_URL; }
	public static String getDiagnosticReportPerformerReference() { return DIAGNOSTIC_REPORT_PERFORMER_REFERENCE; }
	public static String getDiagnosticReportCodeSystem() {
		String[] tokens = DIAGNOSTIC_REPORT_CODE_STRING.split("[|]+");
		return tokens[0];
	}
	public static String getDiagnosticReportCodeCode() {
		String[] tokens = DIAGNOSTIC_REPORT_CODE_STRING.split("[|]+");
		return tokens[1];
	}
	public static String getDiagnosticReportCodeText() {
		String[] tokens = DIAGNOSTIC_REPORT_CODE_STRING.split("[|]+");
		return tokens[2];
	}
	private static Gson gson = new Gson();
	private static FhirContext ctx = FhirContext.forDstu3();

	/**
	 * Gets MRN for patient
	 * @param pat Patient resource
	 * @return the MRN (first MRN for now)
	 * @throws Exception on error, including patient not found.
    */
	private static String getPatientMrn(String pid, Patient pat) throws Exception {

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
		PidLookup.put(pid, mrns.get(0));
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
	 * QIDO
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
				String prefix = Utl.getQidoURL();
				if (cmd.startsWith("/") == false) {
					prefix += "/";
				}
				cmd = prefix + cmd;
			}
		final URL url = new URL(cmd);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Accept-Charset", "utf-8");
		conn.setRequestProperty("Accept-Encoding", "deflate,sdch");
		conn.setRequestProperty("Accept", "application/json");

		conn.setUseCaches(false);
		conn.setDoInput(true);
		System.out.println("**** " + cmd + " ****");
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
		String responseBody = StringUtils.trimToEmpty(writer.toString());
		// cluge
			if (responseBody.startsWith("[") && !responseBody.endsWith("]")) responseBody += "]";
		if (responseBody.isEmpty())
			throw new Exception("Response body empty");

		/*
		 * parse the json returned by the WADO query.
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
	 * Authorization validation using fhir patient reference id
	 * @param pid patient resource reference id
	 * @param authToken authorization token
	 * @param requestedResource resource, or "*"
	 * @param requestedAccess "read", "write", or "*" for both.
	 * @return patient MRN, or null;
	 * @throws AuthenticationException unless token is valid for this patient and requested access.
	 */
	public static String validatePid(String pid, String authToken, String requestedResource, String requestedAccess)
		throws AuthenticationException, Exception {
		if (authToken == null || authToken.isEmpty()) throw new AuthenticationException("Authorization token missing");

		// Query token introspection service
		URL url = null;
		try {
			url = new URL(INTROSPECTION_SERVICE_URL);
			System.out.println("***** " + url.toString() + " *****");
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

			JsonObject responseJson = new JsonParser().parse(responseBody).getAsJsonObject();
			Utl.is (responseJson, "active", "true");
			String scope = Utl.is (responseJson, "scope", "*");
			if (Utl.isScopeAuthorized(requestedResource, requestedAccess, scope) == false)
				throw new AuthenticationException("Authorization failed");
			// pull MRN out of identifier list
			JsonObject patient = responseJson.getAsJsonObject("patient");
			String patientstr = gson.toJson(patient);
			Patient p = ctx.newJsonParser().parseResource(Patient.class, patientstr);
			return getPatientMrn(pid, p);

		} catch (MalformedURLException e ) {
			throw new AuthenticationException(e.getMessage());
		} catch (IOException io) {
			throw new AuthenticationException(io.getMessage());
		}

	}/**
	 * Authorization validation
	 * @param mrn patient medical record number
	 * @param authToken authorization token
	 * @param requestedResource resource, or "*"
	 * @param requestedAccess "read", "write", or "*" for both.
	 * @return String Patient resource id of patient
	 * @throws AuthenticationException unless token is valid for this patient and requested access.
	 */
	public static String validateMrn(String mrn, String authToken, String requestedResource, String requestedAccess)
		throws AuthenticationException, Exception {
		if (authToken == null || authToken.isEmpty()) throw new AuthenticationException("Authorization token missing");

		String pid = StringUtils.trimToNull(PidLookup.get(mrn));
		if (pid == null)
			throw new AuthenticationException("unknown mrn: " + mrn);
		validatePid(pid, authToken, requestedResource, requestedAccess);
		return pid;
	}

	private static String get(JsonObject object, String name) {
		if (!object.has(name)) return null;
		JsonElement element = object.get(name);
		if (element == null) return null;
		return StringUtils.trimToNull(element.getAsString());
	}

	private static String is(JsonObject object, String name, String match) {
		boolean is = true;
		String value = get(object, name);
		if (value == null) is = false;
		if (match.equals("*") == false && value.equalsIgnoreCase(match) == false) is = false;
		if (!is) throw new AuthenticationException("Authorization failed");
		return value;
	}

}
