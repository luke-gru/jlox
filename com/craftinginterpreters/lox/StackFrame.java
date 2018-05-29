package com.craftinginterpreters.lox;

class StackFrame {
    final Object stmtOrCallable;
    final Token token;

    StackFrame(Object stmtOrCallable, Token tok) {
        this.stmtOrCallable = stmtOrCallable;
        this.token = tok;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (stmtOrCallable instanceof Stmt.Throw) {
            builder.append("<!throw>");
        } else if (stmtOrCallable instanceof Stmt.Function) {
            Stmt.Function func = (Stmt.Function)stmtOrCallable;
            // TODO: format the function name different if it's a method, the function type, etc.
            String funcName = null;
            if (func.name != null) {
                funcName = func.name.lexeme;
            } else {
                funcName = "(anon)";
            }
            builder.append("<" + funcName + ">");
        } else if (stmtOrCallable instanceof LoxCallable) {
            LoxCallable func = (LoxCallable)stmtOrCallable;
            builder.append(func.toString());
        } else {
            throw new RuntimeException("bad class given to stackframe. " +
                "Must be Throw or Callable, is: " +
                stmtOrCallable.getClass().getName() +  ". BUG");
        }
        if (token != null) {
            builder.append(" at line " + token.line + ".");
        }
        return builder.toString();
    }
}
