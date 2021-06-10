/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jdk.incubator.foreign.ResourceScope;
import org.testng.annotations.*;
import static org.testng.Assert.*;

/*
 * @test
 * @run testng TestToCStringWide
 */

public class TestToCStringWide {

    @Test(dataProvider = "strings")
    public void testStrings(Charset charset, int bytesPerChar) {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            String testString = "testing";
            MemorySegment text = CLinker.toCString(testString, charset, scope);

            // Only if we have no surrogate pairs:
            int expectedByteLength = (testString.length() + 1) * bytesPerChar;
            assertEquals(text.byteSize(), expectedByteLength);

            for (long i = text.byteSize() - bytesPerChar; i < text.byteSize(); i++) {
                assertEquals(MemoryAccess.getByteAtOffset(text, i), 0);
            }
        }
    }

    @DataProvider
    public static Object[][] strings() {
        return new Object[][]{
            { StandardCharsets.US_ASCII,   1 },
            { StandardCharsets.UTF_8,      1 },
            { StandardCharsets.UTF_16LE,   2 },
            { Charset.forName("UTF_32LE"), 4 },
        };
    }
}
