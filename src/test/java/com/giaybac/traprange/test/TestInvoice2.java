package com.giaybac.traprange.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
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
 * "REINV.pdf").toString(); 3. Run: mvn compiler:compile -f pom.xml && mvn test
 * -Dtest=com.giaybac.traprange.test.TestInvoice2 test
 */

public class TestInvoice2 {

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

			String[] patternStrings = new String[] { "(\\d+)", "(\\d+\\/\\d+\\/\\d+\\s\\d+\\:\\d+)",
					"(\\d+\\/\\d+\\/\\d+\\s\\d+\\:\\d+)", "([a-z0-9A-Z]{5,})" };
			String patternString = "\\s+" + String.join("\\s+", patternStrings) + "\\s+";
			Pattern p = Pattern.compile(patternString);
			for (String line : lines) {
				Matcher matcher = p.matcher(line);
				if (matcher.find()) {
					String orderNo = matcher.group(1);
					String orderDate = matcher.group(2);
					String creationDate = matcher.group(3);
					String supplierCode = matcher.group(4);
					System.out.print("Order No: " + orderNo + ", orderDate: " + orderDate + ", creationDate: "
							+ creationDate + ", supplierCode: " + supplierCode);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		;
	}
}
