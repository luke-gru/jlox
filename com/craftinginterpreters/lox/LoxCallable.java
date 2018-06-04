package com.craftinginterpreters.lox;

import java.util.List;

interface LoxCallable {
  public Object call(Interpreter interpreter, List<Object> arguments, Token callToken);
  public int arityMin();
  public int arityMax();
  public String getName(); // ex: "typeof"
  public String toString(); // ex: "<fn typeof>"
  public LoxModule getModuleDefinedIn(); // returns module or class or null
  public void setModuleDefinedIn(LoxModule modOrClass);
  public LoxCallable bind(LoxInstance instance, Environment env);
  public Stmt.Function getDecl(); // NOTE: can be null, like for native (builtin) functions
}
