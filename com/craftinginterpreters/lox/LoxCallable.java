package com.craftinginterpreters.lox;

import java.util.List;

interface LoxCallable {
  public Object call(Interpreter interpreter, List<Object> arguments, Token callToken);
  public int arity();
  public String getName();
  public String toString();
  public Stmt.Function getDecl();
}
