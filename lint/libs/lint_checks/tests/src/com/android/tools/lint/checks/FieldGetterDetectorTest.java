/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class FieldGetterDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new FieldGetterDetector();
    }

    public void test() throws Exception {
        assertEquals(
                "GetterTest.java:48: Warning: Calling getter method getFoo1() on self is slower than field access\n" +
                "GetterTest.java:49: Warning: Calling getter method getFoo2() on self is slower than field access\n" +
                "GetterTest.java:53: Warning: Calling getter method isBar1() on self is slower than field access\n" +
                "GetterTest.java:55: Warning: Calling getter method getFoo1() on self is slower than field access\n" +
                "GetterTest.java:56: Warning: Calling getter method getFoo2() on self is slower than field access",

                lintProject(
                    "bytecode/.classpath=>.classpath",
                    "bytecode/AndroidManifest.xml=>AndroidManifest.xml",
                    "bytecode/GetterTest.java.txt=>src/test/bytecode/GetterTest.java",
                    "bytecode/GetterTest.class.data=>bin/classes/test/bytecode/GetterTest.class"
                    ));
    }
}