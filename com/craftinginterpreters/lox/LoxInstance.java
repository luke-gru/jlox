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
    private final Map<String, Object> hiddenProps = new HashMap<>();
    public boolean isFrozen = false;

    LoxInstance(LoxClass klass, String className) {
        this.klass = klass;
        this.klassName = className;
        this.singletonKlass = null;
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
    //
    // XXX: Stopped here. make getProperty get methods and getters (maybe
    // properties, too?) from singletonClass, if there is a singleton class
    // for this instance.
    public Object getProperty(String name, Interpreter interp) {
        if (singletonKlass != null) {
            Object singletonProp = getProperty(name, interp, singletonKlass);
            if (singletonProp != null) {
                return singletonProp;
            }
        }
        return getProperty(name, interp, getKlass());
        //while (klass != null) {
            //if (klass.getters.containsKey(name)) {
                //LoxCallable getter = klass.getters.get(name);
                //List<Object> objs = new ArrayList<>();
                //return getter.bind(this, interp.environment).call(interp, objs, null);
            //}
            //klass = klass.getSuper();
        //}
        //if (properties.containsKey(name)) {
            //return properties.get(name);
        //} else {
            //LoxCallable method = getKlass().boundMethod(this, interp.environment, name);
            //if (method != null) return method;
            //return null;
        //}
    }

    public Object getProperty(String name, Interpreter interp, LoxClass lookupKlass) {
        LoxClass klass = lookupKlass;
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
            // `boundMethod` looks in super classes as well
            LoxCallable method = lookupKlass.boundMethod(this, interp.environment, name);
            if (method != null) return method;
            return null;
        }
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
        return this.klass;
    }

    public LoxClass getSingletonKlass() {
        if (this.singletonKlass == null) {
            this.singletonKlass = new LoxClass(klassName + " (meta)", null, new HashMap<String, LoxCallable>());
            this.singletonKlass.isSingletonKlass = true;
        }
        return this.singletonKlass;
    }

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
        LoxClass klass = this.getKlass();
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
