package com.craftinginterpreters.lox;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

class LoxNativeCallable implements LoxCallable {
    public String name;
    final int arityMin;
    final int arityMax;
    final List<Object> defaultArgs;
    public Map<String,Object> kwArgs = null;
    LoxInstance boundInstance = null;
    protected LoxModule modDefinedIn = null;

    LoxNativeCallable(String name, int arityMin, int arityMax,
            List<Object> defaultArgs, Map<String,Object> kwargs) {
        this.name = name;
        this.arityMin = arityMin;
        this.arityMax = arityMax;
        if (defaultArgs == null) {
            defaultArgs = LoxUtil.EMPTY_ARGS;
        }
        if (kwargs == null) {
            kwargs = LoxUtil.EMPTY_KWARGS;
        }
        this.defaultArgs = defaultArgs;
        this.kwArgs = kwargs;
    }

    @Override
    public Object call(Interpreter interp, List<Object> args,
            Map<String,Object> kwargs, Token tok) {
        Object ret = null;
        Environment oldEnv = interp.environment;
        LoxInstance oldBoundInstance = boundInstance;
        interp.stack.add(new StackFrame(this, tok));
        try {
            interp.environment = new Environment(oldEnv);
            if (boundInstance != null) {
                interp.environment.define("this", boundInstance);
            }
            ret = _call(interp, args, kwargs, tok);
        } finally {
            interp.environment = oldEnv;
            this.boundInstance = oldBoundInstance;
        }
        interp.stack.pop(); // must be outside of finally clause so we don't pop the frame if we throw a RuntimeThrow from inside the try { } block
        return ret;
    }

    @Override
    public LoxCallable bind(LoxInstance instance, Environment env) {
        this.boundInstance = instance;
        return this;
    }

    // to override in subclass
    protected Object _call(Interpreter interp, List<Object> args, Map<String,Object> kwargs, Token tok) {
        throw new RuntimeError(tok, name + "() unimplemented!");
    }

    @Override
    public String getName() {
        String prefix = "";
        if (modDefinedIn != null) {
            prefix = modDefinedIn.getName() + "#";
        }
        return prefix + this.name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public LoxCallable clone() {
        //LoxNativeCallable callable = new LoxNativeCallable(
            //name, arityMin, arityMax, new ArrayList<Object>(defaultArgs),
            //new HashMap<String,Object>(kwArgs)
        //);
        //callable.modDefinedIn = modDefinedIn;
        //return callable;
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

    @Override
    public String toString() {
        return "<fn " + getName() + ">";
    }

    @Override
    public Stmt.Function getDecl() {
        return null;
    }

    @Override
    public int arityMin() {
        return this.arityMin;
    }

    @Override
    public int arityMax() {
        return this.arityMax;
    }

    // return kwarg defaults
    @Override
    public Map<String,Object> getDefaultKwargs(Interpreter interp) {
        return this.kwArgs;
    }

}
