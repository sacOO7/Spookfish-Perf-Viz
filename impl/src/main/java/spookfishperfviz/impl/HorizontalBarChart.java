/*
  Copyright 2014 Rahul Bakale

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

 */

package spookfishperfviz.impl;

/**
 * @author Rahul Bakale
 * @since Nov, 2014
 */
final class HorizontalBarChart {
	
	private static final int DEFAULT_LIMIT_FOR_TEXT = 80;
	private static final int DEFAULT_LIMIT_FOR_SVG = 500;

	static final class ChartSymbol {
		public static final String FULLWIDTH_HYPHEN = "\uFF0D";
		public static final String UNDERSCORE = "_";
		public static final String DEFAULT = UNDERSCORE;
	}
	
	static HorizontalBarChart create(double[] data, String[] dataLabels) {
		return create(data, dataLabels, null);
	}

	static HorizontalBarChart create(double[] data, String[] dataLabels, String headerLabel) {
		return new HorizontalBarChart(data, dataLabels, headerLabel);
	}
	
	static HorizontalBarChart create(int[] data, String[] dataLabels) {
		return create(data, dataLabels, null);
	}

	static HorizontalBarChart create(int[] data, String[] dataLabels, String headerLabel) {
		return create(Utils.toDoubles(data), dataLabels, headerLabel);
	}

	private final String headerLabel;
	private final String[] dataLabels;
	private final double[] data;
	private final double max;

	private HorizontalBarChart(double[] data, String[] dataLabels, String headerLabel) {
		
		this.data = data;
		this.dataLabels = dataLabels;
		this.headerLabel = headerLabel;
		this.max = Utils.getMax(data);
	}

	@Override
	public String toString() {
		return toString(DEFAULT_LIMIT_FOR_TEXT, ChartSymbol.DEFAULT);
	}

	/**
	 * TODO - include header label if present
	 */
	String toString(long limit, String mark) {
		var data = this.data;
		var size = data.length;
		var max = this.max;
		CharSequence[] dataLabels = this.dataLabels;

		var maxLabelLength = getMaxLabelLength(dataLabels);

		var NL = System.lineSeparator();

		var result = new StringBuilder();

		for (var i = 0; i < size; i++) {
			var dataLabel = Utils.getPaddedLabel(dataLabels[i], maxLabelLength, false);

			result.append(dataLabel).append("  ");

			var scaled = scale(limit, max, data[i]);
			for (var k = 0; k < scaled; k++) {
				result.append(mark);
			}

			result.append(NL);
		}

		return result.toString();
	}

	String toSVG(boolean wrapInHtmlBody, ColorRampScheme colorRampScheme) {
		return toSVG(DEFAULT_LIMIT_FOR_SVG, wrapInHtmlBody, colorRampScheme);
	}

	private String toSVG(int maxLineLength, boolean wrapInHtmlBody, ColorRampScheme colorRampScheme) {
		return wrapInHtmlBody ? toSVGHtml(maxLineLength, colorRampScheme) : toSVG(maxLineLength, colorRampScheme);
	}

	private String toSVGHtml(int maxLineLength, ColorRampScheme colorRampScheme) {
		var NL = System.lineSeparator();
		return "<!DOCTYPE html>" + NL + "<html>" + NL + "  <body>" + NL + toSVG(maxLineLength, colorRampScheme) + NL + "  </body>" + NL + "</html>";
	}

	private String toSVG(int maxLineLength, ColorRampScheme colorRampScheme) {

		var data = this.data;
		var size = data.length;
		var max = this.max;
		var dataLabels = this.dataLabels;
		var headerLabel = this.headerLabel;

		var hasHeaderLabel = headerLabel != null;

		var SPACE_BETWEEN_LABEL_AND_LINE = 10;
		var LABEL_FONT_FAMILY = SVGConstants.MONOSPACE_FONT_FAMILY;
		var LABEL_FONT_SIZE = SVGConstants.MONOSPACE_FONT_SIZE;
		var LABEL_FONT_WIDTH = SVGConstants.MONOSPACE_FONT_WIDTH;
		var LINE_GAP = SVGConstants.LINE_GAP;
		var LEFT_RIGHT_MARGIN = SVGConstants.LEFT_RIGHT_MARGIN;
		var LABEL_START_X = LEFT_RIGHT_MARGIN;

		var NL = System.lineSeparator();
		var INDENT = "    ";
		
		int maxLabelLength;
		{
			var maxLen = getMaxLabelLength(dataLabels);
			maxLabelLength = hasHeaderLabel ? getMaxLabelLength(headerLabel, maxLen) : maxLen;	
		}

		var maxLabelWidth = maxLabelLength * LABEL_FONT_WIDTH;
		var xLineStart = LABEL_START_X + maxLabelWidth + SPACE_BETWEEN_LABEL_AND_LINE;
		var boxWidth = xLineStart + maxLineLength + LEFT_RIGHT_MARGIN;

		var boxHeight = (size + 1 + (hasHeaderLabel ? 2 : 0)) * LINE_GAP;

		var svgLines = new StringBuilder();
		svgLines.append("<g style=\"stroke:grey; stroke-width:5\">").append(NL);

		var svgLabels = new StringBuilder();
		
		svgLabels
			.append("<g fill=\"black\" style=\"font-family:").append(LABEL_FONT_FAMILY)
			.append(";font-size:").append(LABEL_FONT_SIZE).append("px;\">").append(NL);

		var y1 = LINE_GAP;

		
		if (hasHeaderLabel) {

			{
				// add header text

				svgLabels.append(INDENT)
				.append("<text x=\"").append(LABEL_START_X).append("\"")
				.append(" y=\"").append(y1).append("\"").append(">")
				.append(Utils.getPaddedLabel(headerLabel, maxLabelLength, true)).append("</text>").append(NL);

				y1 += LINE_GAP;
			}

			{
				// add separator

				svgLabels.append(INDENT)
				.append("<text x=\"").append(LABEL_START_X).append("\"")
				.append(" y=\"").append(y1).append("\"").append(">")
				.append(Utils.repeat("-", maxLabelLength)).append("</text>").append(NL);

				y1 += LINE_GAP;
			}
		}

		var colors = colorRampScheme == null ? null : ColorRampCalculator.getColorMap(data, colorRampScheme);

		for (var i = 0; i < size; i++, y1 += LINE_GAP) {

			var d = data[i];

			var scaledLineLength = scale(maxLineLength, max, d);
			var dataLabel = Utils.getPaddedLabel(dataLabels[i], maxLabelLength, true);
																						
			svgLines.append(INDENT)
					.append("<line x1=\"").append(xLineStart).append("\"")
					.append(" y1=\"").append(y1).append("\"")
					.append(" x2=\"").append(xLineStart + scaledLineLength).append("\"")
					.append(" y2=\"").append(y1).append("\"");

			var lineColor = colors == null ? null : colors[i];
			if (lineColor != null) {
				svgLines.append(" style=\"stroke:").append(lineColor).append("\"");
			}
			
			svgLines.append("/>").append(NL);

			svgLabels.append(INDENT)
					.append("<text x=\"").append(LABEL_START_X).append("\"")
					.append(" y=\"").append(y1).append("\"");
			
			svgLabels.append(">").append(dataLabel).append("</text>").append(NL);
		}

		svgLines.append("</g>");
		svgLabels.append("</g>");

		var rect = "<rect width=\"" + boxWidth + "\" height=\"" + boxHeight + "\" style=\"fill:white;stroke:black;stroke-width:1\"/>" + NL;

		var svg =
				"  <svg width=\"" + boxWidth + "\" height=\"" + boxHeight + "\">" + NL + 
				INDENT + rect + NL + 
				svgLines + NL + 
				svgLabels + NL + 
				"  </svg>";

		return svg;
	}

	private static long scale(long limit, double maxData, double data) {
		return Math.round(Math.ceil((data * limit) / maxData));
	}

	private static int getMaxLabelLength(CharSequence[] charSequences) {
		var padding = -1;
		for (var charSequence : charSequences) {
			padding = getMaxLabelLength(charSequence, padding);
		}
		return padding;
	}

	private static int getMaxLabelLength(CharSequence cs, int currentLength) {
		var len = cs == null ? 4 : cs.length();
		return Math.max(currentLength, len);
	}
}
