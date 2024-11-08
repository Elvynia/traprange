/**
* Copyright (C) 2016, GIAYBAC
*
* Released under the MIT license
*/
package com.giaybac.traprange;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.giaybac.traprange.entity.Table;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Ints;

/**
 *
 * @author thoqbk
 */
public class MAIN {

	private static final Logger LOGGER = LoggerFactory.getLogger(MAIN.class);

	private static void extractTables(final String[] args) {
		try {
			List<Integer> pages = MAIN.getPages(args);
			List<Integer> exceptPages = MAIN.getExceptPages(args);
			List<Integer[]> exceptLines = MAIN.getExceptLines(args);
			String in = MAIN.getIn(args);
			String out = MAIN.getOut(args);
			MAIN.LOGGER.info("Extraction started with file {} into {}", in, out);

			PDFTableExtractor extractor = new PDFTableExtractor().setSource(in);
			// page
			for (Integer page : pages) {
				extractor.addPage(page);
			}
			// except page
			for (Integer exceptPage : exceptPages) {
				extractor.exceptPage(exceptPage);
			}
			// except lines
			List<Integer> exceptLineIdxes = new ArrayList<>();
			Multimap<Integer, Integer> exceptLineInPages = LinkedListMultimap.create();
			for (Integer[] exceptLine : exceptLines) {
				if (exceptLine.length == 1) {
					exceptLineIdxes.add(exceptLine[0]);
				} else if (exceptLine.length == 2) {
					int lineIdx = exceptLine[0];
					int pageIdx = exceptLine[1];
					exceptLineInPages.put(pageIdx, lineIdx);
				}
			}
			if (!exceptLineIdxes.isEmpty()) {
				extractor.exceptLine(Ints.toArray(exceptLineIdxes));
			}
			if (!exceptLineInPages.isEmpty()) {
				for (int pageIdx : exceptLineInPages.keySet()) {
					extractor.exceptLine(pageIdx, Ints.toArray(exceptLineInPages.get(pageIdx)));
				}
			}
			// begin parsing pdf file
			List<Table> tables = extractor.extract();

			Writer writer = new OutputStreamWriter(new FileOutputStream(out), "UTF-8");
			try {
				for (Table table : tables) {
					writer.write("Page: " + (table.getPageIdx() + 1) + "\n");
					writer.write(table.toHtml());
				}
			} finally {
				try {
					writer.close();
				} catch (Exception e) {
				}
			}
		} catch (Exception e) {
			MAIN.LOGGER.error(null, e);
		}
		MAIN.LOGGER.info("Extraction ended");
	}

	private static String getArg(final String[] args, final String name) {
		return MAIN.getArg(args, name, null);
	}

	private static String getArg(final String[] args, final String name, final String defaultValue) {
		int argIdx = -1;
		for (int idx = 0; idx < args.length; idx++) {
			if (("-" + name).equals(args[idx])) {
				argIdx = idx;
				break;
			}
		}
		if (argIdx == -1) {
			return defaultValue;
		} else if (argIdx < args.length - 1) {
			return args[argIdx + 1].trim();
		} else {
			throw new RuntimeException("Missing argument value. Argument name: " + name);
		}
	}

	private static List<Integer[]> getExceptLines(final String[] args) {
		List<Integer[]> retVal = new ArrayList<>();
		String exceptLinesInString = MAIN.getArg(args, "el");
		if (exceptLinesInString == null) {
			return retVal;
		}
		// ELSE:
		String[] exceptLineStrings = exceptLinesInString.split(",");
		for (String exceptLineString : exceptLineStrings) {
			if (exceptLineString.contains("@")) {
				String[] exceptLineItems = exceptLineString.split("@");
				if (exceptLineItems.length != 2) {
					throw new RuntimeException("Invalid except lines argument (-el): " + exceptLinesInString);
				} else {
					try {
						int lineIdx = Integer.parseInt(exceptLineItems[0].trim());
						int pageIdx = Integer.parseInt(exceptLineItems[1].trim());
						retVal.add(new Integer[] { lineIdx, pageIdx });
					} catch (Exception e) {
						throw new RuntimeException("Invalid except lines argument (-el): " + exceptLinesInString, e);
					}
				}
			} else {
				try {
					int lineIdx = Integer.parseInt(exceptLineString.trim());
					retVal.add(new Integer[] { lineIdx });
				} catch (Exception e) {
					throw new RuntimeException("Invalid except lines argument (-el): " + exceptLinesInString, e);
				}
			}
		}
		return retVal;
	}

	private static List<Integer> getExceptPages(final String[] args) {
		return MAIN.getInts(args, "ep");
	}

	private static String getIn(final String[] args) {
		String retVal = MAIN.getArg(args, "in", null);
		if (retVal == null) {
			throw new RuntimeException("Missing input file");
		}
		return retVal;
	}

	private static List<Integer> getInts(final String[] args, final String name) {
		List<Integer> retVal = new ArrayList<>();
		String intsInString = MAIN.getArg(args, name);
		if (intsInString != null) {
			String[] intInStrings = intsInString.split(",");
			for (String intInString : intInStrings) {
				try {
					retVal.add(Integer.parseInt(intInString.trim()));
				} catch (Exception e) {
					throw new RuntimeException("Invalid argument (-" + name + "): " + intsInString, e);
				}
			}
		}
		return retVal;
	}

	private static String getOut(final String[] args) {
		String retVal = MAIN.getArg(args, "out", null);
		if (retVal == null) {
			throw new RuntimeException("Missing output location");
		}
		return retVal;
	}

	private static List<Integer> getPages(final String[] args) {
		return MAIN.getInts(args, "p");
	}

	/**
	 * -in: source <br/>
	 * -out: target <br/>
	 * -el: except lines. Ex: 1,2,3-1,6@8 #line 6 in page 8 <br/>
	 * -p: page <br/>
	 * -ep: except page <br/>
	 * -h: help
	 *
	 * @param args
	 */
	public static void main(final String[] args) {
		if (args.length < 1 || "-h".equals(args[0])) {
			MAIN.printHelp();
		} else {
			MAIN.extractTables(args);
		}
	}

	private static void printHelp() {
		StringBuilder help = new StringBuilder();
		help.append("Argument list: \n")
				.append("\t-in: (required) absolute pdf location path. Ex: \"/Users/thoqbk/table.pdf\"\n")
				.append("\t-out: (required) absolute output file. Ex: \"/Users/thoqbk/table.html\"\n")
				.append("\t-el: skip lines. For example, to skip lines 1,2,3 and -1 (last line) in all pages and line 4 in page 8, the value should be: \"1,2,3,-1,4@8\"\n")
				.append("\t-p: only parse these pages. Ex: 1,2,3\n")
				.append("\t-ep: all pages except these pages. Ex: 1,2\n").append("\t-h: help\n").append("---");
		MAIN.LOGGER.info(help.toString());
	}
}
