package com.craftinginterpreters.lox;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

class LoxUtil {
    static List<Object> EMPTY_ARGS = Collections.unmodifiableList(new ArrayList<>());

    static String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, StandardCharsets.UTF_8);
    }

    // returns absolute file path for given class's ".class" file
    static String classPath(Class klass) {
        File f = new File(klass.getProtectionDomain().getCodeSource().
            getLocation().getPath());
        try {
            return f.getCanonicalPath();
        } catch (IOException err) {
            System.err.println("IOException: " + err.getMessage());
            return "";
        }
    }

    // throws Java error if expr != true
    static void Assert(boolean expr) throws java.lang.AssertionError {
        if (!expr) {
            throw new java.lang.AssertionError(expr);
        }
    }

    // throws Java error if expr != true
    static void Assert(boolean expr, String msg) throws java.lang.AssertionError {
        if (!expr) {
            throw new java.lang.AssertionError(msg);
        }
    }

    // throws lox Error if obj != lox String
    static void checkString(Object obj, Interpreter interp, String errClass, String errMsg, int argNo) {
        checkIsA("String", obj, interp, errClass, errMsg, argNo);
    }

    // throws lox Error if obj != lox class given as argument 1
    static void checkIsA(String loxClassName, Object obj, Interpreter interp, String errClass, String errMsg, int argNo) {
        String typeStr = interp.nativeTypeof(null, obj);
        String objClassName = typeStr;
        if (typeStr.equals("nil") && loxClassName.equals("nil")) {
            return;
        }
        if (typeStr.equals("boolean") && loxClassName.equals("boolean")) {
            return;
        }
        if (typeStr.equals("number") && loxClassName.equals("number")) {
            return;
        }
        if (typeStr.equals("function") && loxClassName.equals("function")) {
            return;
        }
        if (typeStr.equals("class") && (loxClassName.equals("Class") || loxClassName.equals("class"))) {
            return;
        }
        if (typeStr.equals("string") && (loxClassName.equals("String") || loxClassName.equals("string"))) {
            return;
        }
        if (typeStr.equals("array") && (loxClassName.equals("Array") || loxClassName.equals("array"))) {
            return;
        }
        if (typeStr.equals("instance")) {
            LoxInstance instance = Runtime.toInstance(obj);
            objClassName = instance.getKlass().getName();
            LoxClass klass = instance.getKlass();
            while (klass != null) {
                if (klass.getName().equals(loxClassName)) {
                    return;
                }
                klass = klass.superClass;
            }
        }
        // Error time!
        if (errClass == null) {
            errClass = "ArgumentError";
        }
        if (errMsg == null && errClass.equals("ArgumentError")) {
            errMsg = "Expected argument " + String.valueOf(argNo) +
                " to be a " + loxClassName + ", got: " + objClassName;
        }
        interp.throwLoxError(errClass, errMsg);
    }

}
