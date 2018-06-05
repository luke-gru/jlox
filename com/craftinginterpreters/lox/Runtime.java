package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;
import java.util.Collection;
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
        Object instance = interp.evaluateCall(arrayClass, list,LoxUtil.EMPTY_KWARGS, null);
        return Runtime.toInstance(instance);
    }

    static LoxInstance arrayCopy(List<Object> list, Interpreter interp) {
        List<Object> copy = new ArrayList<>();
        copy.addAll(list);
        return array(copy, interp);
    }

    // FIXME: use nKwargs param
    static boolean acceptsNArgs(LoxCallable callable, int nArgs, int nKwargs) {
        int arityMin = callable.arityMin();
        int arityMax = callable.arityMax();
        if (arityMax < 0) { arityMax = 1000; }
        return (nArgs + nKwargs) >= arityMin && (nArgs + nKwargs) <= arityMax;
    }
    // FIXME: remove this method
    static boolean acceptsNArgs(LoxCallable callable, int nArgs) {
        int arityMin = callable.arityMin();
        int arityMax = callable.arityMax();
        if (arityMax < 0) { arityMax = 1000; }
        return nArgs >= arityMin && nArgs <= arityMax;
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

    static boolean isModule(Object obj) {
        return (obj instanceof LoxModule);
    }

    static boolean isArray(Object obj) {
        if (isClass(obj)) { return false; }
        if (!isInstance(obj)) { return false; }
        LoxInstance instance = (LoxInstance)obj;
        return instance.isA("Array");
    }

    static boolean isMap(Object obj) {
        if (isClass(obj)) { return false; }
        if (!isInstance(obj)) { return false; }
        LoxInstance instance = (LoxInstance)obj;
        return instance.isA("Map");
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
            // FIXME: use throwLoxError
            interp.throwLoxError("TypeError", null,
                "Can't 'dup' classes (tried to dup " + interp.stringify(obj) + ")"
            );
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
           interp.callMethod("push", loxLoadPath, pushArgs, LoxUtil.EMPTY_KWARGS);
       }
       globalEnv.define("LOAD_PATH", loxLoadPath);

       globalEnv.define("__DIR__", Runtime.createString("", interp)); // see Interpreter#setRunningFile to see how this is populated
       globalEnv.define("__FILE__", Runtime.createString("", interp)); // see Interpreter#setRunningFile to see how this is populated
    }

    public void defineGlobalFunctions() {
        globalEnv.define("clock", new LoxNativeCallable("clock", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments,
                    Map<String,Object> kwargs, Token tok) {
                return (double)System.currentTimeMillis() / 1000.0;
            }
        });
        globalEnv.define("typeof", new LoxNativeCallable("typeof", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments,
                    Map<String,Object> kwargs, Token tok) {
                Object obj = arguments.get(0);
                return Runtime.createString(interpreter.nativeTypeof(tok, obj), interpreter);
            }

        });
        globalEnv.define("len", new LoxNativeCallable("len", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments,
                    Map<String,Object> kwargs, Token tok) {
                return interpreter.nativeLen(tok, arguments.get(0));
            }
        });
        globalEnv.define("assert", new LoxNativeCallable("assert", 1, 2, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> arguments,
                    Map<String,Object> kwargs, Token tok) {
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
        globalEnv.define("loadScript", new LoxNativeCallable("loadScript", 1, -1, null, null) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments,
                    Map<String,Object> kwargs, Token tok) {
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
        globalEnv.define("loadScriptOnce", new LoxNativeCallable("loadScriptOnce", 1, -1, null, null) {
            @Override
            protected Object _call(Interpreter interpreter, List<Object> arguments,
                    Map<String,Object> kwargs, Token tok) {
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
        globalEnv.define("system", new LoxNativeCallable("system", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
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
                    BufferedReader stdOut = new BufferedReader(
                        new InputStreamReader(p.getInputStream()));
                    BufferedReader stdError = new BufferedReader(
                        new InputStreamReader(p.getErrorStream()));
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

        globalEnv.define("eval", new LoxNativeCallable("eval", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
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
        globalEnv.define("alias", new LoxNativeCallable("alias", 2, 2, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
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
                    interp.throwLoxError("ArgumentError",
                        "invalid identifier given to 'alias' for function '" +
                        callable.getName() + "'");
                }
                interp.environment.enclosing.define(newNameJava, callableObj);
                return null;
            }
        });
        globalEnv.define("isCallable", new LoxNativeCallable("isCallable", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                Object arg = args.get(0);
                return Runtime.isCallable(arg);
            }
        });
    }

    public void defineBuiltinClasses() {
        // class Object
        LoxNativeClass objClass = new LoxNativeClass("Object", null);
        objClass.defineMethod(new LoxNativeCallable("equals", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                return Runtime.bool(
                        // strict equality, must be same java object
                        interp.environment.getThis() == args.get(0)
                );
            }
        });
        objClass.defineMethod(new LoxNativeCallable("delProp", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                if (instance.isFrozen) {
                    interp.throwLoxError("FrozenObjectError",
                        "Object#delProp called on frozen object");
                }
                Object argObj = args.get(0);
                LoxUtil.checkString(argObj, interp, "ArgumentError", null, 1);
                LoxInstance argStr = Runtime.toInstance(argObj);
                String propName = argStr.getHiddenProp("buf").toString();
                if (instance.hasNormalProperty(propName)) {
                    instance.delNormalProperty(propName);
                    return true;
                } else {
                    return false;
                }
            }
        });
        objClass.defineGetter(new LoxNativeCallable("_class", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                return Runtime.classOf(interp.environment.getThis());
            }
        });
        objClass.defineGetter(new LoxNativeCallable("_singletonClass", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                return interp.environment.getThis().getSingletonKlass();
            }
        });
        objClass.defineMethod(new LoxNativeCallable("freeze", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                interp.environment.getThis().freeze();
                return interp.environment.getThis();
            }
        });
        objClass.defineMethod(new LoxNativeCallable("isFrozen", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                return interp.environment.getThis().isFrozen;
            }
        });
        objClass.defineGetter(new LoxNativeCallable("objectId", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                return instance.objectId();
            }
        });
        objClass.defineMethod(new LoxNativeCallable("hashCode", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                return instance.hashCode();
            }
        });
        objClass.defineMethod(new LoxNativeCallable("dup", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                return instance.dup(interp);
            }
        });
        objClass.defineMethod(new LoxNativeCallable("toString", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                return Runtime.createString(instance.toString(), interp);
            }
        });
        // Default property missing method, takes the name of the property as a string.
        // Does nothing by default, except return `nil`.
        objClass.defineMethod(new LoxNativeCallable("propertyMissing", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                return null;
            }
        });
        registerClass(objClass);

        // class Module
        LoxNativeClass modClass = new LoxNativeClass("Module", objClass);
        modClass.defineMethod(new LoxNativeCallable("init", 0, -1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxModule mod = (LoxModule)interp.environment.getThis();
                interp.throwLoxError("TypeError", tok,
                    "Cannot instantiate a module. Tried to insantiate module '" + mod.getName() + "'");
                return null;
            }
        });
        modClass.defineMethod(new LoxNativeCallable("include", 1, -1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxModule thisModOrClass = (LoxModule)interp.environment.getThis();
                for (Object arg : args) {
                    if (!Runtime.isModule(arg)) {
                        interp.throwLoxError("ArgumentError", tok,
                            "Only modules may be included into other modules or classes (" + thisModOrClass.toString() +
                            "tried to include " + interp.stringify(arg) + ")");
                    }
                    ((LoxModule)arg).includeIn(thisModOrClass);
                }
                return null;
            }
        });
        modClass.defineSingletonMethod(new LoxNativeCallable("all", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                List<Object> mods = new ArrayList<>();
                Iterator iter = interp.classMap.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry pair = (Map.Entry)iter.next();
                    mods.add(pair.getValue());
                }
                iter = interp.modMap.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry pair = (Map.Entry)iter.next();
                    mods.add(pair.getValue());
                }
                return Runtime.array(mods, interp);
            }
        });
        registerClass(modClass);

        // class Class
        LoxNativeClass classClass = new LoxNativeClass("Class", modClass);
        objClass.klass = classClass;
        modClass.klass = classClass;
        classClass.defineSingletonMethod(new LoxNativeCallable("all", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                List<Object> classes = new ArrayList<>();
                Iterator iter = interp.classMap.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry pair = (Map.Entry)iter.next();
                    classes.add(pair.getValue());
                }
                return Runtime.array(classes, interp);
            }
        });
        classClass.defineSingletonMethod(new LoxNativeCallable("getByName", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                Object nameStr = args.get(0);
                LoxUtil.checkString(nameStr, interp, "ArgumentError", null, 1);
                LoxInstance name = Runtime.toString(nameStr);
                String javaStr = Runtime.toJavaString(name);
                LoxClass klass = classMap.get(javaStr);
                if (klass != null) {
                    return klass;
                } else {
                    return null;
                }
            }
        });
        classClass.defineMethod(new LoxNativeCallable("init", 0, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxClass klass = interp.environment.getThisClass();
                if (args.size() == 1) {
                    LoxUtil.checkIsA("Class", args.get(0), interp, "ArgumentError", null, 1);
                    LoxClass superClass = (LoxClass)args.get(0);
                    klass.superClass = superClass;
                }
                return klass;
            }
        });
        classClass.defineGetter(new LoxNativeCallable("name", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxClass klass = interp.environment.getThisClass();
                return Runtime.createString(klass.getName(), interp);
            }
        });
        classClass.defineGetter(new LoxNativeCallable("superClass", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxClass klass = interp.environment.getThisClass();
                return klass.getSuper();
            }
        });
        classClass.defineMethod(new LoxNativeCallable("ancestors", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxClass klass = interp.environment.getThisClass();
                boolean isSClass = klass.isSingletonKlass;
                boolean sClassOfClass = false;
                LoxClass sClassOfKlass = null;
                if (isSClass) {
                    sClassOfClass = (klass.singletonOf instanceof LoxModule);
                    if (sClassOfClass) {
                        sClassOfKlass = (LoxClass)klass.singletonOf;
                    }
                }
                List<Object> list = new ArrayList<>();
                while (klass != null) {
                    if (isSClass && sClassOfClass) {
                        list.add(klass); // singleton class of the class
                        LoxClass cSuper = sClassOfKlass.getSuper();
                        while (cSuper != null) {
                            klass = cSuper.getSingletonKlass();
                            list.add(klass);
                            cSuper = cSuper.getSuper();
                        }
                        klass = Runtime.getClass("Class"); // now start at <class Class> and move up
                        while (klass != null) {
                            list.add(klass);
                            klass = klass.getSuper();
                        }
                    } else if (isSClass) { // singleton lookup first, then regular class ancestry lookup
                        list.add(klass);
                        klass = klass.getSuper();
                    } else { // regular class ancestry lookup
                        list.add(klass);
                        klass = klass.getSuper();
                    }
                }
                return interp.createInstance("Array", list);
            }
        });
        classClass.defineMethod(new LoxNativeCallable("methodNames", 0, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxClass klass = interp.environment.getThisClass();
                boolean includeAncestorLookup = true;
                if (args.size() == 1) {
                    includeAncestorLookup = interp.isTruthy(args.get(0));
                }
                List<String> methodNames = klass.getMethodNames(includeAncestorLookup);
                List<Object> methodNameStrs = new ArrayList<>();
                for (String methodName : methodNames) {
                    if (methodName.equals("init")) {
                        continue;
                    }
                    methodNameStrs.add(Runtime.createString(methodName, interp));
                }
                return interp.createInstance("Array", methodNameStrs);
            }
        });
        registerClass(classClass);

        // class Array
        LoxNativeClass arrayClass = new LoxNativeClass("Array", objClass);
        arrayClass.defineMethod(new LoxNativeCallable("init", 0, -1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                List<Object> ary = new ArrayList<>();
                for (Object arg : args) {
                    ary.add(arg);
                }
                instance.setHiddenProp("ary", ary);
                return instance;
            }
        });
        arrayClass.defineGetter(new LoxNativeCallable("length", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                return (double)((List<Object>)instance.getHiddenProp("ary")).size();
            }
        });
        // [1,2] + [1] => [1,2,1]
        arrayClass.defineMethod(new LoxNativeCallable("opAdd", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                Object otherObj = args.get(0);
                LoxUtil.checkIsA("Array", otherObj, interp, "ArgumentError", null, 1);
                LoxInstance otherAry = Runtime.toInstance(otherObj);
                List<Object> list = ((List<Object>)instance.getHiddenProp("ary"));
                LoxInstance newAry = interp.createInstance("Array", list);
                List<Object> otherList = ((List<Object>)otherAry.getHiddenProp("ary"));
                List<Object> newList = ((List<Object>)newAry.getHiddenProp("ary"));
                for (Object el : otherList) {
                    newList.add(el);
                }
                return newAry;
            }
        });
        // [1,2] * 3 => [1,2,1,2,1,2]
        arrayClass.defineMethod(new LoxNativeCallable("opMul", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                Object otherObj = args.get(0);
                LoxUtil.checkIsA("number", otherObj, interp, "ArgumentError", null, 1);
                int otherInt = (int)(double)otherObj;
                List<Object> list = ((List<Object>)instance.getHiddenProp("ary"));
                int origSz = list.size();
                LoxInstance newAry = interp.createInstance("Array", list);
                List<Object> newList = ((List<Object>)newAry.getHiddenProp("ary"));
                for (int i = 1; i < otherInt; i++) {
                    for (int j = 0; j < origSz; j++) {
                        Object el = newList.get(j);
                        newList.add(el);
                    }
                }
                return newAry;
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("push", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                if (instance.isFrozen) {
                    interp.throwLoxError("FrozenObjectError",
                        "<Array#push> called on frozen Array object");
                }
                List<Object> ary = (List<Object>)(instance.getHiddenProp("ary"));
                ary.add(args.get(0));
                return instance;
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("pop", 0, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                if (instance.isFrozen) {
                    interp.throwLoxError("FrozenObjectError",
                        "<Array#pop> called on frozen Array object");
                }
                int popMax = 1;
                Object ret = null;
                List<Object> retInternal = null;
                if (args.size() > 0) {
                    Object argObj = args.get(0);
                    LoxUtil.checkIsA("number", argObj, interp, "ArgumentError", null, 1);
                    popMax = (int)(double)argObj;
                    if (popMax > 1) {
                        ret = interp.createInstance("Array", new ArrayList<Object>());
                        retInternal = (List<Object>)((LoxInstance)ret).getHiddenProp("ary");
                    }
                }
                int popped = 0;
                List<Object> ary = (List<Object>)instance.getHiddenProp("ary");
                Object el = null;
                while (popped < popMax && ary.size() > 0) {
                    el = ary.remove(ary.size()-1);
                    if (popMax > 1) {
                        retInternal.add(el);
                    } else {
                        ret = el;
                    }
                    popped++;
                }
                return ret;
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("contains", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                List<Object> ary = (List<Object>)instance.getHiddenProp("ary");
                Boolean b = ary.contains(args.get(0));
                return b;
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("get", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                Object idx = args.get(0);
                LoxUtil.checkIsA("number", idx, interp, "ArgumentError", null, 1);
                int idxNum = (int)(double)idx;
                List<Object> ary = (List<Object>)instance.getHiddenProp("ary");
                if (idxNum >= ary.size()) {
                    return null;
                }
                return ary.get(idxNum);
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("set", 2, 2, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                Object idx = args.get(0);
                LoxUtil.checkIsA("number", idx, interp, "ArgumentError", null, 1);
                Object val = args.get(1);
                int idxNum = (int)(double)idx;
                List<Object> ary = (List<Object>)instance.getHiddenProp("ary");
                int arySz = ary.size();
                if (idxNum >= arySz) {
                    int i = arySz;
                    while (i <= idxNum) {
                        ary.add(null);
                        i++;
                    }
                }
                ary.set(idxNum, val);
                return instance;
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("indexGet", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                LoxCallable getMethod = instance.getMethod("get", interp);
                if (getMethod == null) {
                    interp.throwLoxError("TypeError",
                        "Array has no method '#get' for '[]' (#indexGet)");
                }
                return interp.evaluateCall(getMethod, args, kwargs, tok);
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("indexSet", 2, 2, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                LoxCallable setMethod = instance.getMethod("set", interp);
                if (setMethod == null) {
                    interp.throwLoxError("TypeError",
                        "Array has no method '#set' for '[]=' (#indexSet)");
                }
                return interp.evaluateCall(setMethod, args, kwargs, tok);
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("shift", 0, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                if (instance.isFrozen) {
                    interp.throwLoxError("FrozenObjectError",
                        "<Array#shift> called on frozen Array object");
                }
                int shiftMax = 1;
                Object ret = null;
                List<Object> retInternal = null;
                if (args.size() > 0) {
                    Object argObj = args.get(0);
                    LoxUtil.checkIsA("number", argObj, interp, "ArgumentError", null, 1);
                    shiftMax = (int)(double)argObj;
                    if (shiftMax > 1) {
                        ret = interp.createInstance("Array", new ArrayList<Object>());
                        retInternal = (List<Object>)((LoxInstance)ret).getHiddenProp("ary");
                    }
                }
                List<Object> ary = (List<Object>)instance.getHiddenProp("ary");
                int shifted = 0;
                while (shifted < shiftMax && ary.size() > 0) {
                    if (shiftMax > 1) {
                        retInternal.add(ary.remove(0));
                    } else {
                        ret = ary.remove(0);
                    }
                    shifted++;
                }
                return ret;
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("unshift", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                if (instance.isFrozen) {
                    interp.throwLoxError("FrozenObjectError",
                        "<Array#unshift> called on frozen Array object");
                }
                List<Object> ary = (List<Object>)instance.getHiddenProp("ary");
                ary.add(0, args.get(0));
                return instance;
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("each", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                Object funcObj = args.get(0);
                if (!Runtime.isCallable(funcObj)) {
                    interp.throwLoxError("ArgumentError", tok,
                        "argument given to Array#each must be a function, is: " +
                        interp.nativeTypeof(tok, funcObj));
                }
                LoxCallable func = (LoxCallable)funcObj;
                if (!(Runtime.acceptsNArgs(func, 1) || Runtime.acceptsNArgs(func, 0))) {
                    interp.throwLoxError("ArgumentError", tok,
                        "function given to Array#each must accept 0 or 1 arguments");
                }
                List<Object> ary = (List<Object>)instance.getHiddenProp("ary");
                int arity = func.arityMax();
                for (Object el : ary) {
                    List<Object> funcArgs = new ArrayList<>();
                    if (arity == 0) {
                        // do nothing
                    } else {
                        funcArgs.add(el);
                    }
                    interp.evaluateCall(func, funcArgs, kwargs, tok);
                }
                return instance;
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("map", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                LoxInstance newInstance = Runtime.array(new ArrayList<Object>(), interp);
                Object funcObj = args.get(0);
                if (!Runtime.isCallable(funcObj)) {
                    interp.throwLoxError("ArgumentError", tok,
                        "argument given to Array#map must be a function, is: " +
                        interp.nativeTypeof(tok, funcObj));
                }
                LoxCallable func = (LoxCallable)funcObj;
                if (!(Runtime.acceptsNArgs(func, 1) || Runtime.acceptsNArgs(func, 0))) {
                    interp.throwLoxError("ArgumentError", tok,
                        "function given to Array#map must accept 0 or 1 arguments");
                }
                List<Object> ary = (List<Object>)instance.getHiddenProp("ary");
                List<Object> retAry = (List<Object>)newInstance.getHiddenProp("ary");
                int arity = func.arityMax();
                for (Object el : ary) {
                    List<Object> funcArgs = new ArrayList<>();
                    if (arity == 0) {
                        // do nothing
                    } else {
                        funcArgs.add(el);
                    }
                    Object ret = interp.evaluateCall(func, funcArgs, kwargs, tok);
                    retAry.add(ret);
                }
                return newInstance;
            }
        });
        arrayClass.defineMethod(new LoxNativeCallable("toString", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                List<Object> ary = (List<Object>)instance.getHiddenProp("ary");
                StringBuffer buf = new StringBuffer("[");
                int sz = ary.size();
                int i = 0;
                for (Object obj : ary) {
                    String objStr = null;
                    if (obj != null && obj.equals(instance)) {
                        objStr = "[instance...]";
                    } else {
                        objStr = interp.stringify(obj);
                    }
                    buf.append(objStr);
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

        // class Map
        LoxNativeClass mapClass = new LoxNativeClass("Map", objClass);
        // Map([[1,2],[3,4]]) or Map([1,2]), Map(1, 2)
        mapClass.defineMethod(new LoxNativeCallable("init", 0, 2, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                Map<Object,Object> internalMap = new HashMap<>();
                instance.setHiddenProp("map", internalMap);
                Object aryObj = null;
                if (args.size() == 1) {
                    aryObj = args.get(0);
                    LoxUtil.checkIsA("Array", aryObj, interp, "ArgumentError", null, 1);
                    LoxInstance aryInstance = Runtime.toInstance(aryObj);
                    List<Object> internalAry = (List<Object>)aryInstance.getHiddenProp("ary");
                    // Map([1,2]) same as Map(1,2)
                    if (internalAry.size() == 2 && !(Runtime.isArray(internalAry.get(0)) || Runtime.isArray(internalAry.get(1)))) {
                        args = new ArrayList<Object>();
                        args.add(internalAry.get(0));
                        args.add(internalAry.get(1));
                        return _call(interp, args, kwargs, tok);
                    }
                    int elNum = 1;
                    for (Object elAry : internalAry) {
                        if (!Runtime.isArray(elAry)) {
                            LoxUtil.checkIsA("Array", elAry, interp, "ArgumentError",
                                "Element " + String.valueOf(elNum) +
                                " of given Array object must be an Array.", 1);
                        }
                        LoxInstance elAryInst = Runtime.toInstance(elAry);
                        List<Object> elAryInternal = (List<Object>)elAryInst.getHiddenProp("ary");
                        if (elAryInternal.size() != 2) {
                            LoxUtil.checkIsA("Array", elAry, interp, "ArgumentError",
                                "Element " + String.valueOf(elNum) +
                                " of given Array object must be an Array of size 2.", 1);
                        } else {
                            Object key = elAryInternal.get(0);
                            Object val = elAryInternal.get(1);
                            internalMap.put(key, val);
                        }
                        elNum++;
                    }
                } else if (args.size() == 2) {
                    internalMap.put(args.get(0), args.get(1));
                }
                return instance;
            }
        });
        mapClass.defineMethod(new LoxNativeCallable("get", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                Map<Object,Object> mapIntern = (Map<Object,Object>)instance.getHiddenProp("map");
                Object argObj = args.get(0);
                if (mapIntern.containsKey(argObj)) {
                    return mapIntern.get(argObj);
                } else {
                    if (instance.hasNormalProperty("default")) {
                        return instance.getNormalProperty("default");
                    }
                    return null;
                }
            }
        });
        mapClass.defineMethod(new LoxNativeCallable("put", 2, 2, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                if (instance.isFrozen) {
                    interp.throwLoxError("FrozenObjectError",
                        "<Map#put> called on frozen map: " + interp.stringify(instance));
                }
                Map<Object,Object> mapIntern = (Map<Object,Object>)instance.getHiddenProp("map");
                Object keyObj = args.get(0);
                Object valObj = args.get(1);
                mapIntern.put(keyObj, valObj);
                return instance;
            }
        });
        mapClass.defineMethod(new LoxNativeCallable("indexGet", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                LoxCallable getMethod = instance.getMethod("get", interp);
                if (getMethod == null) {
                    interp.throwLoxError("TypeError",
                        "Map has no method '#get' for '[]' (#indexGet)");
                }
                return interp.evaluateCall(getMethod, args, kwargs, tok);
            }
        });
        mapClass.defineMethod(new LoxNativeCallable("indexSet", 2, 2, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                LoxCallable putMethod = instance.getMethod("put", interp);
                if (putMethod == null) {
                    interp.throwLoxError("TypeError",
                        "Map has no method '#put' for '[]=' (#indexSet)");
                }
                return interp.evaluateCall(putMethod, args, kwargs, tok);
            }
        });
        mapClass.defineMethod(new LoxNativeCallable("remove", 1, -1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                if (instance.isFrozen) {
                    interp.throwLoxError("FrozenObjectError",
                        "<Map#remove> called on frozen map: " + interp.stringify(instance));
                }
                Map<Object,Object> mapIntern = (Map<Object,Object>)instance.getHiddenProp("map");
                List<Object> retList = new ArrayList<>();
                for (Object key : args) {
                    retList.add(mapIntern.remove(key));
                }
                if (args.size() == 1) {
                    return retList.get(0);
                } else {
                    return interp.createInstance("Array", retList);
                }
            }
        });
        mapClass.defineMethod(new LoxNativeCallable("keys", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                Map<Object,Object> mapIntern = (Map<Object,Object>)instance.getHiddenProp("map");
                Set<Object> keys = mapIntern.keySet();
                List<Object> keysList = new ArrayList<>();
                for (Object key : keys) {
                    keysList.add(key);
                }
                return interp.createInstance("Array", keysList);
            }
        });
        mapClass.defineMethod(new LoxNativeCallable("each", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                Object argObj = args.get(0);
                if (!Runtime.isCallable(argObj)) {
                    interp.throwLoxError("ArgumentError", tok,
                        "Argument given to Map#each must be a function, is: " +
                        interp.nativeTypeof(tok, argObj));
                }
                LoxCallable func = (LoxCallable)argObj;
                int arity = func.arityMax();
                Map<Object,Object> mapIntern = (Map<Object,Object>)instance.getHiddenProp("map");
                Iterator<Map.Entry<Object, Object>> javaMapIter = mapIntern.entrySet().iterator();
                Map.Entry pair = null;
                while (javaMapIter.hasNext()) {
                    List<Object> funcArgs = new ArrayList<>();
                    pair = javaMapIter.next();
                    if (arity == 1) { // build up an array of length 2 for the single argument
                        List<Object> aryArgs = new ArrayList<>();
                        aryArgs.add(pair.getKey());
                        aryArgs.add(pair.getValue());
                        LoxInstance ary = interp.createInstance("Array", aryArgs);
                        funcArgs.add(ary);
                    } else if (arity != 0) { // pass 2 arguments to the function: the key and the value
                        funcArgs.add(pair.getKey());
                        funcArgs.add(pair.getValue());
                    } else {
                        // do nothing, function expects no arguments
                    }
                    interp.evaluateCall(func, funcArgs, kwargs, tok);
                }
                return instance;
            }
        });
        mapClass.defineMethod(new LoxNativeCallable("values", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                Map<Object,Object> mapIntern = (Map<Object,Object>)instance.getHiddenProp("map");
                Collection<Object> values = mapIntern.values();
                List<Object> valuesList = new ArrayList<>();
                for (Object val : values) {
                    valuesList.add(val);
                }
                return interp.createInstance("Array", valuesList);
            }
        });
        mapClass.defineMethod(new LoxNativeCallable("clear", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                if (instance.isFrozen) {
                    interp.throwLoxError("FrozenObjectError",
                        "<Map#clear> called on frozen map: " + interp.stringify(instance));
                }
                Map<Object,Object> mapIntern = (Map<Object,Object>)instance.getHiddenProp("map");
                mapIntern.clear();
                return instance;
            }
        });
        mapClass.defineMethod(new LoxNativeCallable("iter", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                List<Object> mapIterArgs = new ArrayList<>();
                mapIterArgs.add(instance);
                return interp.createInstance("MapIterator", mapIterArgs);
            }
        });
        mapClass.defineMethod(new LoxNativeCallable("toString", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                Map<Object,Object> mapIntern = (Map<Object,Object>)instance.getHiddenProp("map");
                StringBuffer buf = new StringBuffer("{");
                int sz = mapIntern.size();
                int i = 0;

                Iterator iter = mapIntern.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry pair = (Map.Entry)iter.next();
                    Object key = pair.getKey();
                    Object val = pair.getValue();
                    String keyStr = null;
                    if (key != null && key.equals(instance)) {
                        keyStr = "{instance...}";
                    } else {
                        keyStr = interp.stringify(key);
                    }
                    String valStr = null;
                    if (val != null && val.equals(instance)) {
                        valStr = "{instance...}";
                    } else {
                        valStr = interp.stringify(val);
                    }
                    buf.append(keyStr + " => ");
                    buf.append(valStr);
                    i++;
                    if (i < sz) {
                        buf.append(", ");
                    }
                }
                buf.append("}");

                return Runtime.createString(buf, interp);
            }
        });
        registerClass(mapClass);

        // MapIterator class
        LoxNativeClass mapIterClass = new LoxNativeClass("MapIterator", objClass);
        mapIterClass.defineMethod(new LoxNativeCallable("init", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance iter = interp.environment.getThis();
                Object mapInstanceObj = args.get(0);
                LoxUtil.checkIsA("Map", mapInstanceObj, interp, "ArgumentError", null, 1);
                iter.setNormalProperty("iterable", mapInstanceObj);
                LoxInstance mapInstance = Runtime.toInstance(mapInstanceObj);
                Boolean frozenState = mapInstance.isFrozen ? (Boolean)true : (Boolean)false;
                iter.setHiddenProp("iterableOldFrozenState", frozenState);
                mapInstance.freeze();
                Map<Object,Object> javaMap = (Map<Object,Object>)mapInstance.getHiddenProp("map");
                Iterator<Map.Entry<Object, Object>> javaMapIter = javaMap.entrySet().iterator();
                iter.setHiddenProp("iterator", javaMapIter);
                return null;
            }
        });
        mapIterClass.defineMethod(new LoxNativeCallable("nextIter", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance iter = interp.environment.getThis();
                Object mapInstanceObj = iter.getNormalProperty("iterable");
                LoxUtil.checkIsA("Map", mapInstanceObj, interp, "TypeError", "MapIterator#.iterable needs to be a map!", 0);
                LoxInstance mapInstance = Runtime.toInstance(mapInstanceObj);
                Iterator<Map.Entry<Object, Object>> javaMapIter = (Iterator<Map.Entry<Object,Object>>)iter.getHiddenProp("iterator");
                if (javaMapIter.hasNext()) {
                    Map.Entry<Object,Object> pair = javaMapIter.next();
                    Object key = pair.getKey();
                    Object val = pair.getValue();
                    List<Object> newArrayArgs = new ArrayList<>();
                    newArrayArgs.add(key);
                    newArrayArgs.add(val);
                    return interp.createInstance("Array", newArrayArgs);
                } else {
                    Boolean oldFrozenState = (Boolean)iter.getHiddenProp("iterableOldFrozenState");
                    if (oldFrozenState != null && oldFrozenState == (Boolean)false) {
                        mapInstance.unfreeze();
                    }
                    return null;
                }
            }
        });
        mapIterClass.defineMethod(new LoxNativeCallable("hasNext", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance iter = interp.environment.getThis();
                Object mapInstanceObj = iter.getNormalProperty("iterable");
                LoxUtil.checkIsA("Map", mapInstanceObj, interp, "TypeError", "MapIterator#.iterable needs to be a map!", 0);
                LoxInstance mapInstance = Runtime.toInstance(mapInstanceObj);
                Iterator<Map.Entry<Object,Object>> javaMapIter = (Iterator<Map.Entry<Object,Object>>)iter.getHiddenProp("iterator");
                return javaMapIter.hasNext();
            }
        });
        mapIterClass.defineMethod(new LoxNativeCallable("toString", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance iter = interp.environment.getThis();
                Object mapInstanceObj = iter.getNormalProperty("iterable");
                LoxUtil.checkIsA("Map", mapInstanceObj, interp, "TypeError", "MapIterator#.iterable needs to be a map!", 0);
                LoxInstance mapInstance = Runtime.toInstance(mapInstanceObj);
                Iterator<Map.Entry<Object, Object>> javaMapIter = (Iterator<Map.Entry<Object,Object>>)iter.getHiddenProp("iterator");
                Map<Object,Object> javaMap = (Map<Object,Object>)mapInstance.getHiddenProp("map");
                boolean isFinished = false;
                if (javaMapIter != null) { // first time nextIter is called, create the iterator
                    isFinished = !javaMapIter.hasNext();
                }
                StringBuffer buf = new StringBuffer();
                buf.append("<MapIterator instance ").append(interp.stringify(mapInstance));
                if (isFinished) {
                    buf.append(" (done)");
                }
                buf.append(">");
                return Runtime.createString(buf, interp);
            }
        });
        registerClass(mapIterClass);


        // class String
        LoxNativeClass stringClass = new LoxNativeClass("String", objClass);
        stringClass.defineMethod(new LoxNativeCallable("init", 0, -1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
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
        stringClass.defineGetter(new LoxNativeCallable("length", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                return (double)((StringBuffer)instance.getHiddenProp("buf")).length();
            }
        });
        // String#*
        stringClass.defineMethod(new LoxNativeCallable("opMul", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                Object arg = args.get(0);
                LoxUtil.checkIsA("number", arg, interp, "ArgumentError", null, 1);
                StringBuffer newBuf = new StringBuffer(((StringBuffer)instance.getHiddenProp("buf")));
                String origString = newBuf.toString();
                int argInt = (int)(double)arg;
                for (int i = argInt-1; i > 0; i--) {
                    newBuf.append(origString);
                }
                return Runtime.createString(newBuf, interp);
            }
        });
        // String#+
        stringClass.defineMethod(new LoxNativeCallable("opAdd", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                Object arg = args.get(0);
                LoxUtil.checkString(arg, interp, "ArgumentError", null, 1);
                LoxInstance argStr = Runtime.toInstance(arg);
                StringBuffer newBuf = new StringBuffer(((StringBuffer)instance.getHiddenProp("buf")));
                newBuf.append(((StringBuffer)argStr.getHiddenProp("buf")).toString());
                return Runtime.createString(newBuf, interp);
            }
        });
        stringClass.defineMethod(new LoxNativeCallable("push", 0, -1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
                LoxInstance instance = interp.environment.getThis();
                if (instance.isFrozen) {
                    interp.throwLoxError("FrozenObjectError",
                        "<String#push> called on frozen String object");
                }
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
        numClass.defineSingletonMethod(new LoxNativeCallable("parse", 1, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
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

        /* Error classes: */

        // class Error
        LoxNativeClass errorClass = new LoxNativeClass("Error", objClass);
        errorClass.defineMethod(new LoxNativeCallable("init", 0, 1, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
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
        errorClass.defineMethod(new LoxNativeCallable("toString", 0, 0, null, null) {
            @Override
            protected Object _call(Interpreter interp, List<Object> args,
                    Map<String,Object> kwargs, Token tok) {
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
        LoxNativeClass typeErrorClass = new LoxNativeClass("TypeError", errorClass);
        registerClass(typeErrorClass);
        LoxNativeClass assertErrorClass = new LoxNativeClass("AssertionError", errorClass);
        registerClass(assertErrorClass);
        LoxNativeClass frozErrorClass = new LoxNativeClass("FrozenObjectError", errorClass);
        registerClass(frozErrorClass);
        LoxNativeClass logicErrorClass = new LoxNativeClass("LogicError", errorClass);
        registerClass(logicErrorClass);
        LoxNativeClass nameErrorClass = new LoxNativeClass("NameError", errorClass);
        registerClass(nameErrorClass);
        LoxNativeClass syntaxErrorClass = new LoxNativeClass("SyntaxError", errorClass);
        registerClass(syntaxErrorClass);
        LoxNativeClass noSuchFuncError = new LoxNativeClass("NoSuchFunctionError", errorClass);
        registerClass(noSuchFuncError);
        LoxNativeClass noSuchMethError = new LoxNativeClass("NoSuchMethodError", noSuchFuncError);
        registerClass(noSuchMethError);
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
