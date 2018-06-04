package com.craftinginterpreters.lox;

enum TokenType {
  // Single-character tokens.
  LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
  COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR,

  // One or two character tokens.
  BANG, BANG_EQUAL,
  EQUAL, EQUAL_EQUAL,
  GREATER, GREATER_EQUAL,
  LESS, LESS_EQUAL,

  // +=, -=, *=, /=
  PLUS_EQUAL, MINUS_EQUAL, STAR_EQUAL, SLASH_EQUAL,

  // Literals.
  IDENTIFIER, SQ_STRING, DQ_STRING, ST_STRING, NUMBER,

  // Keywords.
  AND, CLASS, MODULE, ELSE, FALSE, FUN, FOR, FOREACH, IN, IF, NIL, OR,
  PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE, BREAK,
  CONTINUE, TRY, CATCH, THROW, LEFT_BRACKET, RIGHT_BRACKET,

  END_SCRIPT,

  EOF
}
