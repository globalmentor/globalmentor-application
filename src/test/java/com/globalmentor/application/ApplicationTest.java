/*
 * Copyright © 2025 GlobalMentor, Inc. <https://www.globalmentor.com/>
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

import static com.globalmentor.java.OperatingSystem.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.*;

/**
 * Tests of {@link Application}.
 * @author Garret Wilson
 */
public class ApplicationTest {

	/** @see Application#getName() */
	@Test
	void tesDefaultName() {
		assertThat(new TestApp().getName(), is("TestApp"));
	}

	/** @see Application#getSlug() */
	@Test
	void tesDefaultSlug() {
		assertThat(new TestApp().getSlug(), is("test-app"));
	}

	/** @see Application#getGlobalConfigDirectory() */
	@Test
	void tesDefaultConfigDirectory() {
		assertThat(new TestApp().getGlobalConfigDirectory(), is(getUserHomeDirectory().resolve(".test-app")));
	}

	static class TestApp implements Application {
		@Override
		public String getVersion() {
			return "0.0.0";
		}

		@Override
		public void run() {
		}
	}

}
