package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
    private static final Interpreter interpreter = new Interpreter();

    static boolean hadError = false;
    static boolean hadRuntimeError = false;

    public static void main(String[] args) throws IOException {
        String fname = null;
        int i = 0;

        while (i < args.length) {
            if (args[i].equals("-f")) {
                fname = args[i+1];
                i = i+2;
            } else {
                System.err.println("Usage: Lox [-f FILENAME]");
                System.exit(1);
            }
        }

        if (fname == null) {
            runPrompt();
        } else {
            runFile(fname);
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));
        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for (;;) {
            System.out.print("> ");
            run(reader.readLine());
            hadError = false;
        }
    }

    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();
        if (hadError) {
            return;
        }
        Resolver resolver = new Resolver(interpreter);
        resolver.resolve(statements);
        if (hadError) {
            return;
        }
        interpreter.interpret(statements);
    }

    static void error(Token tok, String message) {
        if (tok.type == TokenType.EOF) {
            report(tok.line, " at end", message);
        } else {
            report(tok.line, " at '" + tok.lexeme + "'", message);
        }
    }

    static void error(int line, String message) {
        System.err.println("[line " + line + "] Error: " + message);
        hadError = true;
    }

    static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() +
                "\n[line " + error.token.line + "]");
        hadRuntimeError = true;
    }

    private static void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }
}
