package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LoxNativeClass extends LoxClass implements LoxCallable {

    LoxNativeClass(String name, LoxClass superClass) {
        super(name, superClass, new HashMap<>());
    }

    public void defineMethod(String name, LoxNativeCallable callable) {
        methods.put(name, callable);
    }

    public void defineGetter(String name, LoxNativeCallable callable) {
        getters.put(name, callable);
    }

    public void defineSetter(String name, LoxNativeCallable callable) {
        setters.put(name, callable);
    }

}
