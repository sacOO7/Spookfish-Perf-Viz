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
final class Percentiles {
	
	private static final String KEY_HEADER = "Percentile";
	
	
	private final double[] keys;
	private final double[] values;
	private final String valueHeader;

	Percentiles(final double[] keys, final double[] values, final String valueUnit) {
		
		if (keys.length != values.length) {
			throw new IllegalArgumentException();
		}

		this.keys = keys;
		this.values = values;
		this.valueHeader = "Value (" + valueUnit + ")";
	}

	public String toSVG(final boolean wrapInHtmlBody) {
		return toBarChart().toSVG(wrapInHtmlBody, null);
	}

	@Override
	public String toString() {
		return toBarChart().toString();
	}

	private HorizontalBarChart toBarChart() {
		
		final int n = this.keys.length;

		final double[] keys = this.keys;
		final double[] values = this.values;
		final String valHeader = this.valueHeader;

		final String[] keyStrs = new String[n];
		final String[] valueStrs = new String[n];

		final double[] valuesReversed = new double[n];

		int kPadding = -1, vPadding = -1;

		for (int i = 0, k = n - 1; i < n; i++, k--) {
			{
				final String keyStr = String.valueOf(keys[i]);

				final int kLen = keyStr.length();
				if (kLen > kPadding) {
					kPadding = kLen;
				}

				keyStrs[k] = keyStr;
			}

			{
				final double value = values[i];
				final String valueStr = Utils.toDisplayString(value, 3, true);

				final int vLen = valueStr.length();
				if (vLen > vPadding) {
					vPadding = vLen;
				}

				valuesReversed[k] = value;
				valueStrs[k] = valueStr;
			}
		}
		
		kPadding = Math.max(kPadding, KEY_HEADER.length());
		vPadding = Math.max(vPadding, valHeader.length());
		
		final String labelStringFormat = "%" + kPadding + "s    %" + vPadding + "s";

		final String[] dataLabels = new String[n];
		for (int i = 0; i < n; i++) {
			dataLabels[i] = String.format(labelStringFormat, keyStrs[i], valueStrs[i]);
		}
		
		final String headerLabel = String.format(labelStringFormat, KEY_HEADER, valHeader);

		return HorizontalBarChart.create(valuesReversed, dataLabels, headerLabel);
	}
}