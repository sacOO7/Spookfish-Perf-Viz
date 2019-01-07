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
final class VerticalBarChart {
	
	static VerticalBarChart create(double[] data, String[] labels) {
		return new VerticalBarChart(data, labels);
	}

	static VerticalBarChart create(long[] data, String[] labels) {
		return create(Utils.toDoubles(data), labels);
	}

	static VerticalBarChart create(int[] data, String[] labels) {
		return create(Utils.toDoubles(data), labels);
	}

	private final String[] labels;
	private final double[] data;
	private final double max;

	private VerticalBarChart(double[] data, String[] labels) {
		this.data = data;
		this.labels = labels;
		this.max = Utils.getMax(data);
	}

	String toSVG(	int maxBarLength,
					double barWidth,
					double boxStartX,
					String labelFontFamily,
					double labelFontSize,
					int labelSkipCount,
					ColorRampScheme colorRampScheme) {
		
		var data = this.data;
		var size = data.length;
		var max = this.max;
		var labels = this.labels;

		var NL = System.lineSeparator();

		var START_Y = SVGConstants.TOP_DOWN_MARGIN;

		var barChartStartX = boxStartX + /* gutter */barWidth;

		var SPACE_BETWEEN_LABEL_AND_BAR = 10;

		double barStartY = START_Y + maxBarLength;
		var labelStartY = barStartY + SPACE_BETWEEN_LABEL_AND_BAR;

		var boxWidth = (size + 1) * barWidth;

		var indent1 = "  ";
		var indent2 = "    ";

		var colors = colorRampScheme == null ? null : ColorRampCalculator.getColorMap(data, colorRampScheme);

		var svgBars = new StringBuilder();
		svgBars.append("<g style=\"stroke:grey; stroke-width:").append(barWidth).append("\">").append(NL);

		var svgLabels = new StringBuilder();
		svgLabels.append("<g fill=\"black\" style=\"font-family:").append(labelFontFamily).append(";font-size:").append(labelFontSize).append("px;\">").append(NL);

		var maxXAxisLabelPartCount = Integer.MIN_VALUE;
		var x = barChartStartX;

		for (var i = 0; i < size; i++, x += barWidth) {
			var d = data[i];
			var scaledBarLength = scale(maxBarLength, max, d);
			svgBars.append(indent2);
			svgBars.append("<line x1=\"").append(x).append("\"");
			svgBars.append(" y1=\"").append(barStartY).append("\"");
			svgBars.append(" x2=\"").append(x).append("\"");
			svgBars.append(" y2=\"").append(barStartY - scaledBarLength).append("\"");

			var lineColor = colors == null ? null : colors[i];
			if (lineColor != null) {
				svgBars.append(" style=\"stroke:").append(lineColor).append("\"");
			}
			svgBars.append(">");

			/* TOOLTIP */svgBars.append("<title>").append("Value: ").append(d).append("</title>");

			svgBars.append("</line>");
			svgBars.append(NL);

			var skipLabel = Utils.skipLabel(i, size, labelSkipCount);
			if (!skipLabel) {

				var label = Utils.escapeHTMLSpecialChars(labels[i]);

				var multiSpanSVGText = Utils.createMultiSpanSVGText(label, x, labelStartY, labelFontSize, null);

				svgLabels.append(multiSpanSVGText.getSvg()).append(NL);

				maxXAxisLabelPartCount = Math.max(multiSpanSVGText.getSpanCount(), maxXAxisLabelPartCount);
			}
		}

		svgBars.append("</g>");
		svgLabels.append("</g>");

		var boxHeight = labelStartY + (maxXAxisLabelPartCount * labelFontSize) + SVGConstants.TOP_DOWN_MARGIN;

		var rect = "<rect x=\"" + boxStartX + "\" width=\"" + boxWidth + "\" height=\"" + boxHeight + "\" style=\"fill:white;stroke:black;stroke-width:1\"/>" + NL;

		var svgEndX = boxStartX + boxWidth;

		var svg = indent1 + "<svg width=\"" + svgEndX + "\" height=\"" + boxHeight + "\">" + NL + indent2 + rect + NL + svgBars + NL
				+ svgLabels + NL + indent1 + "</svg>";

		return svg;
	}

	private static long scale(long limit, double maxData, double data) {
		return Math.round(Math.ceil((data * limit) / maxData));
	}
}
