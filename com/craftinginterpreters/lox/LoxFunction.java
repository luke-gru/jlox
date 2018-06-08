package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

class LoxFunction implements LoxCallable {
    final Stmt.Function declaration;
    final Environment closure;
    final boolean isInitializer;
    private LoxModule modDefinedIn = null;

    LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer) {
        this.declaration = declaration;
        this.closure = closure;
        this.isInitializer = isInitializer;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> args, Map<String,Object> kwargs, Token callToken) {
        Environment environment = new Environment(closure);
        int numParams = declaration.formals.size(); // num normal and keyword params
        int numArgs = args.size(); // num of normal arguments given (non kwargs)
        boolean hasSplat = false;
        boolean addedSplat = false;
        if (numParams > 0) {
            hasSplat = declaration.formals.get(numParams-1).isSplatted;
        }
        int argsIdx = 0;
        int paramsIdx = 0;
        List<String> kwargsProcessed = new ArrayList<>();
        while (argsIdx < numArgs && paramsIdx < numParams) {
            Param param = declaration.formals.get(paramsIdx);
            if (param.isKwarg) {
                Object kwArgValue = null;
                if (kwargs.containsKey(param.varName())) {
                    //System.err.println("kwarg given to function");
                    kwArgValue = kwargs.get(param.varName());
                } else {
                    if (param.hasDefaultValue()) {
                        //System.err.println("default value from kwarg");
                        Expr defaultExpr = param.getDefaultValue();
                        kwArgValue = interpreter.evaluate(defaultExpr);
                    } else {
                        interpreter.throwLoxError("ArgumentError", callToken,
                            "Function " + getName() + " expected keyword argument '" +
                            param.varName() + ":', but not given");
                    }
                }
                environment.define(param.varName(), kwArgValue);
                kwargsProcessed.add(param.varName());
                paramsIdx++;
                continue;
            }
            boolean isLastParam = (numParams-1) == argsIdx;
            if (isLastParam && param.isSplatted) {
                Object splatAry = Runtime.arrayCopy(args.subList(argsIdx, numArgs), interpreter);
                environment.define(param.varName(), splatAry);
                addedSplat = true;
                argsIdx++;
                paramsIdx++;
                break;
            } else {
                environment.define(param.varName(), args.get(argsIdx));
            }
            argsIdx++;
            paramsIdx++;
        }

        if (numParams > numArgs) { // set default arguments that weren't provided to the call
            int i = numArgs;
            for (i = numArgs; i < numParams; i++) {
                Param param = declaration.formals.get(i);
                if (param.isKwarg && kwargsProcessed.contains(param.varName())) {
                    continue;
                }
                if (param.isKwarg) {
                    if (kwargs.containsKey(param.varName())) {
                        environment.define(param.varName(), kwargs.get(param.varName()));
                        continue;
                    }
                }
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
            String prefix = "";
            if (modDefinedIn != null) {
                prefix = modDefinedIn.getName() + "#";
            }
            return prefix + declaration.name.lexeme;
        }
    }

    @Override
    public LoxModule getModuleDefinedIn() {
        return this.modDefinedIn;
    }

    @Override
    public void setModuleDefinedIn(LoxModule klass) {
        this.modDefinedIn = klass;
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

    @Override
    public Map<String,Object> getDefaultKwargs(Interpreter interp) {
        Stmt.Function funcDecl = getDecl();
        List<Param> params = null;
        if (funcDecl == null) {
            return null;
        } else {
            params = funcDecl.formals;
            Map<String,Object> ret = new HashMap<>();
            for (Param param : params) {
                if (param.isKwarg) {
                    Object value = param.defaultVal;
                    if (interp != null && value != null && (value instanceof Expr)) {
                        value = interp.evaluate((Expr)value);
                    }
                    ret.put(param.varName(), value);
                }
            }
            return ret;
        }
    }

}
