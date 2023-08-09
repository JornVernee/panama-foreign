/*
 *  Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
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
package java.lang.foreign;

import jdk.internal.foreign.MemorySessionImpl;

/**
 * TODO
 */
public class JNISupport {

    private JNISupport() {}

    /**
     * Create a new global JNI reference
     *
     * @param o the object to create a reference for
     * @param arena the arena in which the reference is created
     * @return the newly create reference
     */
    public static MemorySegment newGlobalRef(Object o, Arena arena) {
        long addr = newGlobalRef0(o);
        return MemorySegment.ofAddress(addr).reinterpret(arena, ms -> deleteGlobalRef0(ms.address()));
    }

    /**
     * Resolve a global JNI reference obtained from {@link #newGlobalRef}
     *
     * @param ref the JNI reference to resolve
     * @return the resolved object
     */
    public static Object resolveGlobalRef(MemorySegment ref) {
        MemorySessionImpl.checkValidState(ref);
        return resolveGlobalRef0(ref.address());
    }

    private static native void registerNatives();
    static {
        registerNatives();
    }

    private static native long newGlobalRef0(Object o);
    private static native Object resolveGlobalRef0(long addr);
    private static native void deleteGlobalRef0(long addr);
}
