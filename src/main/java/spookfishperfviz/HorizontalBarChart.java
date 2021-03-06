/**
 * Copyright 2014 Rahul Bakale
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package spookfishperfviz;

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
	
	static HorizontalBarChart create(final double[] data, final String[] dataLabels) {
		return create(data, dataLabels, null);
	}

	static HorizontalBarChart create(final double[] data, final String[] dataLabels, final String headerLabel) {
		return new HorizontalBarChart(data, dataLabels, headerLabel);
	}
	
	static HorizontalBarChart create(final int[] data, final String[] dataLabels) {
		return create(data, dataLabels, null);
	}

	static HorizontalBarChart create(final int[] data, final String[] dataLabels, final String headerLabel) {
		return create(Utils.toDoubles(data), dataLabels, headerLabel);
	}

	private final String headerLabel;
	private final String[] dataLabels;
	private final double[] data;
	private final double max;

	private HorizontalBarChart(final double[] data, final String[] dataLabels, final String headerLabel) {
		
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
	String toString(final long limit, final String mark) {
		final double[] data = this.data;
		final int size = data.length;
		final double max = this.max;
		final CharSequence[] dataLabels = this.dataLabels;

		final int maxLabelLength = getMaxLabelLength(dataLabels);

		final String NL = System.lineSeparator();

		final StringBuilder buf = new StringBuilder();

		for (int i = 0; i < size; i++) {
			final String dataLabel = Utils.getPaddedLabel(dataLabels[i], maxLabelLength, false);

			buf.append(dataLabel).append("  ");

			final long scaled = scale(limit, max, data[i]);
			for (int k = 0; k < scaled; k++) {
				buf.append(mark);
			}

			buf.append(NL);
		}

		return buf.toString();
	}

	String toSVG(final boolean wrapInHtmlBody, final ColorRampScheme colorRampScheme) {
		return toSVG(DEFAULT_LIMIT_FOR_SVG, wrapInHtmlBody, colorRampScheme);
	}

	private String toSVG(final int maxLineLength, final boolean wrapInHtmlBody, final ColorRampScheme colorRampScheme) {
		return wrapInHtmlBody ? toSVGHtml(maxLineLength, colorRampScheme) : toSVG(maxLineLength, colorRampScheme);
	}

	private String toSVGHtml(final int maxLineLength, final ColorRampScheme colorRampScheme) {
		final String NL = System.lineSeparator();
		return "<!DOCTYPE html>" + NL + "<html>" + NL + "  <body>" + NL + toSVG(maxLineLength, colorRampScheme) + NL + "  </body>" + NL + "</html>";
	}

	private String toSVG(final int maxLineLength, final ColorRampScheme colorRampScheme) {
		
		final double[] data = this.data;
		final int size = data.length;
		final double max = this.max;
		final String[] dataLabels = this.dataLabels;
		final String headerLabel = this.headerLabel;
		
		final boolean hasHeaderLabel = headerLabel != null;

		final int SPACE_BETWEEN_LABEL_AND_LINE = 10;
		final String LABEL_FONT_FAMILY = SVGConstants.MONOSPACE_FONT_FAMILY;
		final double LABEL_FONT_SIZE = SVGConstants.MONOSPACE_FONT_SIZE;
		final double LABEL_FONT_WIDTH = SVGConstants.MONOSPACE_FONT_WIDTH;
		final int LINE_GAP = SVGConstants.LINE_GAP;
		final int LEFT_RIGHT_MARGIN = SVGConstants.LEFT_RIGHT_MARGIN;
		final int LABEL_START_X = LEFT_RIGHT_MARGIN;
		
		final String NL = System.lineSeparator();
		final String INDENT = "    ";
		
		final int maxLabelLength;
		{
			final int maxLen = getMaxLabelLength(dataLabels);
			maxLabelLength = hasHeaderLabel ? getMaxLabelLength(headerLabel, maxLen) : maxLen;	
		}
		
		final double maxLabelWidth = maxLabelLength * LABEL_FONT_WIDTH;
		final double xLineStart = LABEL_START_X + maxLabelWidth + SPACE_BETWEEN_LABEL_AND_LINE;
		final double boxWidth = xLineStart + maxLineLength + LEFT_RIGHT_MARGIN;

		final int boxHeight = (size + 1 + (hasHeaderLabel ? 2 : 0)) * LINE_GAP;

		final StringBuilder svgLines = new StringBuilder();
		svgLines.append("<g style=\"stroke:grey; stroke-width:5\">").append(NL);

		final StringBuilder svgLabels = new StringBuilder();
		
		svgLabels
			.append("<g fill=\"black\" style=\"font-family:").append(LABEL_FONT_FAMILY)
			.append(";font-size:").append(LABEL_FONT_SIZE).append("px;\">").append(NL);

		int y1 = LINE_GAP;

		
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

		final String[] colors = colorRampScheme == null ? null : ColorRampCalculator.getColorMap(data, colorRampScheme);

		for (int i = 0; i < size; i++, y1 += LINE_GAP) {
			
			final double d = data[i];

			final long scaledLineLength = scale(maxLineLength, max, d);
			final String dataLabel = Utils.getPaddedLabel(dataLabels[i], maxLabelLength, true); 
																						
			svgLines.append(INDENT)
					.append("<line x1=\"").append(xLineStart).append("\"")
					.append(" y1=\"").append(y1).append("\"")
					.append(" x2=\"").append(xLineStart + scaledLineLength).append("\"")
					.append(" y2=\"").append(y1).append("\"");
			
			final String lineColor = colors == null ? null : colors[i];
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

		final String rect = "<rect width=\"" + boxWidth + "\" height=\"" + boxHeight + "\" style=\"fill:white;stroke:black;stroke-width:1\"/>" + NL;

		final String svg = 
				"  <svg width=\"" + boxWidth + "\" height=\"" + boxHeight + "\">" + NL + 
				INDENT + rect + NL + 
				svgLines + NL + 
				svgLabels + NL + 
				"  </svg>";

		return svg;
	}

	private static long scale(final long limit, final double maxData, final double data) {
		return Math.round(Math.ceil((data * limit) / maxData));
	}

	private static int getMaxLabelLength(final CharSequence[] c) {
		int padding = -1;
		for (final CharSequence e : c) {
			padding = getMaxLabelLength(e, padding);
		}
		return padding;
	}

	private static int getMaxLabelLength(final CharSequence cs, int currentLength) {
		final int len = cs == null ? 4 : cs.length();
		return Math.max(currentLength, len);
	}
}
