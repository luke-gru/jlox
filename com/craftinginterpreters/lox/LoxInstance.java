package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

class LoxInstance {
    public LoxClass klass;
    public String klassName;
    private final Map<String, Object> properties = new HashMap<>();
    private final Map<String, Object> hiddenProps = new HashMap<>();
    public boolean isFrozen = false;

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
                LoxCallable getter = klass.getters.get(name);
                List<Object> objs = new ArrayList<>();
                return getter.bind(this, interp.environment).call(interp, objs, null);
            }
            klass = klass.getSuper();
        }
        if (properties.containsKey(name)) {
            return properties.get(name);
        } else {
            LoxCallable method = getKlass().boundMethod(this, interp.environment, name);
            if (method != null) return method;
            return null;
        }
    }

    public void setProperty(String name, Object value, Interpreter interp, LoxCallable setterFunc) {
        if (isFrozen) {
            throw new RuntimeException("object is frozen");
        }
        if (setterFunc != null) {
            List<Object> objs = new ArrayList<>();
            objs.add(value);
            setterFunc.bind(this, interp.environment).call(interp, objs, null);
            return;
        }
        properties.put(name, value);
    }

    public LoxClass getKlass() {
        if (klass == null) {
            klass = new LoxClass(klassName, null, new HashMap<String, LoxCallable>());
        }
        return klass;
    }

    public Object getMethodOrGetterProp(String name, LoxClass klassBeginSearch, Interpreter interp) {
        LoxClass klass = klassBeginSearch;
        while (klass != null) {
            if (klass.getters.containsKey(name)) {
                LoxCallable getter = klass.getters.get(name);
                List<Object> args = new ArrayList<>();
                return getter.bind(this, interp.environment).call(interp, args, null);
            }
            klass = klass.getSuper();
        }
        LoxCallable method = klassBeginSearch.boundMethod(this, interp.environment, name);
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

    public void freeze() {
        this.isFrozen = true;
    }

    public Object getHiddenProp(String name) {
        if (this.hiddenProps.containsKey(name)) {
            return this.hiddenProps.get(name);
        } else {
            return null;
        }
    }

    public void setHiddenProp(String name, Object val) {
        this.hiddenProps.put(name, val);
    }
}
