/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.jextract;

import java.nio.file.Path;

/**
 * This class represent a native code header file
 */
public final class HeaderFile {
    final Path path;
    final String pkgName;
    final String headerClsName;
    final String staticForwarderClsName;
    private final TypeDictionary dict;

    HeaderFile(HeaderResolver resolver, Path path, String pkgName,
            String headerClsName, String staticForwarderClsName) {
        this.path = path;
        this.pkgName = pkgName;
        this.headerClsName = headerClsName;
        this.staticForwarderClsName = staticForwarderClsName;
        this.dict = new TypeDictionary(resolver, this);
    }

    TypeDictionary dictionary() {
        return dict;
    }

    @Override
    public String toString() {
        return "HeaderFile(path=" + path + ")";
    }

    public String fullyQualifiedName() {
        return pkgName.isEmpty()
                ? headerClsName
                : pkgName + "." + headerClsName;
    }
}