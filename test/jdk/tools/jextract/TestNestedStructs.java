/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;

import jdk.incubator.foreign.MemoryLayout;
import org.testng.annotations.Test;

import static jdk.incubator.foreign.CSupport.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/*
 * @test
 * @modules jdk.incubator.jextract
 * @library /test/lib
 * @build JextractToolRunner
 * @bug 8244512 8252759
 * @summary tests nested struct declarations
 * @run testng/othervm -Dforeign.restricted=permit TestNestedStructs
 */
public class TestNestedStructs extends JextractToolRunner {

    @Test
    public void testNestedStructs() {
        Path nestedOutput = getOutputFilePath("nestedgen");
        Path nestedH = getInputFilePath("nested.h");
        run("-d", nestedOutput.toString(), nestedH.toString()).checkSuccess();
        try(Loader loader = classLoader(nestedOutput)) {
            checkClass(loader, "Foo", EXPECTED_Foo_LAYOUT);
            checkClass(loader, "Foo$Bar", EXPECTED_Bar_LAYOUT);
            checkClass(loader, "U", EXPECTED_U_LAYOUT);
            checkClass(loader, "U$Point", EXPECTED_Point_LAYOUT);
            checkClass(loader, "MyStruct", EXPECTED_MyStruct_LAYOUT);
            checkClass(loader, "MyStruct$MyStruct_Z", EXPECTED_MyStruct_Z_LAYOUT);
            checkClass(loader, "MyStruct$k1", EXPECTED_k1_LAYOUT);
            checkClass(loader, "MyUnion", EXPECTED_MyUnion_LAYOUT);
            checkClass(loader, "MyUnion$MyUnion_Z", EXPECTED_MyUnion_Z_LAYOUT);
            checkClass(loader, "MyUnion$k2", EXPECTED_k2_LAYOUT);
            checkClass(loader, "X", EXPECTED_X_LAYOUT);
            checkClass(loader, "X2", EXPECTED_X2_LAYOUT);
        } finally {
            deleteDir(nestedOutput);
        }
    }

    private void checkClass(Loader loader, String name, MemoryLayout expectedLayout) {
        Class<?> cls = loader.loadClass("nested_h$" + name);
        assertNotNull(cls);
        MemoryLayout actual = findLayout(cls);
        assertEquals(actual, expectedLayout);
    }    
    
    static final MemoryLayout EXPECTED_Foo_LAYOUT = MemoryLayout.ofStruct(
            MemoryLayout.ofStruct(
                C_INT.withName("x"),
                C_INT.withName("y")
            ).withName("bar"),
            C_INT.withName("color")
        ).withName("Foo");

    static final MemoryLayout EXPECTED_Bar_LAYOUT = MemoryLayout.ofStruct(
        C_INT.withName("x"),
        C_INT.withName("y")
    ).withName("Bar");

    static final MemoryLayout EXPECTED_Point_LAYOUT = MemoryLayout.ofStruct(
        C_SHORT.withName("x"),
        C_SHORT.withName("y")
    ).withName("Point");

    static final MemoryLayout EXPECTED_U_LAYOUT = MemoryLayout.ofUnion(
        MemoryLayout.ofStruct(
            C_SHORT.withName("x"),
            C_SHORT.withName("y")
        ).withName("point"),
        C_INT.withName("rgb"),
        C_INT.withName("i")
    ).withName("U");

    static final MemoryLayout EXPECTED_MyStruct_LAYOUT = MemoryLayout.ofStruct(
        C_BOOL.withName("a"),
        MemoryLayout.ofPaddingBits(24),
        MemoryLayout.ofStruct(
            C_INT.withName("b"),
            MemoryLayout.ofUnion(
                C_INT.withName("c")
            ),
            C_BOOL.withName("d"),
            MemoryLayout.ofStruct(
                C_BOOL.withName("e")
            ).withName("f"),
            MemoryLayout.ofPaddingBits(16)
        ),
        MemoryLayout.ofUnion(
            C_INT.withName("g"),
            C_INT.withName("h")
        ),
        MemoryLayout.ofStruct(
            C_INT.withName("i"),
            C_INT.withName("j")
        ).withName("k1")
    ).withName("MyStruct");

    static final MemoryLayout EXPECTED_MyStruct_Z_LAYOUT = MemoryLayout.ofStruct(
        C_BOOL.withName("e")
    ).withName("MyStruct_Z");

    static final MemoryLayout EXPECTED_k1_LAYOUT = MemoryLayout.ofStruct(
        C_INT.withName("i"),
        C_INT.withName("j")
    );

    static final MemoryLayout EXPECTED_MyUnion_LAYOUT = MemoryLayout.ofUnion(
        C_BOOL.withName("a"),
        MemoryLayout.ofStruct(
            C_INT.withName("b"),
            MemoryLayout.ofUnion(
                C_INT.withName("c")
            ),
            C_BOOL.withName("d"),
            MemoryLayout.ofStruct(
                C_BOOL.withName("e")
            ).withName("f"),
            MemoryLayout.ofPaddingBits(16)
        ),
        MemoryLayout.ofStruct(
            C_INT.withName("g"),
            C_INT.withName("h")
        ),
        MemoryLayout.ofUnion(
            C_INT.withName("i"),
            C_INT.withName("j")
        ).withName("k2")
    ).withName("MyUnion");

    static final MemoryLayout EXPECTED_MyUnion_Z_LAYOUT = MemoryLayout.ofStruct(
        C_BOOL.withName("e")
    ).withName("MyUnion_Z");

    static final MemoryLayout EXPECTED_k2_LAYOUT = MemoryLayout.ofUnion(
        C_INT.withName("i"),
        C_INT.withName("j")
    );

    static final MemoryLayout EXPECTED_X_LAYOUT = MemoryLayout.ofStruct(
        MemoryLayout.ofStruct(
            MemoryLayout.ofUnion(
                C_INT.withName("y")
            ).withName("Z")
        )
    ).withName("X");

    static final MemoryLayout EXPECTED_X2_LAYOUT = MemoryLayout.ofStruct(
        MemoryLayout.ofStruct(
            MemoryLayout.ofUnion(
                C_INT.withName("y")
            )
        )
    ).withName("X2");
}
