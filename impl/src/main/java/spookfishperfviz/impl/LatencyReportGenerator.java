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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * @author Rahul Bakale
 * @since Nov, 2014
 */
public final class LatencyReportGenerator {
	
	private LatencyReportGenerator() {
		//
	}

	private static final AtomicInteger uniquifier = new AtomicInteger();

	static void run(Options options) throws Exception {

		var ignorePattern = options.getOptional("ignorePattern", String.class, null);
		var parsePattern = options.getMandatory("parsePattern", String.class);
		var timestampPattern = options.getMandatory("timestampPattern", String.class);
		var inputTimeZone = options.getOptional("inputTimeZone", TimeZone.class, TimeZone.getDefault());
		var outputTimeZone = options.getOptional("outputTimeZone", TimeZone.class, TimeZone.getDefault());
		var parser = SimpleRegexBasedRecordParser.create(ignorePattern, parsePattern, timestampPattern, inputTimeZone);

		var latencyUnit = options.getMandatory("latencyUnit", TimeUnit.class);

		var histogramIntervalPoints = options.getMandatory("histogramIntervalPoints", double[].class);
		var percentilePoints = options.getMandatory("percentilePoints", double[].class);
		var heatMapMaxIntervalPoints = options.getOptional("heatMapMaxIntervalPoints", Integer.class, null);

		var colorRampScheme = options.getOptional("colorRampScheme", ColorRampScheme.class, ColorRampScheme.DEFAULT);

		var inFile = options.getMandatory("inFile", String.class);
		var outFile = options.getMandatory("outFile", String.class);

		var heatMapSingleAreaWidth = 20;

		Path path;

		try (Reader fr = new FileReader(inFile); Reader source = new BufferedReader(fr)) {
			
			path = generateReport(source, parser, latencyUnit, outputTimeZone, histogramIntervalPoints, percentilePoints, 
									heatMapMaxIntervalPoints, heatMapSingleAreaWidth, colorRampScheme, outFile);
		}

		System.out.println("Report generated at <" + path + ">");
	}

	public static Path generateReport(	Reader source,
										RecordParser parser,
										TimeUnit latencyUnit,
										TimeZone outputTimeZone,
										double[] intervalPointsForLatencyHistogram,
										double[] percentileKeys,
										Integer maxIntervalPointsForLatencyDensity,
										double heatMapSingleAreaWidth,
										ColorRampScheme colorRampScheme,
										String outputFilePath) throws IOException {
		
		LatencyStatsToHtmlFunc latencyStatsToHtmlFunc = stats -> {

			var density = TimeSeriesLatencyDensity.create(stats.getLatencies(), stats.getTimestamps(), outputTimeZone, maxIntervalPointsForLatencyDensity);
			return stats.toHtml(intervalPointsForLatencyHistogram, percentileKeys, density, heatMapSingleAreaWidth, colorRampScheme);
		};

		return generateReport(source, parser, latencyUnit, outputTimeZone, latencyStatsToHtmlFunc, outputFilePath);
	}

	public static Path generateReport(	Reader source,
										RecordParser parser,
										TimeUnit latencyUnit,
										TimeZone outputTimeZone,
										double[] intervalPointsForLatencyHistogram,
										double[] percentileKeys,
										double minIntervalPointForLatencyDensity,
										double maxIntervalPointForLatencyDensity,
										Integer maxIntervalPointsForLatencyDensity,
										double heatMapSingleAreaWidth,
										ColorRampScheme colorRampScheme,
										String outputFilePath) throws IOException {
		
		LatencyStatsToHtmlFunc latencyStatsToHtmlFunc = stats -> {

			var density = TimeSeriesLatencyDensity.create(stats.getLatencies(), stats.getTimestamps(), outputTimeZone, minIntervalPointForLatencyDensity, maxIntervalPointForLatencyDensity, maxIntervalPointsForLatencyDensity);
			return stats.toHtml(intervalPointsForLatencyHistogram, percentileKeys, density, heatMapSingleAreaWidth, colorRampScheme);
		};

		return generateReport(source, parser, latencyUnit, outputTimeZone, latencyStatsToHtmlFunc, outputFilePath);
	}

	private static Path generateReport(	Reader source,
										RecordParser parser,
										TimeUnit latencyUnit,
										TimeZone outputTimeZone,
										LatencyStatsToHtmlFunc latencyStatsToHtmlFunc,
										String outputFilePath) throws IOException {

		try (var recordIterator = RecordIterator.create(source, parser))
		{
			Path reportFilePath;

			if (false) {

				// TODO - enable after adding feature to read from raw file if the
				// contents of input file have not changed since last read.

				var rawFile = createRawFile(recordIterator);
				reportFilePath = generateReport(rawFile, latencyUnit, outputTimeZone, latencyStatsToHtmlFunc, outputFilePath);

			} else {
				Map<String, List<TimestampAndLatency>> data = new TreeMap<>();

				while (recordIterator.hasNext()) {
					var record = recordIterator.next();
					addRecord(record.getEventName(), record.getTimestamp(), record.getLatency(), data);
				}

				reportFilePath = generateReport(data, latencyUnit, outputTimeZone, latencyStatsToHtmlFunc, outputFilePath);
			}

			return reportFilePath;
		}
	}

	private static Path generateReport(	File rawDataFile,
										TimeUnit latencyUnit,
										TimeZone outputTimeZone,
										LatencyStatsToHtmlFunc latencyStatsToHtmlFunc,
										String outputFilePath) throws IOException {
		
		return generateReport(parseRawFile(rawDataFile), latencyUnit, outputTimeZone, latencyStatsToHtmlFunc, outputFilePath);
	}

	private static Path generateReport(	Map<String, List<TimestampAndLatency>> data,
										TimeUnit latencyUnit,
										TimeZone outputTimeZone,
										LatencyStatsToHtmlFunc latencyStatsToHtmlFunc,
										String reportFilePath) throws IOException {

		var NL = System.lineSeparator();

		List<TimestampAndLatency> latenciesSuperSet = new ArrayList<>();

		var linksHtml = new StringBuilder();
		var contentsHtml = new StringBuilder();

		linksHtml.append("<table style=\"border:1px solid black; font-size: 14px;\">");
		linksHtml.append("<tr>").append(NL);
		linksHtml.append("<th>Event type</th>").append(NL);
		linksHtml.append("<th>Event count</th>").append(NL);
		linksHtml.append("<th>").append(createHtmlTextWithLink("Median", "http://en.wikipedia.org/wiki/Median")).append(" latency").append("</th>").append(NL);
		linksHtml.append("<th>").append(createHtmlTextWithLink("Mean", "http://en.wikipedia.org/wiki/Mean")).append(" latency").append("</th>").append(NL);
		linksHtml.append("<th>Minimum latency</th>").append(NL);
		linksHtml.append("<th>Maximum latency</th>").append(NL);
		linksHtml.append("<th>Summary</th>").append(NL);
		linksHtml.append(createHtmlColumnHeaderWithLink("Histogram", "http://en.wikipedia.org/wiki/Histogram")).append(NL);
		linksHtml.append(createHtmlColumnHeaderWithLink("Percentiles", "http://en.wikipedia.org/wiki/Percentile")).append(NL);
		linksHtml.append(createHtmlColumnHeaderWithLink("Heat Map", "http://en.wikipedia.org/wiki/Heat_map")).append(NL);
		linksHtml.append(createHtmlColumnHeaderWithLink("Std. Deviation", "http://en.wikipedia.org/wiki/Standard_deviation")).append(NL);
		linksHtml.append(createHtmlColumnHeaderWithLink("Variance", "http://en.wikipedia.org/wiki/Variance")).append(NL);
		linksHtml.append(createHtmlColumnHeaderWithLink("Skewness", "http://en.wikipedia.org/wiki/Skewness")).append(NL);
		linksHtml.append(createHtmlColumnHeaderWithLink("Kurtosis", "http://en.wikipedia.org/wiki/Kurtosis")).append(NL);
		linksHtml.append("</tr>").append(NL);

		TreeMap<Double, String> linkHtmlsSortedByMedian = new TreeMap<>();

		for (var entry : data.entrySet()) {
			var eventType = entry.getKey();
			var latencies = entry.getValue();

			var stats = Stats.create(latencies, latencyUnit, outputTimeZone, eventType);
			var latencyStats = stats.getLatencyStats();
			var h = latencyStatsToHtmlFunc.toHtml(latencyStats);

			contentsHtml.append(h[1]).append(NL);
			contentsHtml.append("<br/><br/>").append(NL);
			
			linkHtmlsSortedByMedian.put(latencyStats.getMedian(), h[0]);

			latenciesSuperSet.addAll(latencies);
		}

		{
			var stats = Stats.create(latenciesSuperSet, latencyUnit, outputTimeZone, "All APIs combined");
			var latencyStats = stats.getLatencyStats();
			var h = latencyStatsToHtmlFunc.toHtml(latencyStats);

			contentsHtml.append(h[1]).append(NL);
			contentsHtml.append("<br/><br/>");
			
			linksHtml.append(h[0]).append(NL);
		}

		for (var linkHtml : linkHtmlsSortedByMedian.descendingMap().values()) {
			linksHtml.append(linkHtml).append(NL);
		}

		linksHtml.append("</table>");

		var advertisementHtml = "This report was generated by <a href=\"https://github.com/rahulbakale/Spookfish-Perf-Viz\" target=\"_blank\">Spookfish-Perf-Viz, a free and open-source tool</a>, developed by Rahul Bakale.<br></br>";

		var perfStatsHtml =
				"<!DOCTYPE html>" + NL +
				"<html>" + NL +
				"<body>" + NL +
				advertisementHtml + NL +
				linksHtml + NL +
				contentsHtml + NL +
				"</html>" + NL +
				"</body>" + NL;

		return Files.write(Paths.get(reportFilePath), perfStatsHtml.getBytes(), WRITE, CREATE, TRUNCATE_EXISTING);

		/*
		 * final LatencyStats statsWithoutOutliers = removeOutliers(stats, 3);
		 * statsWithoutOutliers.print(latencyIntervalPoints, percentileKeys,
		 * System.out);
		 */
	}

	private static Map<String, List<TimestampAndLatency>> parseRawFile(File rawFile) throws IOException {
		Map<String, List<TimestampAndLatency>> data = new TreeMap<>();

		try (var fis = new FileInputStream(rawFile);
			 var bis = new BufferedInputStream(fis);
			 var dis = new DataInputStream(bis)) {

			while (true) {

				String eventType;
				try {
					eventType = dis.readUTF();
				} catch (EOFException e) {
					break;
				}

				var timestamp = dis.readLong();
				var latency = dis.readDouble();

				addRecord(eventType, timestamp, latency, data);
			}
		}

		return data;
	}

	private static void addRecord(	String eventType,
									long timestamp,
									double latency,
									Map<String, List<TimestampAndLatency>> data) {

		data
				.computeIfAbsent(eventType, key -> new ArrayList<>())
				.add(new TimestampAndLatency(timestamp, latency));
	}

	private static File createRawFile(RecordIterator recordIterator) throws IOException {

		var tmpIODir = System.getProperty("java.io.tmpdir");
		var baseDir = Files.createDirectories(Paths.get(tmpIODir, "perfstats_jackpot"));

		Path dir;
		{
			var processName = ManagementFactory.getRuntimeMXBean().getName();
			var timestamp = new SimpleDateFormat("ddMMyyyyHHmmssSSS").format(new Date());
			var id = processName + timestamp + uniquifier.incrementAndGet() + Math.random();
			dir = Files.createDirectory(new File(baseDir.toFile(), id).toPath());

			// TODO - check for universal uniqueness of the directory.
			// Read specification of Files.createDirectory
		}

		var rawFile = new File(dir.toFile(), "raw.dat");

		try (var fw = new FileOutputStream(rawFile);
			 var bos = new BufferedOutputStream(fw);
			 var dos = new DataOutputStream(bos)) {

			while (recordIterator.hasNext()) {

				var record = recordIterator.next();

				var eventName = record.getEventName();
				var timestamp = record.getTimestamp();
				var latency = record.getLatency();

				dos.writeUTF(eventName);
				dos.writeLong(timestamp);
				dos.writeDouble(latency);
			}
		}

		System.out.println("RAW file created : " + rawFile);

		return rawFile;
	}

	private static CharSequence createHtmlColumnHeaderWithLink(String columnName, String link) {
		return "<th>" + createHtmlTextWithLink(columnName, link) + "</th>";
	}

	private static CharSequence createHtmlTextWithLink(String text, String link) {
		return text + "<sup><a href=\"" + link + "\" target=\"_blank\">?</a></sup>";
	}

	private static final class Stats {
		
		static Stats create(List<TimestampAndLatency> latencyData, TimeUnit latencyUnit, TimeZone outputTimeZone, String eventType) {

			var len = latencyData.size();

			var latencies = new double[len];
			var timestamps = new long[len];

			var i = 0;
			for (var datum : latencyData) {
				latencies[i] = datum.latency;
				timestamps[i] = datum.timestamp;
				i++;
			}

			return new Stats(latencies, latencyUnit, timestamps, outputTimeZone, eventType);
		}

		private final LatencyStats latencyStats;

		// TODO - include volume stats in the report
		private final VolumeStats volumeStats;

		Stats(double[] latencies, TimeUnit latencyUnit, long[] timestamps, TimeZone outputTimeZone, String eventType) {
			this.latencyStats = LatencyStats.create(latencies, latencyUnit, timestamps, eventType);
			this.volumeStats = VolumeStats.create(timestamps, outputTimeZone);
		}

		LatencyStats getLatencyStats() {
			return this.latencyStats;
		}
	}

	private static final class DailyVolumeStats {
		
		private static final long MILLIS_IN_A_DAY = TimeUnit.DAYS.toMillis(1);

		private final Long day;
		private final int[] hourlyTrxCount;
		private int totalTrxCount;

		DailyVolumeStats(Long day) {
			this.day = day;
			this.hourlyTrxCount = new int[24]; // hourly volumeStats
		}

		void add(long millis) {
			if ((millis < 0) || (millis > MILLIS_IN_A_DAY)) {
				throw new IllegalArgumentException("" + millis);
			}

			var hour = (int) TimeUnit.MILLISECONDS.toHours(millis);

			var newCount = this.hourlyTrxCount[hour] + 1;

			this.hourlyTrxCount[hour] = newCount;

			this.totalTrxCount++;
		}

		@Override
		public String toString() {

			var day = this.day;
			var dayStr = String.format("%1$td %1$tb %1$tY", day);

			var trxCounts = this.hourlyTrxCount;
			var totalTrxCount = this.totalTrxCount;
			var len = trxCounts.length;

			var highestTrxCount = Integer.MIN_VALUE;
			var lowestTrxCount = Integer.MAX_VALUE;

			//noinspection ForLoopReplaceableByForEach
			for (var hour = 0; hour < len; hour++) {
				var trxCount = trxCounts[hour];

				highestTrxCount = Math.max(trxCount, highestTrxCount);
				lowestTrxCount = Math.min(trxCount, lowestTrxCount);
			}

			var trxCountPadding = String.format("%d", highestTrxCount).length();

			var labels = new String[len];
			List<Integer> peakHours = new ArrayList<>();
			List<Integer> valleyHours = new ArrayList<>();

			for (var hour = 0; hour < len; hour++) {

				var trxCount = trxCounts[hour];
				var perc = (trxCount * 100.0) / totalTrxCount;

				Integer hourBoxed = hour;

				labels[hour] = String.format("%1s    %2$02d    %3$" + trxCountPadding + "d    %4$6.2f%%", dayStr, hourBoxed, trxCount, perc);

				if (trxCount == highestTrxCount) {
					peakHours.add(hourBoxed);
				}

				if (trxCount == lowestTrxCount) {
					valleyHours.add(hourBoxed);
				}
			}

			var NL = System.lineSeparator();
			var BEGIN = " <<";
			var END = ">>";

			return "Volumetric statistics for " + dayStr + BEGIN + NL + 
					NL + 
					"Total transaction count = " + totalTrxCount + NL + 
					"Peak hours = " + peakHours + " each with " + highestTrxCount + " transactions" + NL + 
					"Valley hours = " + valleyHours + " each with " + lowestTrxCount + " transactions" + NL + 
					NL + 
					"Hourly volume" + BEGIN + NL + 
					NL + 
					HorizontalBarChart.create(trxCounts, labels) + END + NL + 
					END;
		}
	}

	private static final class VolumeStats {
		
		static VolumeStats create(long[] timestamps, TimeZone outputTimeZone) {
			return new VolumeStats(timestamps, outputTimeZone);
		}

		private final Map<Long, DailyVolumeStats> data;

		private VolumeStats(long[] timestamps, TimeZone outputTimeZone) {
			this.data = new HashMap<>();

			for (var timestamp : timestamps) {
				add(timestamp, outputTimeZone);
			}
		}

		private void add(long timestamp, TimeZone outputTimeZone) {
			
			//TODO - Utils.getStartOfDay(..) create a new Calendar object per call. Optimization needed.
			var startOfDay = Utils.getStartOfDay(timestamp, outputTimeZone);

			var stats = this.data.get(startOfDay);

			if (stats == null) {
				stats = new DailyVolumeStats(startOfDay);
				this.data.put(startOfDay, stats);
			}

			stats.add(timestamp - startOfDay);
		}

		@Override
		public String toString() {
			var NL = System.lineSeparator();

			var buf = new StringBuilder();

			for (var entry : this.data.entrySet()) {
				buf.append(entry.getValue()).append(NL).append(NL);
			}

			return buf.toString();
		}
	}

	/**
	 * http://www.itl.nist.gov/div898/handbook/eda/section3/eda35h.htm
	 * http://docs.oracle.com/cd/E17236_01/epm.1112/cb_statistical/frameset.htm?ch07s02s10s01.html 
	 * http://www.astm.org/SNEWS/MA_2011/datapoints_ma11.html
	 * http://msdn.microsoft.com/en-us/library/bb924370.aspx
	 * http://www.stanford.edu/class/archive/anthsci/anthsci192/anthsci192.1064/handouts/calculating%20percentiles.pdf
	 * http://web.stanford.edu/~mwaskom/software/seaborn/tutorial/plotting_distributions.html
	 * 
	 * TODO - add implementations for other outlier detection techniques,
	 * especially the one based on mean absolute deviation. See
	 * http://www.itl.nist.gov/div898/handbook/eda/section3/eda35h.htm
	 */
	private static final class LatencyStats {
		
		static LatencyStats create(double[] latencies, TimeUnit latencyUnit, long[] timestamps, String eventType) {
			return new LatencyStats(latencies, latencyUnit, timestamps, eventType);
		}

		// TODO - check correctness
		private static LatencyStats removeOutliers(LatencyStats stats, int outlierThreshold) {

			var outlierIndices = stats.getZScoreOutliers(outlierThreshold).getIndices();
			var outlierCount = outlierIndices.length;

			var latencies = stats.getLatencies();
			var timestamps = stats.getTimestamps();

			var maxCount = latencies.length;
			var latenciesWithoutOutliers = new double[maxCount];
			var timestampsWithoutOutliers = new long[maxCount];
			var count = 0;
			var k = 0;

			var nextOutlierIndex = (k >= outlierCount) ? Integer.MIN_VALUE : outlierIndices[k];
			k++;

			for (var i = 0; i < maxCount; i++) {
				if (i == nextOutlierIndex) {
					nextOutlierIndex = (k >= outlierCount) ? Integer.MIN_VALUE : outlierIndices[k];
					k++;
				} else {
					latenciesWithoutOutliers[count] = latencies[i];
					timestampsWithoutOutliers[count] = timestamps[i];
					count++;
				}
			}

			latenciesWithoutOutliers = Arrays.copyOf(latenciesWithoutOutliers, count);
			timestampsWithoutOutliers = Arrays.copyOf(timestampsWithoutOutliers, count);

			return LatencyStats.create(latenciesWithoutOutliers, stats.getLatencyUnit(), timestampsWithoutOutliers, stats.getEventType());
		}

		private final double[] latencies;
		private final TimeUnit latencyUnit;
		private final long[] timestamps;
		private final double[] sortedLatencies;
		private final int sampleCount;
		private final double min;
		private final double max;
		private final double mean;
		private final double median;
		private final double stdDeviation;
		private final double variance;

		/**
		 * Pearson's moment coefficient of skewness.
		 * 
		 * @see <a href="http://en.wikipedia.org/wiki/Skewness#Pearson.27s_moment_coefficient_of_skewness">Skewness, Pearson's moment coefficient of skewness</a>
		 * @see <a href="http://www.tc3.edu/instruct/sbrown/stat/shape.htm#SkewnessCompute">Skewness</a>
		 */
		private final double skewness;

		/**
		 * Pearson's moment coefficient of kurtosis.
		 * 
		 * @see <a href="http://en.wikipedia.org/wiki/Kurtosis#Pearson_moments">Kurtosis, Pearson moments</a>
		 * @see <a href="http://www.tc3.edu/instruct/sbrown/stat/shape.htm#KurtosisCompute">Kurtosis</a>
		 */
		private final double kurtosis;

		/**
		 * @see <a href="http://en.wikipedia.org/wiki/Kurtosis#Pearson_moments">Kurtosis, Pearson moments</a>
		 */
		private final double excessKurtosis;

		private final double[] zscores;
		private final String eventType;

		private LatencyStats(double[] latencies, TimeUnit latencyUnit, long[] timestamps, String eventType) {

			var n = latencies.length;

			var sum = Utils.sum(latencies);
			var mean = sum / n;

			var minMax = Utils.minMax(latencies);
			var min = minMax[0];
			var max = minMax[1];

			double s1 = 0;
			double s2 = 0;
			double s3 = 0;
			
			for (var latency : latencies) {

				var diff = latency - mean;

				s1 += Math.pow(diff, 2);
				s2 += Math.pow(diff, 3);
				s3 += Math.pow(diff, 4);
			}

			var variance = s1 / n;
			var thirdMoment = s2 / n;
			var fourthMoment = s3 / n;

			var skewness = thirdMoment / Math.pow(variance, 1.5);
			var kurtosis = fourthMoment / Math.pow(variance, 2);
			var excessKurtosis = kurtosis - 3;

			var stdDeviation = Math.sqrt(s1 / (n - 1));
			var sorted = Utils.sort(latencies);
			var median = Utils.getMedian(sorted);

			var zscores = Utils.zScores(latencies, mean, stdDeviation);

			this.sampleCount = n;
			this.latencies = latencies;
			this.sortedLatencies = sorted;
			this.latencyUnit = latencyUnit;
			this.timestamps = timestamps;
			this.min = min;
			this.max = max;
			this.mean = mean;
			this.median = median;
			this.stdDeviation = stdDeviation;
			this.variance = variance;
			this.skewness = skewness;
			this.kurtosis = kurtosis;
			this.excessKurtosis = excessKurtosis;
			this.zscores = zscores;
			this.eventType = eventType;
		}

		Outliers getZScoreOutliers(double threshold) {
			var indices = Utils.getIndicesOfValuesGreaterThan(threshold, this.zscores);
			return new Outliers(indices, Utils.getValuesForIndices(indices, this.latencies), Utils.getValuesForIndices(indices, this.zscores));
		}

		private Histogram<Double> createHistogram(double[] intervalPoints) {
			return Histogram.create(this.latencies, intervalPoints);
		}

		private Percentiles getPercentiles(double[] keys) {
			return Utils.getPercentiles(this.sortedLatencies, keys, Utils.toShortForm(this.latencyUnit));
		}

		private String getShortSummary() {

			var NL = System.lineSeparator();
			var IND = "    ";

			var timeUnit = Utils.toShortForm(this.latencyUnit);

			return 	IND + "       Event count = " + this.sampleCount + NL + 
					IND + "            Median = " + toDisplayString(this.median) + ' ' + timeUnit + NL + 
					IND + "              Mean = " + toDisplayString(this.mean) + ' ' + timeUnit + NL + 
					IND + "           Minimum = " + toDisplayString(this.min) + ' ' + timeUnit + NL + 
					IND + "           Maximum = " + toDisplayString(this.max) + ' ' + timeUnit + NL + 
					IND + "Standard deviation = " + toDisplayString(this.stdDeviation) + NL + 
					IND + "          Variance = " + toDisplayString(this.variance) + NL + 
					IND + "          Skewness = " + toDisplayString(this.skewness) + NL + 
					IND + "          Kurtosis = " + toDisplayString(this.kurtosis) + NL + 
					IND + "   Excess Kurtosis = " + toDisplayString(this.excessKurtosis);
		}

		private String getShortSummaryHtml() {

			var NL = System.lineSeparator();
			var timeUnit = Utils.toShortForm(this.latencyUnit);

			var fontFamily = SVGConstants.SERIF_FONT_FAMILY;
			var fontSize = SVGConstants.SERIF_FONT_SIZE;
			var columnStyle1 = "style=\"padding: 0px 0px 0px 10px; text-align: right;\"";
			var columnStyle2 = "style=\"padding: 0px 0px 0px 30px; text-align: right;\"";
			var columnStyle3 = "style=\"padding: 0px 10px 0px 10px; text-align: left;\"";

			//TODO - create a generic method that creates HTML table and remember to use escape HTML special characters.

			var html =
					"<table style=\"border:1px solid black; font-family: " + fontFamily + "; font-size: " + fontSize + "px;\">" + NL +
					"	<tr style=\"outline:1px solid black;\">" + NL +
					"		<th " + columnStyle1 + ">Name</th>" + NL +
					"		<th " + columnStyle2 + ">Value</th>" + NL + 
					"		<th " + columnStyle3 + ">Unit</th>" + NL + 
					"	</tr>" + NL +
					"	<tr>" + NL +
					"		<td " + columnStyle1 + ">Event count</td>" + NL +
					"		<td " + columnStyle2 + ">" + this.sampleCount + "</td>" + NL +
					"	</tr>" + NL + 
					"	<tr>" + NL +
					"		<td " + columnStyle1 + ">Median</td>" + NL +
					"		<td " + columnStyle2 + ">" + toDisplayString(this.median) + "</td>" + NL +
					"		<td " + columnStyle3 + ">" + timeUnit + "</td>" + NL +
					"	</tr>" + NL +
					"	<tr>" + NL +
					"		<td " + columnStyle1 + ">Mean</td>" + NL +
					"		<td " + columnStyle2 + ">" + toDisplayString(this.mean) + "</td>" + NL +
					"		<td " + columnStyle3 + ">" + timeUnit + "</td>" + NL +
					"	</tr>" + NL +
					"	<tr>" + NL +
					"		<td " + columnStyle1 + ">Minimum</td>" + NL +
					"		<td " + columnStyle2 + ">" + toDisplayString(this.min) + "</td>" + NL +
					"		<td " + columnStyle3 + ">" + timeUnit + "</td>" + NL +
					"	</tr>" + NL +
					"	<tr>" + NL +
					"		<td " + columnStyle1 + ">Maximum</td>" + NL +
					"		<td " + columnStyle2 + ">" + toDisplayString(this.max) + "</td>" + NL +
					"		<td " + columnStyle3 + ">" + timeUnit + "</td>" + NL +
					"	</tr>" + NL +
					"	<tr>" + NL +
					"		<td " + columnStyle1 + ">Standard deviation</td>" + NL +
					"		<td " + columnStyle2 + ">" + toDisplayString(this.stdDeviation) + "</td>" + NL +
					"	</tr>" + NL +
					"	<tr>" + NL +
					"		<td " + columnStyle1 + ">Variance</td>" + NL +
					"		<td " + columnStyle2 + ">" + toDisplayString(this.variance) + "</td>" + NL +
					"	</tr>" + NL +
					"	<tr>" + NL +
					"		<td " + columnStyle1 + ">Skewness</td>" + NL +
					"		<td " + columnStyle2 + ">" + toDisplayString(this.skewness) + "</td>" + NL +
					"	</tr>" + NL +
					"	<tr>" + NL +
					"		<td " + columnStyle1 + ">Kurtosis</td>" + NL +
					"		<td " + columnStyle2 + ">" + toDisplayString(this.kurtosis) + "</td>" + NL +
					"	</tr>" + NL +
					"	<tr>" + NL +
					"		<td " + columnStyle1 + ">Excess Kurtosis</td>" + NL +
					"		<td " + columnStyle2 + ">" + toDisplayString(this.excessKurtosis) + "</td>" + NL +
					"	</tr>" + NL +
					"</table>" + NL;

			return html;
		}

		double[] getLatencies() {
			return this.latencies;
		}
		
		TimeUnit getLatencyUnit() {
			return this.latencyUnit;
		}

		long[] getTimestamps() {
			return this.timestamps;
		}

		String getEventType() {
			return this.eventType;
		}

		public String toString(double[] latencyIntervalPoints, double[] percentileKeys) {

			var eventType = this.eventType;

			var NL = System.lineSeparator();
			var BEGIN = " <<";
			var END = ">>";

			return "Latency summary for " + eventType + BEGIN + NL + 
					NL + 
					getShortSummary() + NL + 
					END + NL + 
					NL + 
					"Latency histogram for " + eventType + BEGIN + NL + 
					NL + 
					createHistogram(latencyIntervalPoints) + NL + 
					END + NL + 
					NL + 
					"Latency percentiles for " + eventType + BEGIN + NL + 
					NL + getPercentiles(percentileKeys) + NL + 
					END;
		}

		public String[] toHtml(	double[] intervalPointsForLatencyHistogram,
				double[] percentileKeys,
				TimeSeriesLatencyDensity density,
				double heatMapSingleAreaWidth,
				ColorRampScheme colorRampScheme) {

			var eventType = this.eventType;

			var heatMapSVG = density.getHeatMapSVG(this.latencyUnit, heatMapSingleAreaWidth, colorRampScheme);
			var trxCountBarChartSVG =
					density.getTrxCountBarChartSVG(heatMapSVG.getXAxisLabelSkipCount(), heatMapSVG.getHeatMapBoxStartX(), heatMapSVG.getHeatMapSingleAreaWidth(), colorRampScheme);

			var NL = System.lineSeparator();
			var BR = "<br/>";

			var style = "style=\"font-family:Courier New, Courier, monospace; font-weight:bold;\"";

			var baseType = "Latency";

			var typeA = "summary";
			var typeB = "histogram";
			var typeC = "percentiles";
			var typeD = "heatmap";

			var textA = baseType + " " + typeA + " | " + eventType;
			var textB = baseType + " " + typeB + " | " + eventType;
			var textC = baseType + " " + typeC + " | " + eventType;
			var textD = baseType + " " + typeD + " | " + eventType;

			var linkIdA = LinkGenerator.next("a");
			var linkIdB = LinkGenerator.next("b");
			var linkIdC = LinkGenerator.next("c");
			var linkIdD = LinkGenerator.next("d");

			var rowStyle = "style=\"outline:1px solid black;\"";
			var columnStyle = "style=\"padding: 8px; text-align: right;\"";

			var links =
					"<tr " + rowStyle + ">" + NL + 
					"<td " + columnStyle + ">" + eventType + "</td>" + NL + 
					"<td " + columnStyle + ">" + this.sampleCount + "</td>" + NL + 
					"<td " + columnStyle + ">" + toDisplayString(this.median) + "</td>" + NL + 
					"<td " + columnStyle + ">" + toDisplayString(this.mean) + "</td>" + NL + 
					"<td " + columnStyle + ">" + toDisplayString(this.min) + "</td>" + NL + 
					"<td " + columnStyle + ">" + toDisplayString(this.max) + "</td>" + NL + 
					"<td " + columnStyle + ">" + linkWithRef(typeA, linkIdA) + "</td>" + NL + 
					"<td " + columnStyle + ">" + linkWithRef(typeB, linkIdB) + "</td>" + NL + 
					"<td " + columnStyle + ">" + linkWithRef(typeC, linkIdC) + "</td>" + NL + 
					"<td " + columnStyle + ">" + linkWithRef(typeD, linkIdD) + "</td>" + NL + 
					"<td " + columnStyle + ">" + toDisplayString(this.stdDeviation) + "</td>" + NL + 
					"<td " + columnStyle + ">" + toDisplayString(this.variance) + "</td>" + NL + 
					"<td " + columnStyle + ">" + toDisplayString(this.skewness) + "</td>" + NL + 
					"<td " + columnStyle + ">" + toDisplayString(this.excessKurtosis) + "</td>" + NL + 
					"</tr>" + NL;

			var content =
					paragraph(linkWithId(textA, linkIdA) + ':', style) + getShortSummaryHtml() + BR + BR +
					paragraph(linkWithId(textB, linkIdB) + ':', style) + createHistogram(intervalPointsForLatencyHistogram).toSVG(d -> Utils.stripTrailingZeroesAfterDecimal(d, false), false, colorRampScheme) + BR + BR +
					paragraph(linkWithId(textC, linkIdC) + ':', style) + getPercentiles(percentileKeys).toSVG(false) + BR + BR + 
					paragraph(linkWithId(textD, linkIdD) + ':', style) + trxCountBarChartSVG + BR + BR + heatMapSVG.getSvg();

			return new String[] { links, content };
		}

		private static String paragraph(String paragraphText, String cssStyle) {
			return "<p " + cssStyle + ">" + paragraphText + "</p>";
		}

		private static String linkWithId(String text, String id) {
			return "<a id=\"" + id + "\">" + text + "</a>";
		}

		private static String linkWithRef(String text, String ref) {
			return "<a href=\"#" + ref + "\">" + text + "</a>";
		}

		double getMedian() {
			return this.median;
		}

		private static String toDisplayString(double d) {
			return Utils.toDisplayString(d, 3, true);
		}
	}

	private static final class Outliers {
		private final int[] indices;
		private final double[] data;
		private final double[] zscores;

		Outliers(int[] indices, double[] data, double[] zscores) {
			this.indices = indices;
			this.data = data;
			this.zscores = zscores;
		}

		int[] getIndices() {
			return this.indices;
		}

		@Override
		public String toString() {

			var n = this.indices.length;

			var b = new StringBuilder();

			b.append('[');
			for (var i = 0; i < n; i++) {
				b.append('(').append(this.indices[i]).append(',').append(this.data[i]).append(',').append(this.zscores[i]).append(')');
				if (i < (n - 1)) {
					b.append(',');
				}
			}
			b.append(']');

			return b.toString();
		}
	}

	private static final class LinkGenerator {
		private static int LINK_COUNTER = 0;

		static String next(String suffix) {
			return "link" + ++LINK_COUNTER + suffix;
		}
	}
	
	private interface LatencyStatsToHtmlFunc {
		String[] toHtml(LatencyStats stats);
	}
}
