package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
    final Stmt.Function declaration;
    final Environment closure;
    final boolean isInitializer;

    LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer) {
        this.declaration = declaration;
        this.closure = closure;
        this.isInitializer = isInitializer;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> args, Token callToken) {
        Environment environment = new Environment(closure);
        for (int i = 0; i < declaration.formals.size(); i++) {
            environment.define(
                declaration.formals.get(i).lexeme,
                args.get(i)
            );
        }

        Environment fnEnv = new Environment(environment);
        try {
            interpreter.stack.add(new StackFrame(declaration, callToken));
            interpreter.executeBlock(
                ((Stmt.Block)declaration.body).statements,
                fnEnv
            );
            interpreter.stack.pop();
            return null;
        } catch (Interpreter.RuntimeReturn ret) {
            interpreter.stack.pop();
            if (isInitializer) {
                return fnEnv.getAt(0, "this");
            } else {
                return ret.value;
            }
        }
    }

    @Override
    public String getName() {
        if (declaration.name == null) {
            return "(anon)";
        } else {
            return declaration.name.lexeme;
        }
    }

    @Override
    public int arity() {
        return declaration.formals.size();
    }

    @Override
    public String toString() {
        return "<fn " + getName() + ">";
    }

    @Override
    public Stmt.Function getDecl() {
        return declaration;
    }

    public LoxFunction bind(LoxInstance instance) {
        Environment environment = new Environment(closure);
        environment.define("this", instance);
        return new LoxFunction(declaration, environment, isInitializer);
    }

}
