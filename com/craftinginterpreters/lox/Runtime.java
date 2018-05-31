package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.io.*;
import static com.craftinginterpreters.lox.Interpreter.LoadScriptError;

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

    static LoxInstance array(List<Object> list, Interpreter interp) {
        LoxClass arrayClass = classMap.get("Array");
        Object instance = interp.evaluateCall(arrayClass, list, null);
        return Runtime.toInstance(instance);
    }

    static LoxInstance arrayCopy(List<Object> list, Interpreter interp) {
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
        return instance.isA("Array");
    }

    static boolean isString(Object obj) {
        if (isClass(obj)) { return false; }
        if (!isInstance(obj)) { return false; }
        LoxInstance instance = (LoxInstance)obj;
        return instance.isA("String");
    }

    static boolean isNumber(Object obj) {
        return (obj instanceof Double);
    }

    static boolean isBool(Object obj) {
        return (obj instanceof Boolean);
    }

    // NOTE: returns true if obj is a LoxFunction or LoxNativeCallable, NOT a LoxClass,
    // even though LoxClasses are callable.
    static boolean isFunction(Object obj) {
        return !Runtime.isInstance(obj) && (obj instanceof LoxCallable);
    }

    static boolean isCallable(Object obj) {
        return (obj instanceof LoxCallable);
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
    // (StringBuffer, ArrayList, etc.). Doesn't dup primitives.
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
                        Object msgObj = arguments.get(1);
                        LoxUtil.checkString(msgObj, interp, "ArgumentError", null, 2);
                        LoxInstance loxMsg = Runtime.toString(msgObj);
                        strBuf.append(": ").append(Runtime.toJavaString(loxMsg));
                    }
                    interp.throwLoxError("AssertionError", strBuf.toString());
                    return null;
                }
            }
        });
        globalEnv.define("loadScript", new LoxNativeCallable("loadScript", 1, -1) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                List<String> fnames = new ArrayList<>();
                boolean ret = true;
                int argNum = 1;
                for (Object arg : arguments) {
                    LoxUtil.checkString(arg, interpreter, "ArgumentError", null, argNum);
                    LoxInstance loxStr = Runtime.toInstance(arg);
                    String javaFname = Runtime.toJavaString(loxStr);
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
                    argNum++;
                }
                return (Boolean)ret;
            }
        });
        globalEnv.define("loadScriptOnce", new LoxNativeCallable("loadScriptOnce", 1, -1) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                List<String> fnames = new ArrayList<>();
                boolean ret = true;
                int argNum = 1;
                for (Object arg : arguments) {
                    LoxUtil.checkString(arg, interpreter, "ArgumentError", null, argNum);
                    LoxInstance loxStr = Runtime.toInstance(arg);
                    String javaFname = Runtime.toJavaString(loxStr);
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
                    argNum++;
                }
                return (Boolean)ret;
            }
        });
        // TODO: should return the exitstatus as well in the array
        globalEnv.define("system", new LoxNativeCallable("system", 1, 1) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxUtil.checkString(args.get(0), interp, "ArgumentError", null, 1);
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

        globalEnv.define("eval", new LoxNativeCallable("eval", 1, 1) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxUtil.checkString(args.get(0), interp, "ArgumentError", null, 1);
                LoxInstance loxSrc = Runtime.toInstance(args.get(0));
                String src = Runtime.toJavaString(loxSrc);
                Parser oldParser = interp.parser;
                Parser parser = Parser.newFromSource(src);
                interp.parser = parser;
                parser.setNativeClassNames(interp.runtime.nativeClassNames());
                List<Stmt> stmts = parser.parse();
                if (parser.getError() != null) {
                    interp.parser = oldParser;
                    return null;
                }
                interp.lastValue = null;
                interp.interpret(stmts);
                interp.parser = oldParser;
                return interp.lastValue;
            }
        });
        // FIXME: actually clone() the callable so that we set the new name on
        // it for stack traces. Also make sure the name is a proper
        // identifier.
        globalEnv.define("alias", new LoxNativeCallable("alias", 2, 2) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                Object callableObj = args.get(0);
                Object newNameObj = args.get(1);
                if (!Runtime.isCallable(callableObj)) {
                    LoxUtil.checkIsA("function", callableObj, interp, "ArgumentError", null, 1);
                }
                LoxUtil.checkString(newNameObj, interp, "ArgumentError", null, 2);

                LoxCallable callable = (LoxCallable)args.get(0);
                LoxInstance newNameLox = Runtime.toString(newNameObj);

                String newNameJava = Runtime.toJavaString(newNameLox);
                if (!LoxUtil.isValidIdentifier(newNameJava)) {
                    interp.throwLoxError("ArgumentError", "invalid identifier given to 'alias' for function '" +
                        callable.getName() + "'");
                }
                interp.environment.enclosing.define(newNameJava, callableObj);
                return null;
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
                return Runtime.classOf(interpreter.environment.getThis());
            }
        });
        objClass.defineGetter(new LoxNativeCallable("_singletonClass", 0, 0) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                return interpreter.environment.getThis().getSingletonKlass();
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
        objClass.defineMethod(new LoxNativeCallable("hashCode", 0, 0) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments, Token tok) {
                LoxInstance instance = interpreter.environment.getThis();
                return instance.hashCode();
            }
        });
        objClass.defineMethod(new LoxNativeCallable("dup", 0, 0) {
            @Override
            protected Object _call(Interpreter interp, List<Object> arguments, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                return instance.dup(interp);
            }
        });
        objClass.defineMethod(new LoxNativeCallable("toString", 0, 0) {
            @Override
            protected Object _call(Interpreter interp, List<Object> arguments, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                return Runtime.createString(instance.toString(), interp);
            }
        });
        registerClass(objClass);

        // class Class
        LoxNativeClass classClass = new LoxNativeClass("Class", objClass);
        objClass.klass = classClass;
        classClass.defineSingletonMethod(new LoxNativeCallable("all", 0, 0) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                List<Object> klasses = new ArrayList<>();
                Iterator iter = interp.classMap.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry pair = (Map.Entry)iter.next();
                    klasses.add(pair.getValue());
                }
                return Runtime.array(klasses, interp);
            }
        });
        classClass.defineSingletonMethod(new LoxNativeCallable("numInstances", 0, 0) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                return (double)LoxInstance.numInstances;
            }
        });
        classClass.defineMethod(new LoxNativeCallable("init", 0, 1) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxClass klass = interp.environment.getThisClass();
                if (args.size() == 1) {
                    LoxUtil.checkIsA("Class", args.get(0), interp, "ArgumentError", null, 1);
                    LoxClass superClass = (LoxClass)args.get(0);
                    klass.superClass = superClass;
                }
                return klass;
            }
        });
        classClass.defineGetter(new LoxNativeCallable("name", 0, 0) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxClass klass = interp.environment.getThisClass();
                return Runtime.createString(klass.getName(), interp);
            }
        });
        classClass.defineGetter(new LoxNativeCallable("superClass", 0, 0) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                System.err.println("Class#superClass called");
                LoxClass klass = interp.environment.getThisClass();
                return klass.getSuper();
            }
        });
        registerClass(classClass);

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
        arrayClass.defineMethod(new LoxNativeCallable("contains", 1, 1) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                List<Object> ary = (List<Object>)instance.getHiddenProp("ary");
                Boolean b = ary.contains(args.get(0));
                return b;
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("map", 1, 1) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                LoxInstance newInstance = Runtime.array(new ArrayList<Object>(), interp);
                LoxCallable func = (LoxCallable)args.get(0);
                List<Object> ary = (List<Object>)instance.getHiddenProp("ary");
                List<Object> retAry = (List<Object>)newInstance.getHiddenProp("ary");
                for (Object el : ary) {
                    List<Object> funcArgs = new ArrayList<>();
                    funcArgs.add(el);
                    Object ret = interp.evaluateCall(func, funcArgs, tok);
                    retAry.add(ret);
                }
                return newInstance;
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("toString", 0, 0) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                List<Object> ary = (List<Object>)instance.getHiddenProp("ary");
                StringBuffer buf = new StringBuffer("[");
                int sz = ary.size();
                int i = 0;
                for (Object obj : ary) {
                    buf.append(interp.stringify(obj));
                    i++;
                    if (i < sz) {
                        buf.append(",");
                    }
                }
                buf.append("]");
                return Runtime.createString(buf, interp);
            }
        });
        registerClass(arrayClass);

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
        stringClass.defineMethod(new LoxNativeCallable("push", 0, -1) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                StringBuffer buf = (StringBuffer)instance.getHiddenProp("buf");
                int argNum = 1;
                for (Object arg : args) {
                    LoxUtil.checkString(arg, interp, "ArgumentError", null, argNum);
                    LoxInstance argStr = Runtime.toString(arg);
                    buf.append(((StringBuffer)argStr.getHiddenProp("buf")).toString());
                    argNum++;
                }
                return instance;
            }
        });
        registerClass(stringClass);

        // class Number
        LoxNativeClass numClass = new LoxNativeClass("Number", objClass);
        numClass.defineSingletonMethod(new LoxNativeCallable("parse", 1, 1) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxUtil.checkString(args.get(0), interp, "ArgumentError", null, 1);
                LoxInstance loxStr = Runtime.toString(args.get(0));
                String javaStr = ((StringBuffer)loxStr.getHiddenProp("buf")).toString();
                try {
                    return Double.parseDouble(javaStr);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        });
        registerClass(numClass);

        // class Error
        LoxNativeClass errorClass = new LoxNativeClass("Error", objClass);
        errorClass.defineMethod(new LoxNativeCallable("init", 0, 1) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                Object msg = null;
                if (args.size() > 0 && args.get(0) != null) {
                    msg = args.get(0);
                    LoxUtil.checkString(msg, interp, "ArgumentError", null, 1);
                }
                instance.setProperty("message", msg, interp, null);
                List<String> javaStacktrace = interp.stacktraceLines();
                List<Object> loxStacktrace = new ArrayList<>();
                int i = 0;
                for (String line : javaStacktrace) {
                    if (i > 0) { // skip first line, because that's the call we're in (Error#init)
                        loxStacktrace.add(Runtime.createString(line, interp));
                    }
                    i++;
                }
                instance.setProperty("stacktrace", Runtime.array(loxStacktrace, interp), interp, null);
                return instance;
            }
        });
        errorClass.defineMethod(new LoxNativeCallable("toString", 0, 0) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                StringBuffer buf = new StringBuffer();
                buf.append(instance.getKlass().getName());
                Object msg = instance.getProperty("message", interp);
                if (msg != null) {
                    LoxInstance loxStr = Runtime.toString(msg);
                    buf.append(": " + ((StringBuffer)loxStr.getHiddenProp("buf")).toString());
                }
                return Runtime.createString(buf, interp);
            }
        });
        registerClass(errorClass);

        LoxNativeClass argErrorClass = new LoxNativeClass("ArgumentError", errorClass);
        registerClass(argErrorClass);
        LoxNativeClass assertErrorClass = new LoxNativeClass("AssertionError", errorClass);
        registerClass(assertErrorClass);
        LoxNativeClass frozError = new LoxNativeClass("FrozenObjectError", errorClass);
        registerClass(frozError);

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

    private void registerClass(LoxNativeClass klass) {
        globalEnv.define(klass.getName(), klass);
        classMap.put(klass.getName(), klass);
    }

}
