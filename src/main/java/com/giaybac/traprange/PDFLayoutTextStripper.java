package com.giaybac.traprange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.text.TextPositionComparator;

class Character {

	private char characterValue;
	private int index;
	private boolean isCharacterAtTheBeginningOfNewLine;
	private boolean isCharacterCloseToPreviousWord;
	private boolean isCharacterPartOfPreviousWord;
	private boolean isFirstCharacterOfAWord;

	public Character(final char characterValue, final int index, final boolean isCharacterPartOfPreviousWord,
			final boolean isFirstCharacterOfAWord, final boolean isCharacterAtTheBeginningOfNewLine,
			final boolean isCharacterPartOfASentence) {
		this.characterValue = characterValue;
		this.index = index;
		this.isCharacterPartOfPreviousWord = isCharacterPartOfPreviousWord;
		this.isFirstCharacterOfAWord = isFirstCharacterOfAWord;
		this.isCharacterAtTheBeginningOfNewLine = isCharacterAtTheBeginningOfNewLine;
		this.isCharacterCloseToPreviousWord = isCharacterPartOfASentence;
		if (PDFLayoutTextStripper.DEBUG) {
			System.out.println(this.toString());
		}
	}

	public char getCharacterValue() {
		return this.characterValue;
	}

	public int getIndex() {
		return this.index;
	}

	public boolean isCharacterAtTheBeginningOfNewLine() {
		return this.isCharacterAtTheBeginningOfNewLine;
	}

	public boolean isCharacterCloseToPreviousWord() {
		return this.isCharacterCloseToPreviousWord;
	}

	public boolean isCharacterPartOfPreviousWord() {
		return this.isCharacterPartOfPreviousWord;
	}

	public boolean isFirstCharacterOfAWord() {
		return this.isFirstCharacterOfAWord;
	}

	public void setIndex(final int index) {
		this.index = index;
	}

	@Override
	public String toString() {
		StringBuilder toString = new StringBuilder();
		toString.append(this.index);
		toString.append(" ");
		toString.append(this.characterValue);
		toString.append(" isCharacterPartOfPreviousWord=").append(this.isCharacterPartOfPreviousWord);
		toString.append(" isFirstCharacterOfAWord=").append(this.isFirstCharacterOfAWord);
		toString.append(" isCharacterAtTheBeginningOfNewLine=").append(this.isCharacterAtTheBeginningOfNewLine);
		toString.append(" isCharacterPartOfASentence=").append(this.isCharacterCloseToPreviousWord);
		toString.append(" isCharacterCloseToPreviousWord=").append(this.isCharacterCloseToPreviousWord);
		return toString.toString();
	}

}

class CharacterFactory {

	private boolean firstCharacterOfLineFound;
	private boolean isCharacterCloseToPreviousWord;
	private boolean isFirstCharacterOfAWord;
	private TextPosition previousTextPosition;

	public CharacterFactory(final boolean firstCharacterOfLineFound) {
		this.firstCharacterOfLineFound = firstCharacterOfLineFound;
	}

	public Character createCharacterFromTextPosition(final TextPosition textPosition,
			final TextPosition previousTextPosition) {
		this.setPreviousTextPosition(previousTextPosition);
		boolean characterPartOfPreviousWord = this.isCharacterPartOfPreviousWord(textPosition);
		this.isFirstCharacterOfAWord = this.isFirstCharacterOfAWord(textPosition);
		boolean isCharacterAtTheBeginningOfNewLine = this.isCharacterAtTheBeginningOfNewLine(textPosition);
		this.isCharacterCloseToPreviousWord = this.isCharacterCloseToPreviousWord(textPosition);
		char character = this.getCharacterFromTextPosition(textPosition);
		int index = (int) textPosition.getX() / PDFLayoutTextStripper.OUTPUT_SPACE_CHARACTER_WIDTH_IN_PT;
		return new Character(character, index, characterPartOfPreviousWord, this.isFirstCharacterOfAWord,
				isCharacterAtTheBeginningOfNewLine, this.isCharacterCloseToPreviousWord);
	}

	private char getCharacterFromTextPosition(final TextPosition textPosition) {
		String string = textPosition.getUnicode();
		char character = string.charAt(0);
		return character;
	}

	private TextPosition getPreviousTextPosition() {
		return this.previousTextPosition;
	}

	private boolean isCharacterAtTheBeginningOfNewLine(final TextPosition textPosition) {
		if (!this.firstCharacterOfLineFound) {
			return true;
		}
		TextPosition previousTextPosition = this.getPreviousTextPosition();
		float previousTextYPosition = previousTextPosition.getY();
		return Math.round(textPosition.getY()) < Math.round(previousTextYPosition);
	}

	private boolean isCharacterCloseToPreviousWord(final TextPosition textPosition) {
		if (!this.firstCharacterOfLineFound) {
			return false;
		}
		double numberOfSpaces = this.numberOfSpacesBetweenTwoCharacters(this.previousTextPosition, textPosition);
		return numberOfSpaces > 1 && numberOfSpaces <= PDFLayoutTextStripper.OUTPUT_SPACE_CHARACTER_WIDTH_IN_PT;
	}

	private boolean isCharacterPartOfPreviousWord(final TextPosition textPosition) {
		TextPosition previousTextPosition = this.getPreviousTextPosition();
		if (" ".equals(previousTextPosition.getUnicode())) {
			return false;
		}
		double numberOfSpaces = this.numberOfSpacesBetweenTwoCharacters(previousTextPosition, textPosition);
		return numberOfSpaces <= 1;
	}

	private boolean isFirstCharacterOfAWord(final TextPosition textPosition) {
		if (!this.firstCharacterOfLineFound) {
			return true;
		}
		double numberOfSpaces = this.numberOfSpacesBetweenTwoCharacters(this.previousTextPosition, textPosition);
		return numberOfSpaces > 1 || this.isCharacterAtTheBeginningOfNewLine(textPosition);
	}

	private double numberOfSpacesBetweenTwoCharacters(final TextPosition textPosition1,
			final TextPosition textPosition2) {
		double previousTextXPosition = textPosition1.getX();
		double previousTextWidth = textPosition1.getWidth();
		double previousTextEndXPosition = previousTextXPosition + previousTextWidth;
		double numberOfSpaces = Math.abs(Math.round(textPosition2.getX() - previousTextEndXPosition));
		return numberOfSpaces;
	}

	private void setPreviousTextPosition(final TextPosition previousTextPosition) {
		this.previousTextPosition = previousTextPosition;
	}

}

/**
 * Java doc to be completed
 *
 * @author Jonathan Link
 *
 */
public class PDFLayoutTextStripper extends PDFTextStripper {

	public static final boolean DEBUG = false;
	public static final int OUTPUT_SPACE_CHARACTER_WIDTH_IN_PT = 4;

	private double currentPageWidth;
	private TextPosition previousTextPosition;
	private List<TextLine> textLineList;

	/**
	 * Constructor
	 */
	public PDFLayoutTextStripper() throws IOException {
		super();
		this.previousTextPosition = null;
		this.textLineList = new ArrayList<>();
	}

	private TextLine addNewLine() {
		TextLine textLine = new TextLine(this.getCurrentPageWidth());
		this.textLineList.add(textLine);
		return textLine;
	}

	private void createNewEmptyNewLines(final int numberOfNewLines) {
		for (int i = 0; i < numberOfNewLines - 1; ++i) {
			this.addNewLine();
		}
	}

	private int getCurrentPageWidth() {
		return (int) Math.round(this.currentPageWidth);
	}

	private int getNumberOfNewLinesFromPreviousTextPosition(final TextPosition textPosition) {
		TextPosition previousTextPosition = this.getPreviousTextPosition();
		if (previousTextPosition == null) {
			return 1;
		}

		float textYPosition = Math.round(textPosition.getY());
		float previousTextYPosition = Math.round(previousTextPosition.getY());

		if (textYPosition > previousTextYPosition && textYPosition - previousTextYPosition > 5.5) {
			double height = textPosition.getHeight();
			int numberOfLines = (int) (Math.floor(textYPosition - previousTextYPosition) / height);
			numberOfLines = Math.max(1, numberOfLines - 1); // exclude current new line
			if (PDFLayoutTextStripper.DEBUG) {
				System.out.println(height + " " + numberOfLines);
			}
			return numberOfLines;
		} else {
			return 0;
		}
	}

	private TextPosition getPreviousTextPosition() {
		return this.previousTextPosition;
	}

	private List<TextLine> getTextLineList() {
		return this.textLineList;
	}

	private void iterateThroughTextList(final Iterator<TextPosition> textIterator) {
		List<TextPosition> textPositionList = new ArrayList<>();

		while (textIterator.hasNext()) {
			TextPosition textPosition = (TextPosition) textIterator.next();
			int numberOfNewLines = this.getNumberOfNewLinesFromPreviousTextPosition(textPosition);
			if (numberOfNewLines != 0) {
				this.writeTextPositionList(textPositionList);
				this.createNewEmptyNewLines(numberOfNewLines);
			}
			textPositionList.add(textPosition);
			this.setPreviousTextPosition(textPosition);
		}
		if (!textPositionList.isEmpty()) {
			this.writeTextPositionList(textPositionList);
		}
	}

	/**
	 *
	 * @param page page to parse
	 */
	@Override
	public void processPage(final PDPage page) throws IOException {
		PDRectangle pageRectangle = page.getMediaBox();
		if (pageRectangle != null) {
			this.setCurrentPageWidth(pageRectangle.getWidth());
			super.processPage(page);
			this.previousTextPosition = null;
			this.textLineList = new ArrayList<>();
		}
	}

	private void setCurrentPageWidth(final double currentPageWidth) {
		this.currentPageWidth = currentPageWidth;
	}

	private void setPreviousTextPosition(final TextPosition setPreviousTextPosition) {
		this.previousTextPosition = setPreviousTextPosition;
	}

	/*
	 * In order to get rid of the warning: TextPositionComparator class should
	 * implement Comparator<TextPosition> instead of Comparator
	 */
	private void sortTextPositionList(final List<TextPosition> textList) {
		TextPositionComparator comparator = new TextPositionComparator();
		Collections.sort(textList, comparator);
	}

	private void writeLine(final List<TextPosition> textPositionList) {
		if (textPositionList.size() > 0) {
			TextLine textLine = this.addNewLine();
			boolean firstCharacterOfLineFound = false;
			for (TextPosition textPosition : textPositionList) {
				CharacterFactory characterFactory = new CharacterFactory(firstCharacterOfLineFound);
				Character character = characterFactory.createCharacterFromTextPosition(textPosition,
						this.getPreviousTextPosition());
				textLine.writeCharacterAtIndex(character);
				this.setPreviousTextPosition(textPosition);
				firstCharacterOfLineFound = true;
			}
		} else {
			this.addNewLine(); // white line
		}
	}

	@Override
	protected void writePage() throws IOException {
		List<List<TextPosition>> charactersByArticle = super.getCharactersByArticle();
		for (int i = 0; i < charactersByArticle.size(); i++) {
			List<TextPosition> textList = charactersByArticle.get(i);
			try {
				this.sortTextPositionList(textList);
			} catch (java.lang.IllegalArgumentException e) {
				System.err.println(e);
			}
			this.iterateThroughTextList(textList.iterator());
		}
		this.writeToOutputStream(this.getTextLineList());
	}

	private void writeTextPositionList(final List<TextPosition> textPositionList) {
		this.writeLine(textPositionList);
		textPositionList.clear();
	}

	private void writeToOutputStream(final List<TextLine> textLineList) throws IOException {
		for (TextLine textLine : textLineList) {
			char[] line = textLine.getLine().toCharArray();
			super.getOutput().write(line);
			super.getOutput().write('\n');
			super.getOutput().flush();
		}
	}

}

class TextLine {

	private static final char SPACE_CHARACTER = ' ';
	private int lastIndex;
	private String line;
	private int lineLength;

	public TextLine(final int lineLength) {
		this.line = "";
		this.lineLength = lineLength / PDFLayoutTextStripper.OUTPUT_SPACE_CHARACTER_WIDTH_IN_PT;
		this.completeLineWithSpaces();
	}

	private void completeLineWithSpaces() {
		for (int i = 0; i < this.getLineLength(); ++i) {
			this.line += TextLine.SPACE_CHARACTER;
		}
	}

	private int computeIndexForCharacter(final Character character) {
		int index = character.getIndex();
		boolean isCharacterPartOfPreviousWord = character.isCharacterPartOfPreviousWord();
		boolean isCharacterAtTheBeginningOfNewLine = character.isCharacterAtTheBeginningOfNewLine();
		boolean isCharacterCloseToPreviousWord = character.isCharacterCloseToPreviousWord();

		if (!this.indexIsInBounds(index)) {
			return -1;
		} else {
			if (isCharacterPartOfPreviousWord && !isCharacterAtTheBeginningOfNewLine) {
				index = this.findMinimumIndexWithSpaceCharacterFromIndex(index);
			} else if (isCharacterCloseToPreviousWord) {
				if (this.line.charAt(index) != TextLine.SPACE_CHARACTER) {
					index = index + 1;
				} else {
					index = this.findMinimumIndexWithSpaceCharacterFromIndex(index) + 1;
				}
			}
			index = this.getNextValidIndex(index, isCharacterPartOfPreviousWord);
			return index;
		}
	}

	private int findMinimumIndexWithSpaceCharacterFromIndex(final int index) {
		int newIndex = index;
		while (newIndex >= 0 && this.line.charAt(newIndex) == TextLine.SPACE_CHARACTER) {
			newIndex = newIndex - 1;
		}
		return newIndex + 1;
	}

	private int getLastIndex() {
		return this.lastIndex;
	}

	public String getLine() {
		return this.line;
	}

	public int getLineLength() {
		return this.lineLength;
	}

	private int getNextValidIndex(final int index, final boolean isCharacterPartOfPreviousWord) {
		int nextValidIndex = index;
		int lastIndex = this.getLastIndex();
		if (!this.isNewIndexGreaterThanLastIndex(index)) {
			nextValidIndex = lastIndex + 1;
		}
		if (!isCharacterPartOfPreviousWord && this.isSpaceCharacterAtIndex(index - 1)) {
			nextValidIndex = nextValidIndex + 1;
		}
		this.setLastIndex(nextValidIndex);
		return nextValidIndex;
	}

	private boolean indexIsInBounds(final int index) {
		return index >= 0 && index < this.lineLength;
	}

	private boolean isNewIndexGreaterThanLastIndex(final int index) {
		int lastIndex = this.getLastIndex();
		return index > lastIndex;
	}

	private boolean isSpaceCharacterAtIndex(final int index) {
		return this.line.charAt(index) != TextLine.SPACE_CHARACTER;
	}

	private void setLastIndex(final int lastIndex) {
		this.lastIndex = lastIndex;
	}

	public void writeCharacterAtIndex(final Character character) {
		character.setIndex(this.computeIndexForCharacter(character));
		int index = character.getIndex();
		char characterValue = character.getCharacterValue();
		if (this.indexIsInBounds(index) && this.line.charAt(index) == TextLine.SPACE_CHARACTER) {
			this.line = this.line.substring(0, index) + characterValue
					+ this.line.substring(index + 1, this.getLineLength());
		}
	}

}
