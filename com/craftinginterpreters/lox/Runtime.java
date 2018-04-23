package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
import java.util.List;
import java.util.Map;

class Runtime {
    final Environment globalEnv;
    static Map<String, LoxClass> classMap;
    boolean inited = false;

    Runtime(Environment globalEnv, Map<String, LoxClass> classMap) {
        this.globalEnv = globalEnv;
        this.classMap = classMap;
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

    static LoxClass classOf(LoxInstance instance) {
        return instance.klass;
    }

    // native or user-defined class
    static LoxClass getClass(String name) {
        LoxClass klass = classMap.get(name);
        if (klass == null) return null;
        return klass;
    }

    // native class only
    static LoxNativeClass getNativeClass(String name) {
        LoxClass klass = classMap.get(name);
        if (klass == null) return null;
        if (!(klass instanceof LoxNativeClass)) return null;
        return (LoxNativeClass)klass;
    }

    public void init() {
        if (inited) {
            return;
        }
        defineGlobalFunctions();
        defineBuiltinClasses();
        inited = true;
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
        objClass.defineMethod(new LoxNativeCallable("equals", 1) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                return Runtime.bool(
                        interpreter.environment.getThis().equals(arguments.get(0))
                );
            }
        });
        objClass.defineGetter(new LoxNativeCallable("_class", 0) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                return Runtime.classOf(
                    interpreter.environment.getThis()
                );
            }
        });
        objClass.defineMethod(new LoxNativeCallable("freeze", 0) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                interpreter.environment.getThis().freeze();
                return null;
            }
        });
        globalEnv.define("Object", objClass);
        classMap.put("Object", objClass);
    }

    public List<String> nativeClassNames() {
        List<String> classNames = new ArrayList<String>();
        for (String className : classMap.keySet()) {
            LoxClass klass = classMap.get(className);
            if (klass instanceof LoxNativeClass) {
                classNames.add(className);
            }
        }
        return classNames;
    }

}
