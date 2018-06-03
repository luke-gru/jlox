package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.io.File;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private static class RuntimeBreak extends RuntimeException {}
    private static class RuntimeContinue extends RuntimeException {}
    public static class RuntimeThrow extends RuntimeException {
        final Object value;
        final Token keywordTok;
        RuntimeThrow(Object value, Token keywordTok) {
            super(null, null, false, false);
            this.value = value;
            this.keywordTok = keywordTok;
        }

        public String toString(Interpreter interp) {
            return interp.stringify(this.value);
        }
    }

    public static class RuntimeReturn extends RuntimeException {
        final Object value;
        RuntimeReturn(Object value) {
            super(null, null, true, true);
            this.value = value;
        }
    }
    // NOTE: should live in Lox land so users can catch it
    public static class LoadScriptError extends RuntimeException {
        public LoadScriptError(String msg) {
            super(msg);
        }
    }

    public String runningFile = null;
    public static Map<String, LoxInstance> staticStringPool = new HashMap<>();

    public final Map<Expr, Integer> locals = new HashMap<>();
    final Environment globals = new Environment();
    final Runtime runtime;
    public Environment environment = globals;
    public LoxCallable fnCall = null; // current function being executed
    private LoxClass currentClass = null; // current class being visited
    public Map<String, LoxClass> classMap = new HashMap<>();

    public HashMap<String, Object> options = null;
    public StringBuffer printBuf = null;
    public StringBuffer errorBuf = null;

    public Stack<StackFrame> stack = new Stack<>();
    public RuntimeException runtimeError = null;
    private Resolver resolver = null;
    public Parser parser = null;
    private String filename; // FIXME: unused
    public LoxInstance _this = null; // FIXME: unused
    private boolean inited = false;
    public Object lastValue;

    public Interpreter() {
        HashMap<String, Object> opts = new HashMap<>();
        opts.put("usePrintBuf", (Boolean)false);
        opts.put("useErrorBuf", (Boolean)false);
        opts.put("filename", null);
        this.options = opts;
        this.resolver = new Resolver(this);
        this.runtime = Runtime.create(globals, classMap);
    }

    public Interpreter(HashMap<String, Object> options) {
        this.options = options;
        if (this.options.get("usePrintBuf") == (Boolean)true) {
            this.printBuf = new StringBuffer();
        }
        // TODO: use
        if (this.options.get("useErrorBuf") == (Boolean)true) {
            this.errorBuf = new StringBuffer();
        }
        if (this.options.get("filename") != null) {
            this.filename = (String)this.options.get("filename");
        }
        this.resolver = new Resolver(this);
        this.runtime = Runtime.create(globals, classMap);
    }


    public boolean interpret(List<Stmt> statements) {
        if (!inited) {
            runtime.init(this);
            if (this.runningFile == null && Lox.initialScriptAbsolute != null) {
                setRunningFile(Lox.initialScriptAbsolute);
            }
            this.inited = true;
        }
        this.resolver.resolve(statements);
        if (this.resolver.hasErrors()) {
            return false;
        }
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
            return true;
        } catch (RuntimeError error) {
            this.runtimeError = error;
            System.err.println("==============");
            System.err.print("RuntimeError: ");
            Lox.runtimeError(error);
            System.err.println("Stacktrace:");
            System.err.println(stacktrace());
            System.err.println("==============");
        } catch (RuntimeThrow error) {
            this.runtimeError = error;
            System.err.println("==============");
            System.err.println("Uncaught error: " + error.toString(this));
            System.err.println("Stacktrace:");
            System.err.println(stacktrace());
            System.err.println("==============");
            Lox.hadRuntimeError = true;
        }
        return false;
    }

    public boolean interpret(String src) {
        if (!inited) {
            runtime.init(this);
            if (this.runningFile == null && Lox.initialScriptAbsolute != null) {
                setRunningFile(Lox.initialScriptAbsolute);
            }
            this.inited = true;
        }
        this.parser = Parser.newFromSource(src);
        this.parser.setNativeClassNames(this.runtime.nativeClassNames());
        List<Stmt> stmts = this.parser.parse();
        if (this.parser.getError() != null) {
            return false;
        }
        return interpret(stmts);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        // regular string
        if (expr.value instanceof StringBuffer) {
            LoxInstance string = createInstance("String", new ArrayList<Object>());
            ((StringBuffer)string.getHiddenProp("buf")).append(expr.value.toString());
            return string;
        // static (frozen) string
        } else if (expr.value instanceof String) {
            String staticStr = (String)expr.value;
            if (staticStringPool.containsKey(staticStr)) {
                return staticStringPool.get(staticStr);
            } else {
                LoxInstance string = createInstance("String", new ArrayList<Object>());
                ((StringBuffer)string.getHiddenProp("buf")).append(staticStr);
                string.freeze();
                staticStringPool.put(staticStr, string);
                return string;
            }
        } else {
            return expr.value;
        }
    }

    @Override
    public Object visitArrayExpr(Expr.Array expr) {
        List<Object> objs = new ArrayList<>();
        for (Expr el : expr.expressions) {
            objs.add(evaluate(el));
        }
        LoxClass arrayClass = classMap.get("Array");
        Token tok = tokenFromExpr(expr);
        // Construct the array instance and return it
        Object instance = evaluateCall(arrayClass, objs, tok);
        return instance;
    }

    @Override
    public Object visitIndexedGetExpr(Expr.IndexedGet expr) {
        Object obj = evaluate(expr.left);
        if (!(obj instanceof LoxInstance)) {
            throwLoxError("TypeError",
                "index access expr (expr[index]), expr must be an object, is: " +
                nativeTypeof(tokenFromExpr(expr.left), obj));
        }
        Object index = evaluate(expr.indexExpr);
        LoxInstance lhsInstance = (LoxInstance)obj;
        LoxCallable indexGetMeth = lhsInstance.getMethod("indexGet", this);
        if (indexGetMeth != null) {
            List<Object> indexValArgs = new ArrayList<>();
            indexValArgs.add(index);
            return evaluateCall(indexGetMeth, indexValArgs, tokenFromExpr(expr));
        }
        if (Runtime.isArray(obj)) {
            LoxInstance ary = Runtime.toInstance(obj);
            Token tok = tokenFromExpr(expr.left);
            List<Object> elements = (List<Object>)ary.getHiddenProp("ary");
            return elements.get(((Double)index).intValue());
        } else if (Runtime.isString(obj)) {
            LoxInstance strInstance = Runtime.toString(obj);
            StringBuffer strBuf = (StringBuffer)strInstance.getHiddenProp("buf");
            int start = ((Double)index).intValue();
            LoxInstance newInstance = createInstance("String", new ArrayList<Object>());
            StringBuffer slicedBuf = new StringBuffer(strBuf.substring(start, start+1));
            newInstance.setHiddenProp("buf", slicedBuf);
            return newInstance;
        } else if (obj instanceof LoxInstance) {
            LoxInstance instance = (LoxInstance)obj;
            if (!Runtime.isString(index)) {
                throwLoxError("TypeError",
                    "object[index], index must be a string, is: " +
                    nativeTypeof(tokenFromExpr(expr.indexExpr), index));
            }
            LoxInstance strIndexInst = Runtime.toString(index);
            StringBuffer strIndex = (StringBuffer)strIndexInst.getHiddenProp("buf");
            return instance.getProperty(strIndex.toString(), this);
        } else {
            throw new RuntimeException("unreachable");
        }
    }

    @Override
    public Object visitIndexedSetExpr(Expr.IndexedSet expr) {
        Object obj = evaluate(expr.left);
        if (!(Runtime.isInstance(obj))) {
            throwLoxError("TypeError",
                "indexed set expr (expr[index] = rval), expr must be an Object, is: " +
                nativeTypeof(tokenFromExpr(expr.left), obj));
        }
        Object index = evaluate(expr.indexExpr);
        Object val = evaluate(expr.value);
        LoxInstance lhsInstance = (LoxInstance)obj;
        LoxCallable indexSetMeth = lhsInstance.getMethod("indexSet", this);
        if (indexSetMeth != null) {
            List<Object> indexValArgs = new ArrayList<>();
            indexValArgs.add(index);
            indexValArgs.add(val);
            return evaluateCall(indexSetMeth, indexValArgs, tokenFromExpr(expr));
        }
        if (Runtime.isArray(obj)) {
            LoxInstance ary = (LoxInstance)obj;
            Token tok = tokenFromExpr(expr.left);
            List<Object> elements = (List<Object>)ary.getHiddenProp("ary");
            // FIXME: check array OOB access!
            elements.set(((Double)index).intValue(), val);
            return val;
        } else if (Runtime.isString(obj)) {
            LoxInstance strBufInst = (LoxInstance)obj;
            StringBuffer strBuf = (StringBuffer)strBufInst.getHiddenProp("buf");
            if (!Runtime.isString(val)) {
                throwLoxError("TypeError",
                    "string[index]=value, value must be a String, is: " +
                    nativeTypeof(tokenFromExpr(expr.value), val));
            }
            LoxInstance strBufValInst = (LoxInstance)val;
            StringBuffer strBufVal = (StringBuffer)strBufValInst.getHiddenProp("buf");
            int start = ((Double)index).intValue();
            int end = start + strBufVal.length();
            if (start > strBuf.length()) { // FIXME: very slow!
                int len = strBuf.length();
                while (len < start) {
                    strBuf.append(" ");
                    len++;
                }
            } else {
                strBuf.delete(start, end);
            }
            strBuf.insert(start, strBufVal.toString());
            return val;
        } else if (obj instanceof LoxInstance) {
            LoxInstance instance = (LoxInstance)obj;
            LoxInstance indexInst = (LoxInstance)index;
            StringBuffer indexBuf = (StringBuffer)indexInst.getHiddenProp("buf");
            String indexStr = indexBuf.toString();
            // FIXME: should look in singleton class, etc, just like getters
            // and methods!
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
            case MINUS: {
                if (Runtime.isInstance(left)) {
                    LoxInstance leftInst = Runtime.toInstance(left);
                    LoxCallable diffMeth = leftInst.getMethod("opDiff", this);
                    if (diffMeth != null) {
                        List<Object> diffArgs = new ArrayList<>();
                        diffArgs.add(right);
                        return evaluateCall(diffMeth, diffArgs, tokenFromExpr(expr));
                    } else {
                        System.err.println("opDiff not found for class " + leftInst.getKlass().getName());
                        // raise error: method opDiff not found
                    }
                }
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            }
            case SLASH: {
                if (Runtime.isInstance(left)) {
                    LoxInstance leftInst = Runtime.toInstance(left);
                    LoxCallable divMeth = leftInst.getMethod("opDiv", this);
                    if (divMeth != null) {
                        List<Object> divArgs = new ArrayList<>();
                        divArgs.add(right);
                        return evaluateCall(divMeth, divArgs, tokenFromExpr(expr));
                    } else {
                        System.err.println("opDiv not found for class " + leftInst.getKlass().getName());
                        // raise error: method opDiv not found
                    }
                }
                checkNumberOperands(expr.operator, left, right);
                if ((double)right == 0.0) {
                    throw new RuntimeError(expr.operator, "division by 0");
                }
                return (double)left / (double)right;
            }
            case STAR: {
                if (Runtime.isInstance(left)) {
                    LoxInstance leftInst = Runtime.toInstance(left);
                    LoxCallable mulMeth = leftInst.getMethod("opMul", this);
                    if (mulMeth != null) {
                        List<Object> mulArgs = new ArrayList<>();
                        mulArgs.add(right);
                        return evaluateCall(mulMeth, mulArgs, tokenFromExpr(expr));
                    } else {
                        System.err.println("opMul not found for class " + leftInst.getKlass().getName());
                        // raise error: method opMul not found
                    }
                }
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            }
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                } else if (Runtime.isInstance(left)) {
                    LoxInstance leftInst = Runtime.toInstance(left);
                    LoxCallable addMeth = leftInst.getMethod("opAdd", this);
                    if (addMeth != null) {
                        List<Object> addArgs = new ArrayList<>();
                        addArgs.add(right);
                        return evaluateCall(addMeth, addArgs, tokenFromExpr(expr));
                    } else {
                        System.err.println("opAdd not found for class " + leftInst.getKlass().getName());
                        // raise error: method opAdd not found
                    }
                }

                throw new RuntimeError(expr.operator, "operands for '+' must be two numbers or two Strings, LHS=" +
                        nativeTypeof(tokenFromExpr(expr.left), left) + ", RHS=" +
                        nativeTypeof(tokenFromExpr(expr.right), right));
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
        unreachable("visitBinaryExpr");
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
        Object objInstance = environment.get("this", true, expr.keyword);
        // This should never happen!
        if (objInstance == null || !(objInstance instanceof LoxInstance)) {
            throw new RuntimeError(expr.keyword, "'super' keyword couldn't resolve 'this'! BUG");
        }
        LoxInstance instance = Runtime.toInstance(objInstance);
        LoxClass lookupClassStart = null;
        if (Runtime.isClass(instance)) {
            lookupClassStart = ((LoxClass)instance).getSuper().getSingletonKlass();
        } else {
            lookupClassStart = instance.getKlass().getSuper();
        }
        // FIXME: throw a better error (lox NameError, maybe?)
        if (lookupClassStart == null) {
            throw new RuntimeError(expr.keyword, "'super' keyword couldn't find superclass!");
        }
        // FIXME: what if `this.super = rval`, should look for setter method!
        Object value = instance.getMethodOrGetterProp(expr.property.lexeme, lookupClassStart, this);
        if (value == null) {
            // FIXME: throw a better error (lox NameError, maybe?)
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
            List<Object> args = null;
            if (callExpr.args.size() == 0) {
                args = LoxUtil.EMPTY_ARGS; // small optimization
            } else {
                args = new ArrayList<>();
            }

            // need to evaluate args first before seeing if the function can
            // accept that number of arguments because splat arguments `fnCall(*args)` can
            // evaluate to multiple arguments at runtime. See below.
            for (Expr expr : callExpr.args) {
                if (expr instanceof Expr.SplatCall) {
                    Object ary = evaluate(((Expr.SplatCall)expr).expression);
                    if (!Runtime.isArray(ary)) {
                        throw new RuntimeError(tokenFromExpr(callExpr), "Splat arg expression must evaluate to an array object");
                    }
                    LoxInstance aryInstance = (LoxInstance)ary;
                    List<Object> elements = (List<Object>)aryInstance.getHiddenProp("ary");
                    args.addAll(elements);
                } else {
                    args.add(evaluate(expr));
                }
            }

            LoxCallable callable = (LoxCallable)obj;
            if (!Runtime.acceptsNArgs(callable, args.size())) {
                int arityMin = callable.arityMin();
                int arityMax = callable.arityMax();
                String expectedNStr;
                if (arityMax < 0) {
                    expectedNStr = String.valueOf(arityMin) + " to n";
                } else if (arityMin == arityMax) {
                    expectedNStr = "exactly " + arityMin;
                } else {
                    expectedNStr = String.valueOf(arityMin) + " to " + String.valueOf(arityMax);
                }
                int actualN = args.size();
                String actualNStr = String.valueOf(actualN);
                // TODO: add token to thrown error, so it will appear in stacktraces
                // tokenfromExpr(callExpr.left)
                throwLoxError("ArgumentError",
                    "Function <" +
                    callable.getName() + "> called with wrong number of arguments. Expected " +
                    expectedNStr + ", got " + actualNStr + "."
                );
            }
            return evaluateCall(callable, args, tokenFromExpr(callExpr.left));
        } else {
            Token tok = tokenFromExpr(callExpr);
            throw new RuntimeError(tok, "Undefined function or method " + tok.lexeme);
        }
    }

    // see visitCallExpr
    @Override
    public Object visitSplatCallExpr(Expr.SplatCall expr) {
        return null;
    }

    // PropGet
    @Override
    public Object visitPropAccessExpr(Expr.PropAccess expr) {
        Object obj = evaluate(expr.left);
        if (obj instanceof LoxInstance) {
            LoxInstance instance = (LoxInstance)obj;
            Object value = instance.getProperty(expr.property.lexeme, this);
            return value;
        } else {
            throw new RuntimeError(expr.property, "Attempt to access property of non-instance, is: " + this.nativeTypeof(tokenFromExpr(expr.left), obj));
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
            Object superKlassObj = classStmt.superClass;
            if (superKlassObj == null) {
                throw new RuntimeError(expr.property, "Couldn't find superclass! BUG");
            }
            LoxClass superKlass = (LoxClass)superKlassObj;
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

    public Object evaluateCall(LoxCallable callable, List<Object> args, Token callToken) {
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
        println(stringify(value));
        return null;
    }

    private void println(String val) {
        if (this.printBuf != null) {
            this.printBuf.append(val + "\n");
        } else {
            System.out.println(val);
        }
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
                    useArrayElements = Runtime.isArray(value);
                }
                if (useArrayElements) {
                    LoxInstance aryValue = (LoxInstance)value;
                    List<Object> aryElements = (List<Object>)aryValue.getHiddenProp("ary");
                    // TODO: check OOB array access
                    Object val = aryElements.get(useArrayElementsIdx);
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
    public Void visitForeachStmt(Stmt.Foreach stmt) {
        Environment oldEnv = this.environment;
        Object evalObj = evaluate(stmt.obj);
        boolean useNextIter = false;
        Object iterMethod = null;
        Object nextIterMethod = null;
        if (!Runtime.isArray(evalObj) && (evalObj instanceof LoxInstance)) {
            LoxInstance instance = (LoxInstance)evalObj;
            iterMethod = instance.getMethod("iter", this);
            nextIterMethod = instance.getMethod("nextIter", this);
            if (iterMethod == null && nextIterMethod == null) {
                throwLoxError("TypeError",
                    "foreach expr must be an Array object or object that responds to " +
                    "iter() or nextIter(), is: " + stringify(instance)
                );
            }
            if (iterMethod != null) {
                LoxCallable iterCallable = (LoxCallable)iterMethod;
                evalObj =  evaluateCall(iterCallable, new ArrayList<Object>(), tokenFromExpr(stmt.obj));
                if (Runtime.isInstance(evalObj) && !Runtime.isArray(evalObj)) {
                    nextIterMethod = ((LoxInstance)evalObj).getMethod("nextIter", this);
                    if (nextIterMethod == null) {
                        throwLoxError("TypeError",
                            "foreach expr returned from iter() must be an Array or respond to nextIter()"
                        );
                    }
                    useNextIter = true;
                } else if (!Runtime.isArray(evalObj)) {
                    throwLoxError("TypeError",
                        "foreach expr returned from iter() must be an Array or respond to nextIter()"
                    );
                }
            } else {
                useNextIter = true;
            }
        }
        LoxInstance instance = (LoxInstance)evalObj;
        int len = 0;
        List<Object> elements = new ArrayList<Object>();
        if (!useNextIter) {
            elements = (List<Object>)instance.getHiddenProp("ary");
            len = elements.size();
        }
        // so that (foreach i, j in expr()), variables are not leaked to outer scope
        this.environment = new Environment(oldEnv);
        int numVars = stmt.variables.size();
        int i = 0;
        try {
            while (true) {
                if (!useNextIter && i >= len) {
                    break;
                }
                // FIXME: check OOB array access
                Object val;
                if (useNextIter) {
                    val = evaluateCall((LoxCallable)nextIterMethod, LoxUtil.EMPTY_ARGS, null);
                    if (val == null) { break; }
                } else {
                    val = elements.get(i);
                    i++;
                }
                if (numVars > 1 && (!Runtime.isArray(val))) {
                    throwLoxError("TypeError", "'foreach' element must be Array object when given more than 1 variable in foreach loop (" +
                        String.valueOf(numVars) + " variables given). Element class: " + this.nativeTypeof(null, val)
                    );
                }
                if (numVars > 1) {
                    LoxInstance valObj = (LoxInstance)val;
                    List<Object> valElements = (List<Object>)valObj.getHiddenProp("ary");
                    for (int j = 0; j < numVars; j++) {
                        Token elemVar = stmt.variables.get(j);
                        // FIXME: check OOB array access
                        environment.define(elemVar.lexeme, valElements.get(j));
                    }
                } else {
                    environment.define(stmt.variables.get(0).lexeme, val);
                }
                execute(stmt.body);
            }
        } finally {
            this.environment = oldEnv;
        }
        return null;
    }

    @Override
    public Void visitInStmt(Stmt.In stmt) {
        Object obj = evaluate(stmt.object);
        if (!Runtime.isInstance(obj)) {
            Token tok = tokenFromExpr(stmt.object);
            throw new RuntimeError(tok,
                    "Expression given to 'in' must evaluate to an instance or class, is: " +
                    nativeTypeof(tok, obj));
        }
        LoxInstance objInst = Runtime.toInstance(obj);
        LoxInstance oldThis = this._this;
        LoxClass oldCurClass = this.currentClass;
        Environment outerEnv = this.environment;

        this._this = objInst;
        LoxClass thisKlass = null;
        if (Runtime.isClass(objInst)) {
            thisKlass = (LoxClass)objInst;
        } else {
            thisKlass = objInst.getSingletonKlass();
        }
        this.currentClass = thisKlass;

        this.environment = new Environment(outerEnv);
        environment.define("this", objInst);
        for (Stmt statement : stmt.body) {
            execute(statement);
        }

        this._this = oldThis;
        this.currentClass = oldCurClass;
        this.environment = outerEnv;
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
        Stmt.Function func = (Stmt.Function)stmt;
        boolean isInitializer = func.type == Parser.FunctionType.INITIALIZER;
        boolean isStaticMethod = func.type == Parser.FunctionType.CLASS_METHOD;
        boolean isGetter = func.type == Parser.FunctionType.GETTER;
        boolean isSetter = func.type == Parser.FunctionType.SETTER;
        boolean isInstanceMethod = func.type == Parser.FunctionType.METHOD;
        LoxClass klass = this.currentClass;
        LoxCallable callable = new LoxFunction(func, this.environment, isInitializer);
        if (isStaticMethod) {
            klass.getSingletonKlass().methods.put(func.name.lexeme, callable);
        } else if (isGetter) {
            klass.getters.put(func.name.lexeme, callable);
        } else if (isSetter) {
            klass.setters.put(func.name.lexeme, callable);
        } else  if (isInstanceMethod){
            klass.methods.put(func.name.lexeme, callable);
        } else {
            environment.defineFunction(stmt.name, stmt);
            environment.define(stmt.name.lexeme, callable);
        }
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        LoxClass enclosingClass = this.currentClass;
        String namePrefix = "";
        if (enclosingClass != null) {
            namePrefix = enclosingClass.getName() + ".";
        }
        String newClassNameFull = namePrefix + stmt.name.lexeme;
        LoxClass existingClass = classMap.get(newClassNameFull);
        LoxClass klass = existingClass;
        if (klass == null) { // new class definition
            environment.define(stmt.name.lexeme, null);
            LoxClass superKlass = null;
            if (stmt.superClassVar == null) {
                superKlass = classMap.get("Object");
                LoxUtil.Assert(superKlass != null);
            } else {
                Object superKlassObj = evaluate(stmt.superClassVar);
                if (superKlassObj == null || !Runtime.isClass(superKlassObj)) {
                    throw new RuntimeError(stmt.name,
                        "Couldn't resolve superclass " + stmt.superClassVar.name.lexeme + " for class '" + stmt.name.lexeme +
                        "'! BUG");
                }
                superKlass = (LoxClass)superKlassObj;
            }
            stmt.superClass = superKlass;
            Map<String, LoxCallable> methods = new HashMap<>();
            klass = new LoxClass(newClassNameFull, superKlass, methods);
            classMap.put(newClassNameFull, klass);
            environment.assign(stmt.name, klass, false);
        }
        this.currentClass = klass;
        Environment outerEnv = environment;
        this.environment = new Environment(outerEnv);
        this.environment.define("this", klass);

        if (enclosingClass != null) {
            Object propVal = enclosingClass.getProperty(stmt.name.lexeme, this);
            if (propVal != null) {
                System.err.println("Warning: Overwriting property " + stmt.name.lexeme +
                    " on class " + enclosingClass.getName() +
                    " due to nested class declaration");
            }
            enclosingClass.setProperty(stmt.name.lexeme, klass, this, null);
        }

        for (Stmt statement : stmt.body) {
            execute(statement);
        }

        this.currentClass = enclosingClass;
        this.environment = outerEnv;
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

    public Object evaluate(Expr expr) {
        this.lastValue = expr.accept(this);
        return lastValue;
    }

    public boolean isTruthy(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Boolean) return (boolean)obj;
        return true;
    }

    // used for '==', not Object#equals(), which is true only when they're the same instance (more strict)
    public boolean isEqual(Object a, Object b) {
        // nil is only equal to nil.
        if (a == null && b == null) return true;
        if (a == null) return false;
        if (b == null) return false;
        if (Runtime.isString(a) && Runtime.isString(b)) {
            LoxInstance aObj = Runtime.toString(a);
            StringBuffer aBuf = (StringBuffer)aObj.getHiddenProp("buf");
            LoxInstance bObj = Runtime.toString(b);
            StringBuffer bBuf = (StringBuffer)bObj.getHiddenProp("buf");
            return aBuf.toString().equals(bBuf.toString());
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

    public String stringify(Object object) {
        if (object == null) return "nil";

        // Hack. Work around Java adding ".0" to integer-valued doubles.
        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        if (Runtime.isInstance(object) && !(Runtime.isString(object) || Runtime.isClass(object))) {
            LoxInstance instance = Runtime.toInstance(object);
            LoxCallable toStringMeth = instance.getMethod("toString", instance.getKlass(), this);
            if (toStringMeth != null) {
                object = evaluateCall(toStringMeth, LoxUtil.EMPTY_ARGS, null);
            }
        }

        if (Runtime.isString(object)) {
            LoxInstance instance = Runtime.toInstance(object);
            return ((StringBuffer)instance.getHiddenProp("buf")).toString();
        }

        return object.toString();
    }

    public String nativeTypeof(Token tok, Object object) {
        if (object == null) { return "nil"; }
        if (object instanceof Boolean) { return "bool"; }
        if (Runtime.isNumber(object)) { return "number"; }
        if (Runtime.isString(object)) { return "string"; }
        if (object instanceof LoxClass) { return "class"; }
        if (Runtime.isArray(object)) { return "array"; }
        if (object instanceof LoxInstance) { return "instance"; }
        if (object instanceof LoxCallable) { return "function"; }
        throw new RuntimeException("typeof() BUG, unknown type (" + object.getClass().getName() + ")!");
    }

    public Object nativeLen(Token tok, Object object) {
        if (object instanceof LoxCallable) { return (double)((LoxCallable)object).arityMin(); }
        if (object instanceof LoxInstance) {
            LoxInstance instance = (LoxInstance)object;
            Object methodOrProp = instance.getProperty("length", this);
            if (methodOrProp == null) {
                throw new RuntimeError(tok, "len() can only be used on Strings, Arrays, Functions and objects with a 'length' method, getter or property");
            }
            if (methodOrProp instanceof Double) {
                return (double)methodOrProp;
            }
            if (methodOrProp instanceof LoxCallable) {
                return evaluateCall((LoxCallable)methodOrProp, new ArrayList<Object>(), tok);
            }
        }
        throw new RuntimeError(tok, "len() can only be used on Strings, Arrays, Functions and objects with a 'length' method, getter or property");
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
        int sz = stack.size();
        int i = sz-1;
        while (i >= 0) {
            StackFrame frame = stack.get(i);
            builder.append(frame.toString() + "\n");
            i--;
        }
        builder.append("<main>");
        return builder.toString();
    }

    // stacktrace lines, most recent call first in list
    public List<String> stacktraceLines() {
        List<String> ret = new ArrayList<>();
        int sz = stack.size();
        int i = sz-1;
        while (i >= 0) {
            StackFrame frame = stack.get(i);
            ret.add(frame.toString());
            i--;
        }
        ret.add("<main>");
        return ret;
    }

    public void clearStack() {
        this.stack.clear();
    }

    public boolean hasUncaughtException() {
        return this.runtimeError != null &&
            (this.runtimeError instanceof RuntimeThrow);
    }

    public boolean hasRuntimeError() {
        return this.runtimeError != null &&
            (this.runtimeError instanceof RuntimeError);
    }

    public boolean hasParseError() {
        return (this.parser != null && this.parser.getError() != null) ||
            this.resolver.hasErrors();
    }

    private void unwindStack(int size) {
        while (stack.size() > size) {
            stack.pop();
        }
    }

    public LoxInstance createInstance(String className, List<Object> initArgs) {
        LoxClass klass = classMap.get(className);
        if (klass == null) {
            throw new RuntimeException("class " + className + " doesn't exist!");
        }
        Object instance = evaluateCall(klass, initArgs, null);
        return Runtime.toInstance(instance);
    }

    public LoxInstance createInstance(String className) {
        return createInstance(className, LoxUtil.EMPTY_ARGS);
    }

    public Object callMethod(String methodName, LoxInstance instance, List<Object> args) {
        LoxCallable method = instance.getMethod(methodName, instance.getKlass(), this); // FIXME: doesn't look in singleton class
        // TODO: raise error
        if (method == null) {
            System.err.println("Error: method not found: " + methodName);
            return null;
        }
        return evaluateCall(method, args, null);
    }

    private List<String> loadPathJavaStrings() {
        List<String> ret = new ArrayList<String>();
        LoxInstance loxInst = Runtime.toInstance(globals.getGlobal("LOAD_PATH"));
        List<Object> loxAry = (List<Object>)loxInst.getHiddenProp("ary");
        for (Object obj : loxAry) {
            if (Runtime.isString(obj)) {
                ret.add(Runtime.toJavaString(Runtime.toString(obj)));
            }
        }
        return ret;
    }

    public boolean loadScriptOnce(String fname) {
        String fullPath = null;
        if ((fullPath = Lox.fullPathToScript(fname, loadPathJavaStrings())) != null) {
            if (Lox.hasLoadedScriptOnce(fullPath)) {
                return false;
            }
            String oldFile = this.runningFile;
            try {
                setRunningFile(fullPath);
                Lox.loadScriptOnceAdd(fname, fullPath);
                evalFile(fullPath);
            } finally {
                setRunningFile(oldFile);
            }
            return true;
        } else {
            throw new LoadScriptError("cannot load file '" + fname + "' (it's not in the load path).");
        }
    }

    public boolean loadScript(String fname) {
        String fullPath = null;
        if ((fullPath = Lox.fullPathToScript(fname, loadPathJavaStrings())) != null) {
            String oldFile = this.runningFile;
            try {
                setRunningFile(fullPath);
                Lox.loadScriptAdd(fname, fullPath);
                evalFile(fullPath);
            } finally {
                setRunningFile(oldFile);
            }
            return true;
        } else {
            throw new LoadScriptError("cannot load file '" + fname + "' (it's not in the load path).");
        }
    }

    public void setRunningFile(String absPath) {
        if (absPath == null) { return; }
        if (absPath.charAt(0) == '(') { // Example: "(eval)" when in evaluated string
            globals.define("__DIR__", Runtime.createString("", this));
            globals.define("__FILE__", Runtime.createString(absPath, this));
        } else {
            File f = new File(absPath);
            String dirPath = f.getAbsoluteFile().getParentFile().getAbsolutePath();
            globals.define("__DIR__", Runtime.createString(dirPath, this));
            globals.define("__FILE__", Runtime.createString(absPath, this));
        }
        this.runningFile = absPath;
    }

    public void throwLoxError(String errClass, String errMsg) throws RuntimeThrow {
        LoxCallable errConstructor = classMap.get(errClass);
        List<Object> args = new ArrayList<>();
        args.add(Runtime.createString(errMsg, this));
        Object objInstance = evaluateCall(errConstructor, args, null);
        throw new RuntimeThrow(objInstance, null);
    }

    private void evalFile(String fullPath) {
        Environment oldEnv = this.environment;
        try {
            String src = LoxUtil.readFile(fullPath);
            this.environment = globals;
            interpret(src);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } finally {
            this.environment = oldEnv;
        }
    }

    private void unreachable(String msg) {
        throw new RuntimeException("unreachable: " + msg);
    }

}
