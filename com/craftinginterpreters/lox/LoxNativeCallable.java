package com.craftinginterpreters.lox;

import java.util.List;

class LoxNativeCallable implements LoxCallable {
    final String name;
    final int arityMin;
    final int arityMax;
    LoxInstance boundInstance = null;

    LoxNativeCallable(String name, int arityMin, int arityMax) {
        this.name = name;
        this.arityMin = arityMin;
        this.arityMax = arityMax;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> args, Token tok) {
        Object ret = null;
        Environment oldEnv = interpreter.environment;
        LoxInstance oldBoundInstance = boundInstance;
        interpreter.stack.add(new StackFrame(this, tok));
        try {
            interpreter.environment = new Environment(oldEnv);
            if (boundInstance != null) {
                interpreter.environment.define("this", boundInstance);
            }
            ret = _call(interpreter, args, tok);
        } finally {
            interpreter.environment = oldEnv;
            this.boundInstance = oldBoundInstance;
        }
        interpreter.stack.pop(); // must be outside of finally clause so we don't pop the frame if we throw a RuntimeThrow from inside the try { } block
        return ret;
    }

    @Override
    public LoxCallable bind(LoxInstance instance, Environment env) {
        this.boundInstance = instance;
        return this;
    }

    // to override
    protected Object _call(Interpreter interp, List<Object> args, Token tok) {
        throw new RuntimeError(tok, name + "() unimplemented!");
    }

    @Override
    public String getName() {
        return name;
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

}
