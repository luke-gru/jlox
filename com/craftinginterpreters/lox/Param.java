package com.craftinginterpreters.lox;

// function parameter
class Param {
    final Token token;
    final Expr defaultVal;
    final boolean isSplatted;

    Param(Token tok, Expr defaultVal, boolean isSplatted) {
        this.token = tok;
        this.defaultVal = defaultVal;
        this.isSplatted = isSplatted;
    }

    String varName() {
        return token.lexeme;
    }

    boolean hasDefaultValue() {
        return defaultVal != null;
    }

    boolean mustReceiveArgument() {
        return !isSplatted && !hasDefaultValue();
    }


}
