package ca.uhn.fhir.jpa.demo;

import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

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


		String mrn = getStudyMrn(cp + sp, theRequest);
		if (mrn == null || mrn.isEmpty()) throw new AuthenticationException();

		authenticate(mrn, theRequest, theResponse);

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
	private String getStudyMrn (String prefix,  HttpServletRequest req) {
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
			final URL url = new URL(Utl.getWadoURL()  // no trailing slash
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
			System.out.println("**** " + cmd + " *****");
			conn.connect();

			final byte[] buffer = new byte[16384];
			while (hasoutbody) {
				final int read = req.getInputStream().read(buffer);
				if (read <= 0) break;
				conn.getOutputStream().write(buffer, 0, read);
			}
			try {
				if(conn.getResponseCode() == 503) {
					System.out.println("Waiting for connection.");
					Thread.sleep(1000);
				} else {
					System.out.println("Response status: " + Integer.toString(conn.getResponseCode()));
				}
			} catch (Throwable e) {
				System.out.println("Response status: " + Integer.toString(conn.getResponseCode()));
			}
			if(conn.getResponseCode() == 503){
				System.out.println("\n\n #### Waiting for image transfer. Re-run to complete. ####\n\n");
			} else {
				resp.setStatus(conn.getResponseCode());
				for (int i = 0; ; ++i) {
					final String header = conn.getHeaderFieldKey(i);
					final String value = conn.getHeaderField(i);
					if (header == null && value == null) break;
					if (header != null) resp.setHeader(header, value);
				}

				InputStream is = conn.getInputStream();
				OutputStream os = resp.getOutputStream();
				IOUtils.copy(is, os);
				is.close();
				os.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
			// pass
		}
	}

	private void authenticate(String mrn, HttpServletRequest theRequest, HttpServletResponse theResponse)
		throws AuthenticationException, InvalidRequestException{

		String authHdr = StringUtils.trimToEmpty(theRequest.getHeader("Authorization"));
		if (StringUtils.startsWithIgnoreCase(authHdr, "Bearer ") == false)
			throw new AuthenticationException("Invalid Authorization token type");
		String authToken = StringUtils.trimToEmpty(authHdr.substring(6));

		try {
			Utl.validateMrn(mrn, authToken, "ImageStudy", "read");
		} catch (Exception e) {
			throw new AuthenticationException(e.getMessage());
		}

	}
}
