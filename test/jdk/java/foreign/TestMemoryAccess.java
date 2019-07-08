/*
 *  Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 * @test
 * @run testng TestMemoryAccess
 */

import jdk.incubator.foreign.GroupLayout;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayout.PathElement;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.SequenceLayout;
import jdk.incubator.foreign.ValueLayout;
import jdk.incubator.foreign.MemoryAddress;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.function.Function;

import org.testng.annotations.*;
import static org.testng.Assert.*;

public class TestMemoryAccess {

    @Test(dataProvider = "elements")
    public void testAccess(Function<MemorySegment, MemorySegment> viewFactory, ValueLayout elemLayout, Class<?> carrier, Checker checker) {
        ValueLayout layout = elemLayout.withName("elem");
        testAccessInternal(viewFactory, layout, layout.varHandle(carrier), checker);
    }

    @Test(dataProvider = "elements")
    public void testPaddedAccessByName(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, Checker checker) {
        GroupLayout layout = MemoryLayout.ofStruct(MemoryLayout.ofPadding(elemLayout.bitSize()), elemLayout.withName("elem"));
        testAccessInternal(viewFactory, layout, layout.varHandle(carrier, PathElement.groupElement("elem")), checker);
    }

    @Test(dataProvider = "elements")
    public void testPaddedAccessByIndexSeq(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, Checker checker) {
        SequenceLayout layout = MemoryLayout.ofSequence(2, elemLayout);
        testAccessInternal(viewFactory, layout, layout.varHandle(carrier, PathElement.sequenceElement(1)), checker);
    }

    @Test(dataProvider = "arrayElements")
    public void testArrayAccess(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, ArrayChecker checker) {
        SequenceLayout seq = MemoryLayout.ofSequence(10, elemLayout.withName("elem"));
        testArrayAccessInternal(viewFactory, seq, seq.varHandle(carrier, PathElement.sequenceElement()), checker);
    }

    @Test(dataProvider = "arrayElements")
    public void testPaddedArrayAccessByName(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, ArrayChecker checker) {
        SequenceLayout seq = MemoryLayout.ofSequence(10, MemoryLayout.ofStruct(MemoryLayout.ofPadding(elemLayout.bitSize()), elemLayout.withName("elem")));
        testArrayAccessInternal(viewFactory, seq, seq.varHandle(carrier, MemoryLayout.PathElement.sequenceElement(), MemoryLayout.PathElement.groupElement("elem")), checker);
    }

    @Test(dataProvider = "arrayElements")
    public void testPaddedArrayAccessByIndexSeq(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, ArrayChecker checker) {
        SequenceLayout seq = MemoryLayout.ofSequence(10, MemoryLayout.ofSequence(2, elemLayout));
        testArrayAccessInternal(viewFactory, seq, seq.varHandle(carrier, PathElement.sequenceElement(), MemoryLayout.PathElement.sequenceElement(1)), checker);
    }

    private void testAccessInternal(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout layout, VarHandle handle, Checker checker) {
        MemoryAddress outer_address;
        try (MemorySegment segment = viewFactory.apply(MemorySegment.ofNative(layout))) {
            MemoryAddress addr = segment.baseAddress();
            try {
                checker.check(handle, addr);
                if (segment.isReadOnly()) {
                    throw new AssertionError(); //not ok, memory should be immutable
                }
            } catch (UnsupportedOperationException ex) {
                if (!segment.isReadOnly()) {
                    throw new AssertionError(); //we should not have failed!
                }
                return;
            }
            try {
                checker.check(handle, addr.offset(layout.byteSize()));
                throw new AssertionError(); //not ok, out of bounds
            } catch (IllegalStateException ex) {
                //ok, should fail (out of bounds)
            }
            outer_address = addr; //leak!
        }
        try {
            checker.check(handle, outer_address);
            throw new AssertionError(); //not ok, scope is closed
        } catch (IllegalStateException ex) {
            //ok, should fail (scope is closed)
        }
    }

    private void testArrayAccessInternal(Function<MemorySegment, MemorySegment> viewFactory, SequenceLayout seq, VarHandle handle, ArrayChecker checker) {
        MemoryAddress outer_address;
        try (MemorySegment segment = viewFactory.apply(MemorySegment.ofNative(seq))) {
            MemoryAddress addr = segment.baseAddress();
            try {
                for (int i = 0; i < seq.elementsCount().getAsLong(); i++) {
                    checker.check(handle, addr, i);
                }
                if (segment.isReadOnly()) {
                    throw new AssertionError(); //not ok, memory should be immutable
                }
            } catch (UnsupportedOperationException ex) {
                if (!segment.isReadOnly()) {
                    throw new AssertionError(); //we should not have failed!
                }
                return;
            }
            try {
                checker.check(handle, addr, seq.elementsCount().getAsLong());
                throw new AssertionError(); //not ok, out of bounds
            } catch (IllegalStateException ex) {
                //ok, should fail (out of bounds)
            }
            outer_address = addr; //leak!
        }
        try {
            checker.check(handle, outer_address, 0);
            throw new AssertionError(); //not ok, scope is closed
        } catch (IllegalStateException ex) {
            //ok, should fail (scope is closed)
        }
    }

    @Test(dataProvider = "matrixElements")
    public void testMatrixAccess(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, MatrixChecker checker) {
        SequenceLayout seq = MemoryLayout.ofSequence(20,
                MemoryLayout.ofSequence(10, elemLayout.withName("elem")));
        testMatrixAccessInternal(viewFactory, seq, seq.varHandle(carrier,
                PathElement.sequenceElement(), PathElement.sequenceElement()), checker);
    }

    @Test(dataProvider = "matrixElements")
    public void testPaddedMatrixAccessByName(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, MatrixChecker checker) {
        SequenceLayout seq = MemoryLayout.ofSequence(20,
                MemoryLayout.ofSequence(10, MemoryLayout.ofStruct(MemoryLayout.ofPadding(elemLayout.bitSize()), elemLayout.withName("elem"))));
        testMatrixAccessInternal(viewFactory, seq,
                seq.varHandle(carrier,
                        PathElement.sequenceElement(), PathElement.sequenceElement(), PathElement.groupElement("elem")),
                checker);
    }

    @Test(dataProvider = "matrixElements")
    public void testPaddedMatrixAccessByIndexSeq(Function<MemorySegment, MemorySegment> viewFactory, MemoryLayout elemLayout, Class<?> carrier, MatrixChecker checker) {
        SequenceLayout seq = MemoryLayout.ofSequence(20,
                MemoryLayout.ofSequence(10, MemoryLayout.ofSequence(2, elemLayout)));
        testMatrixAccessInternal(viewFactory, seq,
                seq.varHandle(carrier,
                        PathElement.sequenceElement(), PathElement.sequenceElement(), PathElement.sequenceElement(1)),
                checker);
    }

    @Test(dataProvider = "badCarriers",
          expectedExceptions = IllegalArgumentException.class)
    public void testBadCarriers(Class<?> carrier) {
        ValueLayout l = MemoryLayout.ofUnsignedInt(32).withName("elem");
        l.varHandle(carrier);
    }

    private void testMatrixAccessInternal(Function<MemorySegment, MemorySegment> viewFactory, SequenceLayout seq, VarHandle handle, MatrixChecker checker) {
        MemoryAddress outer_address;
        try (MemorySegment segment = viewFactory.apply(MemorySegment.ofNative(seq))) {
            MemoryAddress addr = segment.baseAddress();
            try {
                for (int i = 0; i < seq.elementsCount().getAsLong(); i++) {
                    for (int j = 0; j < ((SequenceLayout) seq.elementLayout()).elementsCount().getAsLong(); j++) {
                        checker.check(handle, addr, i, j);
                    }
                }
                if (segment.isReadOnly()) {
                    throw new AssertionError(); //not ok, memory should be immutable
                }
            } catch (UnsupportedOperationException ex) {
                if (!segment.isReadOnly()) {
                    throw new AssertionError(); //we should not have failed!
                }
                return;
            } 
            try {
                checker.check(handle, addr, seq.elementsCount().getAsLong(),
                        ((SequenceLayout)seq.elementLayout()).elementsCount().getAsLong());
                throw new AssertionError(); //not ok, out of bounds
            } catch (IllegalStateException ex) {
                //ok, should fail (out of bounds)
            }
            outer_address = addr; //leak!
        }
        try {
            checker.check(handle, outer_address, 0, 0);
            throw new AssertionError(); //not ok, scope is closed
        } catch (IllegalStateException ex) {
            //ok, should fail (scope is closed)
        }
    }
    
    static Function<MemorySegment, MemorySegment> ID = Function.identity();
    static Function<MemorySegment, MemorySegment> IMMUTABLE = MemorySegment::asReadOnly;

    @DataProvider(name = "elements")
    public Object[][] createData() {                
        return new Object[][] {
                //BE, RW
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 8), byte.class, Checker.BYTE },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 8), byte.class, Checker.BYTE },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 16), short.class, Checker.SHORT },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 16), short.class, Checker.SHORT },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 16), char.class, Checker.CHAR },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 16), char.class, Checker.CHAR },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 32), int.class, Checker.INT },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 32), int.class, Checker.INT },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 64), long.class, Checker.LONG },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 64), long.class, Checker.LONG },
                { ID, MemoryLayout.ofFloatingPoint(ByteOrder.BIG_ENDIAN, 32), float.class, Checker.FLOAT },
                { ID, MemoryLayout.ofFloatingPoint(ByteOrder.BIG_ENDIAN, 64), double.class, Checker.DOUBLE },
                //BE, RO
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 8), byte.class, Checker.BYTE },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 8), byte.class, Checker.BYTE },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 16), short.class, Checker.SHORT },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 16), short.class, Checker.SHORT },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 16), char.class, Checker.CHAR },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 16), char.class, Checker.CHAR },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 32), int.class, Checker.INT },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 32), int.class, Checker.INT },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 64), long.class, Checker.LONG },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 64), long.class, Checker.LONG },
                { IMMUTABLE, MemoryLayout.ofFloatingPoint(ByteOrder.BIG_ENDIAN, 32), float.class, Checker.FLOAT },
                { IMMUTABLE, MemoryLayout.ofFloatingPoint(ByteOrder.BIG_ENDIAN, 64), double.class, Checker.DOUBLE },
                //LE, RW
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 8), byte.class, Checker.BYTE },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 8), byte.class, Checker.BYTE },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 16), short.class, Checker.SHORT },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 16), short.class, Checker.SHORT },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 16), char.class, Checker.CHAR },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 16), char.class, Checker.CHAR },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 32), int.class, Checker.INT },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 32), int.class, Checker.INT },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 64), long.class, Checker.LONG },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 64), long.class, Checker.LONG },
                { ID, MemoryLayout.ofFloatingPoint(ByteOrder.LITTLE_ENDIAN, 32), float.class, Checker.FLOAT },
                { ID, MemoryLayout.ofFloatingPoint(ByteOrder.LITTLE_ENDIAN, 64), double.class, Checker.DOUBLE },
                //LE, RO
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 8), byte.class, Checker.BYTE },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 8), byte.class, Checker.BYTE },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 16), short.class, Checker.SHORT },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 16), short.class, Checker.SHORT },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 16), char.class, Checker.CHAR },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 16), char.class, Checker.CHAR },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 32), int.class, Checker.INT },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 32), int.class, Checker.INT },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 64), long.class, Checker.LONG },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 64), long.class, Checker.LONG },
                { IMMUTABLE, MemoryLayout.ofFloatingPoint(ByteOrder.LITTLE_ENDIAN, 32), float.class, Checker.FLOAT },
                { IMMUTABLE, MemoryLayout.ofFloatingPoint(ByteOrder.LITTLE_ENDIAN, 64), double.class, Checker.DOUBLE },
        };
    }

    interface Checker {
        void check(VarHandle handle, MemoryAddress addr);

        Checker BYTE = (handle, addr) -> {
            handle.set(addr, (byte)42);
            assertEquals(42, (byte)handle.get(addr));
        };

        Checker SHORT = (handle, addr) -> {
            handle.set(addr, (short)42);
            assertEquals(42, (short)handle.get(addr));
        };

        Checker CHAR = (handle, addr) -> {
            handle.set(addr, (char)42);
            assertEquals(42, (char)handle.get(addr));
        };

        Checker INT = (handle, addr) -> {
            handle.set(addr, 42);
            assertEquals(42, (int)handle.get(addr));
        };

        Checker LONG = (handle, addr) -> {
            handle.set(addr, (long)42);
            assertEquals(42, (long)handle.get(addr));
        };

        Checker FLOAT = (handle, addr) -> {
            handle.set(addr, (float)42);
            assertEquals((float)42, (float)handle.get(addr));
        };

        Checker DOUBLE = (handle, addr) -> {
            handle.set(addr, (double)42);
            assertEquals((double)42, (double)handle.get(addr));
        };
    }

    @DataProvider(name = "arrayElements")
    public Object[][] createArrayData() {
        return new Object[][] {
                //BE, RW
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 8), byte.class, ArrayChecker.BYTE },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 8), byte.class, ArrayChecker.BYTE },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 16), short.class, ArrayChecker.SHORT },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 16), short.class, ArrayChecker.SHORT },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 16), char.class, ArrayChecker.CHAR },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 16), char.class, ArrayChecker.CHAR },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 32), int.class, ArrayChecker.INT },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 32), int.class, ArrayChecker.INT },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 64), long.class, ArrayChecker.LONG },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 64), long.class, ArrayChecker.LONG },
                { ID, MemoryLayout.ofFloatingPoint(ByteOrder.BIG_ENDIAN, 32), float.class, ArrayChecker.FLOAT },
                { ID, MemoryLayout.ofFloatingPoint(ByteOrder.BIG_ENDIAN, 64), double.class, ArrayChecker.DOUBLE },
                //BE, RO
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 8), byte.class, ArrayChecker.BYTE },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 8), byte.class, ArrayChecker.BYTE },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 16), short.class, ArrayChecker.SHORT },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 16), short.class, ArrayChecker.SHORT },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 16), char.class, ArrayChecker.CHAR },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 16), char.class, ArrayChecker.CHAR },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 32), int.class, ArrayChecker.INT },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 32), int.class, ArrayChecker.INT },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 64), long.class, ArrayChecker.LONG },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 64), long.class, ArrayChecker.LONG },
                { IMMUTABLE, MemoryLayout.ofFloatingPoint(ByteOrder.BIG_ENDIAN, 32), float.class, ArrayChecker.FLOAT },
                { IMMUTABLE, MemoryLayout.ofFloatingPoint(ByteOrder.BIG_ENDIAN, 64), double.class, ArrayChecker.DOUBLE },
                //LE, RW
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 8), byte.class, ArrayChecker.BYTE },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 8), byte.class, ArrayChecker.BYTE },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 16), short.class, ArrayChecker.SHORT },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 16), short.class, ArrayChecker.SHORT },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 16), char.class, ArrayChecker.CHAR },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 16), char.class, ArrayChecker.CHAR },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 32), int.class, ArrayChecker.INT },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 32), int.class, ArrayChecker.INT },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 64), long.class, ArrayChecker.LONG },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 64), long.class, ArrayChecker.LONG },
                { ID, MemoryLayout.ofFloatingPoint(ByteOrder.LITTLE_ENDIAN, 32), float.class, ArrayChecker.FLOAT },
                { ID, MemoryLayout.ofFloatingPoint(ByteOrder.LITTLE_ENDIAN, 64), double.class, ArrayChecker.DOUBLE },
                //LE, RO
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 8), byte.class, ArrayChecker.BYTE },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 8), byte.class, ArrayChecker.BYTE },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 16), short.class, ArrayChecker.SHORT },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 16), short.class, ArrayChecker.SHORT },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 16), char.class, ArrayChecker.CHAR },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 16), char.class, ArrayChecker.CHAR },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 32), int.class, ArrayChecker.INT },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 32), int.class, ArrayChecker.INT },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 64), long.class, ArrayChecker.LONG },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 64), long.class, ArrayChecker.LONG },
                { IMMUTABLE, MemoryLayout.ofFloatingPoint(ByteOrder.LITTLE_ENDIAN, 32), float.class, ArrayChecker.FLOAT },
                { IMMUTABLE, MemoryLayout.ofFloatingPoint(ByteOrder.LITTLE_ENDIAN, 64), double.class, ArrayChecker.DOUBLE },
        };
    }

    interface ArrayChecker {
        void check(VarHandle handle, MemoryAddress addr, long index);

        ArrayChecker BYTE = (handle, addr, i) -> {
            handle.set(addr, i, (byte)i);
            assertEquals(i, (byte)handle.get(addr, i));
        };

        ArrayChecker SHORT = (handle, addr, i) -> {
            handle.set(addr, i, (short)i);
            assertEquals(i, (short)handle.get(addr, i));
        };

        ArrayChecker CHAR = (handle, addr, i) -> {
            handle.set(addr, i, (char)i);
            assertEquals(i, (char)handle.get(addr, i));
        };

        ArrayChecker INT = (handle, addr, i) -> {
            handle.set(addr, i, (int)i);
            assertEquals(i, (int)handle.get(addr, i));
        };

        ArrayChecker LONG = (handle, addr, i) -> {
            handle.set(addr, i, (long)i);
            assertEquals(i, (long)handle.get(addr, i));
        };

        ArrayChecker FLOAT = (handle, addr, i) -> {
            handle.set(addr, i, (float)i);
            assertEquals((float)i, (float)handle.get(addr, i));
        };

        ArrayChecker DOUBLE = (handle, addr, i) -> {
            handle.set(addr, i, (double)i);
            assertEquals((double)i, (double)handle.get(addr, i));
        };
    }

    @DataProvider(name = "matrixElements")
    public Object[][] createMatrixData() {
        return new Object[][] {
                //BE, RW
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 8), byte.class, MatrixChecker.BYTE },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 8), byte.class, MatrixChecker.BYTE },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 16), short.class, MatrixChecker.SHORT },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 16), short.class, MatrixChecker.SHORT },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 16), char.class, MatrixChecker.CHAR },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 16), char.class, MatrixChecker.CHAR },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 32), int.class, MatrixChecker.INT },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 32), int.class, MatrixChecker.INT },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 64), long.class, MatrixChecker.LONG },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 64), long.class, MatrixChecker.LONG },
                { ID, MemoryLayout.ofFloatingPoint(ByteOrder.BIG_ENDIAN, 32), float.class, MatrixChecker.FLOAT },
                { ID, MemoryLayout.ofFloatingPoint(ByteOrder.BIG_ENDIAN, 64), double.class, MatrixChecker.DOUBLE },
                //BE, RO
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 8), byte.class, MatrixChecker.BYTE },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 8), byte.class, MatrixChecker.BYTE },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 16), short.class, MatrixChecker.SHORT },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 16), short.class, MatrixChecker.SHORT },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 16), char.class, MatrixChecker.CHAR },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 16), char.class, MatrixChecker.CHAR },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 32), int.class, MatrixChecker.INT },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 32), int.class, MatrixChecker.INT },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.BIG_ENDIAN, 64), long.class, MatrixChecker.LONG },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.BIG_ENDIAN, 64), long.class, MatrixChecker.LONG },
                { IMMUTABLE, MemoryLayout.ofFloatingPoint(ByteOrder.BIG_ENDIAN, 32), float.class, MatrixChecker.FLOAT },
                { IMMUTABLE, MemoryLayout.ofFloatingPoint(ByteOrder.BIG_ENDIAN, 64), double.class, MatrixChecker.DOUBLE },
                //LE, RW
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 8), byte.class, MatrixChecker.BYTE },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 8), byte.class, MatrixChecker.BYTE },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 16), short.class, MatrixChecker.SHORT },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 16), short.class, MatrixChecker.SHORT },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 16), char.class, MatrixChecker.CHAR },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 16), char.class, MatrixChecker.CHAR },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 32), int.class, MatrixChecker.INT },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 32), int.class, MatrixChecker.INT },
                { ID, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 64), long.class, MatrixChecker.LONG },
                { ID, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 64), long.class, MatrixChecker.LONG },
                { ID, MemoryLayout.ofFloatingPoint(ByteOrder.LITTLE_ENDIAN, 32), float.class, MatrixChecker.FLOAT },
                { ID, MemoryLayout.ofFloatingPoint(ByteOrder.LITTLE_ENDIAN, 64), double.class, MatrixChecker.DOUBLE },
                //LE, RO
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 8), byte.class, MatrixChecker.BYTE },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 8), byte.class, MatrixChecker.BYTE },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 16), short.class, MatrixChecker.SHORT },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 16), short.class, MatrixChecker.SHORT },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 16), char.class, MatrixChecker.CHAR },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 16), char.class, MatrixChecker.CHAR },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 32), int.class, MatrixChecker.INT },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 32), int.class, MatrixChecker.INT },
                { IMMUTABLE, MemoryLayout.ofUnsignedInt(ByteOrder.LITTLE_ENDIAN, 64), long.class, MatrixChecker.LONG },
                { IMMUTABLE, MemoryLayout.ofSignedInt(ByteOrder.LITTLE_ENDIAN, 64), long.class, MatrixChecker.LONG },
                { IMMUTABLE, MemoryLayout.ofFloatingPoint(ByteOrder.LITTLE_ENDIAN, 32), float.class, MatrixChecker.FLOAT },
                { IMMUTABLE, MemoryLayout.ofFloatingPoint(ByteOrder.LITTLE_ENDIAN, 64), double.class, MatrixChecker.DOUBLE },
        };
    }

    interface MatrixChecker {
        void check(VarHandle handle, MemoryAddress addr, long row, long col);

        MatrixChecker BYTE = (handle, addr, r, c) -> {
            handle.set(addr, r, c, (byte)(r + c));
            assertEquals(r + c, (byte)handle.get(addr, r, c));
        };

        MatrixChecker SHORT = (handle, addr, r, c) -> {
            handle.set(addr, r, c, (short)(r + c));
            assertEquals(r + c, (short)handle.get(addr, r, c));
        };

        MatrixChecker CHAR = (handle, addr, r, c) -> {
            handle.set(addr, r, c, (char)(r + c));
            assertEquals(r + c, (char)handle.get(addr, r, c));
        };

        MatrixChecker INT = (handle, addr, r, c) -> {
            handle.set(addr, r, c, (int)(r + c));
            assertEquals(r + c, (int)handle.get(addr, r, c));
        };

        MatrixChecker LONG = (handle, addr, r, c) -> {
            handle.set(addr, r, c, r + c);
            assertEquals(r + c, (long)handle.get(addr, r, c));
        };

        MatrixChecker FLOAT = (handle, addr, r, c) -> {
            handle.set(addr, r, c, (float)(r + c));
            assertEquals((float)(r + c), (float)handle.get(addr, r, c));
        };

        MatrixChecker DOUBLE = (handle, addr, r, c) -> {
            handle.set(addr, r, c, (double)(r + c));
            assertEquals((double)(r + c), (double)handle.get(addr, r, c));
        };
    }

    @DataProvider(name = "badCarriers")
    public Object[][] createBadCarriers() {
        return new Object[][] {
                { void.class },
                { boolean.class },
                { Object.class },
                { int[].class }
        };
    }
}
