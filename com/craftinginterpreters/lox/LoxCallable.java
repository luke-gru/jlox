package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;

interface LoxCallable {
    public Object call(Interpreter interp, List<Object> args, Map<String,Object> kwargs, Token callToken);
    public int arityMin();
    public int arityMax();
    public String getName(); // ex: "typeof"
    public String toString(); // ex: "<fn typeof>"
    public LoxModule getModuleDefinedIn(); // returns module or class or null
    public void setModuleDefinedIn(LoxModule modOrClass);
    public LoxCallable bind(LoxInstance instance, Environment env);
    public Stmt.Function getDecl(); // NOTE: can be null, like for native (builtin) functions
}
