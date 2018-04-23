package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LoxNativeClass extends LoxClass implements LoxCallable {

    LoxNativeClass(String name, LoxClass superClass) {
        super(name, superClass, new HashMap<>());
    }

    public void defineMethod(LoxNativeCallable callable) {
        methods.put(callable.getName(), callable);
    }

    public void defineGetter(LoxNativeCallable callable) {
        if (callable.arity() != 0) {
            throw new RuntimeException("defineGetter() callable must have arity of 0: " + name + "#" + callable.getName());
        }
        getters.put(callable.getName(), callable);
    }

    public void defineSetter(LoxNativeCallable callable) {
        if (callable.arity() != 1) {
            throw new RuntimeException("defineSetter() callable must have arity of 1: " + name + "#" + callable.getName());
        }
        setters.put(callable.getName(), callable);
    }

}
