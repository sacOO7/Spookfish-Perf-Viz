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

import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * @author Rahul Bakale
 * @since Nov, 2014
 * 
 * @param <C> data type
 */
final class Histogram2<C extends Comparable<C>> extends Histogram<C> {
	
	private final SortedMap<Interval<C>, Integer> histogram;

	static Histogram2<Double> newInstance(double[] data, double[] intervalPoints, boolean ignoreEmptyIntervals) {
		return newInstance(Utils.asList(data), intervalPoints, ignoreEmptyIntervals);
	}

	static Histogram2<Double> newInstance(double[] data, int nIntervalPoints, boolean ignoreEmptyIntervals) {
		return newInstance(Utils.asList(data), nIntervalPoints, ignoreEmptyIntervals);
	}

	static Histogram2<Double> newInstance(Collection<Double> data, int nIntervalPoints, boolean ignoreEmptyIntervals) {
		return newInstance(data, Utils.createIntervalPoints(data, nIntervalPoints), ignoreEmptyIntervals);
	}

	static Histogram2<Double> newInstance(Collection<Double> data, double[] intervalPoints, boolean ignoreEmptyIntervals) {
		return new Histogram2<>(data, Utils.toHashSet(intervalPoints), ignoreEmptyIntervals);
	}

	static <T extends Comparable<T>> Histogram2<T> newInstance(Collection<T> data, Set<T> intervalPoints,
															   boolean ignoreEmptyIntervals) {
		return new Histogram2<>(data, intervalPoints, ignoreEmptyIntervals);
	}

	private Histogram2(Collection<C> data, Set<C> intervalPoints, boolean ignoreEmptyIntervals) {
		
		Set<Interval<C>> intervals = new HashSet<>();
		var loop = true;
		
		var iterator = new TreeSet<>(intervalPoints).iterator();
		
		DataPoint<C> low = DataPoint.createNegativeInfinite();
		
		do {
			DataPoint<C> high;

			if (iterator.hasNext()) {
				high = DataPoint.createFinite(iterator.next());
			} else {
				high = DataPoint.createPositiveInfinite();
				loop = false;
			}

			intervals.add(new Interval<>(low, high));

			low = high;
			
		} while (loop);

		SortedMap<Interval<C>, Integer> hist = new TreeMap<>();

		if (!ignoreEmptyIntervals) {
			Integer ZERO = 0;
			for (var interval : intervals) {
				hist.put(interval, ZERO);
			}
		}

		for (var datum : data) {
			for (var interval : intervals) {
				if (interval.contains(DataPoint.createFinite(datum))) {
					hist.put(interval, hist.containsKey(interval) ? Integer.valueOf(hist.get(interval) + 1) : Integer.valueOf(1));
					break;
				}
			}
		}

		this.histogram = hist;
	}

	@Override
	public String toString() {
		return toBarChart(null).toString();
	}

	@Override
	String toString(Function<C, String> dataPointFormatter, int maxHeight, String mark) {
		return toBarChart(dataPointFormatter).toString(maxHeight, mark);
	}

	@Override
	String toSVG(Function<C, String> dataPointFormatter, boolean wrapInHtmlBody, ColorRampScheme colorRampScheme) {
		return toBarChart(dataPointFormatter).toSVG(wrapInHtmlBody, colorRampScheme);
	}

	private HorizontalBarChart toBarChart(Function<C, String> dataPointFormatter) {
		var size = this.histogram.size();
		Entry<Interval<C>, Integer>[] entries = this.histogram.entrySet().toArray(new Entry[size]);

		var labelMaker = new LabelMaker<>(entries, dataPointFormatter);
		var data = new int[size];
		var labels = new String[size];

		for (var k = 0; k < size; k++) {
			data[k] = entries[k].getValue();
			labels[k] = labelMaker.getDataLabel(k);
		}

		var headerLabel = labelMaker.getHeaderLabel();

		return HorizontalBarChart.create(data, labels, headerLabel);
	}

	private static final class Interval<C extends Comparable<C>> implements Comparable<Interval<C>> {
		private final DataPoint<C> low;
		private final DataPoint<C> high;

		Interval(DataPoint<C> low, DataPoint<C> high) {
			
			Objects.requireNonNull(low);
			Objects.requireNonNull(high);

			if (low.compareTo(high) >= 0) {
				throw new IllegalArgumentException("Low = <" + low + ">. High = <" + high + ">.");
			}

			this.low = low;
			this.high = high;
		}

		boolean contains(DataPoint<C> val) {
			Objects.requireNonNull(val);

			return (val.compareTo(this.low) >= 0) && (val.compareTo(this.high) < 0);
		}

		@Override
		public int compareTo(Interval<C> other) {
			if (this.equals(other)) {
				return 0;
			}

			if (this.high.compareTo(other.low) <= 0) {
				return -1;
			}

			if (this.low.compareTo(other.high) >= 0) {
				return 1;
			}

			throw new RuntimeException("The following intervals cannot be compared: <" + this + ">, <" + other + ">");
		}

		@Override
		public int hashCode() {
			var prime = 31;
			var result = 1;
			result = (prime * result) + ((this.high == null) ? 0 : this.high.hashCode());
			result = (prime * result) + ((this.low == null) ? 0 : this.low.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			Interval<?> other = (Interval<?>) obj;
			if (this.high == null) {
				if (other.high != null) {
					return false;
				}
			} else if (!this.high.equals(other.high)) {
				return false;
			}
			if (this.low == null) {
				return other.low == null;
			} else return this.low.equals(other.low);
		}

		@Override
		public String toString() {
			return toString(null);
		}

		String toString(Function<C, String> dataPointFormatter) {
			return "[" + this.low.toString(dataPointFormatter) + ',' + this.high.toString(dataPointFormatter) + "]";
		}
	}

	private static final class LabelMaker<C extends Comparable<C>> {
		
		private static final String INTERVAL_HEADER = "Interval";
		private static final String FREQUENCY_HEADER = "Count";
		private static final String PERCENTAGE_HEADER = "%";
		private static final String CUMULATIVE_PERCENTAGE_HEADER = "Sum of %";
		
		private final Entry<Interval<C>, Integer>[] table;
		private final int intrvlpadding;
		private final int freqPadding;
		private final double[] percs;
		private final double[] cumulatives;
		private final Function<C, String> dataPointFormatter;
		private final String labelStringFormat;
		private final String headerLabel;

		LabelMaker(Entry<Interval<C>, Integer>[] entries, Function<C, String> dataPointFormatter) {
			
			long sumOfFrequencies = 0;
			int iPadding = -1, fPadding = -1;

			for (var row : entries) {
				var interval = row.getKey();
				var frequency = row.getValue();

				sumOfFrequencies += frequency;
				iPadding = Math.max(iPadding, interval.toString(dataPointFormatter).length());
				fPadding = Math.max(fPadding, String.valueOf(frequency).length());
			}

			iPadding = Math.max(iPadding, INTERVAL_HEADER.length());
			fPadding = Math.max(fPadding, FREQUENCY_HEADER.length());

			var pPadding = Math.max(7 /*xxx.xx%*/, PERCENTAGE_HEADER.length());
			var cPadding = Math.max(7 /*xxx.xx%*/, CUMULATIVE_PERCENTAGE_HEADER.length());

			var size = entries.length;

			var p = new double[size];
			var c = new double[size];

			double cumulative = 0;

			for (var i = 0; i < size; i++) {
				int frequency = entries[i].getValue();

				var perc = (frequency * 100.0) / sumOfFrequencies;
				cumulative += perc;

				p[i] = perc;
				c[i] = cumulative;
			}
			
			

			this.table = entries;
			this.percs = p;
			this.cumulatives = c;
			this.intrvlpadding = iPadding;
			this.freqPadding = fPadding;
			this.dataPointFormatter = dataPointFormatter;
			
			this.labelStringFormat = 
					"%1$" + this.intrvlpadding + "s" +  "   " + 
					"%2$" + this.freqPadding + "s" + "   " + 
					"%3$" + pPadding + "s" + "   " +
					"%4$" + cPadding + "s";
			
			this.headerLabel = String.format(this.labelStringFormat, 
												INTERVAL_HEADER, 
												FREQUENCY_HEADER, 
												PERCENTAGE_HEADER, 
												CUMULATIVE_PERCENTAGE_HEADER);
		}

		String getDataLabel(int index) {

			var row = this.table[index];
			var interval = row.getKey();
			var frequency = row.getValue();

			var perc = this.percs[index];
			var cumulative = this.cumulatives[index];

			return String.format(this.labelStringFormat, 
					interval.toString(this.dataPointFormatter), 
					frequency, 
					Utils.toDisplayString(perc, 2, false) + '%', 
					Utils.toDisplayString(cumulative, 2, false) + '%');
		}

		String getHeaderLabel() {
			return this.headerLabel;
		}
	}
}
