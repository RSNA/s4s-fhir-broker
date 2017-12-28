package ca.uhn.fhir.jpa.demo;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "S4S_MRN_TO_PID")
public class MrnToPid implements Serializable {

	@Id
	private String mrn = null;

	private String pid = null;

	public MrnToPid() {}

	public String getMrn() {
		return mrn;
	}

	public void setMrn(String mrn) {
		this.mrn = mrn;
	}

	public String getPid() {
		return pid;
	}

	public void setPid(String pid) {
		this.pid = pid;
	}
}
