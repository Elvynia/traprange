/**
 * Copyright (C) 2015, GIAYBAC
 *
 * Released under the MIT license
 */
package com.giaybac.traprange.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Test;

import com.giaybac.traprange.PDFTableExtractor;
import com.giaybac.traprange.entity.Table;

/**
 *
 * @author Tho Mar 22, 2015 5:36:40 PM
 */
public class TestExtractor {
	@Test
	public void test() throws IOException {
		String homeDirectory = System.getProperty("user.dir");

		String sourceDirectory = Paths.get(homeDirectory, "_Docs").toString();
		String resultDirectory = Paths.get(homeDirectory, "_Docs", "result").toString();

		for (int idx = 0; idx < 5; idx++) {
			PDFTableExtractor extractor = new PDFTableExtractor()
					.setSource(sourceDirectory + File.separator + "sample-" + (idx + 1) + ".pdf");
			switch (idx) {
			case 0: {
				extractor.exceptLine(new int[] { 0, 1, -1 });
				break;
			}
			case 1: {
				extractor.exceptLine(new int[] { 0, 1 });
				break;
			}
			case 2: {
				extractor.exceptPage(0).exceptLine(new int[] { 0 });
				break;
			}
			case 3: {
				extractor.exceptLine(new int[] { 0 });
				break;
			}
			case 4: {
				extractor.exceptLine(0, new int[] { 0, 1 });
				break;
			}
			}
			List<Table> tables = extractor.extract();
			try (Writer writer = new OutputStreamWriter(
					new FileOutputStream(resultDirectory + "//sample-" + (idx + 1) + ".html"), "UTF-8")) {
				for (Table table : tables) {
					writer.write("Page: " + (table.getPageIdx() + 1) + "\n");
					writer.write(table.toHtml());
				}
			}
		}
	}
}
