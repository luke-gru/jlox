package com.craftinginterpreters.lox;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
//import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

class LoxUtil {
    static String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, StandardCharsets.UTF_8);
    }

}
