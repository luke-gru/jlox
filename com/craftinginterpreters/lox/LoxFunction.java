package com.craftinginterpreters.lox;

import java.util.ArrayList;
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
        int numParams = declaration.formals.size();
        int numArgs = args.size();
        boolean hasSplat = false;
        boolean addedSplat = false;
        if (numParams > 0) {
            hasSplat = declaration.formals.get(numParams-1).isSplatted;
        }
        for (int i = 0; i < numArgs; i++) {
            boolean isLastParam = (numParams-1) == i;
            Param param = declaration.formals.get(i);
            if (isLastParam && param.isSplatted) {
                Object splatAry;
                splatAry = Runtime.arrayCopy(args.subList(i, numArgs), interpreter);
                environment.define(param.varName(), splatAry);
                addedSplat = true;
                break;
            } else {
                environment.define(param.varName(), args.get(i));
            }
        }

        if (numParams > numArgs) { // set default arguments
            int i = numArgs;
            for (i = numArgs; i < numParams; i++) {
                Param param = declaration.formals.get(i);
                if (param.hasDefaultValue()) {
                    environment.define(param.varName(), interpreter.evaluate(param.defaultVal));
                }
            }
        }

        if (hasSplat && !addedSplat) { // add empty splat array argument
            Param param = declaration.formals.get(numParams-1);
            environment.define(param.varName(), Runtime.array(new ArrayList<Object>(), interpreter));
        }

        Environment fnEnv = new Environment(environment);
        try {
            interpreter.stack.add(new StackFrame(declaration, callToken));
            interpreter.executeBlock(
                ((Stmt.Block)declaration.body).statements,
                fnEnv
            ); // could throw RuntimeThrow, and in that case we don't want to pop the stack frame
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

    // The least amount of arguments the function can get called with.
    // Value is always positive.
    // Example: the arityMin of `fun(a=1, *b) {}` is 0
    //          the arityMin of `fun(a) {}` is 1
    @Override
    public int arityMin() {
        int i = 0;
        for (Param param : declaration.formals) {
            if (param.mustReceiveArgument()) {
                i++;
            }
        }
        return i;
    }

    // The max amount of arguments the function can get called with.
    // If value is negative, there is no max value.
    // Example: the arityMax of `fun(a=1, *b) {}` is -1
    //          the arityMax of `fun(a) {}` is 1
    @Override
    public int arityMax() {
        int i = 0;
        for (Param param : declaration.formals) {
            if (param.isSplatted) {
                return -1;
            } else {
                i++;
            }
        }
        return i;
    }

    @Override
    public String toString() {
        return "<fn " + getName() + ">";
    }

    @Override
    public Stmt.Function getDecl() {
        return declaration;
    }

    @Override
    public LoxCallable bind(LoxInstance instance, Environment env) {
        Environment environment = new Environment(env);
        environment.define("this", instance);
        return new LoxFunction(declaration, environment, isInitializer);
    }

}
