/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package java.nicl;

import jdk.internal.nicl.ScopeImpl;
import jdk.internal.nicl.Util;

import java.nicl.layout.Layout;
import java.nicl.types.Array;
import java.nicl.types.LayoutType;
import java.nicl.types.Pointer;
import java.nicl.types.Struct;

/**
 * A scope models a block of code in a native language. It provides primitive for memory allocation; when the scope
 * is closed (see {@link AutoCloseable#close()}, the pointers associated with this scope can no longer be used.
 */
public interface Scope extends AutoCloseable {

    /**
     * Is this scope alive?
     */
    void checkAlive();

    /**
     * Allocate region of memory with given {@code LayoutType}.
     * @param <X> the carrier type associated with the memory region to be allocated.
     * @param type the {@code LayoutType}.
     * @return a pointer to the newly allocated memory region.
     */
    <X> Pointer<X> allocate(LayoutType<X> type);

    /**
     * Allocate region of memory with given layout.
     * @param type the layout.
     * @return a pointer to the newly allocated memory region.
     */
    default Pointer<?> allocate(Layout type) {
        return allocate(LayoutType.ofVoid(type));
    }

    /**
     * Allocate region of memory as an array of elements with given {@code LayoutType}. This is effectively the same as:
     * <p>
     *     <code>
     *         allocateArray(type, size).elementPointer()
     *     </code>
     * </p>
     * @param <X> the carrier type associated with the element type of the native array to be allocated.
     * @param elementType the {@code LayoutType} of the array element.
     * @param size the array size.
     * @return a pointer to the first element of the array.
     */
    default <X> Pointer<X> allocate(LayoutType<X> elementType, long size) {
        return allocateArray(elementType, size).elementPointer();
    }

    /**
     * Allocate region of memory as an array of elements with given {@code LayoutType}. This is effectively the same as:
     * <p>
     *     <code>
     *         allocate(elementType.array(size)).withLimit()
     *     </code>
     * </p>
     * @param <X> the carrier type associated with the element type of the native array to be allocated.
     * @param elementType the {@code LayoutType} of the array element.
     * @param size the array size.
     * @return an array to the first element of the array.
     */
    <X> Array<X> allocateArray(LayoutType<X> elementType, long size);

    /**
     * Allocate region of memory as an array of elements with given layout. The resulting pointer will have no
     * carrier information associated with it. This is effectively the same as:
     * <p>
     *     <code>
     *         allocate(LayoutType.ofVoid(elementType).array(size)).withLimit();
     *     </code>
     * </p>
     * @param elementType the {@code LayoutType} of the array element.
     * @param size the array size.
     * @return an array to the first element of the array.
     */
    default Array<?> allocateArray(Layout elementType, int size) {
        return allocateArray(LayoutType.ofVoid(elementType), size);
    }

    /**
     * Allocate region of memory with given native data.
     * @param carrier the carrier type modelling the native data.
     * @param <T> the carrier type.
     * @return a new struct instance (of type {@link Struct}).
     * @see Struct
     */
    <T extends Struct<T>> T allocateStruct(Class<T> carrier);

    @Override
    void close();


    private Pointer<Byte> toCString(byte[] ar) {
        try {
            Pointer<Byte> buf = allocate(Util.BYTE_TYPE, ar.length + 1);
            Pointer<Byte> src = Util.createArrayElementsPointer(ar);
            Util.copy(src, buf, ar.length);
            buf.offset(ar.length).set((byte)0);
            return buf;
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }

    default Pointer<Byte> toCString(String str) {
        return toCString(str.getBytes());
    }

    default Pointer<Pointer<Byte>> toCStrArray(String[] ar) {
        if (ar.length == 0) {
            return null;
        }

        Pointer<Pointer<Byte>> ptr = allocate(Util.BYTE_PTR_TYPE, ar.length);
        for (int i = 0; i < ar.length; i++) {
            Pointer<Byte> s = toCString(ar[i]);
            ptr.offset(i).set(s);
        }

        return ptr;
    }

    /**
     * Create a scope backed by off-heap memory.
     * @return the new native scope.
     */
    static Scope newNativeScope() {
        return new ScopeImpl.NativeScope();
    }

    /**
     * Create a scope backed by on-heap memory.
     * @return the new native scope.
     */
    static Scope newHeapScope() {
        return new ScopeImpl.HeapScope();
    }
}
