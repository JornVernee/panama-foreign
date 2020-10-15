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
 *
 */

#include "precompiled.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "code/vmreg.hpp"
#include "opto/optoreg.hpp"
#include "opto/matcher.hpp"
#include CPU_HEADER(adGlobals)

class VMStorageToVMReg {
private:
  JNIEnv* _env;
  jmethodID _typeMID;
  jmethodID _indexMID;
public:
  VMStorageToVMReg(JNIEnv* env) : _env(env) {
    jclass clsVMStorageProxy = _env->FindClass("jdk/internal/invoke/VMStorageProxy");
    _typeMID = _env->GetMethodID(clsVMStorageProxy, "type", "()I");
    _indexMID = _env->GetMethodID(clsVMStorageProxy, "index", "()I");
  }

  VMReg toVMReg(jobject storage) {
    int type = _env->CallIntMethod(storage, _typeMID);
    int index = _env->CallIntMethod(storage, _indexMID);
    return VMRegImpl::vmStorageToVMReg(type, index);
  }
};

JVM_LEAF(jlong, NEP_vmStorageToVMReg(JNIEnv* env, jclass _unused, jint type, jint index))
  return VMRegImpl::vmStorageToVMReg(type, index)->value();
JNI_END

JVM_ENTRY(jstring, NEP_computeRegSavePolicy(JNIEnv* env, jclass _unused, jobjectArray vmStorages))
  ThreadToNativeFromVM ttnfvm(thread);

  ResourceMark rm;
  int length = env->GetArrayLength(vmStorages);
  GrowableArray<OptoReg::Name> socRegs; // FIXME use a set for faster lookup?
  VMStorageToVMReg converter(env);
  for (int i = 0; i < length; i++) {
    jobject storage = env->GetObjectArrayElement(vmStorages, i);
    OptoReg::Name reg = OptoReg::as_OptoReg(converter.toVMReg(storage));
    socRegs.append(reg);
  }

  char policy[REG_COUNT];
  //   if      (!strcmp(calling_convention, "NS"))  callconv = 'N';
  // else if (!strcmp(calling_convention, "SOE")) callconv = 'E';
  // else if (!strcmp(calling_convention, "SOC")) callconv = 'C';
  // else if (!strcmp(calling_convention, "AS"))  callconv = 'A';
  // else                                         callconv = 'Z';

  OptoReg::Name framePointer = Matcher::c_frame_pointer(); // FIXME get from ABI?
  for (OptoReg::Name i = 0; i < REG_COUNT; i++) {
    if (i == framePointer || i == framePointer + 1) { // RSP_num and RSP_H_num
      policy[i] = 'N';
    } else if (socRegs.contains(i)) {
      policy[i] = 'C';
    } else {
      policy[i] = 'E';
    }
  }

  return env->NewStringUTF(policy);
JVM_END

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

static JNINativeMethod NEP_methods[] = {
  {CC "vmStorageToVMReg", CC "(II)J", FN_PTR(NEP_vmStorageToVMReg)},
  {CC "computeRegSavePolicy", CC "([Ljdk/internal/invoke/VMStorageProxy;)Ljava/lang/String;", FN_PTR(NEP_computeRegSavePolicy)},
};

JNI_LEAF(void, JVM_RegisterNativeEntryPointMethods(JNIEnv *env, jclass NEP_class))
  int status = env->RegisterNatives(NEP_class, NEP_methods, sizeof(NEP_methods)/sizeof(JNINativeMethod));
  guarantee(status == JNI_OK && !env->ExceptionOccurred(),
            "register jdk.internal.invoke.NativeEntryPoint natives");
JNI_END