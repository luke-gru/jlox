package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

class LoxArray {
    final List<Object> elements;

    LoxArray(List<Object> elements) {
        this.elements = elements;
    }

    @Override
    public String toString() {
        return "<array #" + hashCode() + ">";
    }

    public Object get(int n, Token tok) {
        checkAccess(n, "get", tok);
        return elements.get(n);
    }

    public void set(int n, Object obj, Token tok) {
        checkAccess(n, "set", tok);
        elements.set(n, obj);
    }

    public Object length() {
        return (double)elements.size();
    }

    private void checkAccess(int n, String accessType, Token tok) {
        if (elements.size() < (n+1)) {
            throw new RuntimeError(tok, "Array out of bounds exception for array access: " + accessType);
        }
    }
}
