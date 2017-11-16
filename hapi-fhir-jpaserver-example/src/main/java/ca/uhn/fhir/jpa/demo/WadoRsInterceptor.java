package ca.uhn.fhir.jpa.demo;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.method.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import jdk.nashorn.internal.runtime.GlobalConstants;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Resource;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class WadoRsInterceptor extends InterceptorAdapter {


	static final String DCM_TAG_PATIENT_ID = "00100020";

	public WadoRsInterceptor() {
		super();
	}

	@Override
	public boolean incomingRequestPreProcessed(HttpServletRequest theRequest, HttpServletResponse theResponse)
		throws AuthenticationException {

		// Catch the /studies request
		String url = theRequest.getRequestURL().toString();
		String cp = theRequest.getContextPath().toLowerCase();
		String sp = theRequest.getServletPath();
		if (url.contains(cp + sp + "/studies/") == false) return true;


		String pid = getStudyPid(cp + sp, theRequest);
		if (pid == null || pid.isEmpty()) throw new AuthenticationException();

		authenticate(pid, theRequest, theResponse);

		// This is the forward
		forwardRequest(cp + sp, theRequest, theResponse);

		return false;
	}

	/**
	 * WADO Query to retrieve patient MRN corresponding to study.
	 * @param prefix original query context path and servlet path, concatenated.
	 * @param req original request.
	 * @return Medical Records number of patient from study, or null. Note:
	 * at this time exceptions are caught and printed, but ignored. This
	 * won't do later on.
	 */
	private String getStudyPid (String prefix,  HttpServletRequest req) {
		try {
			prefix += "/studies/";
			String cmd = req.getRequestURI().substring(prefix.length());
			if (cmd.indexOf("?") > 0) cmd = cmd.substring(0, cmd.indexOf("?"));
			cmd = "studies?StudyInstanceUID=" + cmd;

			List<Map<String, List<String>>> studies = Utl.wadoQuery(cmd, null);
			if (studies.isEmpty())
				throw new Exception("study not found");
			return studies.get(0).get(DCM_TAG_PATIENT_ID).get(0).toString();

		} catch (Exception e) {
			e.printStackTrace();
			// pass
		}
		return null;
	}

	/**
	 * Forwards a "studies" request to WADO RS and returns result.
	 * @param prefix the string in the url immediately preceding "/studies/"
	 * @param req the HttpServletRequest
	 * @param resp the HttpServletResponse
	 */
	private void forwardRequest(String prefix, HttpServletRequest req, HttpServletResponse resp) {
		String method = req.getMethod();
		final boolean hasoutbody = (method.equals("POST"));

		try {
			String cmd = req.getRequestURI().substring(prefix.length());
			final URL url = new URL(Utl.getWadoSrvUrl()  // no trailing slash
				+ cmd
				+ (req.getQueryString() != null ? "?" + req.getQueryString() : ""));
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod(method);

			final Enumeration<String> headers = req.getHeaderNames();
			while (headers.hasMoreElements()) {
				final String header = headers.nextElement();
				final Enumeration<String> values = req.getHeaders(header);
				while (values.hasMoreElements()) {
					final String value = values.nextElement();
					conn.addRequestProperty(header, value);
				}
			}

			conn.setUseCaches(false);
			conn.setDoInput(true);
			conn.setDoOutput(hasoutbody);
			conn.connect();

			final byte[] buffer = new byte[16384];
			while (hasoutbody) {
				final int read = req.getInputStream().read(buffer);
				if (read <= 0) break;
				conn.getOutputStream().write(buffer, 0, read);
			}

			resp.setStatus(conn.getResponseCode());
			for (int i = 0; ; ++i) {
				final String header = conn.getHeaderFieldKey(i);
				if (header == null) break;
				final String value = conn.getHeaderField(i);
				resp.setHeader(header, value);
			}

			InputStream is = conn.getInputStream();
			OutputStream os = resp.getOutputStream();
			IOUtils.copy(is, os);
			is.close();
			os.close();
		} catch (Exception e) {
			e.printStackTrace();
			// pass
		}
	}

	private void authenticate(String pid, HttpServletRequest theRequest, HttpServletResponse theResponse)
		throws AuthenticationException, InvalidRequestException {
		String body = null;
		Bundle bundleResource = null;
		Patient patientResource = null;

		if (!Utl.AUTHENTICATION_ENABLED) return;

		try {
			body = Utl.fhirQuery("Patient?identifier=" + pid);
		} catch (Exception e) {
			throw new AuthenticationException(e.getMessage());
		}
		try {
			FhirContext ctx = FhirContext.forDstu3();
			IParser parser = ctx.newJsonParser();
			bundleResource = parser.parseResource(Bundle.class, body);
			Bundle.BundleEntryComponent firstEntry = bundleResource.getEntryFirstRep();
			Resource resrc = firstEntry.getResource();
			if (resrc instanceof Patient) patientResource = (Patient) resrc;
			else throw new AuthenticationException("invalid patient");
		} catch (DataFormatException dfe) {
			throw new AuthenticationException(dfe.getMessage());
		}
		pid = patientResource.getId();
		String[] tokens = pid.split("/");
		for (int i = 0; i<(tokens.length -1); i++) if (tokens[i].equals("Patient")) pid = tokens[i+1];

		// TODO this is the cludge, until we get consistent test data
		// if (pid.equals("34952")) pid = "smart-1288992";

		String authHdr = StringUtils.trimToEmpty(theRequest.getHeader("Authorization"));
		if (StringUtils.startsWithIgnoreCase(authHdr, "Bearer ") == false)
			throw new AuthenticationException("Invalid Authorization token type");
		String authToken = StringUtils.trimToEmpty(authHdr.substring(6));

		Utl.validate(pid, authToken, "ImageStudy", "read");

	}
}
