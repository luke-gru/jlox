package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

class LoxInstance {
    public LoxClass klass;
    public String klassName;
    private final Map<String, Object> properties = new HashMap<>();

    LoxInstance(LoxClass klass, String className) {
        this.klass = klass;
        this.klassName = className;
    }

    @Override
    public String toString() {
        return "<instance " + klassName + " #" + hashCode() + ">";
    }

    public Object getProperty(String name, Interpreter interp) {
        LoxClass klass = getKlass();
        while (klass != null) {
            if (klass.getters.containsKey(name)) {
                LoxFunction getter = klass.getters.get(name);
                List<Object> objs = new ArrayList<>();
                return getter.bind(this).call(interp, objs);
            }
            klass = klass.getSuper();
        }
        if (properties.containsKey(name)) {
            return properties.get(name);
        } else {
            LoxFunction method = getKlass().boundMethod(this, name);
            if (method != null) return method;
            return null;
        }
    }

    public void setProperty(String name, Object value, Interpreter interp) {
        LoxClass curClass = getKlass();
        if (curClass.setters.containsKey(name)) {
            LoxFunction setter = curClass.setters.get(name);
            List<Object> objs = new ArrayList<>();
            objs.add(value);
            setter.bind(this).call(interp, objs);
            return;
        }
        properties.put(name, value);
    }

    public LoxClass getKlass() {
        if (klass == null) {
            klass = new LoxClass(klassName, null, new HashMap<String, LoxFunction>());
        }
        return klass;
    }

    public Object getMethodOrGetterProp(String name, LoxClass klassBeginSearch, Interpreter interp) {
        LoxClass klass = klassBeginSearch;
        while (klass != null) {
            if (klass.getters.containsKey(name)) {
                LoxFunction getter = klass.getters.get(name);
                List<Object> args = new ArrayList<>();
                return getter.bind(this).call(interp, args);
            }
            klass = klass.getSuper();
        }
        LoxFunction method = klassBeginSearch.boundMethod(this, name);
        if (method != null) return method;
        return null;
    }

    public boolean isA(LoxClass testKlass) {
        LoxClass klass = this.klass;
        while (klass != null) {
            if (klass == testKlass) {
                return true;
            }
            klass = klass.getSuper();
        }
        return false;
    }

    public boolean isInstance(LoxClass testKlass) {
        return this.klass == testKlass;
    }
}
