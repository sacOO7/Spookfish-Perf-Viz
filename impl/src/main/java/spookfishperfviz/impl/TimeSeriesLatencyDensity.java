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

import static spookfishperfviz.impl.Utils.forEach;
import static spookfishperfviz.impl.Utils.reverse;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import spookfishperfviz.impl.Density.IndexedDataPoint;

/**
 * @see <a href="http://www.brendangregg.com/HeatMaps/latency.html">Latency Heat Maps</a>
 * 
 * @author Rahul Bakale
 * @since Nov, 2014
 */
final class TimeSeriesLatencyDensity {

	private static final String[] EMPTY_STRING_ARRAY = new String[0];
	private static final int DEFAULT_HEAT_MAP_SINGLE_AREA_HEIGHT = 10;

	/**
	 * TODO - take this as input parameter
	 */
	private static final int MAX_HEAT_MAP_HEIGHT = 400;

	private static final int DEFAULT_MAX_INTERBAL_POINTS_FOR_LATENCY_DENSITY = MAX_HEAT_MAP_HEIGHT / DEFAULT_HEAT_MAP_SINGLE_AREA_HEIGHT;

	private static final double X_AXIS_LABEL_FONT_SIZE = 10; // TODO - add to SVGConstants.
	private static final String X_AXIS_LABEL_FONT_FAMILY = SVGConstants.MONOSPACE_FONT_FAMILY;

	private static final UnaryOperator<Long> LONG_INC_OPERATOR = l -> l == null ? Long.valueOf(0) : Long.valueOf(l + 1);

	private static final Function<IndexedDataPoint<Double>, String> Y_AXIS_LABEL_MAKER = i -> i.toString(d -> Utils.stripTrailingZeroesAfterDecimal(d, true));

	private static final class TimestampLabelMaker implements Function<Long, String> {

		private static final String NL = System.lineSeparator();

		private final SimpleDateFormat dayMonthFormat;
		private final SimpleDateFormat yearFormat;
		private final SimpleDateFormat timeFormat;

		TimestampLabelMaker(TimeZone timeZone) {

			var dayMonth = new SimpleDateFormat("dd/MM");
			dayMonth.setTimeZone(timeZone);

			var year = new SimpleDateFormat("yyyy");
			year.setTimeZone(timeZone);

			var time = new SimpleDateFormat("HH:mm");
			time.setTimeZone(timeZone);

			this.dayMonthFormat = dayMonth;
			this.yearFormat = year;
			this.timeFormat = time;
		}

		@Override
		public String apply(Long time) {
			var d = new Date(time);
			return this.timeFormat.format(d) + NL + this.dayMonthFormat.format(d) + NL + this.yearFormat.format(d);
		}
	}

	private static final class TimestampTooltipMaker implements Function<Long, String> {

		private final SimpleDateFormat format;

		TimestampTooltipMaker(TimeZone timeZone) {

			var dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
			dateFormat.setTimeZone(timeZone);

			this.format = dateFormat;
		}

		@Override
		public String apply(Long time) {
			return this.format.format(time);
		}
	}

	static TimeSeriesLatencyDensity create(	double[] latencies,
											long[] timestamps,
											TimeZone outputTimeZone,
											Integer maxIntervalPointsForLatencyDensity) {

		var minMax = Utils.minMax(latencies);
		var minIntervalPoint = minMax[0];
		var maxIntervalPoint = minMax[1];

		return create0(latencies, timestamps, outputTimeZone, minIntervalPoint, maxIntervalPoint, maxIntervalPointsForLatencyDensity);
	}

	static TimeSeriesLatencyDensity create(	double[] latencies,
											long[] timestamps,
											TimeZone outputTimeZone,
											double minIntervalPointForLatencyDensity,
											double maxIntervalPointForLatencyDensity,
											Integer maxIntervalPointsForLatencyDensity) {
		
		
		if (minIntervalPointForLatencyDensity > maxIntervalPointForLatencyDensity) {
			throw new IllegalArgumentException("min = <" + minIntervalPointForLatencyDensity + ">, max = <" + maxIntervalPointForLatencyDensity + ">");
		}

		var minMax = Utils.minMax(latencies);
		var minLatency = minMax[0];
		var maxLatency = minMax[1];

		double minIntervalPoint;
		double maxIntervalPoint;

		if ((maxIntervalPointForLatencyDensity < minLatency) || (minIntervalPointForLatencyDensity > maxLatency)) {
			minIntervalPoint = minIntervalPointForLatencyDensity;
			maxIntervalPoint = maxIntervalPointForLatencyDensity;
		} else {
			minIntervalPoint = Math.max(minLatency, minIntervalPointForLatencyDensity);
			maxIntervalPoint = Math.min(maxLatency, maxIntervalPointForLatencyDensity);
		}

		return create0(latencies, timestamps, outputTimeZone, minIntervalPoint, maxIntervalPoint, maxIntervalPointsForLatencyDensity);
	}
	
	private static TimeSeriesLatencyDensity create0(double[] latencies,
													long[] timestamps,
													TimeZone outputTimeZone,
													double adjustedMinIntervalPointForLatencyDensity,
													double adjustedMaxIntervalPointForLatencyDensity,
													Integer maxIntervalPointsForLatencyDensity) {

		var maxIntervalPoints =
				maxIntervalPointsForLatencyDensity == null ? 
						DEFAULT_MAX_INTERBAL_POINTS_FOR_LATENCY_DENSITY : maxIntervalPointsForLatencyDensity;

		var intervalPointsForLatencyDensity =
				createIntervalPoints(adjustedMinIntervalPointForLatencyDensity, adjustedMaxIntervalPointForLatencyDensity, maxIntervalPoints);
		
		return new TimeSeriesLatencyDensity(latencies, timestamps, outputTimeZone, intervalPointsForLatencyDensity);
	}
	
	private static double[] createIntervalPoints(double minIntervalPoint, double maxIntervalPoint, int maxIntervalPoints) {

		var adjustedMin = Math.floor(minIntervalPoint);
		var adjustedMax = Math.ceil(maxIntervalPoint);
		
		if (adjustedMin > adjustedMax) {
			throw new IllegalArgumentException("min = <" + adjustedMin + ">, max = <" + adjustedMax + ">");
		}
		
		//adjustedMin will be equal to adjustedMax in cases like minIntervalPoint=3.0 and maxIntervalPoint=3.0. 
		//In such cases nIntervalPoints can be taken as 1. 

		var nIntervalPoints = adjustedMin == adjustedMax ? 1 : Math.min(maxIntervalPoints, (int) Math.ceil(adjustedMax - adjustedMin));
		
		return Utils.createIntervalPoints(adjustedMin, adjustedMax, nIntervalPoints);
	}

	
	private final Density<Double, Long, Long> density;
	private final int defaultTimeLabelSkipCount;

	
	private final TimestampLabelMaker timestampLabelMaker;
	private final TimestampTooltipMaker timestampTooltipMaker;

	private TimeSeriesLatencyDensity(double[] latencies, long[] timestamps, TimeZone outputTimeZone, double[] responseTimeIntervalPoints) {
		this(latencies, timestamps, outputTimeZone, null, Utils.toHashSet(responseTimeIntervalPoints));
	}

	private TimeSeriesLatencyDensity(double[] latencies,
									 long[] timestamps,
									 TimeZone outputTimeZone,
									 Set<Long> inputTimestampIntervalPoints,
									 Set<Double> responseTimeIntervalPoints) {
		
		Objects.requireNonNull(latencies);
		Objects.requireNonNull(timestamps);
		Objects.requireNonNull(outputTimeZone);
		
		this.timestampLabelMaker = new TimestampLabelMaker(outputTimeZone);
		this.timestampTooltipMaker = new TimestampTooltipMaker(outputTimeZone);

		if (latencies.length != timestamps.length) {
			throw new IllegalArgumentException("Number of latencies must be same as number of timestamps");
		}

		var sortedTimestamps = Utils.sort(timestamps);
		var minTime = sortedTimestamps[0];
		var maxTime = sortedTimestamps[sortedTimestamps.length - 1];
		var duration = maxTime - minTime;
		var threshold = TimeUnit.HOURS.toMillis(5);

		var defaultTimeLabelSkipCount = (duration > threshold) ? 1 : 2;

		Set<Long> timestampIntervalPoints;
		if (inputTimestampIntervalPoints == null) {
			var timeIntervalInMillis = (duration > threshold) ? TimeUnit.MINUTES.toMillis(30) : TimeUnit.MINUTES.toMillis(5);
			timestampIntervalPoints = Utils.getTimestampIntervalPoints(timestamps, outputTimeZone, timeIntervalInMillis);
		} else {
			timestampIntervalPoints = inputTimestampIntervalPoints;
		}

		var d = Density.create(responseTimeIntervalPoints, timestampIntervalPoints, 0L, Long.class);

		for (var i = 0; i < latencies.length; i++) {
			Double row = latencies[i];
			Long column = timestamps[i];

			d.apply(row, column, LONG_INC_OPERATOR);
		}

		this.density = d;
		this.defaultTimeLabelSkipCount = defaultTimeLabelSkipCount;
	}

	HeatMapSVG getHeatMapSVG(TimeUnit latencyUnit, double heatMapSingleAreaWidth, ColorRampScheme colorScheme) {
		return getHeatMapSVG(latencyUnit, this.defaultTimeLabelSkipCount, heatMapSingleAreaWidth, colorScheme);
	}

	HeatMapSVG getHeatMapSVG(TimeUnit latencyUnit, int timeLabelSkipCount, double heatMapSingleAreaWidth, ColorRampScheme colorScheme) {

		return getHeatMapSVG(this.density, colorScheme, timeLabelSkipCount, latencyUnit, this.timestampLabelMaker, this.timestampTooltipMaker, heatMapSingleAreaWidth);
	}

	/**
	 * TODO - re-factor common code from this and BarChart.
	 */
	private static HeatMapSVG getHeatMapSVG(Density<Double, Long, Long> density,
											ColorRampScheme colorScheme,
											int timeLabelSkipCount,
											TimeUnit latencyUnit,
											TimestampLabelMaker timestampLabelMaker,
											TimestampTooltipMaker timestampTooltipMaker,
											double heatMapSingleAreaWidth) {

		var matrix = density.getMatrix();
		
		var heatMap = getColoredHeatMap(matrix, colorScheme);
		
		var rowCount = heatMap.length;
		var columnCount = heatMap[0].length;
		
		var NL = System.lineSeparator();
		var START_X = SVGConstants.LEFT_RIGHT_MARGIN;
		var START_Y = SVGConstants.TOP_DOWN_MARGIN;
		
		var TICK_LENGTH = 10;
		var SPACE_BETWEEN_LABEL_AND_TICK = 10;
		var SPACE_BETWEEN_TITLE_AND_LABEL = 10;
		
		var latencyUnitShortForm = Utils.toShortForm(latencyUnit);
		var yAxisTitle = "Latency" + " (" + latencyUnitShortForm + ")";
		var Y_AXIS_TITLE_FONT_SIZE = SVGConstants.MONOSPACE_FONT_SIZE;
		var Y_AXIS_TITLE_FONT_FAMILY = SVGConstants.MONOSPACE_FONT_FAMILY;
		double Y_AXIS_TITLE_START_X = START_X;
		var Y_AXIS_TITLE_END_X = Y_AXIS_TITLE_START_X + SVGConstants.MONOSPACE_FONT_SIZE;
		
		var Y_AXIS_LABEL_FONT_SIZE = SVGConstants.MONOSPACE_FONT_SIZE;
		var Y_AXIS_LABEL_FONT_FAMILY = SVGConstants.MONOSPACE_FONT_FAMILY;
		var Y_AXIS_LABEL_START_X = Y_AXIS_TITLE_END_X + SPACE_BETWEEN_TITLE_AND_LABEL;
		
		var X_AXIS_TITLE = "Time";
		var X_AXIS_TITLE_FONT_SIZE = SVGConstants.MONOSPACE_FONT_SIZE;
		var X_AXIS_TITLE_FONT_FAMILY = SVGConstants.MONOSPACE_FONT_FAMILY;
		
		double BOX_START_Y = START_Y;
		
		var yAxisLabels =
				forEach(reverse(density.getRowIntervalPoints(), ArrayList::new), Y_AXIS_LABEL_MAKER, ArrayList::new);
		
		int yAxisMaxLabelLength =
				Collections.max(forEach(yAxisLabels, String::length, () -> new ArrayList<>()));
		
		var yAxisPaddedLabels =
				Utils.getPaddedLabels(yAxisLabels, yAxisMaxLabelLength, ArrayList::new, true);
		
		var yAxisMaxLabelWidth = yAxisMaxLabelLength * SVGConstants.MONOSPACE_FONT_WIDTH;
		
		var yAxisMajorTickStartX = Y_AXIS_LABEL_START_X + yAxisMaxLabelWidth + SPACE_BETWEEN_LABEL_AND_TICK;
		var yAxisTickEndX = yAxisMajorTickStartX + TICK_LENGTH;
		
		double heatMapSingleAreaHeight = Math.min(MAX_HEAT_MAP_HEIGHT / rowCount, DEFAULT_HEAT_MAP_SINGLE_AREA_HEIGHT);
		
		var heatMapHeight = rowCount * heatMapSingleAreaHeight;
		var heatMapWidth = columnCount * heatMapSingleAreaWidth;
		
		var heatMapBoxStartX = yAxisTickEndX;
		var heatMapStartX = heatMapBoxStartX + heatMapSingleAreaWidth;
		var heatMapStartY = BOX_START_Y + /* gutter */DEFAULT_HEAT_MAP_SINGLE_AREA_HEIGHT;
		var heatMapBoxEndX = heatMapStartX + heatMapWidth + heatMapSingleAreaWidth;
		var heatMapBoxEndY = heatMapStartY + heatMapHeight + /* gutter */DEFAULT_HEAT_MAP_SINGLE_AREA_HEIGHT;
		var heatMapBoxHeight = heatMapBoxEndY - BOX_START_Y;
		var heatMapBoxWidth = heatMapBoxEndX - heatMapBoxStartX;
		
		var yAxisTitleStartY = BOX_START_Y + ((heatMapBoxHeight / 2.0) - (Y_AXIS_TITLE_FONT_SIZE / 2.0));
		
		var xAxisTickStartY = heatMapBoxEndY;
		var xAxisMajorTickEndY = xAxisTickStartY + TICK_LENGTH;
		var xAxisMinorTickEndY = xAxisTickStartY + (TICK_LENGTH / 2.0);
		
		// TODO - check if cast to int is OK
		var yAxisLabelSkipCount = (int) (DEFAULT_HEAT_MAP_SINGLE_AREA_HEIGHT / heatMapSingleAreaHeight);
		
		var xAxisLabelStartY = xAxisMajorTickEndY + SPACE_BETWEEN_LABEL_AND_TICK;
		
		StringBuilder yAxisTitleSVG;
		{
			yAxisTitleSVG = new StringBuilder();
		
			yAxisTitleSVG.append("<text ");
			yAxisTitleSVG.append("style=\"");
			yAxisTitleSVG.append("font-family:").append(Y_AXIS_TITLE_FONT_FAMILY).append(";");
		
			yAxisTitleSVG.append("font-size:").append(Y_AXIS_TITLE_FONT_SIZE).append("px;");
			yAxisTitleSVG.append("text-anchor: middle;"); // related to rotation of the title
			yAxisTitleSVG.append("dominant-baseline: middle;"); // related to rotation of the title
			yAxisTitleSVG.append("\"");
			yAxisTitleSVG.append(" x=\"").append(Y_AXIS_TITLE_START_X).append("\"");
			yAxisTitleSVG.append(" y=\"").append(yAxisTitleStartY).append("\"");
			yAxisTitleSVG.append(" transform=\"rotate(-90,").append(Y_AXIS_TITLE_START_X).append(",").append(yAxisTitleStartY).append(")\"");
			yAxisTitleSVG.append(">");
			yAxisTitleSVG.append(yAxisTitle);
			yAxisTitleSVG.append("</text>");
		}
		
		StringBuilder yAxisLabelsSVG;
		StringBuilder yAxisTicksSVG;
		{
			yAxisLabelsSVG = new StringBuilder();
			yAxisTicksSVG = new StringBuilder();
		
			yAxisLabelsSVG.append("<g style=\"font-family:").append(Y_AXIS_LABEL_FONT_FAMILY).append(";font-size:").append(Y_AXIS_LABEL_FONT_SIZE)
					.append("px;\">").append(NL);
			yAxisTicksSVG.append("<g style=\"stroke:black; stroke-width:1\">").append(NL);

			var yAxisLabelStartY = heatMapStartY;
		
			for (var i = 0; i <= rowCount; i++) {
		
				var skipLabel = Utils.skipLabel(i, rowCount, yAxisLabelSkipCount);
		
				if (!skipLabel) {
					yAxisLabelsSVG.append("<text style=\"dominant-baseline: central;\" x=\"").append(Y_AXIS_LABEL_START_X).append("\" y=\"")
							.append(yAxisLabelStartY).append("\">").append(yAxisPaddedLabels.get(i)).append("</text>").append(NL);
					
					yAxisTicksSVG
						.append("<line x1=\"").append(yAxisMajorTickStartX)
						.append("\" y1=\"").append(yAxisLabelStartY)
						.append("\" x2=\"").append(yAxisTickEndX)
						.append("\" y2=\"").append(yAxisLabelStartY)
						.append("\"/>").append(NL);
				}
		
				yAxisLabelStartY += heatMapSingleAreaHeight;
			}
		
			yAxisLabelsSVG.append("</g>");
			yAxisTicksSVG.append("</g>");
		}
		
		ArrayList<IndexedDataPoint<Long>> timestampPoints = new ArrayList<>(density.getColumnIntervalPoints());
		
		double xAxisLabelEndY;
		StringBuilder xAxisTicksSVG;
		StringBuilder xAxisLabelsSVG;
		{
			xAxisTicksSVG = new StringBuilder();
			xAxisLabelsSVG = new StringBuilder();
		
			xAxisTicksSVG.append("<g style=\"stroke:black; stroke-width:1\">").append(NL);
			xAxisLabelsSVG.append("<g style=\"font-family:").append(X_AXIS_LABEL_FONT_FAMILY).append(";font-size:").append(X_AXIS_LABEL_FONT_SIZE).append("px;\">").append(NL);

			var x = heatMapStartX; // boxStartX;

			var maxXAxisLabelPartCount = Integer.MIN_VALUE;
			var fontSize = X_AXIS_LABEL_FONT_SIZE;
		
			for (var i = 0; i <= columnCount; i++) {
				var skipLabel = Utils.skipLabel(i, columnCount, timeLabelSkipCount);
		
				var xAxisTickEndY = skipLabel ? xAxisMinorTickEndY : xAxisMajorTickEndY;
		
				xAxisTicksSVG.append("<line x1=\"").append(x).append("\" y1=\"").append(xAxisTickStartY).append("\" x2=\"").append(x)
						.append("\" y2=\"").append(xAxisTickEndY).append("\"/>").append(NL);
		
				if (!skipLabel) {
					var multiLineLabel = timestampPoints.get(i).toString(timestampLabelMaker);
		
					var label = Utils.createMultiSpanSVGText(multiLineLabel, x, xAxisLabelStartY, fontSize, null);
		
					xAxisLabelsSVG.append(label.getSvg());
		
					maxXAxisLabelPartCount = Math.max(label.getSpanCount(), maxXAxisLabelPartCount);
				}
		
				x += heatMapSingleAreaWidth;
			}
		
			xAxisLabelEndY = xAxisLabelStartY + (maxXAxisLabelPartCount * fontSize);
		
			xAxisTicksSVG.append("</g>");
			xAxisLabelsSVG.append("</g>");
		}
		
		double xAxisTitleEndY;
		StringBuilder xAxisTitleSVG;
		{
			var xAxisTitleStartY = xAxisLabelEndY + SPACE_BETWEEN_TITLE_AND_LABEL;
			xAxisTitleEndY = xAxisTitleStartY + X_AXIS_TITLE_FONT_SIZE;
		
			xAxisTitleSVG = new StringBuilder();
		
			xAxisTitleSVG.append("<text ");
			xAxisTitleSVG.append("style=\"");
			xAxisTitleSVG.append("font-family:").append(X_AXIS_TITLE_FONT_FAMILY).append(";");
			xAxisTitleSVG.append("font-size:").append(X_AXIS_TITLE_FONT_SIZE).append("px;");
			xAxisTitleSVG.append("text-anchor: middle;");
			xAxisTitleSVG.append("\"");
			xAxisTitleSVG.append(" x=\"").append(heatMapBoxStartX + (heatMapBoxWidth / 2.0)).append("\"");
			xAxisTitleSVG.append(" y=\"").append(xAxisTitleStartY).append("\"");
			xAxisTitleSVG.append(">");
			xAxisTitleSVG.append(X_AXIS_TITLE);
			xAxisTitleSVG.append("</text>");
		}
		
		var colorForZeroVal = colorScheme.getBackgroundColor();
		
		StringBuilder boxSVG;
		{
			boxSVG = new StringBuilder();
			boxSVG.append("<rect x=\"").append(heatMapBoxStartX).append("\" y=\"").append(BOX_START_Y).append("\" width=\"").append(heatMapBoxWidth)
					.append("\" height=\"").append(heatMapBoxHeight).append("\" style=\"fill:").append(colorForZeroVal)
					.append(";stroke:black;stroke-width:1\"/>").append(NL);
		}
		
		StringBuilder colorMapSVG;
		{
			colorMapSVG = new StringBuilder();

			var y = heatMapStartY;
		
			for (int rowNum = rowCount - 1, r = 0; rowNum >= 0; rowNum--, r++) {
				
				var row = heatMap[rowNum];
				
				var yTooltip1 = yAxisLabels.get(r + 1);
				var yTooltip2 = yAxisLabels.get(r);

				var x = heatMapStartX;
		
				for (var colNum = 0; colNum < columnCount; colNum++) {
					
					var color = row[colNum];
		
					if (!Objects.equals(color, colorForZeroVal)) {
						
						colorMapSVG.append("<rect");
						colorMapSVG.append(" x=\"").append(x).append("\"");
						colorMapSVG.append(" y=\"").append(y).append("\"");
						colorMapSVG.append(" fill=\"").append(color).append("\"");
						colorMapSVG.append(" width=\"").append(heatMapSingleAreaWidth).append("\"");
						colorMapSVG.append(" height=\"").append(heatMapSingleAreaHeight).append("\"");
						colorMapSVG.append(">");
						
						{//TOOLTIP
							
							//TODO - escape HTML special characters in tooltip text. For e.g. spaces.
							
							colorMapSVG.append("<title>");
							colorMapSVG.append("Count = ").append(matrix[rowNum][colNum]).append(", Color = ").append(color).append(NL);
							var xTooltip1 = timestampPoints.get(colNum).toString(timestampTooltipMaker);
							var xTooltip2 = timestampPoints.get(colNum + 1).toString(timestampTooltipMaker);
							colorMapSVG.append("Period: (").append(xTooltip1).append(" - ").append(xTooltip2).append(')').append(NL);
							colorMapSVG.append("Latency range: (").append(yTooltip1).append(" - ").append(yTooltip2).append(") ").append(latencyUnitShortForm).append(NL);
							colorMapSVG.append("</title>");
						}
						
						colorMapSVG.append("</rect>");
						colorMapSVG.append(NL);
					}
		
					x += heatMapSingleAreaWidth;
				}
		
				y += heatMapSingleAreaHeight;
			}
		}

		// Need to set the width & height of the SVG to prevent clipping of large SVGs.
		var svgEndX = heatMapBoxEndX + SVGConstants.LEFT_RIGHT_MARGIN;
		var svgEndY = xAxisTitleEndY + SVGConstants.TOP_DOWN_MARGIN;

		var svg =
				"<svg width=\"" + svgEndX + "\" height=\"" + svgEndY + "\">" + NL +
				xAxisTitleSVG + NL +
				xAxisTicksSVG + NL +
				xAxisLabelsSVG + NL +
				yAxisTitleSVG + NL +
				yAxisLabelsSVG + NL +
				yAxisTicksSVG + NL +
				boxSVG + NL +
				colorMapSVG + NL +
				"</svg>";
		return new HeatMapSVG(svg, timeLabelSkipCount, heatMapBoxStartX, heatMapSingleAreaWidth);
	}

	/**
	 * TODO - check if some code can be moved to {@linkplain Density}
	 */
	private static String[][] getColoredHeatMap(Long[][] matrix, ColorRampScheme colorScheme) {

		var colorMapArray = ColorRampCalculator.getColorMap(Utils.toOneDimArray(matrix), colorScheme);

		var rowCount = matrix.length;
		var columnCount = matrix[0].length;

		var colorMapMatrix = new String[rowCount][columnCount];
		Utils.fillMatrix(colorMapArray, colorMapMatrix);

		return colorMapMatrix;
	}

	String getTrxCountBarChartSVG(int labelSkipCount, double boxStartX, double barWidth, ColorRampScheme colorRampScheme) {
		return getTrxCountBarChartSVG(this.density, labelSkipCount, this.timestampLabelMaker, boxStartX, barWidth, colorRampScheme);
	}

	private static String getTrxCountBarChartSVG(Density<Double, Long, Long> density, int labelSkipCount, TimestampLabelMaker timestampLabelMaker, double boxStartX,
												 double barWidth, ColorRampScheme colorRampScheme) {
		var MAX_BAR_LENGTH = 100;

		var matrix = density.getMatrix();
		var columnIntervalPoints = density.getColumnIntervalPoints();

		var rowCount = matrix.length;
		var columnCount = matrix[0].length;

		var columnTotals = new long[columnCount];

		for (var column = 0; column < columnCount; column++) {
			long sum = 0;
			//noinspection ForLoopReplaceableByForEach
			for (var row = 0; row < rowCount; row++) {
				sum += matrix[row][column];
			}

			columnTotals[column] = sum;
		}

		List<String> labels = new ArrayList<>();
		for (var columnIntervalPoint : columnIntervalPoints) {
			labels.add(columnIntervalPoint.toString(timestampLabelMaker));
		}

		var barChart = VerticalBarChart.create(columnTotals, labels.toArray(EMPTY_STRING_ARRAY));

		return barChart.toSVG(MAX_BAR_LENGTH, barWidth, boxStartX, X_AXIS_LABEL_FONT_FAMILY, X_AXIS_LABEL_FONT_SIZE, labelSkipCount, colorRampScheme);
	}

	@Override
	public String toString() {
		return this.density.toString();
	}
}
