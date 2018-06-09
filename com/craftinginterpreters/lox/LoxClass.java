package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

class LoxClass extends LoxModule implements LoxCallable {
    public LoxClass superClass;
    boolean isSingletonKlass = false;
    LoxInstance singletonOf = null;
    private LoxModule modDefinedIn = null; // for LoxCallable
    public LoxModule module = null; // if this is a class created when a class includes another module.

    LoxClass(String name, LoxClass superClass, Map<String, LoxCallable> methods) {
        super(null, "Class", name, methods);
        LoxClass klass = null;
        if (this.name != null && this.name.equals("Class")) {
            klass = this;
        } else {
            klass = Runtime.getClass("Class");
        }
        this.klass = klass;
        this.superClass = superClass;
        this.modDefinedIn = this;
    }

    @Override
    public String toString() {
        if (module == null) {
            return "<class " + getName() + ">";
        } else {
            return "<module " + getName() + ">";
        }
    }

    @Override
    public LoxCallable clone() {
        // TODO: don't allow clone
        return this;
    }

    @Override
    public LoxModule getModuleDefinedIn() {
        return this.modDefinedIn;
    }

    @Override
    public void setModuleDefinedIn(LoxModule mod) {
        this.modDefinedIn = mod;
    }

    // constructor arity min
    @Override
    public int arityMin() {
        LoxCallable constructor = getMethod("init");
        if (constructor == null) {
            return 0;
        } else {
            return constructor.arityMin();
        }
    }

    // constructor arity max
    @Override
    public int arityMax() {
        LoxCallable constructor = getMethod("init");
        if (constructor == null) {
            return 0;
        } else {
            return constructor.arityMax();
        }
    }

    // constructor call, creates new instance and binds the constructor, if
    // any, to the instance and calls it.
    @Override
    public Object call(Interpreter interp, List<Object> args,
            Map<String,Object> kwargs, Token callToken) {
        LoxInstance instance = null;
        if (getName().equals("Class")) { // var myClass = Class(Object); // creates anonymous class
            Map<String, LoxCallable> methods = new HashMap<>();
            instance = new LoxClass(null, this, methods);
        } else if (getName().equals("Module")) { // creates anonymous module
            Map<String, LoxCallable> methods = new HashMap<>();
            instance = new LoxModule(Runtime.getClass("Class"), "Class", null, methods);
        } else {
            // creates instance
            LoxClass klass = this;
            if (klass.module != null) {
                interp.throwLoxError("TypeError", callToken,
                    "Tried to instantiate Module '" +
                    klass.getName() + "'. Only classes can be instantiated.");
            }
            instance = new LoxInstance(klass, klass.getName());
        }
        LoxCallable constructor = getMethod("init");
        if (constructor != null) {
            if (!Runtime.acceptsNArgs(constructor, args.size(), kwargs.size())) {
                Lox.error(constructor.getDecl().name, "constructor called with wrong number of arguments");
                return null;
            }
            constructor.bind(instance, interp.environment).call(
                interp, args, kwargs, callToken);
        }
        return instance;
    }

    // constructor declaration
    @Override
    public Stmt.Function getDecl() {
        LoxCallable constructor = getMethod("init");
        if (constructor != null) {
            return constructor.getDecl();
        }
        return null;
    }

    @Override
    public Map<String,Object> getDefaultKwargs(Interpreter interp) {
        Stmt.Function funcDecl = getDecl();
        List<Param> params = null;
        if (funcDecl == null) {
            return null;
        } else {
            params = funcDecl.formals;
            Map<String,Object> ret = new HashMap<>();
            for (Param param : params) {
                if (param.isKwarg) {
                    Object value = param.defaultVal;
                    if (interp != null && value != null && (value instanceof Expr)) {
                        value = interp.evaluate((Expr)value);
                    }
                    ret.put(param.varName(), value);
                }
            }
            return ret;
        }
    }

    // does nothing, as the `call` method does the binding to the constructor,
    // if the class has an `init` method.
    @Override
    public LoxCallable bind(LoxInstance instance, Environment env) {
        return this;
    }

    // returns a bound LoxCallable
    public LoxCallable boundMethod(LoxInstance instance, Environment env, String name) {
        LoxCallable method = getMethod(name);
        if (method != null) {
            return method.bind(instance, env);
        }
        return null;
    }

    public LoxClass getSuper() {
        return superClass;
    }

    // returns an unbound LoxCallable instance method for the class
    public LoxCallable getMethod(String name) {
        LoxClass klass = this;
        while (klass != null) {
            LoxUtil.debug("mlookup", "Looking up method " + name + " in " + klass.toString());
            LoxCallable func = klass.methods.get(name);
            if (func != null) {
                LoxUtil.debug("mlookup", "  Method " + name + " found");
                return func;
            }
            klass = klass.getSuper();
        }
        LoxUtil.debug("mlookup", "Method " + name + " not found");
        return null;
    }

    // returns an unbound LoxCallable instance getter method for the class
    public LoxCallable getGetter(String name) {
        LoxClass klass = this;
        while (klass != null) {
            LoxCallable func = klass.getters.get(name);
            if (func != null) {
                return func;
            }
            klass = klass.getSuper();
        }
        return null;
    }

    // returns an unbound LoxCallable instance setter method for the class
    public LoxCallable getSetter(String name) {
        LoxClass klass = this;
        while (klass != null) {
            LoxCallable func = klass.setters.get(name);
            if (func != null) {
                return func;
            }
            klass = klass.getSuper();
        }
        return null;
    }

    public List<String> getMethodNames(boolean includeAncestorLookup) {
        LoxClass klass = this;
        Set<String> methods =  new HashSet<>();
        while (klass != null) {
            Set<String> keys = klass.methods.keySet();
            methods.addAll(keys);
            if (!includeAncestorLookup) break;
            klass = klass.getSuper();
        }
        return new ArrayList<String>(methods);
    }

    public List<Object> ancestors() {
        LoxClass klass = this;
        boolean isSClass = klass.isSingletonKlass;
        boolean sClassOfClass = false;
        LoxClass sClassOfKlass = null;
        if (isSClass) {
            sClassOfClass = (klass.singletonOf instanceof LoxClass);
            if (sClassOfClass) {
                sClassOfKlass = (LoxClass)klass.singletonOf;
            }
        }
        List<Object> list = new ArrayList<>();
        List<LoxModule> modList = new ArrayList<>();
        while (klass != null) {
            LoxModule mod = klass.module == null ? klass : klass.module;
            if (isSClass && sClassOfClass) {
                if (!modList.contains(mod)) {
                    list.add(klass); // singleton class of the class
                    modList.add(mod);
                }
                LoxClass cSuper = sClassOfKlass.getSuper();
                while (cSuper != null) {
                    klass = cSuper.getSingletonKlass();
                    mod = klass.module == null ? klass : klass.module;
                    if (!modList.contains(mod)) {
                        list.add(klass);
                        modList.add(mod);
                    }
                    cSuper = cSuper.getSuper();
                }
                klass = Runtime.getClass("Class"); // now start at <class Class> and move up
                while (klass != null) {
                    mod = klass.module == null ? klass : klass.module;
                    if (!modList.contains(mod)) {
                        list.add(klass);
                        modList.add(mod);
                    }
                    klass = klass.getSuper();
                }
            } else if (isSClass) { // singleton lookup first, then regular class ancestry lookup
                if (!modList.contains(mod)) {
                    list.add(klass);
                    modList.add(mod);
                }
                klass = klass.getSuper();
            } else { // regular class ancestry lookup
                if (!modList.contains(mod)) {
                    list.add(klass);
                    modList.add(mod);
                }
                klass = klass.getSuper();
            }
        }
        return list;
    }

}
