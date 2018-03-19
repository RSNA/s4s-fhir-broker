package ca.uhn.fhir.jpa.demo;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.method.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import com.sun.org.apache.bcel.internal.classfile.Code;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.Date;

public class DiagnosticReportInterceptor extends InterceptorAdapter {

	private static Connection conn = null;
	private static PreparedStatement queryPidOnly = null;
	private static PreparedStatement queryPidDate = null;

	private void connectToEdge() {
		try {
			System.out.println("\n\n### Connecting to EdgeServer #####");
			Class.forName("org.postgresql.Driver");
			conn = DriverManager.getConnection(Utl.getDiagnosticReportDbURL(), "edge", "psword");
			queryPidOnly = conn.prepareStatement("SELECT * FROM v_exam_status WHERE mrn = ?");
			queryPidDate = conn.prepareStatement("SELECT * FROM v_exam_status WHERE mrn = ? AND status_timestamp >= ?");
			System.out.println("queryPidOnly: " + queryPidOnly);
			System.out.println("queryPidDate: " + queryPidDate);
			System.out.println("### Success #####\n");
		} catch (Throwable e) {
			System.out.println("\n\n## Failed ##\n\n");
			System.out.println(Utl.getDiagnosticReportDbURL());
			System.out.println(e.getMessage());
			e.printStackTrace();
			System.out.println("\n\n####\n\n");
		}
	}

	public DiagnosticReportInterceptor() { super(); }

	@Override
	public boolean incomingRequestPostProcessed(RequestDetails theRequestDetails,
															  HttpServletRequest theRequest, HttpServletResponse theResponse)
		throws AuthenticationException {
		connectToEdge();

		try {

			// ImagingStudy searches.
			String rn = theRequestDetails.getResourceName();
			if (rn == null || rn.equals("DiagnosticReport") == false) return true;
			RequestTypeEnum rt = theRequestDetails.getRequestType();
			if (rt == null || rt != RequestTypeEnum.GET) return true;
			RestOperationTypeEnum ot = theRequestDetails.getRestOperationType();
			if (ot == null || ot != RestOperationTypeEnum.SEARCH_TYPE) return true;
			System.out.println("DiagnosticReport intercepted");

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

			ResultSet result = null;
			try {
				if (lu == null) {
					queryPidOnly.setString(1, mrn);
					result = queryPidOnly.executeQuery();
				} else {
					queryPidDate.setString(1, mrn);
					queryPidDate.setString(2, lu);
					result = queryPidDate.executeQuery();
				}
			}  catch (SQLException e) {
				e.printStackTrace();
				try {
					if (result != null) result.close();
				} catch (SQLException se) {
					System.out.println("#####\n" + se.getMessage());
					se.printStackTrace();
				}
			}


			Bundle bundle = new Bundle();
			bundle.setId(UUID.randomUUID().toString());
			bundle.addLink(new Bundle.BundleLinkComponent().setRelation("self").setUrl("queryUrl"));
			bundle.setType(Bundle.BundleType.SEARCHSET);
			int numberOfReports = 0;
			while (result.next()) {
				numberOfReports++;
				DiagnosticReport dr = new DiagnosticReport();

				// TODO These mappings are preliminary, actual mapping has not been decided.

				/* report_id => identifier  (ignored for now)
				Integer ri = result.getInt("report_id");
				List<Identifier> ids = new ArrayList<>();
				ids.add(new Identifier().setValue(ri.toString()));
				dr.setIdentifier(ids);
				*/

				/* status => status (for now, only FINAL)
				   Note: sample db had only "finalized" reports, which are mapped to DiagnosticReportStatus code "final".
				   Will all reports be "final"? probably not, so we need mappings. Here we presume that all the other
				   codes will be the same as the DiagnosticReportStatus code, failing which the code is unknown.
				String rs = result.getString("status").toUpperCase();
				DiagnosticReport.DiagnosticReportStatus rsc = null;
				if (rs.equalsIgnoreCase("FINALIZED")) {
					rsc = DiagnosticReport.DiagnosticReportStatus.FINAL;
				} else {
					rsc = DiagnosticReport.DiagnosticReportStatus.valueOf(rs);
				}
				if (rsc == null) rsc = DiagnosticReport.DiagnosticReportStatus.UNKNOWN;
				dr.setStatus(rsc);
				 */
				dr.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);

				// pid => subject
				dr.setSubject(new Reference().setReference("Patient/" + pid));

				// status_timestamp ==> effective[x]
				Date dt= result.getTimestamp("status_timestamp");
				DateTimeType effective = new DateTimeType(dt, TemporalPrecisionEnum.SECOND);
				dr.setEffective(effective);

				// modified_date ==> issued
				dt = result.getTimestamp("modified_date");
				dr.setIssued(dt);

				// System parameters => code
				Coding cdn = new Coding();
				cdn.setSystem(Utl.getDiagnosticReportCodeSystem());
				cdn.setCode(Utl.getDiagnosticReportCodeCode());
				cdn.setDisplay(Utl.getDiagnosticReportCodeText());
				List<Coding> cdns = new ArrayList<>();
				cdns.add(cdn);
				CodeableConcept cd = new CodeableConcept();
				cd.setCoding(cdns);
				cd.setText(Utl.getDiagnosticReportCodeText());
				dr.setCode(cd);

				/* System parameter => performer (ignored for now
				DiagnosticReport.DiagnosticReportPerformerComponent pc = new DiagnosticReport.DiagnosticReportPerformerComponent();
				pc.setActor(new Reference(Utl.getDiagnosticReportPerformerReference()));
				List<DiagnosticReport.DiagnosticReportPerformerComponent> pcs = new ArrayList<>();
				dr.setPerformer(pcs);
				*/

				// report_text => presentedForm
				String txt = result.getString("report_text");
				byte[] bytes = Base64.getEncoder().encode(txt.getBytes("UTF-8"));

				Attachment att = new Attachment();
				att.setContentType("application/text");
				att.setLanguage("en-US");
				att.setData(bytes);
				att.setTitle(result.getString("exam_description"));
				List<Attachment> atts = new ArrayList<>();
				atts.add(att);
				dr.setPresentedForm(atts);

				// Add DiagnosticReport resource to bundle
				Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
				entry.setResource(dr);
				bundle.addEntry(entry);
			} // EO process SQL query rows


			bundle.setTotal(numberOfReports);

			FhirContext ctx = FhirContext.forDstu3();
			IParser parser = ctx.newJsonParser();
			String body = parser.encodeResourceToString(bundle);

			theResponse.addHeader("Content-Type", "application/fhir+json");
			try {
				theResponse.getWriter().write(body);
			} catch (IOException ioe) {
				ioe.printStackTrace();
				String em = "Error writing httpresponse body: " + ioe.getMessage();
				System.out.println("#####\n" + em);
				throw new InternalErrorException(em, ioe);
			}

			return false;

		} catch (Exception e) {
			e.printStackTrace();
			String em = "Error processing diagnostic report request " + e.getMessage();
			System.out.println("#####\n" + em);
			throw new InternalErrorException(em, e);
		}
	} // EO incomingRequestPostProcessed method


	private String authenticate(RequestDetails theRequestDetails,
										 HttpServletRequest theRequest, HttpServletResponse theResponse)
		throws AuthenticationException, InvalidRequestException, Exception {
		connectToEdge();

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

		return Utl.validatePid(pid, authToken, "ImageStudy", "read");

	}

} // EO DiagnosticReportInterceptor class
