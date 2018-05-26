package com.craftinginterpreters.lox;

import java.util.List;

class LoxNativeCallable implements LoxCallable {
    final String name;
    final int arity;

    LoxNativeCallable(String name, int arity) {
        this.name = name;
        this.arity = arity;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> args, Token tok) {
        if (!Runtime.acceptsNArgs(this, args.size())) {
            throw arityError(tok, args.size());
        }
        try {
            interpreter.stack.add(new StackFrame(this, tok));
            return _call(interpreter, args, tok);
        } finally {
            interpreter.stack.pop();
        }
    }

    @Override
    public LoxCallable bind(LoxInstance instance, Environment env) {
        env.define("this", instance);
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
    public int arity() {
        return this.arity;
    }

    private String arityString() {
        if (this.arity >= 0) {
            return String.valueOf(this.arity);
        } else {
            return "at least " + String.valueOf(-(this.arity+1));
        }
    }

}
