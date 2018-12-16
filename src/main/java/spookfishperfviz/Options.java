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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Rahul Bakale
 * @since Nov, 2014
 */
final class Options {

	static Options create(String[] args) throws BadOptionsException {

		var options = new Options();

		if ((args != null) && (args.length > 0)) {

			for (var i = 0; i < args.length; i++) {

				String optName;
				{
					var name = args[i];

					if (name == null) {
						throw BadOptionsException.badOptionName("Option's name is null", args, i);
					}

					var trimmedName = name.trim();

					if (!trimmedName.startsWith("-")) {
						throw BadOptionsException.badOptionName("Option's name does not start with '-'", args, i);
					}

					optName = trimmedName.substring(1);

					if (optName.isEmpty()) {
						throw BadOptionsException.badOptionName("Option's name is invalid", args, i);
					}
				}

				String optValue;
				{
					i++;

					if (i >= args.length) {
						throw BadOptionsException.valueNotSpecified(optName);
					}

					optValue = args[i];
				}

				options.add(optName, optValue);
			}
		}

		return options;
	}

	private static <T> T parse(String optionName, String value, Class<T> valueType) throws BadOptionsException {
		try {
			return Utils.parseType(valueType, value);
		} catch (ParseException e) {
			throw BadOptionsException.illegalValue(optionName, e.getMessage(), e);
		}
	}

	private final Map<String, Optional<String>> options;

	private Options() {
		this.options = new HashMap<>();
	}

	private void add(String name, String value) {
		this.options.put(name, Optional.of(value));
	}

	<T> T getMandatory(String optionName, Class<T> valueType) throws BadOptionsException {

		var value = getMandatory(optionName);
		return parse(optionName, value, valueType);
	}

	<T> T getOptional(String optionName, Class<T> valueType, T defaultValue) throws BadOptionsException {

		var optional = this.options.get(optionName);
		return (optional != null && optional.isPresent()) ? parse(optionName, optional.get(), valueType) : defaultValue;
	}

	private String getMandatory(String optionName) throws BadOptionsException {

		var optional = this.options.get(optionName);

		if (optional == null) {
			throw BadOptionsException.optionNotSpecified(optionName);
		}

		return optional.orElseThrow(() -> BadOptionsException.illegalValue(optionName, "Value is null.", null));
	}
}
