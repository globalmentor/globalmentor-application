/*
 * Copyright © 2019-2025 GlobalMentor, Inc. <https://www.globalmentor.com/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.globalmentor.application;

import picocli.CommandLine;

/**
 * A CLI application based on <a href="https://picocli.info/">picocli</a>.
 * @author Garret Wilson
 */
public interface PicocliApplication extends CliApplication {

	/**
	 * Returns the picocli command line instance. This method must never be called before initialization.
	 * @apiNote This is useful for further customizing picocli, e.g. by installing additional type converters specific to the application.
	 * @return The picocli command line instance.
	 * @throws IllegalStateException if this method is called before the application is initialized.
	 * @see #initialize()
	 */
	public CommandLine getCommandLine();

}
