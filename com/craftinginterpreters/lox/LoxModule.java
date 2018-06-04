package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;

class LoxModule extends LoxInstance {
    public String name;
    public Map<String, LoxCallable> methods;
    public Map<String, LoxCallable> getters = new HashMap<>();
    public Map<String, LoxCallable> setters = new HashMap<>();
    public List<LoxModule> includedModules = new ArrayList<>();

    LoxModule(LoxClass klass, String klassName, String name, Map<String, LoxCallable> methods) {
        super(klass, klassName);
        this.name = name;
        this.methods = methods;
    }

    @Override
    public String toString() {
        return "<module " + getName() + ">";
    }

    public String getName() {
        if (name == null) {
            return "(Anon)";
        } else {
            return name;
        }
    }

    // Add a new class in this given class's class hierarchy, right above the given class.
    // A new LoxClass is created with the name of this module.
    public void includeIn(LoxModule modOrClass) {
        if (modOrClass instanceof LoxClass) {
            LoxClass klass = (LoxClass)modOrClass;
            LoxClass klassSuperOrig = klass.getSuper();
            klass.superClass = new LoxClass(getName(), klassSuperOrig, methods);
            klass.superClass.getters = getters;
            klass.superClass.setters = setters;
            klass.module = this;
        } else {
            modOrClass.includedModules.add(this);
        }
    }
}
