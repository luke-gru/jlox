package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    public final Interpreter interpreter;
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();
    public List<String> errorBuf = new ArrayList<>();
    private List<String> nativeClassNames = null;

    private Stmt.Class currentClass = null;
    private Stmt.In currentIn = null;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitArrayExpr(Expr.Array expr) {
        resolveExprs(expr.expressions);
        return null;
    }

    @Override
    public Void visitIndexedGetExpr(Expr.IndexedGet expr) {
        resolve(expr.left);
        resolve(expr.indexExpr);
        return null;
    }

    @Override
    public Void visitIndexedSetExpr(Expr.IndexedSet expr) {
        resolve(expr.left);
        resolve(expr.indexExpr);
        resolve(expr.value);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        // do nothing
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr) {
        return null;
    }

    @Override
    public Void visitSplatCallExpr(Expr.SplatCall expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
            error(expr.name, "Cannot read local variable in its own initializer.");
        }
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.left);
        resolveExprs(expr.args);
        return null;
    }

    @Override
    public Void visitPropAccessExpr(Expr.PropAccess expr) {
        resolve(expr.left);
        return null;
    }

    @Override
    public Void visitPropSetExpr(Expr.PropSet expr) {
        resolve(expr.object);
        resolve(expr.value);
        return null;
    }

    @Override
    public Void visitAnonFnExpr(Expr.AnonFn expr) {
        beginScope();
        for (Param param : expr.formals) {
            declare(param.token);
            define(param.token);
        }
        resolve(expr.body); // Stmt.Block
        endScope();
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        for (Token varTok : stmt.names) {
            declare(varTok);
        }
        for (Expr init : stmt.initializers) {
            resolve(init);
        }
        for (Token varTok : stmt.names) {
            define(varTok);
        }
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.ifBranch);
        if (stmt.elseBranch != null) {
            resolve(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    @Override
    public Void visitForStmt(Stmt.For stmt) {
        beginScope();
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        if (stmt.test != null) {
            resolve(stmt.test);
        }
        if (stmt.increment != null) {
            resolve(stmt.increment);
        }
        resolve(stmt.body);
        endScope();
        return null;
    }

    @Override
    public Void visitTryStmt(Stmt.Try stmt) {
        resolve(stmt.tryBlock);
        for (Stmt.Catch catchStmt : stmt.catchStmts) {
            resolve(catchStmt);
        }
        return null;
    }

    @Override
    public Void visitCatchStmt(Stmt.Catch stmt) {
        resolve(stmt.catchExpr);
        if (stmt.catchVar != null) {
            beginScope();
            declare(stmt.catchVar.name);
            define(stmt.catchVar.name);
        }
        resolve(stmt.block);
        if (stmt.catchVar != null) {
            endScope();
        }
        return null;
    }

    @Override
    public Void visitThrowStmt(Stmt.Throw stmt) {
        resolve(stmt.throwExpr);
        return null;
    }

    @Override
    public Void visitContinueStmt(Stmt.Continue stmt) {
        // do nothing
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        // do nothing
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);
        resolveFunction(stmt);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (stmt.expression != null) {
            resolve(stmt.expression);
        }
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        declare(stmt.name);
        define(stmt.name);
        if (stmt.superClassVar != null) {
            resolve(stmt.superClassVar);
        }

        Stmt.Class enclosingClass = this.currentClass;
        this.currentClass = stmt;

        beginScope();
        scopes.peek().put("this", true);
        resolve(stmt.body);
        endScope();

        this.currentClass = enclosingClass;

        return null;
    }

    @Override
    public Void visitInStmt(Stmt.In stmt) {
        resolve(stmt.object);
        Stmt.In enclosingIn = this.currentIn;
        this.currentIn = stmt;

        beginScope();
        scopes.peek().put("this", true);
        resolve(stmt.body);
        endScope();
        this.currentIn = enclosingIn;

        return null;
    }

    @Override
    public Void visitForeachStmt(Stmt.Foreach stmt) {
        resolve(stmt.obj);
        beginScope();
        for (Token var : stmt.variables) {
            declare(var);
            define(var);
        }
        resolve(stmt.body);
        endScope();
        return null;
    }

    // API function
    public void resolve(List<Stmt> stmts) {
        if (nativeClassNames == null) {
            setNativeClassNames();
        }
        for (Stmt stmt : stmts) {
            resolve(stmt);
        }
    }

    public boolean hasErrors() {
        return this.errorBuf.size() > 0;
    }

    private void resolveExprs(List<Expr> exprs) {
        for (Expr expr : exprs) {
            resolve(expr);
        }
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    private void resolveFunction(Stmt.Function stmt) {
        beginScope();
        for (Param param : stmt.formals) {
            declare(param.token);
            define(param.token);
        }
        resolve(stmt.body);
        endScope();
    }

    private void beginScope() {
        scopes.push(new HashMap<String, Boolean>());
    }

    private void endScope() {
        scopes.pop();
    }

    private void declare(Token name) {
        if (scopes.isEmpty()) return;
        if (scopes.peek().containsKey(name.lexeme)) {
            error(name, "cannot redeclare variable " + name.lexeme + ".");
        }
        scopes.peek().put(name.lexeme, false);
    }

    private void define(Token name) {
        if (scopes.isEmpty()) return;
        scopes.peek().put(name.lexeme, true);
    }

    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
    }

    private void error(Token tok, String msg) {
        Lox.error(tok, msg);
        this.errorBuf.add(msg);
    }

    private void setNativeClassNames() {
        this.nativeClassNames = interpreter.runtime.nativeClassNames();
    }
}
