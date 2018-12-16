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

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * @author Rahul Bakale
 * @since Nov, 2014
 */
final class Density<R extends Comparable<R>, C extends Comparable<C>, V> {
	
	static final class IndexedDataPoint<C extends Comparable<C>> implements Comparable<IndexedDataPoint<C>> {
		
		static <C extends Comparable<C>> IndexedDataPoint<C> createFinite(C actualData) {
			return new IndexedDataPoint<>(DataPoint.createFinite(actualData));
		}

		static <C extends Comparable<C>> IndexedDataPoint<C> createPositiveInfinite() {
			return new IndexedDataPoint<>(DataPoint.<C> createPositiveInfinite());
		}

		static <C extends Comparable<C>> IndexedDataPoint<C> createNegativeInfinite() {
			return new IndexedDataPoint<>(DataPoint.<C> createNegativeInfinite());
		}

		private final DataPoint<C> dataPoint;
		private int index;

		private IndexedDataPoint(DataPoint<C> dataPoint) {
			this.dataPoint = dataPoint;
		}

		int getIndex() {
			return this.index;
		}

		void setIndex(int index) {
			this.index = index;
		}

		@Override
		public int compareTo(IndexedDataPoint<C> o) {
			return this.dataPoint.compareTo(o.dataPoint);
		}

		@Override
		public int hashCode() {
			var prime = 31;
			var result = 1;
			result = (prime * result) + ((this.dataPoint == null) ? 0 : this.dataPoint.hashCode());
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
			IndexedDataPoint<?> other = (IndexedDataPoint<?>) obj;
			if (this.dataPoint == null) {
				return other.dataPoint == null;
			} else return this.dataPoint.equals(other.dataPoint);
		}

		@Override
		public String toString() {
			return toString(null);
		}

		String toString(Function<C, String> formatter) {
			return this.dataPoint.toString(formatter);
		}
	}

	private static <T extends Comparable<T>> NavigableSet<IndexedDataPoint<T>> sortAndIndex(Set<T> intervalPoints) {
		NavigableSet<IndexedDataPoint<T>> points = new TreeSet<>();

		for (var iPoint : intervalPoints) {
			points.add(IndexedDataPoint.createFinite(iPoint));
		}

		points.add(IndexedDataPoint.createNegativeInfinite());
		points.add(IndexedDataPoint.createPositiveInfinite());

		var index = 0;
		for (var point : points) {
			point.setIndex(index++);
		}

		return points;
	}

	private static <T extends Comparable<T>> int calculateIndex(T t, NavigableSet<IndexedDataPoint<T>> set) {
		return set.lower(IndexedDataPoint.createFinite(t)).getIndex();
	}

	static <R extends Comparable<R>, C extends Comparable<C>, V> Density<R, C, V> create(Set<R> rowIntervalPoints,
																						 Set<C> columnIntervalPoints, V nullValue, Class<V> valueType) {
		return new Density<>(rowIntervalPoints, columnIntervalPoints, nullValue, valueType);
	}

	private final NavigableSet<IndexedDataPoint<R>> rowIntervalPoints;
	private final NavigableSet<IndexedDataPoint<C>> columnIntervalPoints;

	private final V[][] matrix;

	private Density(Set<R> rowIntervalPoints, Set<C> columnIntervalPoints, V nullValue, Class<V> valueType) {

		var cip = sortAndIndex(columnIntervalPoints);
		var rip = sortAndIndex(rowIntervalPoints);

		var rowCount = rip.size() - 1;
		var colCount = cip.size() - 1;

		var matrix = (V[][]) Array.newInstance(valueType, rowCount, colCount);

		for (var r = 0; r < rowCount; r++) {
			for (var c = 0; c < colCount; c++) {
				matrix[r][c] = nullValue;
			}
		}

		this.columnIntervalPoints = cip;
		this.rowIntervalPoints = rip;

		this.matrix = matrix;
	}

	void apply(R row, C column, UnaryOperator<V> operator) {
		var rowNum = calculateIndex(row, this.rowIntervalPoints);
		var columnNum = calculateIndex(column, this.columnIntervalPoints);

		this.matrix[rowNum][columnNum] = operator.apply(this.matrix[rowNum][columnNum]);
	}

	@Override
	public String toString() {
		var NL = System.lineSeparator();

		var str = new StringBuilder("Points on X axis=" + this.columnIntervalPoints + NL + "Points on Y axis=" + this.rowIntervalPoints + NL);

		for (var row : this.matrix) {
			str.append(Arrays.deepToString(row)).append(NL);
		}
		return str.toString();
	}

	V[][] getMatrix() {
		return this.matrix;
	}

	NavigableSet<IndexedDataPoint<R>> getRowIntervalPoints() {
		return this.rowIntervalPoints;
	}

	NavigableSet<IndexedDataPoint<C>> getColumnIntervalPoints() {
		return this.columnIntervalPoints;
	}

}
