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
                if (i == numArgs-1) { // single splat argument
                    splatAry = Runtime.arrayCopy(args.subList(i, numArgs), interpreter);
                } else {
                    splatAry = Runtime.arrayCopy(args.subList(i, numArgs-1), interpreter);
                }
                environment.define(param.varName(), splatAry);
                addedSplat = true;
                break;
            } else {
                environment.define(param.varName(), args.get(i));
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
        int i = 0;
        for (Param param : declaration.formals) {
            if (param.isSplatted) {
                i++;
                return -i;
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
        //if (env != closure) {
            //throw new RuntimeException("BUG! closure should == env passed to bind()");
        //}
        Environment environment = new Environment(env);
        environment.define("this", instance);
        return new LoxFunction(declaration, environment, isInitializer);
    }

}
