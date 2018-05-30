package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Iterator;

class LoxInstance {
    public LoxClass klass;
    public LoxClass singletonKlass;
    public String klassName;
    private final Map<String, Object> properties = new HashMap<>();
    // Used internally, Objects are Java-land objects
    private final Map<String, Object> hiddenProps = new HashMap<>();
    public boolean isFrozen = false;
    public static int numInstances = 0;

    LoxInstance(LoxClass klass, String className) {
        this.klass = klass;
        this.klassName = className;
        this.singletonKlass = null;
        numInstances++;
    }

    @Override
    public String toString() {
        return "<instance " + klassName + " #" + objectId() + ">";
    }

    public Object objectId() {
        return (double)System.identityHashCode(this);
    }

    // Property access, 'instance.prop'. 'prop' here can be a regular
    // property, a getter method, in which case it's called, or a regular
    // method, in which case it's returned, uncalled but bound to the
    // instance. The check is done in that order.
    public Object getProperty(String name, Interpreter interp) {
        if (properties.containsKey(name)) {
            return properties.get(name);
        }
        if (singletonKlass != null) {
            Object singletonProp = getProperty(name, interp, singletonKlass);
            if (singletonProp != null) {
                return singletonProp;
            }
        }
        return getProperty(name, interp, getKlass());
    }

    public Object getProperty(String name, Interpreter interp, LoxClass lookupKlass) {
        if (properties.containsKey(name)) {
            return properties.get(name);
        }
        LoxClass klass = lookupKlass;
        while (klass != null) {
            if (klass.getters.containsKey(name)) {
                LoxCallable getter = klass.getters.get(name);
                List<Object> objs = new ArrayList<>();
                return getter.bind(this, interp.environment).call(interp, objs, null);
            }
            klass = klass.getSuper();
        }
        // `boundMethod` looks in super classes as well
        LoxCallable method = lookupKlass.boundMethod(this, interp.environment, name);
        if (method != null) return method;
        return null;
    }

    public LoxInstance dup(Interpreter interp) {
        LoxInstance newInstance = new LoxInstance(getKlass(), this.klassName);
        Iterator iter = properties.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry pair = (Map.Entry)iter.next();
            newInstance.setProperty((String)pair.getKey(), Runtime.dupObject(pair.getValue(), interp), null, null);
        }
        Iterator iter2 = hiddenProps.entrySet().iterator();
        while (iter2.hasNext()) {
            Map.Entry pair = (Map.Entry)iter2.next();
            newInstance.setHiddenProp((String)pair.getKey(), Runtime.dupObject(pair.getValue(), interp));
        }
        if (isFrozen) {
            newInstance.freeze();
        }
        Object initDup = newInstance.getMethod("initDup", newInstance.getKlass(), interp);
        if (initDup != null) {
            LoxCallable initDupMeth = (LoxCallable)initDup;
            List<Object> args = new ArrayList<>();
            args.add(this);
            interp.evaluateCall(initDupMeth, args, null);
        }
        return newInstance;
    }

    // Tries to set property on object, using setter function if given.
    public void setProperty(String name, Object value, Interpreter interp, LoxCallable setterFunc) {
        if (isFrozen) {
            interp.throwLoxError("FrozenObjectError", "object is frozen");
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
        return this.klass;
    }

    public LoxClass getSingletonKlass() {
        if (this.singletonKlass == null) {
            this.singletonKlass = new LoxClass(klassName + " (meta)", null, new HashMap<String, LoxCallable>());
            this.singletonKlass.isSingletonKlass = true;
        }
        return this.singletonKlass;
    }

    // looks up getter prop first, then tries method if none found. If getter
    // prop found, calls it.
    public Object getMethodOrGetterProp(String name, LoxClass klassBeginSearch, Interpreter interp) {
        if (singletonKlass != null) {
            Object ret = getMethodOrGetterProp(name, singletonKlass, interp);
            if (ret != null) {
                return ret;
            }
        }
        LoxClass klass = klassBeginSearch;
        while (klass != null) {
            if (klass.getters.containsKey(name)) {
                LoxCallable getter = klass.getters.get(name);
                List<Object> args = LoxUtil.EMPTY_ARGS;
                return getter.bind(this, interp.environment).call(interp, args, null);
            }
            klass = klass.getSuper();
        }
        return getMethod(name, klassBeginSearch, interp);
    }

    // lookup method: checks given class hierarchy
    public LoxCallable getMethod(String name, LoxClass klassBeginSearch, Interpreter interp) {
        LoxCallable method = klassBeginSearch.boundMethod(this, interp.environment, name);
        if (method != null) return method;
        return null;
    }

    // lookup method: checks singleton class, and then regular class hierarchy
    public LoxCallable getMethod(String name, Interpreter interp) {
        LoxCallable method = null;
        if (singletonKlass != null) {
            method = singletonKlass.boundMethod(this, interp.environment, name);
        }
        if (method != null) return method;
        method = getKlass().boundMethod(this, interp.environment, name);
        if (method != null) return method;
        return null;
    }

    // is an instance of given class, checking superclass hierarchy of the
    // instance
    public boolean isA(LoxClass testKlass) {
        LoxClass klass = this.getKlass();
        while (klass != null) {
            if (klass == testKlass) {
                return true;
            }
            klass = klass.getSuper();
        }
        return false;
    }

    // is an exact instance of given class, without checking superclass hierarchy
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

    public void finalize() {
        numInstances--;
    }

}
