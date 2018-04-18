package com.craftinginterpreters.lox;

class StackFrame {
    final Expr.Call callExpr;
    final Stmt.Function fnStmt;

    StackFrame(Expr.Call callExpr, Stmt.Function fnStmt) {
        this.callExpr = callExpr;
        this.fnStmt = fnStmt;
    }
}
