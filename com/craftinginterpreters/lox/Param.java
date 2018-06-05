package com.craftinginterpreters.lox;

// function parameter
class Param {
    final Token token;
    final Expr defaultVal;
    final boolean isSplatted;
    final boolean isKwarg;

    Param(Token tok, Expr defaultVal, boolean isSplatted, boolean isKwarg) {
        this.token = tok;
        this.defaultVal = defaultVal;
        this.isSplatted = isSplatted;
        this.isKwarg = isKwarg;
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
