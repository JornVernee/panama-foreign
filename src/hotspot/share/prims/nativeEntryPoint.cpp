/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/javaClasses.inline.hpp"
#include "code/codeCache.hpp"
#include "code/vmreg.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oopCast.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "opto/optoreg.hpp"
#include "opto/matcher.hpp"
#include "prims/downcallLinker.hpp"
#include "prims/foreignGlobals.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include CPU_HEADER(adGlobals)

JNI_ENTRY(jlong, NEP_makeDowncallStub(JNIEnv* env, jclass _unused, jobject method_type, jobject jabi,
                                      jobjectArray arg_moves, jobjectArray ret_moves,
                                      jboolean needs_return_buffer, jint captured_state_mask,
                                      jboolean needs_transition))
  ResourceMark rm;
  const ABIDescriptor abi = ForeignGlobals::parse_abi_descriptor(jabi);

  oop type = JNIHandles::resolve(method_type);
  objArrayOop arg_moves_oop = oop_cast<objArrayOop>(JNIHandles::resolve(arg_moves));
  objArrayOop ret_moves_oop = oop_cast<objArrayOop>(JNIHandles::resolve(ret_moves));
  int pcount = java_lang_invoke_MethodType::ptype_count(type);
  int pslots = java_lang_invoke_MethodType::ptype_slot_count(type);
  BasicType* basic_type = NEW_RESOURCE_ARRAY(BasicType, pslots);

  GrowableArray<VMStorage> input_regs(pcount);
  for (int i = 0, bt_idx = 0; i < pcount; i++) {
    oop type_oop = java_lang_invoke_MethodType::ptype(type, i);
    BasicType bt = java_lang_Class::as_BasicType(type_oop);
    basic_type[bt_idx++] = bt;
    oop reg_oop = arg_moves_oop->obj_at(i);
    if (reg_oop != nullptr) {
      input_regs.push(ForeignGlobals::parse_vmstorage(reg_oop));
    }

    if (bt == BasicType::T_DOUBLE || bt == BasicType::T_LONG) {
      basic_type[bt_idx++] = T_VOID;
      // we only need these in the basic type
      // NativeCallingConvention ignores them, but they are needed
      // for JavaCallingConvention
    }
  }


  jint outs = ret_moves_oop->length();
  GrowableArray<VMStorage> output_regs(outs);
  oop type_oop = java_lang_invoke_MethodType::rtype(type);
  BasicType  ret_bt = java_lang_Class::primitive_type(type_oop);
  for (int i = 0; i < outs; i++) {
    // note that we don't care about long/double upper halfs here:
    // we are NOT moving Java values, we are moving register-sized values
    output_regs.push(ForeignGlobals::parse_vmstorage(ret_moves_oop->obj_at(i)));
  }

  RuntimeStub* stub = DowncallLinker::make_downcall_stub(basic_type, pslots, ret_bt, abi,
                                                         input_regs, output_regs,
                                                         needs_return_buffer, captured_state_mask,
                                                         needs_transition);
  if (stub == nullptr) {
    return 0;
  }
  return (jlong) stub->code_begin();
JNI_END

JNI_ENTRY(jstring, NEP_computeRegSavePolicy(JNIEnv* env, jclass _unused, jobjectArray volatile_regs))
  ResourceMark rm;
  objArrayOop volatile_regs_oop = oop_cast<objArrayOop>(JNIHandles::resolve(volatile_regs));
  GrowableArray<OptoReg::Name> soc_regs; // FIXME use a set for faster lookup?
  for (int i = 0; i < volatile_regs_oop->length(); i++) {
    OptoReg::Name reg = OptoReg::as_OptoReg(as_VMReg(ForeignGlobals::parse_vmstorage(volatile_regs_oop->obj_at(i))));
    soc_regs.append(reg);
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
    } else if (soc_regs.contains(i)) {
      policy[i] = 'C';
    } else {
      policy[i] = 'E';
    }
  }

  oop result = java_lang_String::create_oop_from_str(policy, CHECK_NULL);
  return (jstring) JNIHandles::make_local(THREAD, result);
JNI_END

JNI_ENTRY(jboolean, NEP_freeDowncallStub(JNIEnv* env, jclass _unused, jlong invoker))
  // safe to call without code cache lock, because stub is always alive
  CodeBlob* cb = CodeCache::find_blob((char*) invoker);
  if (cb == nullptr) {
    return false;
  }
  RuntimeStub::free(cb->as_runtime_stub());
  return true;
JNI_END

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)
#define METHOD_TYPE "Ljava/lang/invoke/MethodType;"
#define ABI_DESC "Ljdk/internal/foreign/abi/ABIDescriptor;"
#define VM_STORAGE_ARR "[Ljdk/internal/foreign/abi/VMStorage;"

static JNINativeMethod NEP_methods[] = {
  {CC "makeDowncallStub", CC "(" METHOD_TYPE ABI_DESC VM_STORAGE_ARR VM_STORAGE_ARR "ZIZ)J", FN_PTR(NEP_makeDowncallStub)},
  {CC "computeRegSavePolicy", CC "(" VM_STORAGE_ARR ")Ljava/lang/String;", FN_PTR(NEP_computeRegSavePolicy)},
  {CC "freeDowncallStub0", CC "(J)Z", FN_PTR(NEP_freeDowncallStub)},
};

#undef METHOD_TYPE
#undef ABI_DESC
#undef VM_STORAGE_ARR

JNI_ENTRY(void, JVM_RegisterNativeEntryPointMethods(JNIEnv *env, jclass NEP_class))
  ThreadToNativeFromVM ttnfv(thread);
  int status = env->RegisterNatives(NEP_class, NEP_methods, sizeof(NEP_methods)/sizeof(JNINativeMethod));
  guarantee(status == JNI_OK && !env->ExceptionCheck(),
            "register jdk.internal.foreign.abi.NativeEntryPoint natives");
JNI_END
