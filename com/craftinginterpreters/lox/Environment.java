package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class Environment {
  private final Map<String, Object> values = new HashMap<>();
  private final Map<String, Stmt.Function> functions = new HashMap<>();
  final Environment enclosing;

  Environment() {
      enclosing = null;
  }

  Environment(Environment enclosing) {
      this.enclosing = enclosing;
  }

  public void define(String name, Object value) {
      values.put(name, value);
  }

  // assign an already defined name
  public void assign(Token name, Object value, boolean assignOuter) {
      if (values.containsKey(name.lexeme)) {
          values.put(name.lexeme, value);
          return;
      }

      if (enclosing != null && assignOuter) {
          enclosing.assign(name, value, true);
          return;
      }

      throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "' (assignment).");
  }

  public void defineFunction(Token name, Stmt.Function fnStmt) {
      if (functions.containsKey(name.lexeme)) {
          throw new RuntimeError(name, "Cannot redefine function '" + name.lexeme + "'.");
      }
      functions.put(name.lexeme, fnStmt);
  }

  public Stmt.Function getFunction(String name) {
      if (functions.containsKey(name)) {
          return functions.get(name);
      }

      if (enclosing != null) {
          return enclosing.getFunction(name);
      }

      return null;
  }

  public Object get(Token name, boolean checkEnclosing) {
      if (values.containsKey(name.lexeme)) {
          return values.get(name.lexeme);
      }

      if (enclosing != null && checkEnclosing) {
          return enclosing.get(name, true);
      }

      throw new RuntimeError(name,
              "Undefined variable '" + name.lexeme + "'.");
  }

  public Object get(String name, boolean checkEnclosing, Token errTok) {
      if (values.containsKey(name)) {
          return values.get(name);
      }

      if (enclosing != null && checkEnclosing) {
          return enclosing.get(name, true, errTok);
      }

      throw new RuntimeError(errTok,
              "Undefined variable '" + name + "'.");
  }

  public Object getAt(int distance, Token name) {
      Environment env = this;
      while (distance > 0) {
          env = env.enclosing;
          distance--;
      }
      return env.get(name, false);
  }

  public Object getAt(int distance, String name) {
      Environment env = this;
      while (distance > 0) {
          env = env.enclosing;
          distance--;
      }
      return values.get(name);
  }

  public LoxInstance getThis() {
      Object instance = getAt(0, "this");
      if (instance == null) {
          return null;
      }
      if (instance instanceof LoxInstance) {
          return (LoxInstance)instance;
      } else {
          return null;
      }
  }

  public LoxClass getThisClass() {
      Object instance = getAt(0, "this");
      if (instance == null) { return null; }
      if (instance instanceof LoxClass) {
          return (LoxClass)instance;
      } else {
          return null;
      }
  }

  public void assignAt(int distance, Token name, Object value) {
      Environment env = this;
      while (distance > 0) {
          env = env.enclosing;
          distance--;
      }
      env.assign(name, value, false);
  }

  public Object getGlobal(String name) {
      Environment env = this;
      while (env.enclosing != null) {
          env = env.enclosing;
      }
      return env.getAt(0, name);
  }

}
