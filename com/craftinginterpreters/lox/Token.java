package com.craftinginterpreters.lox;

class Token {
  TokenType type;
  String lexeme;
  final Object literal;
  final String file;
  final int line;

  Token(TokenType type, String lexeme, Object literal, String file, int line) {
    this.type = type;
    this.lexeme = lexeme;
    this.literal = literal;
    this.file = file;
    this.line = line;
  }

  public String toString() {
    return type + " " + lexeme + " " + literal;
  }

  public void setType(TokenType type, String lexeme) {
      this.type = type;
      this.lexeme = lexeme;
  }

}
