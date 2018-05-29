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
        if (!Runtime.acceptsNArgs(this, args.size())) {
            throw arityError(tok, args.size());
        }
        Environment oldEnv = interpreter.environment;
        LoxInstance oldBoundInstance = boundInstance;
        try {
            interpreter.stack.add(new StackFrame(this, tok));
            interpreter.environment = new Environment(oldEnv);
            if (boundInstance != null) {
                interpreter.environment.define("this", boundInstance);
            }
            return _call(interpreter, args, tok);
        } finally {
            interpreter.stack.pop();
            interpreter.environment = oldEnv;
            this.boundInstance = oldBoundInstance;
        }
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

    private RuntimeError arityError(Token tok, int got) {
        return new RuntimeError(tok,
            name + "() takes " + arityString() + " arguments, got " +
            String.valueOf(got)
        );
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

    private String arityString() {
        if (this.arityMax >= 0) {
            if (this.arityMin == this.arityMax) {
                return String.valueOf(this.arityMin);
            } else {
                return String.valueOf(this.arityMin) + " to " + String.valueOf(this.arityMax);
            }
        } else {
            return String.valueOf(this.arityMin) + " to n";
        }
    }

}
