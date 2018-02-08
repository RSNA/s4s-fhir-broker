package ca.uhn.fhir.jpa.demo;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.*;
import java.util.Properties;
import java.util.Set;

import static java.nio.file.Files.getPosixFilePermissions;

public class PidLookup {
   // live url
	private static final String dbURL = "jdbc:derby:directory:/var/lib/jetty/data/pidLookup;create=true";
	// local testing url
	// private static final String dbURL = "jdbc:derby:directory:data/pidLookup;create=true";
	private static Connection connection = null;
	private static PreparedStatement insert = null;
	private static PreparedStatement update = null;
	private static PreparedStatement query = null;

	static {
		String create =
		"CREATE TABLE pid_lookup ( " +
			"pid_in  varchar(255) NOT NULL, " +
			"pid_out varchar(255) NOT NULL, " +
			"PRIMARY KEY (pid_in))";

			try {
				connection = DriverManager.getConnection(dbURL);
				insert = connection.prepareStatement("INSERT INTO pid_lookup VALUES (?, ?)");
				update = connection.prepareStatement("UPDATE pid_lookup SET pid_out = ? WHERE pid_in = ?");
				query = connection.prepareStatement("SELECT * FROM pid_lookup WHERE pid_in = ?");
				Statement stmt = connection.createStatement();
				stmt.executeUpdate(create);
				stmt.close();
			} catch (SQLException e ) {
				e.printStackTrace();
			}
		}

	public static void put(String pidIn, String pidOut) {
		// In testing environment, the relationship will often already be there.
		String s = PidLookup.get(pidIn);
		if (s != null && s.equals(pidOut)) return;
		// Insert if not there, update if there and not the same
		Integer i = null;
		try {
		if (s == null) {

			// INSERT INTO pid_lookup VALUES (pidIn, pidOut)
			insert.setString(1, pidIn);
			insert.setString(2, pidOut);
			i = insert.executeUpdate();
		} else {
			// UPDATE pid_lookup SET pid_out = pidOut WHERE pid_in = pidIn
			update.setString(1, pidOut);
			update.setString(2, pidIn);
			i = update.executeUpdate();
		}
			String stl = "result was " + i.toString();
		} catch (SQLException e) {

			e.printStackTrace();
		}
	}

	public static String get(String pidIn) {
		ResultSet result = null;
		String pidOut = null;
		try {
			// SELECT * FROM pid_lookup WHERE pid_in = ?
			query.setString(1, pidIn);
			result = query.executeQuery();
			if (result.next()) {
				pidOut = result.getString("pid_out");
			}
		} catch (SQLException e) {

			e.printStackTrace();
		} finally {
			try {
				if (result != null) result.close();
			} catch (SQLException se) {

				se.printStackTrace();
			}
		}
		return pidOut;
	}
}
