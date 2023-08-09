/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @library ../ /test/lib
 * @build NativeTestHelper
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestJNISupport
 */

import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.JNISupport;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static org.testng.Assert.*;

public class TestJNISupport extends NativeTestHelper {

    static {
        System.loadLibrary("JNISupport");
    }

    private record Widget(int x) {}

    @Test
    public void testUpcallWithRef() throws Throwable {
        MethodHandle upcallWithRef = downcallHandle("upcall_with_ref", FunctionDescriptor.ofVoid(C_POINTER, C_POINTER));

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ref = JNISupport.newGlobalRef(new Widget(42), arena);
            MemorySegment stub = upcallStub(TestJNISupport.class, "target", FunctionDescriptor.ofVoid(C_POINTER));

            upcallWithRef.invokeExact(ref, stub);
        }
    }

    public static void target(MemorySegment ref) {
        Widget w = (Widget) JNISupport.resolveGlobalRef(ref);

        assertEquals(w.x(), 42);
    }
}
