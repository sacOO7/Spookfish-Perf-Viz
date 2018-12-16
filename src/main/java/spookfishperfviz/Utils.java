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

package spookfishperfviz;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;

/**
 * @author Rahul Bakale
 * @since Nov, 2014
 */
final class Utils {
	
	private Utils() {
		//
	}

	static String toSvgText(String text) {

		var NL = System.lineSeparator();
		var lineSpace = SVGConstants.LINE_GAP;

		var texts = new StringBuilder();

		var height = lineSpace;

		var rawLines = text.lines().collect(Collectors.toList());

		for (var rawLine : rawLines) {

			var line = rawLine.replace(" ", "&nbsp;");

			texts.append("<text x=\"").append(10).append("\" y=\"").append(height).append("\">").append(line).append("</text>").append(NL);

			height += lineSpace;
		}

		var maxLineLength =
				rawLines
						.stream()
						.mapToInt(String::length)
						.max()
						.getAsInt();
		
		var width = (SVGConstants.LEFT_RIGHT_MARGIN * 2) + (maxLineLength * SVGConstants.MONOSPACE_FONT_WIDTH);

		var buf =
				"<svg height=\"" + height + "\" width=\"" + width + "\">" + NL +
				"<rect height=\"" + height + "\" width=\"" + width + "\" style=\"fill:white;stroke:black;stroke-width:1\"/>" + NL +
				"<g fill=\"black\" style=\"font-family:" + SVGConstants.MONOSPACE_FONT_FAMILY + ";font-size:" + SVGConstants.MONOSPACE_FONT_SIZE + "px;\">" + NL +
				texts +
				"</g>" + NL +
				"</svg>";

		return buf;
	}

	static <T, R, C extends Collection<R>> C forEach(Collection<T> c, Function<? super T, R> f, Supplier<C> s) {

		return c.stream().map(f::apply).collect(Collectors.toCollection(s));
	}

	static <T, C extends List<T>> C reverse(Collection<T> c, Supplier<C> s) {

		var ret = s.get();
		ret.addAll(c);
		Collections.reverse(ret);

		return ret;
	}

	// TODO - use this in BarChart
	static <C extends Collection<String>> C getPaddedLabels(Collection<? extends CharSequence> labels, Supplier<C> s, boolean escapeHTMLSpecialChars) {
		
		int maxLabelLength = Collections.max(forEach(labels, CharSequence::length, () -> new ArrayList<>()));
		return getPaddedLabels(labels, maxLabelLength, s, escapeHTMLSpecialChars);
	}

	static <C extends Collection<String>> C getPaddedLabels(Collection<? extends CharSequence> labels, int maxLabelLength, Supplier<C> s, boolean escapeHTMLSpecialChars) {
		
		Function<CharSequence, String> paddingFunc = cs -> getPaddedLabel(cs, maxLabelLength, escapeHTMLSpecialChars);

		return forEach(labels, paddingFunc, s);
	}

	static String getPaddedLabel(CharSequence label, int maxLabelLength, boolean escapeHTMLSpecialChars) {
		
		var paddedLabel = String.format("%1$" + maxLabelLength + "s", label);
		return escapeHTMLSpecialChars ? escapeHTMLSpecialChars(paddedLabel) : paddedLabel;
	}

	static String escapeHTMLSpecialChars(String str) {
		return str.replace("&", "&amp;").replace(" ", "&nbsp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	static String stripTrailingZeroesAfterDecimal(double d, boolean useGrouping) {
		
		var df = new DecimalFormat();
		df.setMinimumFractionDigits(0);
		df.setMaximumFractionDigits(Integer.MAX_VALUE);
		df.setGroupingUsed(useGrouping);
		return df.format(d);
	}

	static String stripTrailingZeroesAfterDecimal(Double d, boolean useGrouping) {
		return stripTrailingZeroesAfterDecimal(d.doubleValue(), useGrouping);
	}

	/**
	 * TODO - check if this is the right place for this method
	 */
	static Set<Long> getTimestampIntervalPoints(long[] timestamps, TimeZone timeZone, long timeIntervalInMillis) {
		
		if (timeIntervalInMillis <= 0) {
			throw new IllegalArgumentException("Invalid time interval: <" + timeIntervalInMillis + ">. Time interval must be a positive value");
		}

		var minTime = Long.MAX_VALUE;
		var maxTime = Long.MIN_VALUE;

		for (var timestamp : timestamps) {
			if (timestamp < minTime) {
				minTime = timestamp;
			}

			if (timestamp > maxTime) {
				maxTime = timestamp;
			}
		}

		var flooredMinTime = getStartOfHour(minTime, timeZone);

		Set<Long> timestampIntervalPoints = new HashSet<>();
		for (long point = flooredMinTime, interval = timeIntervalInMillis; point <= (maxTime + interval); point += interval) {
			timestampIntervalPoints.add(point);
		}

		return timestampIntervalPoints;
	}

	static Set<Double> toHashSet(double[] doubles) {
		Set<Double> set = new HashSet<>(doubles.length);
		for (var d : doubles) {
			set.add(d);
		}
		return set;
	}

	static Long getStartOfDay(long timestamp, TimeZone timeZone) {
		var calendar = Calendar.getInstance();
		calendar.setTimeZone(timeZone);
		calendar.setTimeInMillis(timestamp);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		return calendar.getTimeInMillis();
	}

	static long getStartOfHour(long timestamp, TimeZone timeZone) {
		var calendar = Calendar.getInstance();
		calendar.setTimeZone(timeZone);
		calendar.setTimeInMillis(timestamp);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar.getTimeInMillis();
	}

	static double[] primArr(Collection<Double> x) {
		var data = new double[x.size()];

		var i = 0;
		for (double d : x) {
			data[i++] = d;
		}
		return data;
	}

	static double sum(double[] data) {
		double sum = 0;
		for (var d : data) {
			sum += d;
		}
		return sum;
	}

	static double[] minMax(double[] data) {
		var min = Double.MAX_VALUE;
		var max = Double.MIN_VALUE;

		for (var d : data) {
			if (d > max) {
				max = d;
			}

			if (d < min) {
				min = d;
			}
		}

		return new double[] { min, max };
	}

	static double[] minMax(Collection<Double> data) {
		var min = Double.MAX_VALUE;
		var max = Double.MIN_VALUE;

		for (double d : data) {
			if (d > max) {
				max = d;
			}

			if (d < min) {
				min = d;
			}
		}

		return new double[] { min, max };
	}

	static double getMedian(double[] sortedData) {
		var n = sortedData.length;

		double median;
		if ((n % 2) == 0) {
			var k = (n / 2);
			median = (sortedData[k - 1] + sortedData[k]) / 2;
		} else {
			var k = (n + 1) / 2;
			median = sortedData[k - 1];
		}
		return median;
	}

	static double[] sort(double[] data) {
		var copy = Arrays.copyOf(data, data.length);
		Arrays.sort(copy);
		return copy;
	}

	static long[] sort(long[] data) {
		var copy = Arrays.copyOf(data, data.length);
		Arrays.sort(copy);
		return copy;
	}

	static Percentiles getPercentiles(double[] sortedData, double[] keys, String valueUnit) {
		
		var sortedKeys = Utils.sort(keys);

		var n = sortedKeys.length;

		var result = new double[n];
		var validKeys = new double[n];
		var k = 0;

		for (var key : sortedKeys) {
			try {
				result[k] = Utils.getPthPercentile(sortedData, key);
			} catch (IllegalPercentileKeyException e) {
				continue; // ignore this key and proceed to other keys
			}

			validKeys[k] = key;
			k++;
		}

		return new Percentiles(Arrays.copyOfRange(validKeys, 0, k), Arrays.copyOfRange(result, 0, k), valueUnit);
	}

	/**
	 * Slightly modified form of what is described here ->
	 * http://www.stanford.edu/class/archive/anthsci/anthsci192/anthsci192.1064/handouts/calculating%20percentiles.pdf
	 * 
	 * @throws IllegalArgumentException
	 *             if percentile can not be calculated for <code>p</code>
	 */
	static double getPthPercentile(double[] sortedData, double p) {
		var n = sortedData.length;

		var pos = (n * (p / 100)) + 0.5; // TODO - check if this is the correct way
		var integerPart = Math.floor(pos);
		var index = ((int) integerPart) - 1; // array index begins at 0

		if (index < 0) {
			throw new IllegalPercentileKeyException(n, p);
		}

		var fraction = pos - integerPart;

		var x = sortedData[index];

		double result;

		if ((fraction == 0) || (index == (n - 1))) {
			result = x;
		} else {
			// interpolate

			var y = sortedData[index + 1];
			var diff = y - x;

			result = x + (fraction * diff);
		}

		return result;
	}

	private static final class IllegalPercentileKeyException extends RuntimeException {
		
		private static final long serialVersionUID = -2793561757886762344L;

		IllegalPercentileKeyException(int n, double p) {
			super("n=" + n + ", p=" + p);
		}
	}

	/**
	 * TODO implement this. 
	 * See http://www.stanford.edu/class/archive/anthsci/anthsci192/anthsci192.1064/handouts/calculating%20percentiles.pdf
	 */
	private static double getPercentileOfValue() {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	static double[] zScores(double[] data, double mean, double stdDeviation) {
		var n = data.length;

		var zscores = new double[n];
		for (var i = 0; i < n; i++) {
			zscores[i] = (data[i] - mean) / stdDeviation;
		}
		return zscores;
	}

	static double[] getValuesForIndices(int[] indices, double[] data) {
		var n = indices.length;
		var result = new double[n];

		for (var i = 0; i < n; i++) {
			var index = indices[i];
			result[i] = data[index];
		}
		return result;
	}

	static int[] getIndicesOfValuesGreaterThan(double threshold, double[] values) {
		var n = values.length;
		var indices = new int[n];

		var c = 0;
		for (var i = 0; i < n; i++) {
			if (values[i] > threshold) {
				indices[c++] = i;
			}
		}

		return Arrays.copyOf(indices, c);
	}

	static double[] toDoubles(int[] ints) {

		var len = ints.length;
		var doubles = new double[len];

		for (var i = 0; i < len; i++) {
			doubles[i] = safeToDouble(ints[i]);
		}
		return doubles;
	}

	static double[] toDoubles(long[] longs) {

		var len = longs.length;
		var doubles = new double[len];

		for (var i = 0; i < len; i++) {
			doubles[i] = safeToDouble(longs[i]);
		}
		return doubles;
	}

	static MultiSpanSVGText createMultiSpanSVGText(String multiLineText, double x, double y, double fontSize, String color) {

		var lines = multiLineText.lines().collect(Collectors.toList());
		var spanCount = lines.size();

		var NL = System.lineSeparator();

		var label = new StringBuilder();
		label.append("<text text-anchor=\"middle\"");
		if (color != null) {
			label.append(" fill=\"").append(color).append("\"");
		}
		label.append(">");
		label.append(NL);

		for (var i = 0; i < spanCount; i++) {
			var line = lines.get(i);
			label.append("<tspan x=\"").append(x).append("\" y=\"").append(y + (i * fontSize)).append("\">").append(line).append("</tspan>").append(NL);
		}

		label.append("</text>").append(NL);

		return new MultiSpanSVGText(label, spanCount);
	}

	static boolean skipLabel(int currentLabelIndex, int totalLabelCount, int labelSkipCount) {
		
		var dontSkipLabel =
				(currentLabelIndex == 0) || (currentLabelIndex == totalLabelCount) || (((currentLabelIndex - 1) % (labelSkipCount + 1)) == 0);
		
		return !dontSkipLabel;
	}

	static String wrapInHTMLBody(String svgString) {
		
		var NL = System.lineSeparator();
		return "<!DOCTYPE html>" + NL + "<html>" + NL + "  <body>" + NL + svgString + NL + "  </body>" + NL + "</html>";
	}

	static void createFile(String content, String filePath) throws IOException {
		try (var fw = new FileWriter(filePath); var bw = new BufferedWriter(fw)) {
			bw.write(content);
		}
	}

	static List<Double> asList(double[] data) {

		List<Double> l = new ArrayList<>(data.length);
		for (var d : data) {
			l.add(d);
		}
		return l;
	}

	static double[] createIntervalPoints(Collection<Double> data, int nIntervalPoints) {
		var minMax = minMax(data);
		var min = minMax[0];
		var max = minMax[1];

		return createIntervalPoints(min, max, nIntervalPoints);
	}

	/**
	 * TODO - test with various data samples TODO - verify mathematical
	 * precision
	 * 
	 * @see <a href="http://stackoverflow.com/questions/326679/choosing-an-attractive-linear-scale-for-a-graphs-y-axis">Choosing an attractive linear scale for a graph's Y Axis</a>
	 */
	static double[] createIntervalPoints(double min, double max, int nIntervalPoints) {

		if (min < 0) {
			throw new IllegalArgumentException("min = <" + min + ">");
		}

		if (max < 0) {
			throw new IllegalArgumentException("max = <" + max + ">");
		}

		if (min > max) {
			throw new IllegalArgumentException("min = <" + min + ">, max = <" + max + ">");
		}

		if (nIntervalPoints < 1) {
			throw new IllegalArgumentException("Too few interval points <" + nIntervalPoints + ">");
		}

		var minBD = BigDecimal.valueOf(min);
		BigDecimal maxBD;

		if (min == max) {
			if ((min > 0) && (min < 1)) {
				var scale = minBD.scale();
				var pow = TEN.pow(scale);
				maxBD = minBD.multiply(pow).add(ONE).divide(pow);
			} else {
				maxBD = minBD.add(ONE);
			}
		} else {
			maxBD = BigDecimal.valueOf(max);
		}

		var range = maxBD.subtract(minBD);
		var interval = range.divide(BigDecimal.valueOf(nIntervalPoints), MathContext.DECIMAL128);

		// TODO - check if casting to int is OK.
		var x = Utils.safeToInt(Math.floor(Math.log10(interval.doubleValue()) + 1));

		var tenPowerX = x < 0 ? ONE.divide(TEN.pow(x * -1)) : TEN.pow(x);

		var y = interval.divide(tenPowerX).doubleValue();

		if ((y < 0.1) || (y > 1.0)) {
			throw new RuntimeException("Internal error: " + y);
		}

		double z;

		if (y == 0.1) {
			z = 0.1;
		} else if (y <= 0.2) {
			z = 0.2;
		} else if (y <= 0.25) {
			z = 0.25;
		} else if (y <= 0.3) {
			z = 0.3;
		} else if (y <= 0.4) {
			z = 0.4;
		} else if (y <= 0.5) {
			z = 0.5;
		} else if (y <= 0.6) {
			z = 0.6;
		} else if (y <= 0.7) {
			z = 0.7;
		} else if (y <= 0.75) {
			z = 0.75;
		} else if (y <= 0.8) {
			z = 0.8;
		} else if (y <= 0.9) {
			z = 0.9;
		} else {
			z = 1;
		}

		// Necessary to use BigDecimal to keep precision
		var niceInterval = BigDecimal.valueOf(z).multiply(tenPowerX);

		var intervalPoints = new double[nIntervalPoints];

		var niceMin = niceInterval.multiply(minBD.divide(niceInterval, 0, RoundingMode.FLOOR));

		intervalPoints[0] = niceMin.doubleValue();
		var prev = niceMin;

		for (var i = 1; i < intervalPoints.length; i++) {
			var current = prev.add(niceInterval);
			intervalPoints[i] = current.doubleValue();
			prev = current;
		}

		return intervalPoints;
	}

	static <T> T parseType(Class<T> type, String s) throws ParseException {

		var internalError = false;

		try {
			Object value;

			if ((type == Boolean.class) || (type == boolean.class)) {
				value = Boolean.valueOf(s);

			} else if ((type == Byte.class) || (type == byte.class)) {
				value = Byte.valueOf(s);

			} else if ((type == Character.class) || (type == char.class)) {

				if ((s == null) || (s.length() != 1)) {
					throw new ParseException(type, s);
				}
				value = s.charAt(0);

			} else if ((type == Short.class) || (type == short.class)) {
				value = Short.valueOf(s);

			} else if ((type == Integer.class) || (type == int.class)) {
				value = Integer.valueOf(s);

			} else if ((type == Long.class) || (type == long.class)) {
				value = Long.valueOf(s);

			} else if ((type == Float.class) || (type == float.class)) {
				value = Float.valueOf(s);

			} else if ((type == Double.class) || (type == double.class)) {
				value = Double.valueOf(s);

			} else if (type == String.class) {
				value = s;

			} else if (type == TimeUnit.class) {
				value = TimeUnit.valueOf(s);

			} else if (type == ColorRampScheme.class) {
				value = ColorRampScheme.valueOf(s);

			} else if (type == TimeZone.class){
				value = TimeZone.getTimeZone(s);

			} else if ((type == Boolean[].class) || (type == boolean[].class) || (type == Short[].class) || (type == short[].class)
					|| (type == Integer[].class) || (type == int[].class) || (type == Long[].class) || (type == long[].class)
					|| (type == Float[].class) || (type == float[].class) || (type == Double[].class) || (type == double[].class)
					|| (type == TimeUnit[].class) || (type == ColorRampScheme[].class) || (type == TimeZone[].class)) {

				var elements = s.split("\\s*,\\s*", -1);
				var len = elements.length;
				value = Array.newInstance(type.getComponentType(), len);
				for (var i = 0; i < len; i++) {
					Array.set(value, i, parseType(type.getComponentType(), elements[i]));
				}
			} else {
				internalError = true;
				throw new IllegalArgumentException("Internal error: Illegal type <" + type + ">");
			}

			return type.cast(value);

		} catch (Exception e) {

			if ((e instanceof ParseException) || internalError) {
				throw e;
			}

			throw new ParseException(type, s);
		}
	}

	static String toShortForm(TimeUnit timeUnit) {

		String result;

		switch (timeUnit) {
		case DAYS:
			result = "days";
			break;
		case HOURS:
			result = "hours";
			break;
		case MINUTES:
			result = "minutes";
			break;
		case SECONDS:
			result = "s";
			break;
		case MILLISECONDS:
			result = "ms";
			break;
		case MICROSECONDS:
			result = "\u00B5s";
			break;
		case NANOSECONDS:
			result = "ns";
			break;

		default:
			throw new IllegalArgumentException("Internal error: Illegal time unit <" + timeUnit + ">");
		}

		return result;
	}

	static String toDisplayString(double value, int precision, boolean useGroupSeparator) {

		String result;

		if (Double.isNaN(value)) {
			result = String.valueOf(value);
			
		} else {

			var format = new StringBuilder("%1$");

			if (useGroupSeparator) {
				format.append(',');
			}

			format.append('.').append(precision).append('f');

			result = String.format(format.toString(), value);
		}

		return result;
	}

	static String repeat(String str, int n) {

		Objects.requireNonNull(str);

		if (n < 1) {
			throw new IllegalArgumentException("n (" + n + ") is less than 1");
		}

		var b = new StringBuilder();

		for (var i = 0; i < n; i++) {
			b.append(str);
		}
		return b.toString();
	}

	static int safeToInt(double d) {
		
		if ((d > Integer.MAX_VALUE) || (d < Integer.MIN_VALUE)) {
			throw new RuntimeException("Internal error: " + d);
		}
	
		return (int) d;
	}

	static double safeToDouble(long l) {

		double d = l;

		if (((long) d) != l) {
			throw new RuntimeException("Internal error: " + l);
		}

		return d;
	}

	static long[] toOneDimArray(Long[][] matrix) {

		var rowCount = matrix.length;
		var colCount = matrix[0].length;

		var array = new long[rowCount * colCount];

		for (int r = 0, i = 0; r < rowCount; r++) {
			for (var c = 0; c < colCount; c++) {
				array[i++] = matrix[r][c];
			}
		}

		return array;
	}

	static <T> void fillMatrix(T[] sourceArray, T[][] targetMatrix) {

		var length = sourceArray.length;
		var rowCount = targetMatrix.length;
		var colCount = targetMatrix[0].length;

		if (length != (rowCount * colCount)) {
			throw new RuntimeException("Internal error: length = " + length + ", rowCount = " + rowCount + ", colCount = " + colCount);
		}

		for (int r = 0, i = 0; r < rowCount; r++) {
			for (var c = 0; c < colCount; c++) {
				targetMatrix[r][c] = sourceArray[i++];
			}
		}
	}

	static double getMax(double[] values) {

		var max = Double.MIN_VALUE;

		for (var value : values) {
			if (value > max) {
				max = value;
			}
		}
		return max;
	}

	static Double[] getNonZeroMinMax(double[] sorted) {

		var min = sorted[0];
		var max = sorted[sorted.length - 1];

		Double nonZeroMin;

		if (min == 0) {
			Double nzmi = null;

			for (var i = 1; i < sorted.length; i++) {
				var d = sorted[i];
				if (d != 0) {
					nzmi = d;
					break;
				}
			}

			nonZeroMin = nzmi;
		} else {
			nonZeroMin = min;
		}

		Double nonZeroMax;

		if (max == 0) {
			Double nzma = null;

			for (var i = sorted.length - 2; i >= 0; i--) {
				var d = sorted[i];
				if (d != 0) {
					nzma = d;
					break;
				}
			}

			nonZeroMax = nzma;
		} else {
			nonZeroMax = max;
		}

		return new Double[] { nonZeroMin, nonZeroMax };
	}

}
