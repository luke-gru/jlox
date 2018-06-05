package com.craftinginterpreters.lox;

import java.util.Map;
import java.util.HashMap;

class LoxNativeModule extends LoxModule {

    LoxNativeModule(String name) {
        super(Runtime.getClass("Module"), "Module", name, new HashMap<String,LoxCallable>());
    }

    public void defineMethod(LoxNativeCallable callable) {
        methods.put(callable.getName(), callable);
        callable.setModuleDefinedIn(this);
    }

    public void defineSingletonMethod(LoxNativeCallable callable) {
        getSingletonKlass().methods.put(callable.getName(), callable);
        callable.setModuleDefinedIn(getSingletonKlass());
    }

    public void defineGetter(LoxNativeCallable callable) {
        if (callable.arityMin() != 0 || callable.arityMax() != 0) {
            throw new RuntimeException("defineGetter() callable must have arity of exactly 0: " + name + "#" + callable.getName());
        }
        getters.put(callable.getName(), callable);
        callable.setModuleDefinedIn(this);
    }

    public void defineSetter(LoxNativeCallable callable) {
        if (callable.arityMin() != 1 || callable.arityMax() != 1) {
            throw new RuntimeException("defineSetter() callable must have arity of exactly 1: " + name + "#" + callable.getName());
        }
        setters.put(callable.getName(), callable);
        callable.setModuleDefinedIn(this);
    }

}
