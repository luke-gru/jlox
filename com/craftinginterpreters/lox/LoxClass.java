package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LoxClass extends LoxInstance implements LoxCallable {
    final String name;
    final LoxClass superClass;
    final Map<String, LoxFunction> methods;
    final Map<String, LoxFunction> getters;
    final Map<String, LoxFunction> setters;

    LoxClass(String name, LoxClass superClass, Map<String, LoxFunction> methods) {
        super(null, "metaclass " + name);
        this.name = name;
        this.superClass = superClass;
        this.methods = methods;
        this.getters = new HashMap<String, LoxFunction>();
        this.setters = new HashMap<String, LoxFunction>();
    }

    @Override
    public String toString() {
        return "<class " + name + ">";
    }

    @Override
    public String getName() {
        return name;
    }

    // constructor arity
    @Override
    public int arity() {
        LoxFunction constructor = getMethod("init");
        if (constructor == null) {
            return 0;
        } else {
            return constructor.arity();
        }
    }

    // constructor call
    @Override
    public Object call(Interpreter interpreter, List<Object> args, Token callToken) {
        LoxInstance instance = new LoxInstance(this, name);
        LoxFunction constructor = getMethod("init");
        if (constructor != null) {
            if (args.size() != constructor.arity()) {
                Lox.error(constructor.getDecl().name, "constructor called with wrong number of arguments");
                return null;
            }
            constructor.bind(instance).call(interpreter, args, callToken);
        }
        return instance;
    }

    // constructor declaration
    @Override
    public Stmt.Function getDecl() {
        LoxFunction constructor = getMethod("init");
        if (constructor != null) {
            return constructor.getDecl();
        }
        return null;
    }

    public LoxFunction boundMethod(LoxInstance instance, String name) {
        LoxFunction method = getMethod(name);
        if (method != null) {
            return method.bind(instance);
        }
        return null;
    }

    public LoxClass getSuper() {
        return superClass;
    }

    public LoxFunction getMethod(String name) {
        LoxClass klass = this;
        while (klass != null) {
            LoxFunction func = klass.methods.get(name);
            if (func != null) {
                return func;
            }
            klass = klass.getSuper();
        }
        return null;
    }

    public LoxFunction getGetter(String name) {
        LoxClass klass = this;
        while (klass != null) {
            LoxFunction func = klass.getters.get(name);
            if (func != null) {
                return func;
            }
            klass = klass.getSuper();
        }
        return null;
    }

    public LoxFunction getSetter(String name) {
        LoxClass klass = this;
        while (klass != null) {
            LoxFunction func = klass.setters.get(name);
            if (func != null) {
                return func;
            }
            klass = klass.getSuper();
        }
        return null;
    }

    public Object getMethodOrGetterProp(String name, LoxInstance instance, Interpreter interp) {
        return instance.getMethodOrGetterProp(name, this, interp);
    }
}
