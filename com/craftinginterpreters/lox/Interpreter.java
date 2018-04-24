package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
import java.util.List;
import java.util.Map;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private static class RuntimeBreak extends RuntimeException {}
    private static class RuntimeContinue extends RuntimeException {}
    private static class RuntimeThrow extends RuntimeException {
        final Object value;
        final Token keywordTok;
        RuntimeThrow(Object value, Token keywordTok) {
            super(null, null, false, false);
            this.value = value;
            this.keywordTok = keywordTok;
        }
    }

    public static class RuntimeReturn extends RuntimeException {
        final Object value;
        RuntimeReturn(Object value) {
            super(null, null, true, true);
            this.value = value;
        }
    }

    public final Map<Expr, Integer> locals = new HashMap<>();
    final Environment globals = new Environment();
    final Runtime runtime;
    public Environment environment = globals;
    private LoxCallable fnCall = null; // current function being executed
    private LoxClass currentClass = null; // current class being visited
    private Map<String, LoxClass> classMap = new HashMap<>();

    public Stack<StackFrame> stack = new Stack<>();

    Interpreter() {
        this.runtime = new Runtime(globals, classMap);
        runtime.init();
    }

    public void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            System.err.println("==============");
            System.err.print("RuntimeError: ");
            Lox.runtimeError(error);
            System.err.println("Stacktrace:");
            System.err.println(stacktrace());
            System.err.println("==============");
        } catch (RuntimeThrow error) {
            System.err.println("==============");
            System.err.println("Uncaught error: " + stringify(error.value));
            System.err.println("Stacktrace:");
            System.err.println(stacktrace());
            System.err.println("==============");
            Lox.hadRuntimeError = true;
        }
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitArrayExpr(Expr.Array expr) {
        List<Object> objs = new ArrayList<>();
        for (Expr el : expr.expressions) {
            objs.add(evaluate(el));
        }
        return new LoxArray(objs);
    }

    @Override
    public Object visitIndexedGetExpr(Expr.IndexedGet expr) {
        Object obj = evaluate(expr.left);
        if (!(obj instanceof LoxArray) && !(obj instanceof StringBuffer) &&
           (!(obj instanceof LoxInstance))) {
            throw new RuntimeError(tokenFromExpr(expr.left), "index access expr (expr[]), LHS must be array, string or object.");
        }
        Object index = evaluate(expr.indexExpr);
        boolean needsNumberIndex = (obj instanceof LoxArray) || (obj instanceof StringBuffer);
        boolean needsStringIndex = (obj instanceof LoxInstance);
        if (needsNumberIndex && !(index instanceof Double)) {
            throw new RuntimeError(tokenFromExpr(expr.indexExpr), "index access accessor (expr[accessor]) must be number for arrays and strings.");
        } else if (needsStringIndex && !(index instanceof StringBuffer)) {
            throw new RuntimeError(tokenFromExpr(expr.indexExpr), "index access accessor (expr[accessor]) must be string for objects.");
        }
        if (obj instanceof LoxArray) {
            LoxArray ary = (LoxArray)obj;
            Token tok = tokenFromExpr(expr.left);
            return ary.get(((Double)index).intValue(), tok);
        } else if (obj instanceof StringBuffer) {
            StringBuffer strBuf = (StringBuffer)obj;
            int start = ((Double)index).intValue();
            return new StringBuffer(strBuf.substring(start, start+1));
        } else if (obj instanceof LoxInstance) {
            LoxInstance instance = (LoxInstance)obj;
            StringBuffer strIndex = (StringBuffer)index;
            return instance.getProperty(strIndex.toString(), this);
        } else {
            throw new RuntimeException("unreachable");
        }
    }

    @Override
    public Object visitIndexedSetExpr(Expr.IndexedSet expr) {
        Object obj = evaluate(expr.left);
        if (!(obj instanceof LoxArray) && !(obj instanceof StringBuffer) &&
            !(obj instanceof LoxInstance)) {
            throw new RuntimeError(tokenFromExpr(expr.left), "indexed set expr (expr[index] = rval), LHS must be an array, string or object.");
        }
        boolean needsNumberIndex = (obj instanceof LoxArray) || (obj instanceof StringBuffer);
        boolean needsStringIndex = (obj instanceof LoxInstance);
        Object index = evaluate(expr.indexExpr);
        if (needsNumberIndex && !(index instanceof Double)) {
            throw new RuntimeError(tokenFromExpr(expr.indexExpr), "indexed set index (expr[index] = rval) must be a number when expr evaluates to a string or array.");
        } else if (needsStringIndex && !(index instanceof StringBuffer)) {
            throw new RuntimeError(tokenFromExpr(expr.indexExpr), "indexed set index (expr[index] = rval) must be a string when expr evaluates to an object.");
        }
        Object val = evaluate(expr.value);
        if (obj instanceof LoxArray) {
            LoxArray ary = (LoxArray)obj;
            Token tok = tokenFromExpr(expr.left);
            ary.set(((Double)index).intValue(), val, tok);
            return val;
        } else if (obj instanceof StringBuffer) {
            StringBuffer strBuf = (StringBuffer)obj;
            if (!(val instanceof StringBuffer)) {
                throw new RuntimeError(tokenFromExpr(expr.indexExpr), "string[]=value, value must be a string!");
            }
            int start = ((Double)index).intValue();
            int end = start + ((StringBuffer)val).length();
            if (start > strBuf.length()) { // FIXME: very slow
                int len = strBuf.length();
                while (len < start) {
                    strBuf.append(" ");
                    len++;
                }
            } else {
                strBuf.delete(start, end);
            }
            // TODO
            strBuf.insert(start, ((StringBuffer)val).toString());
            return val;
        } else if (obj instanceof LoxInstance) {
            LoxInstance instance = (LoxInstance)obj;
            String indexStr = ((StringBuffer)index).toString();
            LoxCallable setterFunc = instance.getKlass().getSetter(indexStr);
            LoxCallable oldFnCall = this.fnCall;
            if (setterFunc != null) {
                this.fnCall = setterFunc;
            }
            instance.setProperty(indexStr, val, this, setterFunc);
            if (setterFunc != null) {
                this.fnCall = oldFnCall;
            }
            return val;
        } else {
            throw new RuntimeException("unreachable");
        }
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                if ((double)right == 0.0) {
                    throw new RuntimeError(expr.operator, "division by 0");
                }
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }

                if (left instanceof StringBuffer && right instanceof StringBuffer) {
                    return new StringBuffer(
                            ((StringBuffer)left).toString() + ((StringBuffer)right).toString()
                    );
                }

                throw new RuntimeError(expr.operator, "operands must be two numbers or two strings");
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case BANG_EQUAL: return !isEqual(left, right);
            case EQUAL_EQUAL: return isEqual(left, right);
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);
        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left; // TODO: should return boolean "false" value
        }

        return evaluate(expr.right);
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }


    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        Integer distance = locals.get(expr);
        if (distance != null) {
            return environment.getAt(distance, expr.name);
        } else {
            return globals.get(expr.name, false);
        }
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        Integer distance = locals.get(expr);
        if (distance != null) {
            return environment.getAt(distance, expr.keyword);
        } else {
            Lox.error(expr.keyword, "this can only be used inside function/method declarations");
            return null;
        }
    }

    @Override
    public Object visitSuperExpr(Expr.Super expr) {
        Object instance = environment.get("this", true, expr.keyword);
        if (instance == null || !(instance instanceof LoxInstance)) {
            throw new RuntimeError(expr.keyword, "'super' keyword couldn't resolve 'this'! BUG");
        }
        Stmt.Class classStmt = this.fnCall.getDecl().klass;
        LoxClass enclosingClass = classMap.get(classStmt.name.lexeme);
        //System.err.println("fncall enclosing class: " + enclosingClass.getName());
        LoxClass superClass = enclosingClass.getSuper();
        if (superClass == null) {
            throw new RuntimeError(expr.keyword, "'super' keyword couldn't find superclass! BUG");
        }
        //System.err.println("fncall enclosing class: " + enclosingClass.getName());
        //System.err.println("fncall super class: " + superClass.getName());
        Object value = superClass.getMethodOrGetterProp(expr.property.lexeme, (LoxInstance)instance, this);
        if (value == null) {
            throw new RuntimeError(expr.keyword, "'super." + expr.property.lexeme + "' doesn't reference a valid method or getter!");
        }
        return value;
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value, false);
        }
        return value;
    }

    @Override
    public Object visitCallExpr(Expr.Call callExpr) {
        Object obj = evaluate(callExpr.left);
        if (obj instanceof LoxCallable) {
            List<Object> args = new ArrayList<>();
            for (Expr expr : callExpr.args) {
                if (expr instanceof Expr.SplatCall) {
                    Object ary = evaluate(((Expr.SplatCall)expr).expression);
                    if (!(ary instanceof LoxArray)) {
                        throw new RuntimeError(tokenFromExpr(callExpr), "Splat arg expression must evaluate to an array");
                    }
                    args.addAll(((LoxArray)ary).elements);
                } else {
                    args.add(evaluate(expr));
                }
            }
            LoxCallable callable = (LoxCallable)obj;
            if (!Runtime.acceptsNArgs(callable, args.size())) {
                int arity = callable.arity();
                int expectedN = arity;
                String expectedNStr;
                if (expectedN < 0) {
                    expectedNStr = "at least " + String.valueOf(-expectedN);
                } else {
                    expectedNStr = String.valueOf(expectedN);
                }
                int actualN = args.size();
                String actualNStr = String.valueOf(actualN);
                throw new RuntimeError(
                    tokenFromExpr(callExpr.left), "Function <" +
                    callable.getName() + "> called with wrong number of arguments. Expected " +
                    expectedNStr + ", got " + actualNStr + "."
                );
            }
            //System.err.println("CALL(" + ((LoxCallable)obj).getName() + "): args size: ");
            //System.err.println(args.size());
            return evaluateCall(callable, args, tokenFromExpr(callExpr.left));
        } else {
            Token tok = tokenFromExpr(callExpr);
            throw new RuntimeError(tok, "Undefined function or method " + tok.lexeme);
        }
    }

    @Override
    public Object visitSplatCallExpr(Expr.SplatCall expr) {
        return null;
    }

    @Override
    public Object visitPropAccessExpr(Expr.PropAccess expr) {
        Object obj = evaluate(expr.left);
        if (obj instanceof LoxInstance) {
            LoxInstance instance = (LoxInstance)obj;
            LoxCallable getterFunc = instance.getKlass().getGetter(expr.property.lexeme);
            LoxCallable oldFnCall = this.fnCall;
            if (getterFunc != null) {
                this.fnCall = getterFunc;
            }
            Object value = instance.getProperty(expr.property.lexeme, this);
            if (getterFunc != null) {
                this.fnCall = oldFnCall;
            }
            return value;
        } else {
            throw new RuntimeError(expr.property, "Attempt to access property of non-instance");
        }
    }

    @Override
    public Object visitPropSetExpr(Expr.PropSet expr) {
        Object obj = null;
        LoxCallable setterFunc = null;
        // "super.property = rvalue"
        if (expr.object instanceof Expr.Super) {
            obj = environment.get("this", true, ((Expr.Super)expr.object).keyword);
            Stmt.Class classStmt = this.fnCall.getDecl().klass;
            Stmt.Class superClassStmt = classStmt.superClass;
            if (superClassStmt == null) {
                throw new RuntimeError(expr.property, "Couldn't find superclass! BUG");
            }
            LoxClass superKlass = classMap.get(superClassStmt.name.lexeme);
            setterFunc = superKlass.getSetter(expr.property.lexeme);
            if (setterFunc == null) {
                throw new RuntimeError(
                    expr.property,
                    "'super." + expr.property.lexeme + " = VALUE' needs to refer to a setter method"
                );
            }
        } else {
            obj = evaluate(expr.object);
            if (!(obj instanceof LoxInstance)) {
                throw new RuntimeError(expr.property, "Attempt to set property of non-instance");
            }
            setterFunc = ((LoxInstance)obj).getKlass().getSetter(expr.property.lexeme);
        }
        if (obj instanceof LoxInstance) {
            LoxInstance instance = (LoxInstance)obj;
            LoxCallable oldFnCall = this.fnCall;
            if (setterFunc != null) {
                this.fnCall = setterFunc;
            }
            Object value = evaluate(expr.value);
            instance.setProperty(expr.property.lexeme, value, this, setterFunc);
            if (setterFunc != null) {
                this.fnCall = oldFnCall;
            }
            return value;
        } else {
            throw new RuntimeError(expr.property, "Attempt to set property of non-instance");
        }
    }

    private Object evaluateCall(LoxCallable callable, List<Object> args, Token callToken) {
        LoxCallable oldFnCall = this.fnCall;
        try {
            this.fnCall = callable;
            return callable.call(this, args, callToken);
        } finally {
            this.fnCall = oldFnCall;
        }
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        int varIdx = 0;
        boolean useArrayElements = false;
        boolean canUseArray = false;
        int useArrayElementsIdx = 0;
        for (Token varTok : stmt.names) {
            if (!useArrayElements) {
                boolean isLastVar = (varIdx+1) == stmt.names.size();
                canUseArray = (!isLastVar) && ((varIdx+1) <= stmt.initializers.size());
            }
            Expr init = null;
            boolean initEvaled = false;
            if (stmt.initializers.size() >= (varIdx+1)) {
                init = stmt.initializers.get(varIdx);
            }
            if (init == null && !useArrayElements) {
                environment.define(varTok.lexeme, null);
            } else {
                if (!useArrayElements && canUseArray) {
                    value = evaluate(init);
                    initEvaled = true;
                    useArrayElements = (value instanceof LoxArray);
                }
                if (useArrayElements) {
                    LoxArray ary = (LoxArray)value;
                    Object val = ary.get(useArrayElementsIdx, varTok);
                    environment.define(varTok.lexeme, val);
                    useArrayElementsIdx++;
                } else {
                    if (!initEvaled) {
                        value = evaluate(init);
                        initEvaled = true;
                    }
                    environment.define(varTok.lexeme, value);
                }
            }
            varIdx++;
        }
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        Object value = evaluate(stmt.condition);
        if (isTruthy(value)) {
            execute(stmt.ifBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        Object value = evaluate(stmt.condition);
        boolean runtimeBreak = false;
        while (isTruthy(value)) {
            try {
                execute(stmt.body);
            } catch (RuntimeBreak err) {
                runtimeBreak = true;
            } catch (RuntimeContinue err) {
                // do nothing, continue
            }
            if (runtimeBreak) break;
            value = evaluate(stmt.condition);
        }
        return null;
    }

    @Override
    public Void visitForStmt(Stmt.For stmt) {
        Environment oldEnv = this.environment;
        // so that (var i = 0) initializer is not leaked to outer scope
        this.environment = new Environment(oldEnv);
        try {
            if (stmt.initializer != null) {
                execute(stmt.initializer);
            }
            boolean runtimeBreak = false;
            if (stmt.test != null) {
                Object evalBody = evaluate(stmt.test);
                while (isTruthy(evalBody)) {
                    try {
                        execute(stmt.body);
                    } catch (RuntimeBreak err) {
                        runtimeBreak = true;
                    } catch (RuntimeContinue err) {
                        // do nothing, continue
                    }
                    if (runtimeBreak) break;
                    if (stmt.increment != null) {
                    evaluate(stmt.increment);
                    }
                    evalBody = evaluate(stmt.test);
                }
            } else {
                while (true) {
                    try  {
                        execute(stmt.body);
                    } catch (RuntimeBreak err) {
                        runtimeBreak = true;
                    } catch (RuntimeContinue err) {
                        // do nothing, continue
                    }
                    if (runtimeBreak) break;
                    if (stmt.increment != null) {
                        evaluate(stmt.increment);
                    }
                }
            }
        } finally {
            this.environment = oldEnv;
        }
        return null;
    }

    @Override
    public Void visitTryStmt(Stmt.Try stmt) {
        int oldStackSz = stack.size();
        try {
            execute(stmt.tryBlock);
        } catch (RuntimeThrow throwErr) {
            Object throwVal = throwErr.value;
            for (Stmt.Catch catchStmt : stmt.catchStmts) {
                Object catchVal = evaluate(catchStmt.catchExpr);
                if (isCatchEqual(throwVal, catchVal)) {
                    unwindStack(oldStackSz);
                    Environment blockEnv = new Environment(this.environment);
                    if (catchStmt.catchVar != null) {
                        blockEnv.define(catchStmt.catchVar.name.lexeme, throwVal);
                    }
                    executeBlock(catchStmt.block.statements, new Environment(blockEnv));
                    return null;
                }
            }
            throw throwErr;
        }
        return null;
    }

    @Override
    public Void visitCatchStmt(Stmt.Catch stmt) {
        return null; // see visitTryStmt
    }

    @Override
    public Void visitThrowStmt(Stmt.Throw stmt) {
        Object throwValue = evaluate(stmt.throwExpr);
        stack.add(new StackFrame(stmt, stmt.keyword));
        throw new RuntimeThrow(throwValue, stmt.keyword);
    }


    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        throw new RuntimeBreak();
    }

    @Override
    public Void visitContinueStmt(Stmt.Continue stmt) {
        throw new RuntimeContinue();
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        boolean isInitializer = false;
        if (stmt.type == Parser.FunctionType.METHOD && stmt.name.lexeme.equals("init")) {
            isInitializer = true;
        }
        LoxCallable callable = new LoxFunction(stmt, this.environment, isInitializer);
        environment.defineFunction(stmt.name, stmt);
        environment.define(stmt.name.lexeme, callable);
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        environment.define(stmt.name.lexeme, null);
        LoxClass enclosingClass = this.currentClass;
        Map<String, LoxCallable> methods = new HashMap<>();
        LoxClass superKlass = null;
        if (stmt.superClass == null) {
            superKlass = classMap.get("Object");
        } else {
            superKlass = classMap.get(stmt.superClass.name.lexeme);
            if (superKlass == null) {
                throw new RuntimeError(stmt.name, "Couldn't resolve superclass! BUG");
            }
        }
        LoxClass klass = new LoxClass(stmt.name.lexeme, superKlass, methods);
        classMap.put(stmt.name.lexeme, klass);
        this.currentClass = klass;

        for (Stmt statement : stmt.body) {
            if (statement instanceof Stmt.Function) {
                Stmt.Function method = (Stmt.Function)statement;
                boolean isInitializer = method.type == Parser.FunctionType.INITIALIZER;
                boolean isStaticMethod = method.type == Parser.FunctionType.CLASS_METHOD;
                boolean isGetter = method.type == Parser.FunctionType.GETTER;
                boolean isSetter = method.type == Parser.FunctionType.SETTER;
                LoxCallable func = new LoxFunction(method, environment, isInitializer);
                if (isStaticMethod) {
                    klass.getKlass().methods.put(method.name.lexeme, func);
                } else if (isGetter) {
                    klass.getters.put(method.name.lexeme, func);
                } else if (isSetter) {
                    klass.setters.put(method.name.lexeme, func);
                } else {
                    methods.put(method.name.lexeme, func);
                }
            } else {
                throw new RuntimeError(stmt.name, "Unexpected statement in class body: " + statement.getClass().getName());
            }
        }

        environment.assign(stmt.name, klass, false);
        this.currentClass = enclosingClass;
        return null;
    }

    @Override
    public Object visitAnonFnExpr(Expr.AnonFn expr) {
        Stmt.Function stmt = new Stmt.Function(null, expr.formals, expr.body, Parser.FunctionType.FUNCTION, null);
        LoxCallable callable = new LoxFunction(stmt, this.environment, false);
        return callable;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (fnCall == null) {
            throw new RuntimeError(stmt.token, "Keyword 'return' can only be used inside function/method bodies");
        }
        Object value = null;
        if (stmt.expression != null) {
            value = evaluate(stmt.expression);
        }
        throw new RuntimeReturn(value);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private boolean isTruthy(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Boolean) return (boolean)obj;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        // nil is only equal to nil.
        if (a == null && b == null) return true;
        if (a == null) return false;
        if (a instanceof StringBuffer && b instanceof StringBuffer) {
            return ((StringBuffer)a).toString().equals(
                ((StringBuffer)b).toString()
            );
        }

        return a.equals(b);
    }

    private boolean isCatchEqual(Object thrown, Object caught) {
        if (isEqual(thrown, caught)) {
            return true;
        }
        if (thrown instanceof LoxInstance && caught instanceof LoxClass) {
            return ((LoxInstance)thrown).isA((LoxClass)caught);
        }
        return false;
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object a, Object b) {
        if (a instanceof Double && b instanceof Double) return;
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private String stringify(Object object) {
        if (object == null) return "nil";

        // Hack. Work around Java adding ".0" to integer-valued doubles.
        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    public Object nativeTypeof(Token tok, Object object) {
        if (object == null) { return "nil"; }
        if (object instanceof Boolean) { return "bool"; }
        if (object instanceof Double) { return "number"; }
        if (object instanceof StringBuffer) { return "string"; }
        if (object instanceof LoxClass) { return "class"; }
        if (object instanceof LoxInstance) { return "instance"; }
        if (object instanceof LoxCallable) { return "function"; }
        if (object instanceof LoxArray) { return "array"; }
        throw new RuntimeException("typeof() BUG, unknown type (" + object.getClass().getName() + ")!");
    }

    public Object nativeLen(Token tok, Object object) {
        if (object instanceof StringBuffer) { return (double)((StringBuffer)object).length(); }
        if (object instanceof LoxArray) { return ((LoxArray)object).length(); }
        if (object instanceof LoxCallable) { return (double)((LoxCallable)object).arity(); }
        if (object instanceof LoxInstance) {
            LoxInstance instance = (LoxInstance)object;
            Object methodOrProp = instance.getProperty("length", this);
            if (methodOrProp == null) {
                throw new RuntimeError(tok, "len() can only be used on Strings, Arrays, Functions and objects with the length property or method");
            }
            if (methodOrProp instanceof Double) {
                return (double)methodOrProp;
            }
            if (methodOrProp instanceof LoxCallable) {
                return ((LoxCallable)methodOrProp).call(this, new ArrayList<Object>(), tok);
            }
        }
        throw new RuntimeError(tok, "len() can only be used on Strings, Arrays, Functions and objects with the length property or method");
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    public void executeBlock(List<Stmt> stmts, Environment env) {
        Environment oldEnv = environment;
        try {
            this.environment = env;
            for (Stmt stmt : stmts) {
                execute(stmt);
            }
        } finally {
            this.environment = oldEnv;
        }
    }

    private Token tokenFromExpr(Expr expr) {
        Token tok = null;
        if (expr instanceof Expr.Binary) {
            tok = ((Expr.Binary)expr).operator;
        } else if (expr instanceof Expr.Logical) {
            tok = ((Expr.Logical)expr).operator;
        } else if (expr instanceof Expr.Unary) {
            tok = ((Expr.Unary)expr).operator;
        } else if (expr instanceof Expr.Variable) {
            tok = ((Expr.Variable)expr).name;
        } else if (expr instanceof Expr.Assign) {
            tok = ((Expr.Assign)expr).name;
        } else if (expr instanceof Expr.Call) {
            tok = tokenFromExpr(((Expr.Call)expr).left);
        } else if (expr instanceof Expr.Grouping) {
            tok = tokenFromExpr(((Expr.Grouping)expr).expression);
        } else if (expr instanceof Expr.Literal) {
            tok = null;
        } else if (expr instanceof Expr.PropAccess) {
            tok = ((Expr.PropAccess)expr).property;
        } else if (expr instanceof Expr.PropSet) {
            tok = ((Expr.PropSet)expr).property;
        } else if (expr instanceof Expr.This) {
            tok = ((Expr.This)expr).keyword;
        } else if (expr instanceof Expr.Super) {
            tok = ((Expr.Super)expr).keyword;
        } else if (expr instanceof Expr.AnonFn) {
            tok = ((Expr.AnonFn)expr).fun;
        } else if (expr instanceof Expr.Array) {
            tok = ((Expr.Array)expr).lbracket;
        } else if (expr instanceof Expr.IndexedGet) {
            tok = ((Expr.IndexedGet)expr).lbracket;
        } else if (expr instanceof Expr.IndexedSet) {
            tok = ((Expr.IndexedSet)expr).lbracket;
        } else if (expr instanceof Expr.SplatCall) {
            tok = tokenFromExpr(((Expr.SplatCall)expr).expression);
        }
        return tok;
    }

    public void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    public String stacktrace() {
        StringBuilder builder = new StringBuilder();
        while (!stack.empty()) {
            StackFrame frame = stack.pop();
            builder.append(frame.toString() + "\n");
        }
        builder.append("<main>");
        return builder.toString();
    }

    private void unwindStack(int size) {
        while (stack.size() > size) {
            stack.pop();
        }
    }
}
