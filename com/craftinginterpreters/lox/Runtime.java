package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
import java.util.List;
import java.util.Map;
import java.io.*;
import static com.craftinginterpreters.lox.Interpreter.LoadScriptError;
import static com.craftinginterpreters.lox.Interpreter.LoxAssertionError;

class Runtime {
    final Environment globalEnv;
    static Map<String, LoxClass> classMap;
    boolean inited = false;

    private Runtime(Environment globalEnv, Map<String, LoxClass> classMap) {
        this.globalEnv = globalEnv;
        this.classMap = classMap;
    }

    public static Runtime create(Environment globalEnv, Map<String, LoxClass> classMap) {
        return new Runtime(globalEnv, classMap);
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
        int arityMin = callable.arityMin();
        int arityMax = callable.arityMax();
        if (arityMax < 0) { arityMax = 1000; }
        return n >= arityMin && n <= arityMax;
    }

    static LoxClass classOf(LoxInstance instance) {
        return instance.getKlass();
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

    static boolean isInstance(Object obj) {
        return (obj instanceof LoxInstance);
    }

    static boolean isClass(Object obj) {
        return (obj instanceof LoxClass);
    }

    static boolean isArray(Object obj) {
        if (isClass(obj)) { return false; }
        if (!isInstance(obj)) { return false; }
        LoxInstance instance = (LoxInstance)obj;
        return classOf(instance).getName().equals("Array");
    }

    static boolean isString(Object obj) {
        if (isClass(obj)) { return false; }
        if (!isInstance(obj)) { return false; }
        LoxInstance instance = (LoxInstance)obj;
        return classOf(instance).getName().equals("String");
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

    static LoxInstance toString(Object obj) {
        if (isString(obj)) {
            return (LoxInstance)obj;
        } else {
            return null;
        }
    }

    static LoxInstance createString(String obj, Interpreter interp) {
        LoxInstance loxStr = interp.createInstance("String");
        ((StringBuffer)loxStr.getHiddenProp("buf")).append(obj);
        return loxStr;
    }

    static LoxInstance createString(StringBuffer obj, Interpreter interp) {
        return createString(obj.toString(), interp);
    }

    static String toJavaString(LoxInstance loxStr) {
        return ((StringBuffer)loxStr.getHiddenProp("buf")).toString();
    }

    // dup either Lox object or Lox internal representation of the object
    // (StringBuffer, ArrayList, etc.)
    static Object dupObject(Object obj, Interpreter interp) {
        if (obj == null) { return null; }
        if (isNumber(obj) || isBool(obj)) { return obj; }
        if (isClass(obj)) {
            throw new RuntimeError(null, "Can't dup a class");
        }
        if (isArray(obj) || isString(obj))  { return ((LoxInstance)obj).dup(interp); }
        if (isInstance(obj))  { return ((LoxInstance)obj).dup(interp); }
        if (obj instanceof ArrayList) {
            List newList = new ArrayList<Object>((ArrayList<Object>)obj);
            return newList;
        }
        if (obj instanceof StringBuffer) {
            StringBuffer newBuf = new StringBuffer((StringBuffer)obj);
            return newBuf;
        }
        throw new RuntimeException("Unreachable (dupObject) " + obj.getClass().getName());
    }

    public void init(Interpreter interp) {
        if (inited) {
            return;
        }
        defineGlobalFunctions();
        defineBuiltinClasses();
        defineGlobalVariables(interp);
        inited = true;
    }

    public void defineGlobalVariables(Interpreter interp) {
       LoxInstance loxLoadPath = interp.createInstance("Array", new ArrayList<Object>());
       for (String path : Lox.initialLoadPath) {
           LoxInstance loxPath = Runtime.createString(path, interp);
           List<Object> pushArgs = new ArrayList<Object>();
           pushArgs.add(loxPath);
           interp.callMethod("push", loxLoadPath, pushArgs);
       }
       globalEnv.define("LOAD_PATH", loxLoadPath);

       globalEnv.define("__DIR__", Runtime.createString("", interp)); // see Interpreter#setRunningFile to see how this is populated
       globalEnv.define("__FILE__", Runtime.createString("", interp)); // see Interpreter#setRunningFile to see how this is populated
    }

    public void defineGlobalFunctions() {
        globalEnv.define("clock", new LoxNativeCallable("clock", 0, 0) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                return (double)System.currentTimeMillis() / 1000.0;
            }
        });
        globalEnv.define("typeof", new LoxNativeCallable("typeof", 1, 1) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                Object obj = arguments.get(0);
                return Runtime.createString(interpreter.nativeTypeof(tok, obj), interpreter);
            }

        });
        globalEnv.define("len", new LoxNativeCallable("len", 1, 1) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                return interpreter.nativeLen(tok, arguments.get(0));
            }
        });
        globalEnv.define("assert", new LoxNativeCallable("assert", 1, 2) {
            @Override
            protected Object _call(Interpreter interp, List<Object> arguments, Token tok) {
                Object expr = arguments.get(0);
                if (interp.isTruthy(expr)) {
                    return null;
                } else {
                    StringBuffer strBuf = new StringBuffer("Assertion failure");
                    if (arguments.size() > 1) {
                        LoxInstance loxMsg = Runtime.toString(arguments.get(1));
                        strBuf.append(": ").append(Runtime.toJavaString(loxMsg));
                    }
                    interp.throwLoxError(LoxAssertionError.class, tok, strBuf.toString());
                    return null;
                }
            }
        });
        globalEnv.define("loadScript", new LoxNativeCallable("loadScript", 1, -1) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                List<String> fnames = new ArrayList<>();
                boolean ret = true;
                for (Object arg : arguments) {
                    if (Runtime.isString(arg)) {
                        String javaFname = Runtime.toJavaString(Runtime.toInstance(arg));
                        boolean loaded = false;
                        try {
                            loaded = interpreter.loadScript(javaFname);
                        } catch (LoadScriptError e) {
                            System.err.println("Load script error: " + e.getMessage());
                            loaded = false;
                        }
                        if (loaded && ret) {
                            ret = true;
                        } else {
                            ret = false;
                        }
                    } else {
                        // TODO: throw ArgumentError
                    }
                }
                return (Boolean)ret;
            }
        });
        globalEnv.define("loadScriptOnce", new LoxNativeCallable("loadScriptOnce", 1, -1) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                List<String> fnames = new ArrayList<>();
                boolean ret = true;
                for (Object arg : arguments) {
                    if (Runtime.isString(arg)) {
                        String javaFname = Runtime.toJavaString(Runtime.toInstance(arg));
                        boolean loaded = false;
                        try {
                            loaded = interpreter.loadScriptOnce(javaFname);
                        } catch (LoadScriptError e) {
                            System.err.println("Load script error: " + e.getMessage());
                            loaded = false;
                        }
                        if (loaded && ret) {
                            ret = true;
                        } else {
                            ret = false;
                        }
                    } else {
                        // TODO: throw ArgumentError
                    }
                }
                return (Boolean)ret;
            }
        });
        globalEnv.define("system", new LoxNativeCallable("system", 1, 1) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance loxStr = Runtime.toInstance(args.get(0));
                String javaStr = Runtime.toJavaString(loxStr);
                LoxInstance loxAry = interp.createInstance("Array");
                List<Object> javaAry = (List<Object>)loxAry.getHiddenProp("ary");
                try {
                    Process p = java.lang.Runtime.getRuntime().exec(javaStr);
                    String s;

                    StringBuffer outBuf = new StringBuffer();
                    StringBuffer errBuf = new StringBuffer();
                    BufferedReader stdOut = new BufferedReader(new
                            InputStreamReader(p.getInputStream()));
                    BufferedReader stdError = new BufferedReader(new
                            InputStreamReader(p.getErrorStream()));
                    while ((s = stdOut.readLine()) != null) {
                        outBuf.append(s).append("\n");
                    }
                    while ((s = stdError.readLine()) != null) {
                        errBuf.append(s).append("\n");
                    }
                    javaAry.add(Runtime.createString(outBuf, interp));
                    javaAry.add(Runtime.createString(errBuf, interp));
                } catch (IOException err) {
                    javaAry.add(Runtime.createString("", interp));
                    javaAry.add(Runtime.createString(err.getMessage(), interp));
                }
                return loxAry;

            }
        });
    }

    public void defineBuiltinClasses() {
        // class Object
        LoxNativeClass objClass = new LoxNativeClass("Object", null);
        objClass.defineMethod(new LoxNativeCallable("equals", 1, 1) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                return Runtime.bool(
                        interpreter.environment.getThis().equals(arguments.get(0))
                );
            }
        });
        objClass.defineGetter(new LoxNativeCallable("_class", 0, 0) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                return Runtime.classOf(
                    interpreter.environment.getThis()
                );
            }
        });
        objClass.defineMethod(new LoxNativeCallable("freeze", 0, 0) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                interpreter.environment.getThis().freeze();
                return null;
            }
        });
        objClass.defineGetter(new LoxNativeCallable("objectId", 0, 0) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                LoxInstance instance = interpreter.environment.getThis();
                return instance.objectId();
            }
        });
        objClass.defineMethod(new LoxNativeCallable("dup", 0, 0) {
            @Override
            protected Object _call(Interpreter interp, List<Object> arguments, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                return instance.dup(interp);
            }
        });
        globalEnv.define("Object", objClass);
        classMap.put("Object", objClass);

        // class Class
        LoxNativeClass classClass = new LoxNativeClass("Class", objClass);
        objClass.klass = classClass;
        classClass.defineMethod(new LoxNativeCallable("init", 0, 1) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                // TODO: register class with classmap if given a name.
                return instance;
            }
        });
        classClass.defineGetter(new LoxNativeCallable("name", 0, 0) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxClass klass = interp.environment.getThisClass();
                // TODO: register class with classmap if given a name.
                return Runtime.createString(klass.getName(), interp);
            }
        });
        globalEnv.define("Class", classClass);
        classMap.put("Class", classClass);

        // class Array
        LoxNativeClass arrayClass = new LoxNativeClass("Array", objClass);
        arrayClass.klass = classClass;
        arrayClass.defineMethod(new LoxNativeCallable("init", 0, -1) {
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
        arrayClass.defineGetter(new LoxNativeCallable("length", 0, 0) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                return (double)((List<Object>)instance.getHiddenProp("ary")).size();
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("push", 1, 1) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                List<Object> ary = (List<Object>)(instance.getHiddenProp("ary"));
                ary.add(args.get(0));
                return instance;
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("pop", 0, 0) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                List<Object> ary = (List<Object>)instance.getHiddenProp("ary");
                Object el = ary.remove(ary.size()-1);
                return el;
            }
        });
        globalEnv.define("Array", arrayClass);
        classMap.put("Array", arrayClass);

        // class String
        LoxNativeClass stringClass = new LoxNativeClass("String", objClass);
        stringClass.klass = classClass;
        stringClass.defineMethod(new LoxNativeCallable("init", 0, -1) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                StringBuffer buf = new StringBuffer();
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        buf.append(" ");
                    }
                    buf.append(interp.stringify(args.get(i)));
                }
                instance.setHiddenProp("buf", buf);
                return instance;
            }
        });
        stringClass.defineGetter(new LoxNativeCallable("length", 0, 0) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                return (double)((StringBuffer)instance.getHiddenProp("buf")).length();
            }
        });
        globalEnv.define("String", stringClass);
        classMap.put("String", stringClass);
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
