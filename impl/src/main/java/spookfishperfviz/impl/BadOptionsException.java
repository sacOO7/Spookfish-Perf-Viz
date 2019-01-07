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
final class BadOptionsException extends Exception {

	private static final long serialVersionUID = 7736583174452576492L;

	static BadOptionsException badOptionName(String message, String[] args, int argNum) {
		return new BadOptionsException(message + ": args[" + argNum + "] = <" + args[argNum] + ">");
	}

	static BadOptionsException optionNotSpecified(String optionName) {
		return new BadOptionsException("Option <" + optionName + "> is not specified");
	}

	static BadOptionsException valueNotSpecified(String optionName) {
		return new BadOptionsException("Value is not specified for option <" + optionName + ">");
	}

	static BadOptionsException illegalValue(String optionName, String message, Throwable cause) {
		return new BadOptionsException("Option <" + optionName + "> has illegal value. " + message, cause);
	}

	private BadOptionsException(String message) {
		this(message, null);
	}

	private BadOptionsException(String message, Throwable cause) {
		super(message, cause);
	}
}
