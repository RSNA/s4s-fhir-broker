package org.hl7.fhir.utilities;

/*
 * #%L
 * HAPI FHIR - Core Library
 * %%
 * Copyright (C) 2014 - 2017 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import java.util.ArrayList;
import java.util.List;

public class Table<T> {

//	private int rowCount;
	private int colCount;
	private List<List<T>> rows = new ArrayList<List<T>>();
	
	public Table(int rowCount, int colCount) {
//		this.rowCount = rowCount;
		this.colCount = colCount;
		for (int r = 0; r < rowCount; r++) {
			rows.add(new ArrayList<T>());
			for (int c = 0; c < colCount; c++) {
				rows.get(r).add(null);
			}
		}
	}

	public void setValue(int r, int c, T value) {
	  rows.get(r).set(c, value);
	}

	public T get(int r, int c) {
  	return rows.get(r).get(c);
	}

	public int colCount() {
		return colCount;
	}

}
