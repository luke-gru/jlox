package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LoxClass extends LoxInstance implements LoxCallable {
    public String name;
    public LoxClass superClass;
    final Map<String, LoxCallable> methods;
    final Map<String, LoxCallable> getters;
    final Map<String, LoxCallable> setters;
    boolean isSingletonKlass = false;
    LoxInstance singletonOf = null;

    LoxClass(String name, LoxClass superClass, Map<String, LoxCallable> methods) {
        super(null, "Class");
        this.name = name;
        LoxClass klass = null;
        if (this.name != null && this.name.equals("Class")) {
            klass = this;
        } else {
            klass = Runtime.getClass("Class");
        }
        this.klass = klass;
        if (superClass == null && this.name != null && !this.name.equals("Object")) {
            superClass = Runtime.getClass("Object");
        }
        this.superClass = superClass;
        this.methods = methods;
        this.getters = new HashMap<>();
        this.setters = new HashMap<>();
    }

    @Override
    public String toString() {
        return "<class " + getName() + ">";
    }

    @Override
    public String getName() {
        if (name == null) {
            return "(anon)";
        } else {
            return name;
        }
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
    public Object call(Interpreter interpreter, List<Object> args, Token callToken) {
        LoxInstance instance = null;
        if (getName().equals("Class")) { // var myClass = Class(Object); // creates anonymous class
            Map<String, LoxCallable> methods = new HashMap<>();
            instance = new LoxClass(null, this, methods);
        } else {
            instance = new LoxInstance(this, this.name);
        }
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

}
