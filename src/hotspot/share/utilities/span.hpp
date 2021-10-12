/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_SPAN_HPP
#define SHARE_UTILITIES_SPAN_HPP

#include "memory/allocation.hpp"

// A thin wrapper around a pointer + an element count
// Loosely modelled after std::span
template<class T>
class Span : StackObj {
  template<class X> friend bool operator==(const Span<X>& r1, const Span<X>& r2);
private:
  T* _ptr;
  int _element_count;
public:
  Span() : _ptr(nullptr), _element_count(0) {}
  Span(T* ptr, int element_count) : _ptr(ptr), _element_count(element_count) {}

  T& operator[](int idx) {
    return internal_at(idx);
  }
  const T& operator[](int idx) const {
    return internal_at(idx);
  }

  // iterator pairs
  T* begin() { return _ptr; }
  T* end() { return _ptr + _element_count; }

  const T* begin() const { return _ptr; }
  const T* end() const { return _ptr + _element_count; }

  bool elements_equal(const Span<T>& other) const {
    if (_element_count != other._element_count) {
      return false;
    }

    for (int i = 0; i < _element_count; i++) {
      if (_ptr[i] != other[i]) {
        return false;
      }
    }

    return true;
  }

  bool contains(const T& t) const {
    for (const T& e : *this) {
      if (e == t) {
        return true;
      }
    }

    return false;
  }

  bool contains(const void* addr) const {
    return addr >= (void*)begin() && addr < (void*)end();
  }

  Span<T> slice(int offset, int length) {
    return internal_slice(offset, length);
  }
  const Span<T> slice(int offset, int length) const {
    return internal_slice(offset, length);
  }

  bool is_empty() const { return _element_count == 0; }
  int byte_size() const { return _element_count * sizeof(T); }
  int element_count() const { return _element_count; }

  const T* ptr() const { return _ptr; }
  T* ptr() { return _ptr; }
private:
  T& internal_at(int idx) const {
    assert(idx < _element_count, "OOB access!");
    return _ptr[idx];
  }
  Span<T> internal_slice(int offset, int length) const {
    assert(offset <= _element_count - length, "Out of bounds");
    return Span<T>(_ptr + offset, length);
  }
};

template<class T>
inline bool operator==(const Span<T>& r1, const Span<T>& r2) {
  return r1._ptr == r2._ptr && r1._element_count == r2._element_count;
}

template<class T>
inline bool operator!=(const Span<T>& r1, const Span<T>& r2) {
  return !(r1 == r2);
}

#endif // SHARE_UTILITIES_SPAN_HPP
