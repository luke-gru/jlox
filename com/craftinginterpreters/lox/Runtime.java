package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
import java.util.List;
import java.util.Map;

class Runtime {
    final Environment globalEnv;

    Runtime(Environment globalEnv) {
        this.globalEnv = globalEnv;
    }

    static Object bool(boolean val) {
        return (Boolean)val;
    }

    static Object array(List<Object> list) {
        return new LoxArray(list);
    }

    static Object arrayCopy(List<Object> list) {
        List<Object> copy = new ArrayList<>();
        copy.addAll(list);
        return new LoxArray(copy);
    }

    static boolean acceptsNArgs(LoxCallable callable, int n) {
        int arity = callable.arity();
        if (arity >= 0) {
            return arity == n;
        } else {
            int necessaryArgs = -(arity+1);
            return necessaryArgs <= n;
        }
    }

    public void defineGlobalFunctions() {
        globalEnv.define("clock", new LoxNativeCallable("clock", 0) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                return (double)System.currentTimeMillis() / 1000.0;
            }
        });
        globalEnv.define("typeof", new LoxNativeCallable("typeof", 1) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                Object obj = arguments.get(0);
                return interpreter.nativeTypeof(tok, obj);
            }

        });
        globalEnv.define("len", new LoxNativeCallable("len", 1) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                return interpreter.nativeLen(tok, arguments.get(0));
            }
        });
    }

    public void defineBuiltinClasses() {
        LoxNativeClass objClass = new LoxNativeClass("Object", null);
        objClass.defineMethod("equals", new LoxNativeCallable("equals", 1) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                return Runtime.bool(
                        interpreter.environment.getThis().equals(arguments.get(0))
                );
            }
        });
        globalEnv.define("Object", objClass);
    }

}
