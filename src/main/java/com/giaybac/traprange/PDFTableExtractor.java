/**
 * Copyright (C) 2015, GIAYBAC
 *
 * Released under the MIT license
 */
package com.giaybac.traprange;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.giaybac.traprange.entity.Table;
import com.giaybac.traprange.entity.TableCell;
import com.giaybac.traprange.entity.TableRow;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Range;

/**
 *
 * @author Tho Mar 22, 2015 3:34:29 PM
 */
public class PDFTableExtractor {
	private static class TextPositionExtractor extends PDFTextStripper {

		private final int pageId;
		private final List<TextPosition> textPositions = new ArrayList<>();

		private TextPositionExtractor(final PDDocument document, final int pageId) throws IOException {
			super();
			super.setSortByPosition(true);
			super.document = document;
			this.pageId = pageId;
		}

		/**
		 * and order by textPosition.getY() ASC
		 *
		 * @return
		 * @throws IOException
		 */
		private List<TextPosition> extract() throws IOException {
			this.stripPage(this.pageId);
			// sort
			Collections.sort(this.textPositions, new Comparator<TextPosition>() {
				@Override
				public int compare(final TextPosition o1, final TextPosition o2) {
					int retVal = 0;
					if (o1.getY() < o2.getY()) {
						retVal = -1;
					} else if (o1.getY() > o2.getY()) {
						retVal = 1;
					}
					return retVal;

				}
			});
			return this.textPositions;
		}

		public void stripPage(final int pageId) throws IOException {
			this.setStartPage(pageId + 1);
			this.setEndPage(pageId + 1);
			try (Writer writer = new OutputStreamWriter(new ByteArrayOutputStream())) {
				this.writeText(this.document, writer);
			}
		}

		@Override
		protected void writeString(final String string, final List<TextPosition> textPositions) throws IOException {
			this.textPositions.addAll(textPositions);
		}
	}

	private PDDocument document;
	private final List<Integer> exceptedPages = new ArrayList<>();
	// contains pages that will be extracted table content.
	// If this variable doesn't contain any page, all pages will be extracted
	private final List<Integer> extractedPages = new ArrayList<>();

	private InputStream inputStream;
	private final Logger logger = LoggerFactory.getLogger(PDFTableExtractor.class);
	// contains avoided line idx-s for each page,
	// if this multimap contains only one element and key of this element equals -1
	// then all lines in extracted pages contains in multi-map value will be avoided
	private final Multimap<Integer, Integer> pageNExceptedLinesMap = HashMultimap.create();

	private String password;

	/**
	 * This page will be analyze and extract its table content
	 *
	 * @param pageIdx
	 * @return
	 */
	public PDFTableExtractor addPage(final int pageIdx) {
		this.extractedPages.add(pageIdx);
		return this;
	}

	private TableCell buildCell(final int columnIdx, final List<TextPosition> cellContent) {
		Collections.sort(cellContent, new Comparator<TextPosition>() {
			@Override
			public int compare(final TextPosition o1, final TextPosition o2) {
				int retVal = 0;
				if (o1.getX() < o2.getX()) {
					retVal = -1;
				} else if (o1.getX() > o2.getX()) {
					retVal = 1;
				}
				return retVal;
			}
		});
		// String cellContentString = Joiner.on("").join(cellContent.stream().map(e ->
		// e.getCharacter()).iterator());
		StringBuilder cellContentBuilder = new StringBuilder();
		for (TextPosition textPosition : cellContent) {
			cellContentBuilder.append(textPosition.getUnicode());
		}
		String cellContentString = cellContentBuilder.toString();
		return new TableCell(columnIdx, cellContentString);
	}

	/**
	 *
	 * @param rowIdx
	 * @param rowContent
	 * @param columnTrapRanges
	 * @return
	 */
	private TableRow buildRow(final int rowIdx, final List<TextPosition> rowContent,
			final List<Range<Integer>> columnTrapRanges) {
		TableRow retVal = new TableRow(rowIdx);
		// Sort rowContent
		Collections.sort(rowContent, new Comparator<TextPosition>() {
			@Override
			public int compare(final TextPosition o1, final TextPosition o2) {
				int retVal = 0;
				if (o1.getX() < o2.getX()) {
					retVal = -1;
				} else if (o1.getX() > o2.getX()) {
					retVal = 1;
				}
				return retVal;
			}
		});
		int idx = 0;
		int columnIdx = 0;
		List<TextPosition> cellContent = new ArrayList<>();
		while (idx < rowContent.size()) {
			TextPosition textPosition = rowContent.get(idx);
			Range<Integer> columnTrapRange = columnTrapRanges.get(columnIdx);
			Range<Integer> textRange = Range.closed((int) textPosition.getX(),
					(int) (textPosition.getX() + textPosition.getWidth()));
			if (columnTrapRange.encloses(textRange)) {
				cellContent.add(textPosition);
				idx++;
			} else {
				TableCell cell = this.buildCell(columnIdx, cellContent);
				retVal.getCells().add(cell);
				// next column: clear cell content
				cellContent.clear();
				columnIdx++;
			}
		}
		if (!cellContent.isEmpty() && columnIdx < columnTrapRanges.size()) {
			TableCell cell = this.buildCell(columnIdx, cellContent);
			retVal.getCells().add(cell);
		}
		// return
		return retVal;
	}

	/**
	 * Texts in tableContent have been ordered by .getY() ASC
	 *
	 * @param pageIdx
	 * @param tableContent
	 * @param rowTrapRanges
	 * @param columnTrapRanges
	 * @return
	 */
	private Table buildTable(final int pageIdx, final List<TextPosition> tableContent,
			final List<Range<Integer>> rowTrapRanges, final List<Range<Integer>> columnTrapRanges) {
		Table retVal = new Table(pageIdx, columnTrapRanges.size());
		int idx = 0;
		int rowIdx = 0;
		List<TextPosition> rowContent = new ArrayList<>();
		while (idx < tableContent.size()) {
			TextPosition textPosition = tableContent.get(idx);
			Range<Integer> rowTrapRange = rowTrapRanges.get(rowIdx);
			Range<Integer> textRange = Range.closed((int) textPosition.getY(),
					(int) (textPosition.getY() + textPosition.getHeight()));
			if (rowTrapRange.encloses(textRange)) {
				rowContent.add(textPosition);
				idx++;
			} else {
				TableRow row = this.buildRow(rowIdx, rowContent, columnTrapRanges);
				retVal.getRows().add(row);
				// next row: clear rowContent
				rowContent.clear();
				rowIdx++;
			}
		}
		// last row
		if (!rowContent.isEmpty() && rowIdx < rowTrapRanges.size()) {
			TableRow row = this.buildRow(rowIdx, rowContent, columnTrapRanges);
			retVal.getRows().add(row);
		}
		// return
		return retVal;
	}

	/**
	 * Avoid a specific line in a specific page. LineIdx can be negative number, -1
	 * is the last line
	 *
	 * @param pageIdx
	 * @param lineIdxes
	 * @return
	 */
	public PDFTableExtractor exceptLine(final int pageIdx, final int[] lineIdxes) {
		for (int lineIdx : lineIdxes) {
			this.pageNExceptedLinesMap.put(pageIdx, lineIdx);
		}
		return this;
	}

	/**
	 * Avoid this line in all extracted pages. LineIdx can be negative number, -1 is
	 * the last line
	 *
	 * @param lineIdxes
	 * @return
	 */
	public PDFTableExtractor exceptLine(final int[] lineIdxes) {
		this.exceptLine(-1, lineIdxes);
		return this;
	}

	public PDFTableExtractor exceptPage(final int pageIdx) {
		this.exceptedPages.add(pageIdx);
		return this;
	}

	public List<Table> extract() {
		List<Table> retVal = new ArrayList<>();
		Multimap<Integer, Range<Integer>> pageIdNLineRangesMap = LinkedListMultimap.create();
		Multimap<Integer, TextPosition> pageIdNTextsMap = LinkedListMultimap.create();
		try {
			this.document = this.password != null ? PDDocument.load(this.inputStream, this.password)
					: PDDocument.load(this.inputStream);
			for (int pageId = 0; pageId < this.document.getNumberOfPages(); pageId++) {
				boolean b = !this.exceptedPages.contains(pageId)
						&& (this.extractedPages.isEmpty() || this.extractedPages.contains(pageId));
				if (b) {
					List<TextPosition> texts = this.extractTextPositions(pageId);// sorted by .getY() ASC
					// extract line ranges
					List<Range<Integer>> lineRanges = this.getLineRanges(pageId, texts);
					// extract column ranges
					List<TextPosition> textsByLineRanges = this.getTextsByLineRanges(lineRanges, texts);

					pageIdNLineRangesMap.putAll(pageId, lineRanges);
					pageIdNTextsMap.putAll(pageId, textsByLineRanges);
				}
			}
			// Calculate columnRanges
			List<Range<Integer>> columnRanges = this.getColumnRanges(pageIdNTextsMap.values());
			for (int pageId : pageIdNTextsMap.keySet()) {
				Table table = this.buildTable(pageId, (List<TextPosition>) pageIdNTextsMap.get(pageId),
						(List<Range<Integer>>) pageIdNLineRangesMap.get(pageId), columnRanges);
				retVal.add(table);
				// debug
				this.logger.debug("Found " + table.getRows().size() + " row(s) and " + columnRanges.size()
						+ " column(s) of a table in page " + pageId);
			}
		} catch (IOException ex) {
			throw new RuntimeException("Parse pdf file fail", ex);
		} finally {
			if (this.document != null) {
				try {
					this.document.close();
				} catch (IOException ex) {
					this.logger.error(null, ex);
				}
			}
		}
		// return
		return retVal;
	}

	private List<TextPosition> extractTextPositions(final int pageId) throws IOException {
		TextPositionExtractor extractor = new TextPositionExtractor(this.document, pageId);
		return extractor.extract();
	}

	/**
	 * @param texts
	 * @return
	 */
	private List<Range<Integer>> getColumnRanges(final Collection<TextPosition> texts) {
		TrapRangeBuilder rangesBuilder = new TrapRangeBuilder();
		for (TextPosition text : texts) {
			Range<Integer> range = Range.closed((int) text.getX(), (int) (text.getX() + text.getWidth()));
			rangesBuilder.addRange(range);
		}
		return rangesBuilder.build();
	}

	private List<Range<Integer>> getLineRanges(final int pageId, final List<TextPosition> pageContent) {
		TrapRangeBuilder lineTrapRangeBuilder = new TrapRangeBuilder();
		for (TextPosition textPosition : pageContent) {
			Range<Integer> lineRange = Range.closed((int) textPosition.getY(),
					(int) (textPosition.getY() + textPosition.getHeight()));
			// add to builder
			lineTrapRangeBuilder.addRange(lineRange);
		}
		List<Range<Integer>> lineTrapRanges = lineTrapRangeBuilder.build();
		List<Range<Integer>> retVal = this.removeExceptedLines(pageId, lineTrapRanges);
		return retVal;
	}

	/**
	 *
	 * Remove all texts in excepted lines
	 *
	 * TexPositions are sorted by .getY() ASC
	 *
	 * @param lineRanges
	 * @param textPositions
	 * @return
	 */
	private List<TextPosition> getTextsByLineRanges(final List<Range<Integer>> lineRanges,
			final List<TextPosition> textPositions) {
		List<TextPosition> retVal = new ArrayList<>();
		int idx = 0;
		int lineIdx = 0;
		while (idx < textPositions.size() && lineIdx < lineRanges.size()) {
			TextPosition textPosition = textPositions.get(idx);
			Range<Integer> textRange = Range.closed((int) textPosition.getY(),
					(int) (textPosition.getY() + textPosition.getHeight()));
			Range<Integer> lineRange = lineRanges.get(lineIdx);
			if (lineRange.encloses(textRange)) {
				retVal.add(textPosition);
				idx++;
			} else if (lineRange.upperEndpoint() < textRange.lowerEndpoint()) {
				lineIdx++;
			} else {
				idx++;
			}
		}
		// return
		return retVal;
	}

	private boolean isExceptedLine(final int pageIdx, final int lineIdx) {
		boolean retVal = this.pageNExceptedLinesMap.containsEntry(pageIdx, lineIdx)
				|| this.pageNExceptedLinesMap.containsEntry(-1, lineIdx);
		return retVal;
	}

	private List<Range<Integer>> removeExceptedLines(final int pageIdx, final List<Range<Integer>> lineTrapRanges) {
		List<Range<Integer>> retVal = new ArrayList<>();
		for (int lineIdx = 0; lineIdx < lineTrapRanges.size(); lineIdx++) {
			boolean isExceptedLine = this.isExceptedLine(pageIdx, lineIdx)
					|| this.isExceptedLine(pageIdx, lineIdx - lineTrapRanges.size());
			if (!isExceptedLine) {
				retVal.add(lineTrapRanges.get(lineIdx));
			}
		}
		// return
		return retVal;
	}

	public PDFTableExtractor setSource(final File file) {
		try {
			return this.setSource(new FileInputStream(file));
		} catch (FileNotFoundException ex) {
			throw new RuntimeException("Invalid pdf file", ex);
		}
	}

	public PDFTableExtractor setSource(final File file, final String password) {
		try {
			return this.setSource(new FileInputStream(file), password);
		} catch (FileNotFoundException ex) {
			throw new RuntimeException("Invalid pdf file", ex);
		}
	}

	public PDFTableExtractor setSource(final InputStream inputStream) {
		this.inputStream = inputStream;
		return this;
	}

	public PDFTableExtractor setSource(final InputStream inputStream, final String password) {
		this.inputStream = inputStream;
		this.password = password;
		return this;
	}

	public PDFTableExtractor setSource(final String filePath) {
		return this.setSource(new File(filePath));
	}

	public PDFTableExtractor setSource(final String filePath, final String password) {
		return this.setSource(new File(filePath), password);
	}
}
