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

    static void Assert(boolean expr) throws java.lang.AssertionError {
        if (!expr) {
            throw new java.lang.AssertionError(expr);
        }
    }

    static void Assert(boolean expr, String msg) throws java.lang.AssertionError {
        if (!expr) {
            throw new java.lang.AssertionError(msg);
        }
    }

}
