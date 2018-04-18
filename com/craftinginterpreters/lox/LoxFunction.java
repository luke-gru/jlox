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
    public Object call(Interpreter interpreter, List<Object> args) {
        Environment environment = new Environment(closure);
        for (int i = 0; i < declaration.formals.size(); i++) {
            environment.define(
                declaration.formals.get(i).lexeme,
                args.get(i)
            );
        }

        Environment fnEnv = new Environment(environment);
        try {
            interpreter.executeBlock(
                ((Stmt.Block)declaration.body).statements,
                fnEnv
            );
            return null;
        } catch (Interpreter.RuntimeReturn ret) {
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
