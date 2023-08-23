package com.giaybac.traprange.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.Test;

import com.giaybac.traprange.PDFLayoutTextStripper;

/**
 * How to run this file: 0. Open terminal and move to ${projectRootDir} 1. Copy
 * PO file e.g. to `${projectRootDir}/_Docs/invoice` folder 2. Update file name:
 * String filePath = Paths.get(homeDirectory, "_Docs", "invoice",
 * "sample-invoice.pdf").toString(); 3. Run: mvn compiler:compile -f pom.xml &&
 * mvn test -Dtest=com.giaybac.traprange.test.TestInvoice test
 */

public class TestInvoice {

	private static class Row {
		String barcode;
		Map<String, int[]> headerPositions = null;
		List<String> lines = new ArrayList<>();
		float quantity = -1;

		Row(final Map<String, int[]> headerPositions, final int lineIdx, final String barcode) {
			this.headerPositions = headerPositions;
			this.barcode = barcode;
		}

		public String getDescription() {
			int startIdx = this.headerPositions.get("Item number")[1];
			int endIdx = this.headerPositions.get("Quantity")[0];
			String retVal = "";
			for (int idx = 0; idx < this.lines.size() - 1; idx++) { // exclude the last line
				String line = this.lines.get(idx);
				if (retVal.length() > 0) {
					retVal += " ";
				}
				retVal += line.substring(startIdx, endIdx).trim();
			}
			return retVal;
		}

		@Override
		public String toString() {
			return "barcode: " + this.barcode + ", qtty: " + this.quantity + ", desc: " + this.getDescription();
		}
	}

	private static String[] headers = new String[] { "Barcode", "Item number", "Description", "Quantity", "Unit",
			"Unit price", "Amount" };

	private static final int spaceTolerance = 5;

	private String emptyStringToNull(final String s) {
		return s != null && s.length() == 0 ? null : s;
	}

	private Map<String, int[]> headerPositions(final String headerLine) {
		Map<String, int[]> retVal = new HashMap<>();
		for (String header : TestInvoice.headers) {
			int start = headerLine.indexOf(header);
			retVal.put(header, new int[] { start, start + header.length() - 1 }); // inclusive
		}
		return retVal;
	}

	private boolean isTableEnded(final String line) {
		// Found *PO-\\d+* or `Total amount`
		String s1 = this.match(line, "\\s{5}\\*PO\\-\\d+\\*\\s{5}", 0);
		String s2 = this.match(line, "\\s{5}Total\\samount\\s{5}", 0);
		return s1 != null || s2 != null;
	}

	private boolean isTableHeader(final String line) {
		for (String header : TestInvoice.headers) {
			if (!line.contains(header)) {
				return false;
			}
		}
		return true;
	}

	private String match(final String line, final String pattern, final int groupId) {
		Pattern p = Pattern.compile(pattern);
		Matcher matcher = p.matcher(line);
		if (matcher.find()) {
			return this.emptyStringToNull(matcher.group(groupId));
		}
		return null;
	}

	private String matchBarcode(final String line, final Map<String, int[]> headerPositions) {
		int startIdx = headerPositions.get("Barcode")[0] - TestInvoice.spaceTolerance;
		return this.match(line.substring(startIdx), "\\s+(\\d{10,})\\s+", 1);
	}

	private String matchDate(final String line) {
		return this.match(line, "Date\\.+[^\\d]*(\\d+\\/\\d+\\/\\d{4})", 1);
	}

	private String matchPONumber(final String line) {
		return this.match(line, "\\s{5,}(PO\\-\\d+)\\s{5,}", 1);
	}

	private float matchQuantity(final String line, final Map<String, int[]> headerPositions) {
		int startIdx = headerPositions.get("Quantity")[0] - TestInvoice.spaceTolerance;
		try {
			String substring = line.substring(startIdx);
			String qString = this.match(substring, "\\s?([\\d\\.]+)\\s?", 1);
			return Float.parseFloat(qString);
		} catch (Exception e) {
		}
		return -1;
	}

	private String matchVendor(final String line) {
		return this.match(line, "Vendor\\s*\\:\\s*([^\\s]+)", 1);
	}

	@Test
	public void test() throws IOException {
		try {
			String homeDirectory = System.getProperty("user.dir");
			String filePath = Paths.get(homeDirectory, "_Docs", "invoice", "sample-invoice.pdf").toString();
			File file = new File(filePath);
			PDFParser pdfParser = new PDFParser(new RandomAccessFile(file, "r"));
			pdfParser.parse();
			PDDocument pdDocument = new PDDocument(pdfParser.getDocument());
			PDFTextStripper pdfTextStripper = new PDFLayoutTextStripper();
			String invoiceString = pdfTextStripper.getText(pdDocument);
			String[] lines = invoiceString.split("\n");

			String vendor = null;
			String poNumber = null;
			String date = null;
			Map<String, int[]> headerPositions = null;
			List<Row> rows = new ArrayList<>();
			boolean inTableBody = false;
			for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
				String line = lines[lineIdx];
				vendor = vendor == null ? this.matchVendor(line) : vendor;
				date = date == null ? this.matchDate(line) : date;
				poNumber = poNumber == null ? this.matchPONumber(line) : poNumber;
				if (this.isTableHeader(line)) {
					inTableBody = true;
					headerPositions = this.headerPositions(line);
					continue;
				}
				if (this.isTableEnded(line)) {
					inTableBody = false;
					continue;
				}
				// in table body
				if (inTableBody) {
					String barcode = this.matchBarcode(line, headerPositions);
					if (barcode != null) {
						rows.add(new Row(headerPositions, lineIdx, barcode));
					}
					Row row = rows.size() > 0 ? rows.get(rows.size() - 1) : null;
					if (row != null) {
						row.lines.add(line);
						if (row.quantity < 0) {
							row.quantity = this.matchQuantity(line, headerPositions);
						}
					}
				}
			}
			System.out.println("-------------------------");
			System.out.println("Date: " + date);
			System.out.println("Vendor: " + vendor);
			System.out.println("PO Number: " + poNumber);
			System.out.println("-------------------------");
			System.out.println("Table content:");
			for (Row row : rows) {
				System.out.println(row);
			}
			System.out.println("-------------------------");
		} catch (IOException e) {
			e.printStackTrace();
		}
		;
	}
}
