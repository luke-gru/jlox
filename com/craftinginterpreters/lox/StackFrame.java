package com.craftinginterpreters.lox;

class StackFrame {
    final Stmt stmt;
    final Token token;

    StackFrame(Stmt stmt, Token tok) {
        this.stmt = stmt;
        this.token = tok;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (stmt instanceof Stmt.Throw) {
            builder.append("<throw>");
        } else if (stmt instanceof Stmt.Function) {
            Stmt.Function func = (Stmt.Function)stmt;
            String funcName = func.name == null ? "(anon)" : func.name.lexeme;
            builder.append("<fun " + funcName + ">");
        } else {
            throw new RuntimeException("bad statement class given to stackframe. BUG");
        }
        if (token != null) {
            builder.append(" at line " + token.line + ".");
        }
        return builder.toString();
    }
}
