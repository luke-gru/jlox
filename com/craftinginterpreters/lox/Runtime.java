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

    static Object array(List<Object> list, Interpreter interp) {
        LoxClass arrayClass = classMap.get("Array");
        Object instance = arrayClass.call(interp, list, null);
        return instance;
    }

    static Object arrayCopy(List<Object> list, Interpreter interp) {
        List<Object> copy = new ArrayList<>();
        copy.addAll(list);
        return array(copy, interp);
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

    static boolean isArray(Object obj) {
        if (!(obj instanceof LoxInstance)) { return false; }
        LoxInstance instance = (LoxInstance)obj;
        return classOf(instance).getName() == "Array";
    }

    static boolean isInstance(Object obj) {
        return (obj instanceof LoxInstance);
    }

    static boolean isString(Object obj) {
        return (obj instanceof StringBuffer);
    }

    static boolean isNumber(Object obj) {
        return (obj instanceof Double);
    }

    static boolean isBool(Object obj) {
        return (obj instanceof Boolean);
    }

    static LoxInstance toInstance(Object obj) {
        if (obj instanceof LoxInstance) {
            return (LoxInstance)obj;
        } else {
            return null;
        }
    }

    static StringBuffer toString(Object obj) {
        if (obj instanceof StringBuffer) {
            return (StringBuffer)obj;
        } else {
            return null;
        }
    }

    static Object dupObject(Object obj) {
        if (obj == null) { return null; }
        if (isNumber(obj) || isBool(obj)) { return obj; }
        if (isString(obj)) { return new StringBuffer((StringBuffer)obj); }
        if (isArray(obj))  { return ((LoxInstance)obj).dup(); }
        if (isInstance(obj))  { return ((LoxInstance)obj).dup(); }
        if (obj instanceof ArrayList) {
            List newList = new ArrayList<Object>((List)obj);
            return newList;
        }
        throw new RuntimeException("Unreachable (dupObject)");
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
        globalEnv.define("String", new LoxNativeCallable("String", 1) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                return new StringBuffer(interpreter.stringify(arguments.get(0)));
            }
        });
    }

    public void defineBuiltinClasses() {
        // class Object
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
        objClass.defineGetter(new LoxNativeCallable("objectId", 0) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                LoxInstance instance = interpreter.environment.getThis();
                return instance.objectId();
            }
        });
        objClass.defineMethod(new LoxNativeCallable("dup", 0) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                LoxInstance instance = interpreter.environment.getThis();
                return instance.dup();
            }
        });
        globalEnv.define("Object", objClass);
        classMap.put("Object", objClass);

        // class Array
        LoxNativeClass arrayClass = new LoxNativeClass("Array", objClass);
        arrayClass.defineMethod(new LoxNativeCallable("init", -1) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                List<Object> ary = new ArrayList<>();
                for (Object arg : args) {
                    ary.add(arg);
                }
                instance.setHiddenProp("ary", ary);
                return instance;
            }
        });
        arrayClass.defineGetter(new LoxNativeCallable("length", 0) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                return (double)((List<Object>)instance.getHiddenProp("ary")).size();
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("push", 1) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                List<Object> ary = (List<Object>)instance.getHiddenProp("ary");
                ary.add(args.get(0));
                return instance;
            }
        });
        globalEnv.define("Array", arrayClass);
        classMap.put("Array", arrayClass);
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
