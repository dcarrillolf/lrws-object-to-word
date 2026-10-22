package com.dcb.objecttoword.exporter.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;

public class DocxTemplateFiller {

	public static void fill(
			InputStream templateInputStream, OutputStream outputStream,
			Map<String, String> values)
		throws IOException {

		try (XWPFDocument xwpfDocument = new XWPFDocument(
				templateInputStream)) {

			fill(xwpfDocument, values);

			xwpfDocument.write(outputStream);
		}
	}

	public static void fill(
		XWPFDocument xwpfDocument, Map<String, String> values) {

		_processBody(xwpfDocument, values);

		for (XWPFHeader xwpfHeader : xwpfDocument.getHeaderList()) {
			_processBody(xwpfHeader, values);
		}

		for (XWPFFooter xwpfFooter : xwpfDocument.getFooterList()) {
			_processBody(xwpfFooter, values);
		}
	}

	private static boolean _hasPlaceholder(String text) {
		if ((text == null) || !text.contains("{{")) {
			return false;
		}

		return _PLACEHOLDER_PATTERN.matcher(
			text
		).find();
	}

	private static void _processBody(IBody body, Map<String, String> values) {
		for (IBodyElement bodyElement : body.getBodyElements()) {
			if (bodyElement instanceof XWPFParagraph) {
				_processParagraph((XWPFParagraph)bodyElement, values);
			}
			else if (bodyElement instanceof XWPFTable) {
				XWPFTable xwpfTable = (XWPFTable)bodyElement;

				for (XWPFTableRow xwpfTableRow : xwpfTable.getRows()) {
					for (XWPFTableCell xwpfTableCell :
							xwpfTableRow.getTableCells()) {

						_processBody(xwpfTableCell, values);
					}
				}
			}
		}
	}

	// Word may split a placeholder into several runs: replace first within each
	// run (keeps formatting), then merge the paragraph into its first run.
	private static void _processParagraph(
		XWPFParagraph xwpfParagraph, Map<String, String> values) {

		if (!_hasPlaceholder(xwpfParagraph.getText())) {
			return;
		}

		for (XWPFRun xwpfRun : xwpfParagraph.getRuns()) {
			String runText = xwpfRun.text();

			if (_hasPlaceholder(runText)) {
				_setRunText(xwpfRun, _replacePlaceholders(runText, values));
			}
		}

		if (!_hasPlaceholder(xwpfParagraph.getText())) {
			return;
		}

		List<XWPFRun> xwpfRuns = xwpfParagraph.getRuns();

		StringBuilder sb = new StringBuilder();

		for (XWPFRun xwpfRun : xwpfRuns) {
			sb.append(xwpfRun.text());
		}

		for (int i = xwpfRuns.size() - 1; i > 0; i--) {
			xwpfParagraph.removeRun(i);
		}

		_setRunText(
			xwpfParagraph.getRuns().get(0),
			_replacePlaceholders(sb.toString(), values));
	}

	private static String _replacePlaceholders(
		String text, Map<String, String> values) {

		Matcher matcher = _PLACEHOLDER_PATTERN.matcher(text);

		StringBuffer sb = new StringBuffer();

		while (matcher.find()) {
			String value = values.get(matcher.group(1));

			if (value == null) {
				value = "";
			}

			matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
		}

		matcher.appendTail(sb);

		return sb.toString();
	}

	// XWPFRun.setText only replaces the first text element of the run and
	// does not render: rewrite the whole run with breaks and tabs.
	private static void _setRunText(XWPFRun xwpfRun, String text) {
		CTR ctr = xwpfRun.getCTR();

		for (int i = ctr.sizeOfTArray() - 1; i >= 0; i--) {
			ctr.removeT(i);
		}

		for (int i = ctr.sizeOfTabArray() - 1; i >= 0; i--) {
			ctr.removeTab(i);
		}

		for (int i = ctr.sizeOfBrArray() - 1; i >= 0; i--) {
			ctr.removeBr(i);
		}

		for (int i = ctr.sizeOfCrArray() - 1; i >= 0; i--) {
			ctr.removeCr(i);
		}

		String normalizedText = text.replace(
			"\r\n", "\n"
		).replace(
			'\r', '\n'
		);

		String[] lines = normalizedText.split("\n", -1);

		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				xwpfRun.addBreak();
			}

			String[] parts = lines[i].split("\t", -1);

			for (int j = 0; j < parts.length; j++) {
				if (j > 0) {
					xwpfRun.addTab();
				}

				if (!parts[j].isEmpty()) {
					xwpfRun.setText(parts[j]);
				}
			}
		}
	}

	private static final Pattern _PLACEHOLDER_PATTERN = Pattern.compile(
		"\\{\\{\\s*([A-Za-z0-9_.\\-]+)\\s*\\}\\}");

}
