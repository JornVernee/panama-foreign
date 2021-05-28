/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 *
 * @run testng/othervm/native
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   TestUpcallScope
 * @run testng/othervm/native
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   TestUpcallScope
 */

import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.SymbolLookup;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import org.testng.annotations.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static jdk.incubator.foreign.CLinker.C_DOUBLE;
import static jdk.incubator.foreign.CLinker.C_INT;
import static jdk.incubator.foreign.CLinker.C_POINTER;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestUpcallScope {
    static final MethodHandle MH_do_struct_upcall;
    static final MethodHandle MH_do_void_upcall;
    static final CLinker LINKER = CLinker.getInstance();
    static final MethodHandle MH_Consumer_accept;

    // struct S_PDI { void* p0; double p1; int p2; };
    static final MemoryLayout S_PDI_LAYOUT = MemoryLayout.structLayout(
        C_POINTER.withName("p0"),
        C_DOUBLE.withName("p1"),
        C_INT.withName("p2")
    );

    static {
        System.loadLibrary("TestUpcallScope");
        SymbolLookup lookup = SymbolLookup.loaderLookup();
        MH_do_struct_upcall = LINKER.downcallHandle(
            lookup.lookup("do_struct_upcall").get(),
            MethodType.methodType(void.class, MemoryAddress.class, MemorySegment.class),
            FunctionDescriptor.ofVoid(C_POINTER, S_PDI_LAYOUT)
        );
        MH_do_void_upcall = LINKER.downcallHandle(
            lookup.lookup("do_void_upcall").get(),
            MethodType.methodType(void.class, MemoryAddress.class),
            FunctionDescriptor.ofVoid(C_POINTER)
        );

        try {
            MH_Consumer_accept = MethodHandles.publicLookup().findVirtual(Consumer.class, "accept",
                    MethodType.methodType(void.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static MethodHandle methodHandle (Consumer<MemorySegment> callback) {
        return MH_Consumer_accept.bindTo(callback).asType(MethodType.methodType(void.class, MemorySegment.class));
    }

    @Test
    public void testUpcall() throws Throwable {
        AtomicReference<MemorySegment> capturedSegment = new AtomicReference<>();
        MethodHandle target = methodHandle(capturedSegment::set);
        FunctionDescriptor upcallDesc = FunctionDescriptor.ofVoid(S_PDI_LAYOUT);
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemoryAddress upcallStub = LINKER.upcallStub(MethodHandles.dropArguments(target, 0, ResourceScope.class), upcallDesc, scope);
            MemorySegment argSegment = MemorySegment.allocateNative(S_PDI_LAYOUT, scope);
            MH_do_struct_upcall.invokeExact(upcallStub.address(), argSegment);
        }

        MemorySegment captured = capturedSegment.get();
        assertFalse(captured.scope().isAlive());
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*Target handle must have ResourceScope as first parameter.*")
    public void testUpcallHandleNoScopeParam() {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            LINKER.upcallStub(
                MethodHandles.empty(MethodType.methodType(void.class)),
                FunctionDescriptor.ofVoid(),
                scope); // should throw
        }
    }

    @Test
    public void testUpcallScopeNotClosable() throws Throwable {
        Consumer<ResourceScope> test = scope -> {
            try {
                scope.close(); // should throw
                fail("Exception expected");
            } catch (IllegalStateException ise) {
                assertTrue(ise.getMessage().contains("Scope is acquired"));
            }
        };

        MethodHandle target = MH_Consumer_accept.bindTo(test)
                .asType(MethodType.methodType(void.class, ResourceScope.class));

        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemoryAddress stub = LINKER.upcallStub(target, FunctionDescriptor.ofVoid(), scope);
            MH_do_void_upcall.invokeExact(stub);
        }
    }
}
