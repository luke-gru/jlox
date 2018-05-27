package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Iterator;

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

    public Object objectId() {
        return (double)System.identityHashCode(this);
    }

    // Property access, 'instance.prop'. 'prop' here can be a regular
    // property, a getter method, in which case it's called, or a regular
    // method, in which case it's returned, uncalled but bound to the
    // instance.
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

    public LoxInstance dup() {
        // FIXME: instance should go through initialization function
        // (Interpreter#createInstance)
        LoxInstance newInstance = new LoxInstance(this.klass, this.klassName);
        Iterator iter = properties.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry pair = (Map.Entry)iter.next();
            newInstance.setProperty((String)pair.getKey(), (Object)Runtime.dupObject(pair.getValue()), null, null);
        }
        Iterator iter2 = hiddenProps.entrySet().iterator();
        while (iter2.hasNext()) {
            Map.Entry pair = (Map.Entry)iter2.next();
            newInstance.setHiddenProp((String)pair.getKey(), Runtime.dupObject(pair.getValue()));
        }
        if (isFrozen) {
            newInstance.freeze();
        }
        return newInstance;
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
        return getMethod(name, klassBeginSearch, interp);
    }

    public Object getMethod(String name, LoxClass klassBeginSearch, Interpreter interp) {
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
