package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LoxClass extends LoxInstance implements LoxCallable {
    final String name;
    final LoxClass superClass;
    final Map<String, LoxCallable> methods;
    final Map<String, LoxCallable> getters;
    final Map<String, LoxCallable> setters;

    LoxClass(String name, LoxClass superClass, Map<String, LoxCallable> methods) {
        super(null, "metaclass " + name);
        this.name = name;
        this.superClass = superClass;
        this.methods = methods;
        this.getters = new HashMap<>();
        this.setters = new HashMap<>();
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
        LoxCallable constructor = getMethod("init");
        if (constructor == null) {
            return 0;
        } else {
            return constructor.arity();
        }
    }

    // constructor call
    @Override
    public Object call(Interpreter interpreter, List<Object> args, Token callToken) {
        LoxInstance instance = new LoxInstance(this, this.name);
        LoxCallable constructor = getMethod("init");
        if (constructor != null) {
            if (!Runtime.acceptsNArgs(constructor, args.size())) {
                Lox.error(constructor.getDecl().name, "constructor called with wrong number of arguments");
                return null;
            }
            constructor.bind(instance, interpreter.environment).call(interpreter, args, callToken);
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
    public LoxCallable bind(LoxInstance instance, Environment env) {
        return this;
    }

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

    public LoxCallable getMethod(String name) {
        LoxClass klass = this;
        while (klass != null) {
            LoxCallable func = klass.methods.get(name);
            if (func != null) {
                return func;
            }
            klass = klass.getSuper();
        }
        return null;
    }

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

    public Object getMethodOrGetterProp(String name, LoxInstance instance, Interpreter interp) {
        return instance.getMethodOrGetterProp(name, this, interp);
    }
}
